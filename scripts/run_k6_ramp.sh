#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Run staged k6 ramp test (1k -> 2k -> 5k QPS by default).

Usage:
  scripts/run_k6_ramp.sh [options]

Options:
  --base-url <url>            Target base URL (default: http://localhost:8080)
  --rates <csv>               Stage rates, comma-separated (default: 1000,2000,5000)
  --durations <csv>           Stage durations, comma-separated (default: 60s,60s,120s)
  --stage-names <csv>         Stage names, comma-separated
                              (default: warmup,scale,baseline)
  --pre-allocated-vus <n>     Initial VU pool per stage (default: 500)
  --max-vus <n>               Max VU pool per stage (default: 5000)
  --p95-ms <ms>               p95 threshold per stage (default: 150)
  --radius <m>                Search radius (default: 500)
  --limit <n>                 Search limit (default: 20)
  --lat <value>               Base latitude (default: 37.4991)
  --lon <value>               Base longitude (default: 127.0313)
  --lat-jitter <value>        Latitude jitter range (default: 0.01)
  --lon-jitter <value>        Longitude jitter range (default: 0.01)
  --cooldown-seconds <sec>    Cooldown between stages (default: 10)
  --out-dir <dir>             Output root directory (default: loadtest/results/ramp_<timestamp>)
  --no-docker                 Forwarded to run_k6.sh
  --help                      Show this help

Examples:
  scripts/run_k6_ramp.sh
  scripts/run_k6_ramp.sh --rates 1000,3000,7000 --durations 30s,30s,60s
EOF
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "[error] command not found: $cmd" >&2
    exit 1
  fi
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="http://localhost:8080"
RATES_CSV="1000,2000,5000"
DURATIONS_CSV="60s,60s,120s"
STAGE_NAMES_CSV="warmup,scale,baseline"
PRE_ALLOCATED_VUS="500"
MAX_VUS="5000"
P95_MS="150"
RADIUS="500"
LIMIT="20"
LAT="37.4991"
LON="127.0313"
LAT_JITTER="0.01"
LON_JITTER="0.01"
COOLDOWN_SECONDS="10"
OUT_DIR_REL="loadtest/results/ramp_$(date +"%Y%m%d_%H%M%S")"
NO_DOCKER=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="${2:-}"
      shift 2
      ;;
    --rates)
      RATES_CSV="${2:-}"
      shift 2
      ;;
    --durations)
      DURATIONS_CSV="${2:-}"
      shift 2
      ;;
    --stage-names)
      STAGE_NAMES_CSV="${2:-}"
      shift 2
      ;;
    --pre-allocated-vus)
      PRE_ALLOCATED_VUS="${2:-}"
      shift 2
      ;;
    --max-vus)
      MAX_VUS="${2:-}"
      shift 2
      ;;
    --p95-ms)
      P95_MS="${2:-}"
      shift 2
      ;;
    --radius)
      RADIUS="${2:-}"
      shift 2
      ;;
    --limit)
      LIMIT="${2:-}"
      shift 2
      ;;
    --lat)
      LAT="${2:-}"
      shift 2
      ;;
    --lon)
      LON="${2:-}"
      shift 2
      ;;
    --lat-jitter)
      LAT_JITTER="${2:-}"
      shift 2
      ;;
    --lon-jitter)
      LON_JITTER="${2:-}"
      shift 2
      ;;
    --cooldown-seconds)
      COOLDOWN_SECONDS="${2:-}"
      shift 2
      ;;
    --out-dir)
      OUT_DIR_REL="${2:-}"
      shift 2
      ;;
    --no-docker)
      NO_DOCKER=true
      shift
      ;;
    --help|-h)
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

if [[ -z "$BASE_URL" ]]; then
  echo "[error] --base-url is required" >&2
  exit 1
fi

if [[ ! "$PRE_ALLOCATED_VUS" =~ ^[0-9]+$ ]] || [[ "$PRE_ALLOCATED_VUS" -le 0 ]]; then
  echo "[error] --pre-allocated-vus must be a positive integer" >&2
  exit 1
fi
if [[ ! "$MAX_VUS" =~ ^[0-9]+$ ]] || [[ "$MAX_VUS" -le 0 ]]; then
  echo "[error] --max-vus must be a positive integer" >&2
  exit 1
fi
if [[ ! "$COOLDOWN_SECONDS" =~ ^[0-9]+$ ]] || [[ "$COOLDOWN_SECONDS" -lt 0 ]]; then
  echo "[error] --cooldown-seconds must be >= 0" >&2
  exit 1
fi

IFS=',' read -r -a RATES <<< "$RATES_CSV"
IFS=',' read -r -a DURATIONS <<< "$DURATIONS_CSV"
IFS=',' read -r -a STAGE_NAMES <<< "$STAGE_NAMES_CSV"

if [[ ${#RATES[@]} -eq 0 ]]; then
  echo "[error] --rates is empty" >&2
  exit 1
fi

if [[ ${#RATES[@]} -ne ${#DURATIONS[@]} ]]; then
  echo "[error] rates and durations length mismatch (${#RATES[@]} vs ${#DURATIONS[@]})" >&2
  exit 1
fi

if [[ ${#STAGE_NAMES[@]} -ne ${#RATES[@]} ]]; then
  echo "[error] stage-names length must match rates length (${#STAGE_NAMES[@]} vs ${#RATES[@]})" >&2
  exit 1
fi

for rate in "${RATES[@]}"; do
  if [[ ! "$rate" =~ ^[0-9]+$ ]] || [[ "$rate" -le 0 ]]; then
    echo "[error] invalid rate in --rates: $rate" >&2
    exit 1
  fi
done

mkdir -p "$ROOT_DIR/$OUT_DIR_REL"

if command -v jq >/dev/null 2>&1; then
  HAVE_JQ=true
else
  HAVE_JQ=false
fi

RAMP_REPORT="$ROOT_DIR/$OUT_DIR_REL/ramp_report.md"
RAMP_CSV="$ROOT_DIR/$OUT_DIR_REL/ramp_summary.csv"

cat > "$RAMP_REPORT" <<EOF
# k6 Ramp Test Report

- Base URL: $BASE_URL
- Rates: $RATES_CSV
- Durations: $DURATIONS_CSV
- p95 target(ms): $P95_MS
- Generated at: $(date +"%Y-%m-%d %H:%M:%S")

## Stage Summary

| Stage | Target QPS | Achieved QPS | Requests | Dropped | p95(ms) | p99(ms) | Fail Rate | Check Rate | Stage Exit | Verdict |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
EOF

echo "stage,target_qps,achieved_qps,requests,dropped,p95_ms,p99_ms,fail_rate,check_rate,stage_exit,verdict,run_dir" > "$RAMP_CSV"

echo "[info] ramp output dir=$OUT_DIR_REL"
echo "[info] running ${#RATES[@]} stages"

overall_failed=false

for i in "${!RATES[@]}"; do
  stage_name="${STAGE_NAMES[$i]}"
  rate="${RATES[$i]}"
  duration="${DURATIONS[$i]}"
  stage_out_rel="$OUT_DIR_REL/${stage_name}"
  stage_out_abs="$ROOT_DIR/$stage_out_rel"
  mkdir -p "$stage_out_abs"

  echo "[info] stage=$stage_name rate=$rate duration=$duration"

  set +e
  if [[ "$NO_DOCKER" == true ]]; then
    "$ROOT_DIR/scripts/run_k6.sh" \
      --base-url "$BASE_URL" \
      --rate "$rate" \
      --duration "$duration" \
      --pre-allocated-vus "$PRE_ALLOCATED_VUS" \
      --max-vus "$MAX_VUS" \
      --p95-ms "$P95_MS" \
      --radius "$RADIUS" \
      --limit "$LIMIT" \
      --lat "$LAT" \
      --lon "$LON" \
      --lat-jitter "$LAT_JITTER" \
      --lon-jitter "$LON_JITTER" \
      --out-dir "$stage_out_rel" \
      --no-docker
    stage_exit=$?
  else
    "$ROOT_DIR/scripts/run_k6.sh" \
      --base-url "$BASE_URL" \
      --rate "$rate" \
      --duration "$duration" \
      --pre-allocated-vus "$PRE_ALLOCATED_VUS" \
      --max-vus "$MAX_VUS" \
      --p95-ms "$P95_MS" \
      --radius "$RADIUS" \
      --limit "$LIMIT" \
      --lat "$LAT" \
      --lon "$LON" \
      --lat-jitter "$LAT_JITTER" \
      --lon-jitter "$LON_JITTER" \
      --out-dir "$stage_out_rel"
    stage_exit=$?
  fi
  set -e

  run_dir_abs="$(ls -1dt "$stage_out_abs"/* 2>/dev/null | head -n 1 || true)"
  if [[ -z "$run_dir_abs" ]]; then
    echo "[error] stage=$stage_name did not produce output directory" >&2
    overall_failed=true
    continue
  fi
  run_dir_rel="${run_dir_abs#$ROOT_DIR/}"
  summary_json="$run_dir_abs/summary.json"

  requests=0
  achieved_qps=0
  dropped=0
  p95=0
  p99=0
  fail_rate=0
  check_rate=0

  if [[ -f "$summary_json" && "$HAVE_JQ" == true ]]; then
    requests="$(jq -r '(.metrics.http_reqs.values // .metrics.http_reqs).count // 0' "$summary_json")"
    achieved_qps="$(jq -r '(.metrics.http_reqs.values // .metrics.http_reqs).rate // 0' "$summary_json")"
    dropped="$(jq -r '(.metrics.dropped_iterations.values // .metrics.dropped_iterations).count // 0' "$summary_json")"
    p95="$(jq -r '(.metrics.http_req_duration.values // .metrics.http_req_duration)["p(95)"] // 0' "$summary_json")"
    p99="$(jq -r '(.metrics.http_req_duration.values // .metrics.http_req_duration)["p(99)"] // 0' "$summary_json")"
    fail_rate="$(jq -r '
      .metrics.http_req_failed as $m
      | if ($m.value? != null) then $m.value
        else
          (($m.fails // 0) as $f
          | ($m.passes // 0) as $p
          | if ($p + $f) == 0 then 0 else ($f / ($p + $f)) end)
        end
    ' "$summary_json")"
    check_rate="$(jq -r '
      .metrics.checks as $m
      | if ($m.value? != null) then $m.value
        else
          (($m.passes // 0) as $p
          | ($m.fails // 0) as $f
          | if ($p + $f) == 0 then 0 else ($p / ($p + $f)) end)
        end
    ' "$summary_json")"
  fi

  verdict="PASS"
  if ! awk -v a="$p95" -v b="$P95_MS" 'BEGIN { exit !(a <= b) }'; then
    verdict="FAIL"
  fi
  if ! awk -v a="$fail_rate" 'BEGIN { exit !(a < 0.01) }'; then
    verdict="FAIL"
  fi
  if ! awk -v a="$check_rate" 'BEGIN { exit !(a >= 0.99) }'; then
    verdict="FAIL"
  fi
  if ! awk -v a="$achieved_qps" -v t="$rate" 'BEGIN { exit !(a >= t * 0.95) }'; then
    verdict="FAIL"
  fi
  if ! awk -v d="$dropped" 'BEGIN { exit !(d == 0) }'; then
    verdict="FAIL"
  fi
  if [[ "$stage_exit" -ne 0 ]]; then
    verdict="FAIL"
  fi

  if [[ "$verdict" != "PASS" ]]; then
    overall_failed=true
  fi

  printf '| %s | %s | %.2f | %s | %s | %.2f | %.2f | %.4f | %.4f | %s | %s |\n' \
    "$stage_name" "$rate" "$achieved_qps" "$requests" "$dropped" "$p95" "$p99" "$fail_rate" "$check_rate" "$stage_exit" "$verdict" >> "$RAMP_REPORT"

  printf '%s,%s,%.6f,%s,%s,%.6f,%.6f,%.8f,%.8f,%s,%s,%s\n' \
    "$stage_name" "$rate" "$achieved_qps" "$requests" "$dropped" "$p95" "$p99" "$fail_rate" "$check_rate" "$stage_exit" "$verdict" "$run_dir_rel" >> "$RAMP_CSV"

  if [[ "$i" -lt $((${#RATES[@]} - 1)) && "$COOLDOWN_SECONDS" -gt 0 ]]; then
    echo "[info] cooldown ${COOLDOWN_SECONDS}s before next stage"
    sleep "$COOLDOWN_SECONDS"
  fi
done

cat >> "$RAMP_REPORT" <<EOF

## Artifacts

- CSV summary: ${OUT_DIR_REL}/ramp_summary.csv
- Raw runs: ${OUT_DIR_REL}/<stage>/<timestamp>/
EOF

if [[ "$overall_failed" == true ]]; then
  echo "[warn] ramp test finished with failures"
  echo "[done] ramp report: ${OUT_DIR_REL}/ramp_report.md"
  exit 1
fi

echo "[done] ramp report: ${OUT_DIR_REL}/ramp_report.md"
