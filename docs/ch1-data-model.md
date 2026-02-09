# Chapter 1 Data Model

## 1. business

사업장 원본 테이블. 쓰기의 단일 진실 공급원(source of truth)이다.

```sql
CREATE TABLE business (
  id BIGSERIAL PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(40) NOT NULL,
  phone VARCHAR(32),
  address VARCHAR(255) NOT NULL,
  latitude DECIMAL(9,6) NOT NULL,
  longitude DECIMAL(9,6) NOT NULL,
  geohash VARCHAR(12) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_business_owner_id ON business (owner_id);
CREATE INDEX idx_business_updated_at ON business (updated_at);
```

## 2. geohash_index

검색용 색인 테이블. `(geohash, business_id)` 복합 키로 저장한다.

```sql
CREATE TABLE geohash_index (
  geohash VARCHAR(12) NOT NULL,
  business_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (geohash, business_id)
);

CREATE INDEX idx_geohash_index_business_id ON geohash_index (business_id);
```

검색 시에는 정밀도 prefix를 사용한다.

```sql
SELECT business_id
FROM geohash_index
WHERE geohash LIKE :geohash_prefix || '%';
```

## 3. business_change_log

쓰기와 인덱스 반영을 분리하기 위한 변경 로그(비동기 동기화 입력).

```sql
CREATE TABLE business_change_log (
  id BIGSERIAL PRIMARY KEY,
  business_id BIGINT NOT NULL,
  op_type VARCHAR(16) NOT NULL, -- CREATED / UPDATED / DELETED
  payload_json JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  processed_at TIMESTAMP
);

CREATE INDEX idx_change_log_processed_at ON business_change_log (processed_at, created_at);
```

## 4. Write Path

1. `business`에 트랜잭션으로 쓰기
2. 같은 트랜잭션에서 `business_change_log`에 이벤트 기록
3. 배치가 변경 로그를 읽어 `geohash_index` 갱신
4. 배치가 관련 캐시 키를 무효화

## 5. Read Path

1. 사용자 위치로 `중심 geohash + 8개 이웃 geohash` 계산
2. 각 geohash prefix로 `geohash_index` 조회 후 business ID 수집
3. `business` 상세 조회 + Haversine 거리 계산
4. 반경 필터링, 거리 오름차순 정렬, 페이지네이션 반환

## 6. Cache Key

- `geo:{precision}:{geohash}` -> `List<business_id>`
- `biz:{business_id}` -> `business detail object`

TTL(초기값):

- `geo:*`: `5m~30m`
- `biz:*`: `1h~24h` (변경 시 무효화 우선)
