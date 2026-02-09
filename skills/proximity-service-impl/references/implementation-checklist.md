# 구현 체크리스트

## API

- `GET /v1/search/nearby` 구현 및 lat/lon/radius/limit 검증 적용
- `GET /v1/business/{id}` 구현
- `POST /v1/business`, `PUT /v1/business/{id}`, `DELETE /v1/business/{id}` 구현
- 에러 응답 포맷 통일: `code`, `message`, `requestId`

## 검색 코어

- 지오해시 정밀도 선택 로직 존재
- 인접 지오해시(8방향) 확장 로직 존재
- Haversine 거리 필터링 존재
- 거리 기준 정렬 존재
- 페이지네이션(`limit`, `cursor`) 존재

## 영속 계층

- `business` 테이블 및 리포지토리 구현
- `geohash_index` 테이블 및 리포지토리 구현
- `business_change_log` 테이블 및 리포지토리 구현
- 쓰기 트랜잭션에서 `business`와 `change_log` 동시 기록

## 동기화 및 캐시

- 변경 로그 소비자(batch/scheduler) 구현
- geohash 인덱스 upsert/delete 로직 구현
- 캐시 키 패턴 문서 규칙 준수
- 쓰기 동기화 시 캐시 무효화 구현

## 품질

- geohash/거리 계산 단위 테스트
- 검색 API 및 CRUD API 통합 테스트
- 경계 좌표, 빈 결과, 삭제된 사업장 엣지 테스트
- 지연시간 및 동기화 지연 관련 메트릭/로그 추가
