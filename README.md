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
