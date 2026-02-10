# Proximity Service (Week 1)

Spring Boot 기반 근접 검색 서비스. MySQL Geohash → Redis Geohash로의 아키텍처 진화 과정을 다룬다.

## Architecture

<img width="961" height="501" alt="스크린샷 2026-02-10 오전 11 55 39" src="https://github.com/user-attachments/assets/8d26cf21-43d0-4e9c-a5e6-25b2db5a5f9b" />

---

## 설계 변천 과정

### Phase 1: MySQL 9-Cell Geohash 검색

검색 좌표를 Geohash로 인코딩 → 중심 + 8개 이웃 셀 = **9개 LIKE 쿼리**로 후보를 가져온 뒤, 애플리케이션에서 거리 계산 → 정렬 → 페이지네이션.

```
검색 1건 = 9 × SELECT ... WHERE geohash LIKE 'wydm6v%'
```

**문제**: 밀집 지역(강남 등)에서 셀당 수천 건 → MySQL 읽기 부하 폭주.

---

### Phase 2: Hot Zone DB 분리

밀집 지역의 읽기 부하를 분산하기 위해 **물리적 DB 분리**.

| DB | 용도 |
|---|---|
| Primary (3311) | 일반 지역 geohash_index 저장/검색 |
| Hot (3312) | 강남(`wydm`) 등 트래픽 집중 지역 전용 |

- `HotZoneConfigService`가 geohash prefix 기반으로 라우팅
- 반경 제한(radius capping)으로 과도한 후보 수 억제

**효과**: Primary DB 부하 감소.
**한계**: 근본적으로 `LIKE` × 9셀 비효율은 그대로. Hot Zone이 늘어날수록 관리 복잡도 증가.

---

### Phase 3: Redis Geohash 통합 (현재)

Redis `GEORADIUS`로 검색 읽기 경로에서 MySQL을 완전히 제거.

#### Before → After

| | Before (MySQL) | After (Redis) |
|---|---|---|
| 검색 쿼리 | 9 × `LIKE` | 1 × `GEORADIUS` |
| 정렬 | 앱에서 haversine 계산 후 정렬 | Redis가 거리순 반환 |
| 캐시 | `GeoCellCache` (인메모리) | 불필요 (Redis 자체가 인메모리) |
| DB 분리 | Hot Zone DB 필요 | 불필요 |
| 반경 제한 | 필요 (성능 보호) | 불필요 (GEORADIUS가 효율적) |
| MySQL 조회량 | 후보 전체 (수천 건) | 페이지 크기만 (~20건) |

#### 왜 Hot Zone DB가 더 이상 필요 없나

Hot Zone DB의 목적은 **MySQL 읽기 부하 분산**이었다. Redis 도입 후 검색 읽기가 MySQL을 거치지 않으므로 분리할 대상 자체가 사라졌다.

- Redis는 인메모리 → 디스크 병목 없음
- 싱글 스레드 이벤트 루프 → 락 경합 없음
- 밀집 여부와 무관한 마이크로초 응답
- 확장 필요 시 Redis Cluster (자동 샤딩)

---

## 트러블슈팅 기록

### 1. Redis 전환 후에도 MySQL 조회가 병목이었던 이유

Redis가 5000개 ID를 반환하면, `businessService.findAllActiveByIds(5000개)`로 **전체를 MySQL에서 조회**하고 있었다.

**원인**: 기존 MySQL LIKE 쿼리는 거리순 정렬이 불가능해서 전체를 가져와야만 정렬할 수 있었다. Redis 전환 시 이 패턴을 그대로 가져옴.

**해결**: Redis `GEORADIUS`는 이미 거리순 정렬된 결과를 반환하므로, 상위 `offset + limit`개만 MySQL에서 조회.

```
Before: Redis 5000개 → MySQL 5000개 조회 → 정렬 → 20개 반환
After:  Redis 5000개 ID → 상위 20개만 MySQL 조회 → 바로 반환
```

### 2. RedisGeoDataLoader: 기존 데이터 이관

Redis 통합 전에 이미 19만 건의 비즈니스가 MySQL에 존재. 이 데이터를 Redis에 넣기 위해 `CommandLineRunner`로 앱 시작 시 벌크 로드 구현.

- Redis가 비어있으면 MySQL에서 active 비즈니스를 조회 → `GEOADD`
- ~16초에 195,894건 적재

### 3. Dual Write 전략

신규 변경은 `IndexSyncService`에서 MySQL + Redis 동시 쓰기.
- `upsertWithCoordinates(geohash, businessId, lat, lon)`: MySQL INSERT + Redis GEOADD
- `deleteByBusinessId`: MySQL DELETE + Redis ZREM

---

## 부하 테스트 결과 비교

### MySQL 기반 (Phase 2)

| Stage | Target QPS | Achieved QPS | p95(ms) | Verdict |
|---|---:|---:|---:|---|
| low | 200 | 200.00 | 54.43 | PASS |
| mid | 500 | 499.99 | 9.16 | PASS |
| high | 1000 | 972.05 | 225.63 | FAIL |

### Redis 기반 (Phase 3)

| 항목 | 값 |
|---|---|
| 단일 요청 응답시간 | **48ms** |
| 에러율 | 0% |
| 1000 QPS 동시 테스트 | ~101 QPS (로컬 환경) |

> 1000 QPS 미달은 아키텍처 한계가 아닌 **로컬 Docker 환경의 물리적 한계**: 단일 Tomcat(200 스레드, 동기 블로킹), 단일 MySQL/Redis Docker 컨테이너, 모두 한 대의 MacBook에서 실행.

48ms 응답시간 기준, 프로덕션에서 1000+ QPS 달성 방법:
- Spring Boot 인스턴스 수평 확장 (2~3대)
- WebFlux 또는 Virtual Thread 전환
- Redis Cluster + MySQL Read Replica

---

## Quick Start

```bash
# 1. 인프라 (MySQL + Redis)
docker compose -f docker-compose.mysql.yml up -d

# 2. 앱 실행
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun

# 3. 검색 테스트
curl "http://localhost:8080/v1/search/nearby?latitude=37.498&longitude=127.027&radius=1000"
```


## Geohash
실습 url : https://geohash.softeng.co/
<img width="1009" height="511" alt="스크린샷 2026-02-10 오후 1 10 32" src="https://github.com/user-attachments/assets/cf4614fb-7553-4eb9-9450-b833755e6e90" />
