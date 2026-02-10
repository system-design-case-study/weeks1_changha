# Proximity Service (Week 1)

Spring Boot 기반으로 구성한 `system-design-case-study` Week 1 실습 프로젝트입니다.
**Physical Database Separation (Hot Database)** 아키텍처가 적용되어 있습니다.

## Architecture
<img width="693" height="679" alt="스크린샷 2026-02-09 오후 11 32 33" src="https://github.com/user-attachments/assets/43271cb9-3ed6-418e-90d4-050572fd5a13" />

### Database Separation
이 프로젝트는 **트래픽이 집중되는 Hot Zone**을 물리적으로 분리된 데이터베이스에서 처리합니다.

- **Primary DB (Port 3311)**:
  - 일반적인 모든 데이터 저장 및 조회
  - Hot Zone 이외의 검색 쿼리 처리
- **Hot DB (Port 3312)**:
  - 트래픽이 높은 특정 구역(Hot Zone)의 `Geohash Index` 전용 저장소
  - 예: 강남(`wydm`), 홍대(`wydm`...)

### Request Routing
1. 상세 로직은 `SearchService`와 `MysqlGeohashIndexRepository` 참조.
2. 요청된 좌표의 `Geohash Prefix`가 **Hot Zone 설정**(`geohash_prefix` 테이블)에 존재하면 **Hot DB**로 쿼리를 라우팅합니다.
3. 그 외의 요청은 **Primary DB**로 라우팅됩니다.

## Requirements
- Java 17+
- Gradle 8.10+ (or use Gradle Wrapper)
- Docker & Docker Compose

## Run

### 1. Database 실행 (Primary & Hot)
```bash
docker compose -f docker-compose.mysql.yml up -d
```
- Primary MySQL: `localhost:3311`
- Hot MySQL: `localhost:3312`

### 2. Application 실행
`mysql` 프로필을 활성화하여 실행합니다.
```bash
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun
```

### 3. Verification (Test)
강남역(Hot Zone, `wydm`) 근처 검색 시 Hot DB로 라우팅되는지 확인합니다.

```bash
# 강남역, 반경 1km 검색 -> Hot Zone 감지되어 500m로 제한됨
curl "http://localhost:8080/v1/search/nearby?latitude=37.498&longitude=127.027&radius=1000"
```

## Configuration

### `src/main/resources/application-mysql.yml`
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3311/weeks1  # Primary DB
    username: weeks1
    password: weeks1

app:
  datasource:
    hot:
      jdbc-url: jdbc:mysql://localhost:3312/weeks1 # Hot DB (Note: property is jdbc-url)
      username: weeks1
      password: weeks1
```

## Monitoring (Prometheus + Grafana)
1. 애플리케이션 실행
2. 모니터링 스택 실행
   ```bash
   docker compose -f monitoring/docker-compose.yml up -d
   ```
3. 접속
   - Prometheus: `http://localhost:9090`
   - Grafana: `http://localhost:3000` (`admin` / `admin`)

## Scripts (Dummy Data)

### Python Generator
```bash
# 가볍게 테스트용 데이터 생성
python3 scripts/gen_dummy_data.py \
  --business-count 5000 \
  --query-count 20000 \
  --output-dir data/dummy-sample
```

### Data Loader
Hot Zone 테스트를 위해 특정 구역(`wydm`) 데이터를 Hot DB에 적재해야 할 수 있습니다. (현재 스크립트는 Primary 위주)
기본 로더:
```bash
scripts/load_dummy_data_mysql.sh --data-dir data/dummy --create-schema --truncate
```
*Note: Hot DB 스키마 및 데이터 동기화는 현재 별도 수동 설정이 필요할 수 있습니다. (실습 범위)*

## Load Test (k6)

### 목적
- 목적 1: k6 실행/리포트 파이프라인이 정상 동작하는지 검증
- 목적 2: 현재 로컬 단일 인스턴스 기준 안정 처리량(Stable QPS) 구간 파악
- 목적 3: SLA 기준(`references/sla.md`)과의 차이 확인
- 목적 4: 목표 트래픽(5,000 QPS) 대비 병목 구간 사전 식별

### 준비
- 앱 실행: `SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun`
- SLA 기준: `references/sla.md`

### 실행
```bash
scripts/run_k6.sh --rate 1000 --duration 60s
```

기본값:
- `rate=5000`
- `duration=60s`
- `base-url=http://localhost:8080`

결과:
- `loadtest/results/<timestamp>/summary.json`
- `loadtest/results/<timestamp>/report.md`

로컬에 `k6`가 없으면 Docker(`grafana/k6`)로 자동 실행됩니다.

### Ramp Test (1k -> 2k -> 5k)
```bash
scripts/run_k6_ramp.sh
```

경계 구간 확인용 실행 예시:
```bash
scripts/run_k6_ramp.sh \
  --rates 200,500,1000 \
  --durations 60s,60s,120s \
  --stage-names low,mid,high
```

결과:
- `loadtest/results/ramp_<timestamp>/ramp_report.md`
- `loadtest/results/ramp_<timestamp>/ramp_summary.csv`
- stage별 raw 결과: `loadtest/results/ramp_<timestamp>/<stage>/<timestamp>/`

### 최근 실행 결과 요약 (2026-02-10)

아래 수치는 모두 로컬 단일 인스턴스 환경에서 측정한 결과입니다.

| Run | Stage | Target QPS | Achieved QPS | p95(ms) | Dropped | Verdict | 비고 |
|---|---|---:|---:|---:|---:|---|---|
| `ramp_200_500_1000` | low | 200 | 200.00 | 54.43 | 0 | PASS | - |
| `ramp_200_500_1000` | mid | 500 | 499.99 | 9.16 | 0 | PASS | - |
| `ramp_200_500_1000` | high | 1000 | 1012.82 | 58.15 | 592 | FAIL | dropped 발생으로 FAIL |
| `ramp_200_500_1000_rerun_20260210_104737` | low | 200 | 200.00 | 19.19 | 0 | PASS | - |
| `ramp_200_500_1000_rerun_20260210_104737` | mid | 500 | 499.96 | 70.20 | 0 | PASS | - |
| `ramp_200_500_1000_rerun_20260210_104737` | high | 1000 | 972.05 | 225.63 | 3047 | FAIL | `p95<=150ms` 미충족 + dropped 증가 |

주요 아티팩트:
- `loadtest/results/ramp_200_500_1000/ramp_report.md`
- `loadtest/results/ramp_200_500_1000/ramp_summary.csv`
- `loadtest/results/ramp_200_500_1000_rerun_20260210_104737/ramp_report.md`
- `loadtest/results/ramp_200_500_1000_rerun_20260210_104737/ramp_summary.csv`

### 목적별 해석
- 목적 1 (실행 파이프라인 검증): 달성. `run_k6.sh`, `run_k6_ramp.sh`, summary/report 생성이 정상 동작함.
- 목적 2 (안정 처리량 파악): 현재 결과 기준으로 `500 QPS`까지는 안정, `1000 QPS`는 불안정.
- 목적 3 (SLA 갭 확인): 1000 QPS 재실행에서 `p95 225.63ms`로 SLA(`<=150ms`) 미충족.
- 목적 4 (5,000 QPS 대비): 현재 안정 구간(<=500 QPS)과 목표(5,000 QPS) 사이 갭이 큼.
