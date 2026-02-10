# Load Test SLA Reference

이 문서는 k6 부하 테스트의 초기 합격 기준입니다.

## Target Endpoint

- `GET /v1/search/nearby`

## Traffic Assumption

- 평균 QPS: `5,000`
- 피크 QPS: 평균의 `3~5배` (`15,000 ~ 25,000`)

## Baseline Criteria (Cache-hit 중심)

- HTTP 에러율: `< 1%`
- Search API p95: `<= 150ms`
- Search API p99: `<= 300ms` (권장)
- Check pass rate: `>= 99%`

## Load Levels

1. Warm-up: `1,000 QPS`, `60s`
2. Baseline: `5,000 QPS`, `120s`
3. Peak rehearsal: `10,000+ QPS`, `120s` 이상

## Pass/Fail Rule

- Baseline 구간에서 p95/에러율 기준을 만족해야 함
- Peak rehearsal에서 장애 없이 회복 가능한지(5xx 급증/타임아웃) 확인

## Notes

- DB/캐시가 cold 상태면 결과가 왜곡되므로 워밍 후 테스트 권장
- 로컬 단일 인스턴스 결과는 절대 성능이 아니라 상대 비교 기준으로 사용
