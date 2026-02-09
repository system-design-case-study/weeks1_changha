---
name: proximity-service-impl
description: 이 저장소의 1장 근접성 서비스를 구현하거나 수정한다. 주변 사업장 검색 API, 사업장 CRUD, 지오해시 인덱스 흐름, 캐시 전략, 최종 일관성 배치 동기화, 관련 테스트 및 문서 작업 시 사용한다.
---

# 근접성 서비스 구현

## 먼저 설계 문서를 확인

코드 수정 전에 아래 문서를 먼저 읽는다.

- `docs/ch1-scope.md`
- `docs/ch1-api.md`
- `docs/ch1-data-model.md`
- `docs/ch1-runbook.md`

## 작업 절차

1. `docs/ch1-api.md`를 기준으로 API 계약과 에러 포맷을 확인한다.
2. `search`, `business`, `index-sync` 도메인 모듈을 구현하거나 수정한다.
3. 쓰기/읽기 경로 분리를 명확히 유지한다.
   - 쓰기 경로: `business` + `business_change_log`
   - 읽기 경로: `geohash_index` + 상세 조회 + 반경 필터링
4. 문서 규칙에 맞는 캐시 키를 적용한다.
   - `geo:{precision}:{geohash}`
   - `biz:{business_id}`
5. 아래 테스트를 추가한다.
   - 반경 필터링 및 거리 정렬
   - 사업장 CRUD 동작
   - 최종 일관성 동작(인덱스 동기화 지연)
6. API/스키마/운영 가정이 바뀌면 문서도 함께 갱신한다.

## 패키지 구조 가이드

아래 구조를 사용하거나 이에 맞춰 정렬한다.

- `...search` (controller/service/geohash/distance)
- `...business` (controller/service/repository/dto)
- `...indexsync` (batch worker/change-log processor)
- `...common` (error model, validation, response envelope)

## 참고 문서

- 구현 체크리스트: `references/implementation-checklist.md`
- 지오해시 정책: `references/geohash-policy.md`
