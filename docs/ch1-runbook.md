# Chapter 1 Runbook

## 1. 배치 운영

### index-sync-job

- 입력: `business_change_log`에서 `processed_at IS NULL`인 레코드
- 처리: `geohash_index` upsert/delete + 관련 캐시 키 무효화
- 주기:
  - 기본: `5분`
  - 비용/부하 제약 시: `야간 일괄`(예: `02:00`)

### full-reindex-job

- 목적: 인덱스 손상/누락 복구
- 주기: 주 1회 또는 필요 시 수동 실행
- 방법: `business` 전체 스캔 후 `geohash_index` 재구축

## 2. 모니터링 지표

- API 지연시간: `p50/p95/p99` (`/v1/search/nearby`, `/v1/business/{id}`)
- 에러율: `4xx`, `5xx`
- 캐시 히트율: `geo cache`, `biz cache`
- 동기화 지연: `NOW() - MIN(created_at where processed_at is null)`
- 변경 로그 적체량: `unprocessed row count`

## 3. 알람 기준(초기값)

- Search API p95 > `300ms` (5분 이상)
- Search API 5xx > `1%` (5분 이상)
- 변경 로그 적체 > `100,000` 건
- 동기화 지연 > `60분`

## 4. 장애 대응

### 검색 지연 급증

1. 캐시 히트율 하락 여부 확인
2. `geohash_index` 쿼리 슬로우 로그 확인
3. 임시 완화: `radius`/`limit` 상한 축소, 트래픽 셰이핑 적용

### 검색 결과 누락/오염

1. 특정 `business_id`의 `business` vs `geohash_index` 데이터 비교
2. 누락 범위가 크면 `full-reindex-job` 실행
3. 배치 재시작 후 적체 해소 속도 모니터링

### 캐시 장애

1. 캐시 우회하여 DB fallback
2. API 타임아웃/커넥션 설정 완화
3. 장애 해소 후 hot geohash 순으로 워밍

## 5. 배포 정책

- 롤링 배포, 한 번에 전체 인스턴스 교체 금지
- 배포 전후 비교 지표: p95, 5xx, 변경 로그 처리율
- 스키마 변경은 `expand -> migrate -> contract` 순서 준수

## 6. 개인정보 운영 수칙

- 로그에 원본 위경도 직접 기록 금지
- 운영 데이터 추출 시 위치 데이터 최소화/익명화
- 법적 보관기간이 지난 위치성 데이터는 주기적으로 삭제
