#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Run k6 load test for nearby search endpoint.

Usage:
  scripts/run_k6.sh [options]

Options:
  --base-url <url>            Target base URL (default: http://localhost:8080)
  --rate <qps>                Constant arrival rate per second (default: 5000)
  --duration <time>           Test duration (default: 60s)
  --pre-allocated-vus <n>     Initial VU pool (default: 500)
  --max-vus <n>               Max VU pool (default: 5000)
  --p95-ms <ms>               p95 threshold in ms (default: 150)
  --radius <m>                Search radius (default: 500)
  --limit <n>                 Search limit (default: 20)
  --lat <value>               Base latitude (default: 37.4991)
  --lon <value>               Base longitude (default: 127.0313)
  --lat-jitter <value>        Latitude jitter range (default: 0.01)
  --lon-jitter <value>        Longitude jitter range (default: 0.01)
  --out-dir <dir>             Output root directory (default: loadtest/results)
  --scenario <path>           k6 scenario file (default: loadtest/k6/nearby_search.js)
  --no-docker                 Fail instead of Docker fallback when local k6 is missing
  --help                      Show this help

Examples:
  scripts/run_k6.sh --rate 1000 --duration 2m
  scripts/run_k6.sh --base-url http://localhost:8081 --rate 5000 --duration 5m
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
RATE="5000"
DURATION="60s"
PRE_ALLOCATED_VUS="500"
MAX_VUS="5000"
P95_MS="150"
RADIUS="500"
LIMIT="20"
LAT="37.4991"
LON="127.0313"
LAT_JITTER="0.01"
LON_JITTER="0.01"
OUT_DIR_REL="loadtest/results"
SCENARIO_REL="loadtest/k6/nearby_search.js"
NO_DOCKER=false
declare -a EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="${2:-}"
      shift 2
      ;;
    --rate)
      RATE="${2:-}"
      shift 2
      ;;
    --duration)
      DURATION="${2:-}"
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
    --out-dir)
      OUT_DIR_REL="${2:-}"
      shift 2
      ;;
    --scenario)
      SCENARIO_REL="${2:-}"
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
    --)
      shift
      EXTRA_ARGS+=("$@")
      break
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

if [[ ! -f "$ROOT_DIR/$SCENARIO_REL" ]]; then
  echo "[error] scenario file not found: $SCENARIO_REL" >&2
  exit 1
fi

if [[ ! "$RATE" =~ ^[0-9]+$ ]] || [[ "$RATE" -le 0 ]]; then
  echo "[error] --rate must be a positive integer" >&2
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

RUNNER=""
if command -v k6 >/dev/null 2>&1; then
  RUNNER="local"
elif [[ "$NO_DOCKER" == true ]]; then
  echo "[error] k6 is not installed locally and --no-docker is set" >&2
  exit 1
else
  require_cmd docker
  RUNNER="docker"
fi

if [[ "$RUNNER" == "docker" ]]; then
  # Docker Desktop on macOS/Windows requires host.docker.internal to reach host services.
  BASE_URL="${BASE_URL/localhost/host.docker.internal}"
  BASE_URL="${BASE_URL/127.0.0.1/host.docker.internal}"
fi

TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
RUN_DIR_REL="$OUT_DIR_REL/$TIMESTAMP"
RUN_DIR_ABS="$ROOT_DIR/$RUN_DIR_REL"
SUMMARY_REL="$RUN_DIR_REL/summary.json"
SUMMARY_ABS="$ROOT_DIR/$SUMMARY_REL"
LOG_ABS="$RUN_DIR_ABS/run.log"
REPORT_ABS="$RUN_DIR_ABS/report.md"

mkdir -p "$RUN_DIR_ABS"

echo "[info] runner=$RUNNER"
echo "[info] base_url=$BASE_URL"
echo "[info] rate=$RATE duration=$DURATION pre_allocated_vus=$PRE_ALLOCATED_VUS max_vus=$MAX_VUS"
echo "[info] scenario=$SCENARIO_REL"
echo "[info] output_dir=$RUN_DIR_REL"

run_local() {
  local -a cmd=(
    k6 run
    -e "BASE_URL=$BASE_URL"
    -e "RATE=$RATE"
    -e "DURATION=$DURATION"
    -e "PRE_ALLOCATED_VUS=$PRE_ALLOCATED_VUS"
    -e "MAX_VUS=$MAX_VUS"
    -e "P95_MS=$P95_MS"
    -e "RADIUS=$RADIUS"
    -e "LIMIT=$LIMIT"
    -e "LAT=$LAT"
    -e "LON=$LON"
    -e "LAT_JITTER=$LAT_JITTER"
    -e "LON_JITTER=$LON_JITTER"
    --summary-export "$SUMMARY_REL"
    "$SCENARIO_REL"
  )
  if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    cmd+=("${EXTRA_ARGS[@]}")
  fi

  (
    cd "$ROOT_DIR"
    "${cmd[@]}"
  ) | tee "$LOG_ABS"
}

run_docker() {
  local -a cmd=(
    docker run --rm -i
    -v "$ROOT_DIR:/work"
    -w /work
    grafana/k6:0.49.0 run
    -e "BASE_URL=$BASE_URL"
    -e "RATE=$RATE"
    -e "DURATION=$DURATION"
    -e "PRE_ALLOCATED_VUS=$PRE_ALLOCATED_VUS"
    -e "MAX_VUS=$MAX_VUS"
    -e "P95_MS=$P95_MS"
    -e "RADIUS=$RADIUS"
    -e "LIMIT=$LIMIT"
    -e "LAT=$LAT"
    -e "LON=$LON"
    -e "LAT_JITTER=$LAT_JITTER"
    -e "LON_JITTER=$LON_JITTER"
    --summary-export "$SUMMARY_REL"
    "$SCENARIO_REL"
  )
  if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    cmd+=("${EXTRA_ARGS[@]}")
  fi

  "${cmd[@]}" | tee "$LOG_ABS"
}

generate_report() {
  if [[ ! -f "$SUMMARY_ABS" ]]; then
    echo "[warn] summary file not found, skipping report generation" >&2
    return
  fi

  if ! command -v jq >/dev/null 2>&1; then
    cat > "$REPORT_ABS" <<EOF
# k6 Load Test Report

- Timestamp: $TIMESTAMP
- Base URL: $BASE_URL
- Scenario: $SCENARIO_REL
- Summary JSON: $SUMMARY_REL

Install \`jq\` to generate a detailed markdown report.
EOF
    return
  fi

  local p95 p99 avg fail_rate check_rate reqs iterations req_rate dropped
  p95="$(jq -r '
    def mv($name; $key): ((.metrics[$name].values // .metrics[$name])[$key] // 0);
    mv("http_req_duration"; "p(95)")
  ' "$SUMMARY_ABS")"
  p99="$(jq -r '
    def mv($name; $key): ((.metrics[$name].values // .metrics[$name])[$key] // 0);
    mv("http_req_duration"; "p(99)")
  ' "$SUMMARY_ABS")"
  avg="$(jq -r '
    def mv($name; $key): ((.metrics[$name].values // .metrics[$name])[$key] // 0);
    mv("http_req_duration"; "avg")
  ' "$SUMMARY_ABS")"
  fail_rate="$(jq -r '
    .metrics.http_req_failed as $m
    | if ($m.value? != null) then $m.value
      else
        (($m.fails // 0) as $f
        | ($m.passes // 0) as $p
        | if ($p + $f) == 0 then 0 else ($f / ($p + $f)) end)
      end
  ' "$SUMMARY_ABS")"
  check_rate="$(jq -r '
    .metrics.checks as $m
    | if ($m.value? != null) then $m.value
      else
        (($m.passes // 0) as $p
        | ($m.fails // 0) as $f
        | if ($p + $f) == 0 then 0 else ($p / ($p + $f)) end)
      end
  ' "$SUMMARY_ABS")"
  reqs="$(jq -r '
    def mv($name; $key): ((.metrics[$name].values // .metrics[$name])[$key] // 0);
    mv("http_reqs"; "count")
  ' "$SUMMARY_ABS")"
  req_rate="$(jq -r '
    def mv($name; $key): ((.metrics[$name].values // .metrics[$name])[$key] // 0);
    mv("http_reqs"; "rate")
  ' "$SUMMARY_ABS")"
  iterations="$(jq -r '
    def mv($name; $key): ((.metrics[$name].values // .metrics[$name])[$key] // 0);
    mv("iterations"; "count")
  ' "$SUMMARY_ABS")"
  dropped="$(jq -r '
    def mv($name; $key): ((.metrics[$name].values // .metrics[$name])[$key] // 0);
    mv("dropped_iterations"; "count")
  ' "$SUMMARY_ABS")"

  cat > "$REPORT_ABS" <<EOF
# k6 Load Test Report

- Timestamp: $TIMESTAMP
- Base URL: $BASE_URL
- Rate: ${RATE}/s
- Duration: $DURATION
- Pre-Allocated VUs: $PRE_ALLOCATED_VUS
- Max VUs: $MAX_VUS
- Threshold p95: ${P95_MS}ms

## Summary

- Requests: $reqs
- Request rate(/s): $req_rate
- Iterations: $iterations
- Dropped iterations: $dropped
- Failed rate: $fail_rate
- Checks pass rate: $check_rate
- Latency avg(ms): $avg
- Latency p95(ms): $p95
- Latency p99(ms): $p99

## Files

- Summary JSON: $SUMMARY_REL
- Raw log: ${RUN_DIR_REL}/run.log
EOF
}

K6_EXIT_CODE=0
if [[ "$RUNNER" == "local" ]]; then
  if run_local; then
    K6_EXIT_CODE=0
  else
    K6_EXIT_CODE=$?
  fi
else
  if run_docker; then
    K6_EXIT_CODE=0
  else
    K6_EXIT_CODE=$?
  fi
fi

generate_report

echo "[done] summary: $SUMMARY_REL"
echo "[done] report : ${RUN_DIR_REL}/report.md"

if [[ "$K6_EXIT_CODE" -ne 0 ]]; then
  echo "[warn] k6 finished with non-zero exit code: $K6_EXIT_CODE" >&2
fi

exit "$K6_EXIT_CODE"
