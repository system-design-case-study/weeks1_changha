#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Load generated dummy data into PostgreSQL.

Usage:
  scripts/load_dummy_data.sh --data-dir <dir> [options]

Options:
  --data-dir <dir>      Directory containing businesses.csv and geohash_index.csv (required)
  --db-url <url>        PostgreSQL connection URL (optional, else use PG* env vars)
  --schema <name>       Target schema (default: public)
  --create-schema       Create schema/tables/indexes if missing
  --truncate            Truncate business/geohash_index before load
  -h, --help            Show this help

Examples:
  scripts/load_dummy_data.sh --data-dir data/dummy --db-url postgres://user:pass@localhost:5432/weeks1
  scripts/load_dummy_data.sh --data-dir data/dummy --schema proximity --create-schema --truncate
EOF
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[error] required command not found: $cmd" >&2
    exit 1
  fi
}

DATA_DIR=""
DB_URL="${DB_URL:-}"
SCHEMA="public"
CREATE_SCHEMA=false
TRUNCATE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --data-dir)
      DATA_DIR="${2:-}"
      shift 2
      ;;
    --db-url)
      DB_URL="${2:-}"
      shift 2
      ;;
    --schema)
      SCHEMA="${2:-}"
      shift 2
      ;;
    --create-schema)
      CREATE_SCHEMA=true
      shift
      ;;
    --truncate)
      TRUNCATE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[error] unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$DATA_DIR" ]]; then
  echo "[error] --data-dir is required" >&2
  usage
  exit 1
fi

if [[ ! "$SCHEMA" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
  echo "[error] invalid schema name: $SCHEMA" >&2
  exit 1
fi

BUSINESSES_CSV="$DATA_DIR/businesses.csv"
GEOHASH_INDEX_CSV="$DATA_DIR/geohash_index.csv"

if [[ ! -f "$BUSINESSES_CSV" ]]; then
  echo "[error] file not found: $BUSINESSES_CSV" >&2
  exit 1
fi
if [[ ! -f "$GEOHASH_INDEX_CSV" ]]; then
  echo "[error] file not found: $GEOHASH_INDEX_CSV" >&2
  exit 1
fi

require_cmd psql

PSQL=(psql -v ON_ERROR_STOP=1)
if [[ -n "$DB_URL" ]]; then
  PSQL=(psql "$DB_URL" -v ON_ERROR_STOP=1)
fi

echo "[info] loading dummy data"
echo "[info] schema=$SCHEMA"
echo "[info] businesses_csv=$BUSINESSES_CSV"
echo "[info] geohash_index_csv=$GEOHASH_INDEX_CSV"
echo "[info] create_schema=$CREATE_SCHEMA truncate=$TRUNCATE"

if [[ "$CREATE_SCHEMA" == true ]]; then
  "${PSQL[@]}" <<SQL
CREATE SCHEMA IF NOT EXISTS ${SCHEMA};

CREATE TABLE IF NOT EXISTS ${SCHEMA}.business (
  id BIGINT PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  phone VARCHAR(32),
  address VARCHAR(255) NOT NULL,
  latitude DECIMAL(9,6) NOT NULL,
  longitude DECIMAL(9,6) NOT NULL,
  geohash VARCHAR(12) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_business_owner_id ON ${SCHEMA}.business (owner_id);
CREATE INDEX IF NOT EXISTS idx_business_updated_at ON ${SCHEMA}.business (updated_at);

CREATE TABLE IF NOT EXISTS ${SCHEMA}.geohash_index (
  geohash VARCHAR(12) NOT NULL,
  business_id BIGINT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (geohash, business_id)
);

CREATE INDEX IF NOT EXISTS idx_geohash_index_business_id ON ${SCHEMA}.geohash_index (business_id);

CREATE TABLE IF NOT EXISTS ${SCHEMA}.business_change_log (
  id BIGSERIAL PRIMARY KEY,
  business_id BIGINT NOT NULL,
  op_type VARCHAR(16) NOT NULL,
  payload_json JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_change_log_processed_at ON ${SCHEMA}.business_change_log (processed_at, created_at);
SQL
fi

if [[ "$TRUNCATE" == true ]]; then
  "${PSQL[@]}" <<SQL
TRUNCATE TABLE ${SCHEMA}.geohash_index, ${SCHEMA}.business RESTART IDENTITY;
SQL
fi

"${PSQL[@]}" \
  --set=businesses_csv="$BUSINESSES_CSV" \
  --set=geohash_index_csv="$GEOHASH_INDEX_CSV" <<SQL
BEGIN;

CREATE TEMP TABLE staging_business (
  id BIGINT,
  owner_id BIGINT,
  name VARCHAR(120),
  category VARCHAR(40),
  phone VARCHAR(32),
  address VARCHAR(255),
  latitude DECIMAL(9,6),
  longitude DECIMAL(9,6),
  geohash VARCHAR(12),
  status VARCHAR(16),
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
) ON COMMIT DROP;

\copy staging_business (id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at) FROM :'businesses_csv' WITH (FORMAT csv, HEADER true)

INSERT INTO ${SCHEMA}.business (
  id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at
)
SELECT
  id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at
FROM staging_business
ON CONFLICT (id) DO UPDATE SET
  owner_id = EXCLUDED.owner_id,
  name = EXCLUDED.name,
  category = EXCLUDED.category,
  phone = EXCLUDED.phone,
  address = EXCLUDED.address,
  latitude = EXCLUDED.latitude,
  longitude = EXCLUDED.longitude,
  geohash = EXCLUDED.geohash,
  status = EXCLUDED.status,
  updated_at = EXCLUDED.updated_at;

CREATE TEMP TABLE staging_geohash_index (
  geohash VARCHAR(12),
  business_id BIGINT
) ON COMMIT DROP;

\copy staging_geohash_index (geohash, business_id) FROM :'geohash_index_csv' WITH (FORMAT csv, HEADER true)

INSERT INTO ${SCHEMA}.geohash_index (geohash, business_id)
SELECT geohash, business_id
FROM staging_geohash_index
ON CONFLICT (geohash, business_id) DO NOTHING;

COMMIT;
SQL

"${PSQL[@]}" <<SQL
SELECT 'business_count' AS metric, COUNT(*) AS value FROM ${SCHEMA}.business
UNION ALL
SELECT 'geohash_index_count' AS metric, COUNT(*) AS value FROM ${SCHEMA}.geohash_index
ORDER BY metric;
SQL

echo "[done] dummy data loaded successfully"
