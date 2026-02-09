#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Load generated dummy CSV data into MySQL (geohash schema).

Usage:
  scripts/load_dummy_data_mysql.sh --data-dir <dir> [options]

Options:
  --data-dir <dir>      Directory containing businesses.csv and geohash_index.csv (required)
  --host <host>         MySQL host (default: 127.0.0.1)
  --port <port>         MySQL port (default: 3311)
  --database <name>     Database name (default: weeks1)
  --user <user>         MySQL user (default: weeks1)
  --password <pass>     MySQL password (default: env MYSQL_PASSWORD or 'weeks1')
  --create-schema       Create schema/tables/indexes from db/mysql/schema.sql
  --truncate            Truncate business/geohash_index/change_log before load
  -h, --help            Show this help

Examples:
  scripts/load_dummy_data_mysql.sh --data-dir data/dummy --create-schema --truncate
  scripts/load_dummy_data_mysql.sh --data-dir data/dummy --host 127.0.0.1 --port 3311 --database weeks1
EOF
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[error] required command not found: $cmd" >&2
    exit 1
  fi
}

escape_sql_string() {
  printf "%s" "$1" | sed "s/\\\\/\\\\\\\\/g; s/'/''/g"
}

DATA_DIR=""
HOST="127.0.0.1"
PORT="3311"
DATABASE="weeks1"
USER="weeks1"
PASSWORD="${MYSQL_PASSWORD:-weeks1}"
CREATE_SCHEMA=false
TRUNCATE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --data-dir)
      DATA_DIR="${2:-}"
      shift 2
      ;;
    --host)
      HOST="${2:-}"
      shift 2
      ;;
    --port)
      PORT="${2:-}"
      shift 2
      ;;
    --database)
      DATABASE="${2:-}"
      shift 2
      ;;
    --user)
      USER="${2:-}"
      shift 2
      ;;
    --password)
      PASSWORD="${2:-}"
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

if [[ ! "$PORT" =~ ^[0-9]+$ ]]; then
  echo "[error] invalid port: $PORT" >&2
  exit 1
fi

if [[ ! "$DATABASE" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
  echo "[error] invalid database name: $DATABASE" >&2
  exit 1
fi

BUSINESSES_CSV="$DATA_DIR/businesses.csv"
GEOHASH_INDEX_CSV="$DATA_DIR/geohash_index.csv"
SCHEMA_SQL="src/main/resources/db/mysql/schema.sql"

if [[ ! -f "$BUSINESSES_CSV" ]]; then
  echo "[error] file not found: $BUSINESSES_CSV" >&2
  exit 1
fi
if [[ ! -f "$GEOHASH_INDEX_CSV" ]]; then
  echo "[error] file not found: $GEOHASH_INDEX_CSV" >&2
  exit 1
fi
if [[ "$CREATE_SCHEMA" == true && ! -f "$SCHEMA_SQL" ]]; then
  echo "[error] schema file not found: $SCHEMA_SQL" >&2
  exit 1
fi

require_cmd mysql

BUSINESSES_CSV_ABS="$(cd "$(dirname "$BUSINESSES_CSV")" && pwd)/$(basename "$BUSINESSES_CSV")"
GEOHASH_INDEX_CSV_ABS="$(cd "$(dirname "$GEOHASH_INDEX_CSV")" && pwd)/$(basename "$GEOHASH_INDEX_CSV")"
BUSINESSES_CSV_SQL="$(escape_sql_string "$BUSINESSES_CSV_ABS")"
GEOHASH_INDEX_CSV_SQL="$(escape_sql_string "$GEOHASH_INDEX_CSV_ABS")"

MYSQL_BASE=(mysql --local-infile=1 --default-character-set=utf8mb4 -h "$HOST" -P "$PORT" -u "$USER")
if [[ -n "$PASSWORD" ]]; then
  MYSQL_BASE+=("-p$PASSWORD")
fi
MYSQL_DB=("${MYSQL_BASE[@]}" "$DATABASE")

echo "[info] loading dummy data into MySQL"
echo "[info] host=$HOST port=$PORT database=$DATABASE user=$USER"
echo "[info] businesses_csv=$BUSINESSES_CSV_ABS"
echo "[info] geohash_index_csv=$GEOHASH_INDEX_CSV_ABS"
echo "[info] create_schema=$CREATE_SCHEMA truncate=$TRUNCATE"

"${MYSQL_BASE[@]}" -e "CREATE DATABASE IF NOT EXISTS \`$DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

if [[ "$CREATE_SCHEMA" == true ]]; then
  "${MYSQL_DB[@]}" < "$SCHEMA_SQL"
fi

if [[ "$TRUNCATE" == true ]]; then
  "${MYSQL_DB[@]}" <<'SQL'
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE geohash_index;
TRUNCATE TABLE change_log;
TRUNCATE TABLE business;
TRUNCATE TABLE business_id_sequence;
SET FOREIGN_KEY_CHECKS=1;
SQL
fi

"${MYSQL_DB[@]}" <<SQL
SET SESSION sql_mode = 'STRICT_ALL_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';
SET @businesses_csv = '${BUSINESSES_CSV_SQL}';
SET @geohash_index_csv = '${GEOHASH_INDEX_CSV_SQL}';

START TRANSACTION;

CREATE TEMPORARY TABLE staging_business (
  id BIGINT,
  owner_id BIGINT,
  name VARCHAR(120),
  category VARCHAR(40),
  phone VARCHAR(32),
  address VARCHAR(255),
  latitude DECIMAL(9, 6),
  longitude DECIMAL(9, 6),
  geohash VARCHAR(12),
  status VARCHAR(16),
  created_at_raw VARCHAR(64),
  updated_at_raw VARCHAR(64)
);

SET @sql_business_load = CONCAT(
  "LOAD DATA LOCAL INFILE '", @businesses_csv, "' INTO TABLE staging_business ",
  "FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' ESCAPED BY '\"' ",
  "LINES TERMINATED BY '\\n' IGNORE 1 LINES ",
  "(id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at_raw, updated_at_raw)"
);
PREPARE stmt_business_load FROM @sql_business_load;
EXECUTE stmt_business_load;
DEALLOCATE PREPARE stmt_business_load;

INSERT INTO business (
  id, owner_id, name, category, phone, address, latitude, longitude, geohash, status, created_at, updated_at
)
SELECT
  id,
  owner_id,
  name,
  category,
  NULLIF(phone, ''),
  address,
  latitude,
  longitude,
  TRIM(TRAILING '\r' FROM geohash),
  CASE
    WHEN TRIM(TRAILING '\r' FROM status) IN ('ACTIVE', 'DELETED') THEN TRIM(TRAILING '\r' FROM status)
    ELSE 'ACTIVE'
  END,
  STR_TO_DATE(LEFT(REPLACE(TRIM(TRAILING '\r' FROM created_at_raw), 'T', ' '), 19), '%Y-%m-%d %H:%i:%s'),
  STR_TO_DATE(LEFT(REPLACE(TRIM(TRAILING '\r' FROM updated_at_raw), 'T', ' '), 19), '%Y-%m-%d %H:%i:%s')
FROM staging_business
ON DUPLICATE KEY UPDATE
  owner_id = VALUES(owner_id),
  name = VALUES(name),
  category = VALUES(category),
  phone = VALUES(phone),
  address = VALUES(address),
  latitude = VALUES(latitude),
  longitude = VALUES(longitude),
  geohash = VALUES(geohash),
  status = VALUES(status),
  updated_at = VALUES(updated_at);

CREATE TEMPORARY TABLE staging_geohash_index (
  geohash VARCHAR(12),
  business_id BIGINT
);

SET @sql_geohash_load = CONCAT(
  "LOAD DATA LOCAL INFILE '", @geohash_index_csv, "' INTO TABLE staging_geohash_index ",
  "FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' ESCAPED BY '\"' ",
  "LINES TERMINATED BY '\\n' IGNORE 1 LINES ",
  "(geohash, business_id)"
);
PREPARE stmt_geohash_load FROM @sql_geohash_load;
EXECUTE stmt_geohash_load;
DEALLOCATE PREPARE stmt_geohash_load;

INSERT IGNORE INTO geohash_index (geohash, business_id)
SELECT TRIM(TRAILING '\r' FROM geohash), business_id
FROM staging_geohash_index;

DELETE FROM business_id_sequence;
SET @next_id = (SELECT COALESCE(MAX(id), 0) + 1 FROM business);
SET @sql_sequence = CONCAT('ALTER TABLE business_id_sequence AUTO_INCREMENT = ', @next_id);
PREPARE stmt_sequence FROM @sql_sequence;
EXECUTE stmt_sequence;
DEALLOCATE PREPARE stmt_sequence;

COMMIT;
SQL

"${MYSQL_DB[@]}" <<'SQL'
SELECT 'business_count' AS metric, COUNT(*) AS value FROM business
UNION ALL
SELECT 'geohash_index_count' AS metric, COUNT(*) AS value FROM geohash_index
UNION ALL
SELECT 'change_log_unprocessed' AS metric, COUNT(*) AS value FROM change_log WHERE processed = 0
ORDER BY metric;
SQL

echo "[done] MySQL dummy data loaded successfully"
