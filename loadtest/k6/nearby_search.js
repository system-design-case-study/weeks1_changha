import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 5000);
const DURATION = __ENV.DURATION || '60s';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || 500);
const MAX_VUS = Number(__ENV.MAX_VUS || 5000);
const P95_MS = Number(__ENV.P95_MS || 150);

const LAT = Number(__ENV.LAT || 37.4991);
const LON = Number(__ENV.LON || 127.0313);
const LAT_JITTER = Number(__ENV.LAT_JITTER || 0.01);
const LON_JITTER = Number(__ENV.LON_JITTER || 0.01);
const RADIUS = Number(__ENV.RADIUS || 500);
const LIMIT = Number(__ENV.LIMIT || 20);
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME_SECONDS || 0);

function jitter(base, maxDelta) {
  return base + (Math.random() * 2 - 1) * maxDelta;
}

export const options = {
  scenarios: {
    nearby_search: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    http_req_duration: [`p(95)<${P95_MS}`],
  },
  // Exclude "url" system tag to avoid high-cardinality metrics when query params vary per request.
  systemTags: ['status', 'method', 'name', 'scenario', 'group', 'check', 'error', 'error_code'],
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const latitude = jitter(LAT, LAT_JITTER).toFixed(6);
  const longitude = jitter(LON, LON_JITTER).toFixed(6);
  const url =
    `${BASE_URL}/v1/search/nearby` +
    `?latitude=${latitude}` +
    `&longitude=${longitude}` +
    `&radius=${RADIUS}` +
    `&limit=${LIMIT}`;

  const res = http.get(url, {
    tags: {
      name: 'GET /v1/search/nearby',
      endpoint: 'nearby_search',
    },
  });

  let body = null;
  try {
    body = res.json();
  } catch (_) {
    body = null;
  }

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has total': () => body !== null && typeof body.total === 'number',
    'has businesses': () => body !== null && Array.isArray(body.businesses),
  });

  if (THINK_TIME_SECONDS > 0) {
    sleep(THINK_TIME_SECONDS);
  }
}
