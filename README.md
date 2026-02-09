# weeks1_changha

Spring Boot 기반으로 구성한 `system-design-case-study` Week 1 실습 프로젝트입니다.

## Requirements
- Java 17+
- Gradle 8.10+ (or use Gradle Wrapper)
- Docker (MySQL 실행 시)

## Run
```bash
./gradlew bootRun
```

## Run With MySQL + Geohash
1. MySQL 실행
```bash
docker compose -f docker-compose.mysql.yml up -d
```
2. `mysql` 프로필로 애플리케이션 실행
```bash
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun
```
3. 기본 연결 정보 (`src/main/resources/application-mysql.yml`)
- URL: `jdbc:mysql://localhost:3311/weeks1`
- USER/PASSWORD: `weeks1` / `weeks1`

기본 확인 엔드포인트:
- `GET /api/ping` -> `pong`
- `GET /actuator/health`
- `GET /actuator/prometheus`

## Test
```bash
./gradlew test
```

## Monitoring (Prometheus + Grafana)
1. 애플리케이션 실행
```bash
./gradlew bootRun
```
2. 모니터링 스택 실행
```bash
docker compose -f monitoring/docker-compose.yml up -d
```
3. 접속
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (`admin` / `admin`)

기본 대시보드:
- `Weeks1 Proximity Overview` (프로비저닝 자동 로드)

주요 메트릭:
- `http_server_requests_seconds_*` (API 지연시간/처리량)
- `proximity_search_geo_cache_hit_total`
- `proximity_search_geo_cache_miss_total`
- `proximity_indexsync_backlog`
- `proximity_indexsync_oldest_age_seconds`

## Dummy Data
더미 데이터 생성 스크립트:
- `scripts/gen_dummy_data.py`

샘플 생성:
```bash
python3 scripts/gen_dummy_data.py \
  --business-count 5000 \
  --query-count 20000 \
  --output-dir data/dummy-sample
```

스터디용 기본 스케일 생성:
```bash
python3 scripts/gen_dummy_data.py \
  --business-count 200000 \
  --query-count 1000000 \
  --output-dir data/dummy
```

생성 파일:
- `businesses.csv`
- `geohash_index.csv`
- `search_queries.csv`
- `import.sql` (`--skip-sql` 미지정 시)
- `generation_summary.json`

인메모리 앱(API) 로드 스크립트:
- `scripts/load_dummy_data_api.py`

사용 예시:
```bash
python3 scripts/load_dummy_data_api.py \
  --csv data/dummy/businesses.csv \
  --base-url http://localhost:8080 \
  --concurrency 8 \
  --wait-for-index-sync
```

참고:
- 이 프로젝트 기본 구현은 인메모리 저장소를 사용하므로, CSV 적재는 API 로드 스크립트를 사용
- 인덱스 반영은 비동기(`app.index-sync.delay-ms`, 기본 30000ms)라서 적재 직후 검색 결과 반영까지 지연될 수 있음

MySQL 대용량 적재 스크립트:
- `scripts/load_dummy_data_mysql.sh`

사용 예시:
```bash
scripts/load_dummy_data_mysql.sh \
  --data-dir data/dummy \
  --create-schema \
  --truncate
```

권장 실행 순서:
1. `docker compose -f docker-compose.mysql.yml up -d`
2. `scripts/load_dummy_data_mysql.sh --data-dir data/dummy --create-schema --truncate`
3. `SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun`

PostgreSQL 실험용 로드 스크립트(선택):
- `scripts/load_dummy_data.sh`
- PostgreSQL 환경이 있을 때만 사용

No Data가 보일 때 확인:
1. 앱이 실행 중인지 확인 (`http://localhost:8080/actuator/health`)
2. Prometheus Targets에서 `weeks1-app`이 `UP`인지 확인 (`http://localhost:9090/targets`)
3. 대시보드 데이터를 생성할 트래픽 호출
```bash
curl "http://localhost:8080/v1/search/nearby?latitude=37.4991&longitude=127.0313&radius=500"
```
4. 앱이 8081에서 돌고 있다면 `monitoring/prometheus/prometheus.yml`에 8081 타깃이 포함되어 있는지 확인
