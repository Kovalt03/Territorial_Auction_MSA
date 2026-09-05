# 성능 · 부하 테스트 가이드

> ⚠️ **이 문서는 모놀리식 기준이라 갱신이 필요하다.** 부하 스택(`docker-compose.loadtest.yml`)·시더(LoadTestSeeder)·Gatling 시나리오(`backend/src/gatling/`)는 모놀리식과 함께 제거됐다. **MSA 부하 테스트는 게이트웨이(`docker-compose.msa.yml`) 대상으로 재구성**해야 한다 — 아래 방법론·시나리오 설계는 재사용하되 실행 경로는 새로 작성한다. 아래 내용은 역사적 참고.

MSA 분리 전 **모놀리식 상태의 성능 베이스라인을 확보**하고, 병목을 제거하기 위한 실행 가이드.

> 이 문서의 목적은 "테스트를 돌리는 법"이 아니라 **판단 기준을 먼저 세우고 측정하는 절차**를 정의하는 것이다.
> 합격 기준 없이 돌린 부하 테스트는 숫자만 남고 결론이 없다.

---

## 0. 전제: 왜 지금 하는가

| 이유 | 설명 |
|---|---|
| MSA 분리 근거 확보 | 분리 후 "빨라졌다"를 주장하려면 분리 전 숫자가 있어야 한다. 베이스라인 없이 쪼개면 개선을 증명할 수 없다 |
| 병목 위치 확인 | 도메인 경계(= 향후 서비스 경계)마다 부하 특성이 다르다. 어디를 먼저 떼어낼지는 측정이 결정한다 |
| 동시성 검증 | 경매 입찰·금고 차감은 비관적 락 경합 구간이다. 단위 테스트는 락을 검증하지 못한다 |

**분리 순서를 성능으로 정한다** — 가장 먼저 포화되는 도메인이 첫 번째 분리 대상.

---

## 1. 4단계 진행 순서

```
Phase 0  계측 준비   → 서버가 자기 상태를 말할 수 있게 만든다
Phase 1  베이스라인 → 현재 성능을 숫자로 고정한다
Phase 2  부하 테스트 → 한계점과 붕괴 지점을 찾는다
Phase 3  최적화 루프 → 1회 1변경, 매번 재측정
```

**Phase 0을 건너뛰지 않는다.** 계측 없이 부하만 주면 "느리다"는 것만 알고 "왜 느린지"는 모른다.

---

## 2. Phase 0 — 계측 준비

### 2.1 서버 지표 노출 (actuator)

현재 `build.gradle`에 actuator가 없다. 추가 필요.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

```yaml
# application-loadtest.yml
management:
  endpoints.web.exposure.include: health,metrics,prometheus
  metrics.distribution.percentiles-histogram.http.server.requests: true
```

이것만으로 얻는 것:

| 지표 | 엔드포인트 | 의미 |
|---|---|---|
| `http.server.requests` | `/actuator/metrics` | 엔드포인트별 응답시간 분포 |
| `hikaricp.connections.pending` | 〃 | **DB 커넥션 대기 — 가장 흔한 병목 신호** |
| `jvm.gc.pause` | 〃 | GC 정지 시간 |
| `jvm.memory.used` | 〃 | 힙 사용량 |

> Prometheus + Grafana 스택은 **지금 붙이지 않는다.** 단발성 측정은 `/actuator/metrics` 폴링으로 충분하고,
> 상시 모니터링이 필요해지는 시점(MSA 분리 이후)에 도입하는 게 비용 대비 합리적이다.

### 2.2 느린 쿼리 가시화

```yaml
# application-loadtest.yml
spring.jpa.properties.hibernate:
  generate_statistics: true
  session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 100
logging.level.org.hibernate.stat: DEBUG
```

PostgreSQL 쪽:

```sql
-- docker exec 로 접속 후
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
-- 측정 후 상위 쿼리 확인
SELECT calls, mean_exec_time, total_exec_time, query
FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20;
```

N+1은 `generate_statistics`의 쿼리 실행 횟수로 잡는 게 가장 빠르다.

### 2.3 부하 전용 환경 분리

**개발 DB에 부하를 주지 않는다.** 데이터가 오염되고 결과도 왜곡된다.

- `docker-compose.loadtest.yml` — postgres/redis를 별도 포트·별도 볼륨으로 기동
- `SPRING_PROFILES_ACTIVE=loadtest`
- `ddl-auto: create` 로 매 회차 초기화 → 회차 간 데이터 누적 방지

### 2.4 부하용 시드 데이터

현재 시더(`TerritorySeeder`, `AuctionSeeder` 등)는 **정적 마스터 데이터만** 만든다.
부하 테스트에는 **유저·보유 자산 볼륨**이 필요하다. 빈 DB 대상 측정은 의미가 없다 — 인덱스 효과가 드러나지 않는다.

목표 볼륨 (초기 기준, 필요 시 조정):

| 데이터 | 건수 | 이유 |
|---|---|---|
| users | 1,000 | 동시 접속 상한의 10배 |
| territories | 2,500 (고정) | 50×50 맵 전체 |
| 점유된 영토 | 1,500 | 맵 조회 응답 크기를 실사용에 근접시킴 |
| auctions (진행 중) | 200 | 입찰 경합 대상 |
| building_instances | 5,000 | 영토 상세·생산 스케줄러 부하 |
| unit_instances | 10,000 | 공성 정산 시 스택 조회 부하 |
| chat_messages | 100,000 | 페이지네이션·인덱스 검증 |

`LoadTestSeeder`를 `@Profile("loadtest")`로 작성한다 — 운영 프로파일에 절대 실리지 않게.

### 2.5 인증 토큰 준비

모든 보호 엔드포인트가 JWT를 요구한다. 시나리오마다 로그인부터 하면 **auth 도메인이 병목처럼 보이는 착시**가 생긴다.

→ 사전에 N명 로그인해 토큰을 CSV로 덤프하고, Gatling `feeder`로 주입한다.

```
# backend/src/gatling/resources/tokens.csv
token
eyJhbGciOi...
```

---

## 3. 도구 선택

**Gatling (Java DSL)** 을 사용한다.

| 후보 | 판단 |
|---|---|
| **Gatling Java DSL** | ✅ 채택. 백엔드와 같은 Java 17 — 새 언어 학습 없음. Gradle 플러그인으로 `./gradlew gatlingRun` 일원화. HTML 리포트 기본 제공 |
| Gatling Scala DSL | ❌ Scala 도입 비용만 추가. 기능 차이 없음 |
| k6 | 대안. JS라 프론트와 친숙하지만 별도 바이너리·별도 리포트 파이프라인이 생김 |
| JMeter | ❌ GUI 중심, 시나리오의 Git 관리가 나쁨 |

```gradle
plugins {
    id 'io.gatling.gradle' version '<설치 시점 최신 버전 확인>'
}
```

구현된 시나리오는 Java DSL이며 `backend/src/gatling/java/`에서 관리한다.

배치 위치: `backend/src/gatling/java/com/territorial/{domain}/{Domain}Simulation.java`

---

## 4. 테스트 유형과 합격 기준

### 4.1 유형별 목적

| 유형 | 부하 형태 | 목적 | 언제 |
|---|---|---|---|
| **Smoke** | 1~5 VU, 1분 | 시나리오 자체가 맞는지 검증 | 시나리오 작성 직후 매번 |
| **Load** | 목표 부하 유지, 10분 | 정상 부하에서 SLO 충족 여부 | 베이스라인·회귀 확인 |
| **Stress** | 포화까지 단계 증가 | **한계 TPS와 붕괴 지점** 확인 | 용량 산정, 분리 대상 선정 |
| **Spike** | 순간 10배 급증 | 급증 후 회복 여부 | 경매 마감 직전 상황 재현 |
| **Soak** | 중간 부하, 1시간+ | 메모리 누수·커넥션 누수 | 최적화 완료 후 1회 |

**Smoke를 먼저 통과시키지 않은 시나리오는 실행하지 않는다.** 잘못된 시나리오로 얻은 숫자가 가장 비싸다.

### 4.2 SLO (합격 기준)

| 지표 | 기준 | 근거 |
|---|---|---|
| p95 응답시간 (조회) | < 200ms | 체감 즉시성 경계 |
| p95 응답시간 (쓰기) | < 500ms | 락 경합 허용 |
| p99 응답시간 | < 1s | 꼬리 지연 상한 |
| 에러율 | < 1% | 4xx 비즈니스 예외는 제외하고 집계 |
| 목표 동시 사용자 | 100 VU | 개인 프로젝트 규모 기준 |

Gatling assertion으로 코드에 박아 CI에서도 판정 가능하게 한다:

```java
setUp(scn.injectOpen(rampUsers(100).during(60)))
    .protocols(httpProtocol)
    .assertions(
        global().responseTime().percentile(95).lt(200),
        global().failedRequests().percent().lt(1.0)
    );
```

> ⚠️ **4xx를 실패로 세지 않도록 주의.** 입찰 시나리오는 "입찰가 부족"(400)이 정상적으로 대량 발생한다.
> `check(status().in(200, 400))` 로 기대 상태를 명시하고, 5xx만 실패로 잡는다. 이걸 놓치면 에러율이 무의미해진다.

---

## 5. 시나리오 우선순위

부하 특성이 다른 4종을 먼저 만든다. 전 도메인을 다 만들 필요 없다.

### 우선순위 1 — 경매 입찰 (`auction`)

**가장 중요.** 비관적 락(`findByIdWithLock`)이 걸린 유일한 고경합 쓰기 경로.

```
POST /api/v1/auctions/{id}/bid
```

- 100 VU가 **동일 경매 1건**에 동시 입찰 → 락 직렬화 한계 측정
- 200개 경매에 분산 입찰 → 락 없는 상태의 상한 측정
- 두 숫자의 차이가 **락이 만드는 비용**이다
- 검증: 최종 `current_price`가 입찰 성공 건수와 정합한지 (lost update 부재 확인)

### 우선순위 2 — 맵 조회 (`map`)

**최대 트래픽 경로.** 비로그인 포함 전원이 진입 시 호출.

```
GET /api/v1/map/grid
GET /api/v1/map/territories/{id}
```

- 2,500개 영토 응답 직렬화 비용 + N+1 유무
- Redis 캐시(`adjacent_bonus`) 히트율
- 캐시 on/off 비교 측정 → 캐시의 실효를 숫자로 확인

### 우선순위 3 — 금고 · 자산 쓰기 (`building` / `military`)

이번에 비관적 락으로 전환한 9개 경로의 경합 확인.

```
POST /api/v1/military/units        (생산 — 위치 저장소 차감)
POST /api/v1/military/research/{id} (연구 — 금고 차감)
POST /api/v1/vault/transfer        (금고 이전)
```

- 같은 유저가 동시에 여러 요청 → 데드락·타임아웃 발생 여부
- **락 대기 시간**이 p99를 얼마나 끌어올리는지

### 우선순위 4 — STOMP 브로드캐스트 (`social` / `map`)

fan-out 특성이 REST와 완전히 다르다.

- 100 커넥션이 `/sub/map/update` 구독 → 영토 점유 1건 발생 시 브로드캐스트 지연
- Simple In-Memory Broker의 한계 확인 → Redis Pub-Sub 전환 판단 근거

> ⚠️ SockJS + STOMP는 Gatling 시나리오 작성 난도가 높다. REST 3종을 먼저 끝내고 착수한다.
> 여의치 않으면 별도 Java 클라이언트 스크립트로 대체해도 된다 — 목적은 브로드캐스트 지연 측정이지 도구 통일이 아니다.

### 스케줄러 부하 (별도)

`SiegeScheduler`(60초), 생산 스케줄러, 토지세 배치는 **부하 테스트와 동시에 돌려야** 실제 상황이 된다.
스케줄러가 도는 순간 p99가 튀는지 확인 — 배치와 실시간 요청의 간섭이 모놀리식의 전형적 문제다.

---

## 6. 실행 절차

```bash
# 1. 부하 환경 기동
docker compose -f docker-compose.loadtest.yml up -d

# 2. 시드 주입 (loadtest 프로파일)
SPRING_PROFILES_ACTIVE=loadtest ./gradlew bootRun

# 3. 토큰 덤프
./scripts/dump-tokens.sh > backend/src/gatling/resources/tokens.csv

# 4. Smoke
./gradlew gatlingRun --simulation=com.territorial.auction.AuctionSmokeSimulation

# 5. 본 측정
./gradlew gatlingRun --simulation=com.territorial.auction.AuctionLoadSimulation

# 6. 리포트: build/reports/gatling/{simulation}-{timestamp}/index.html
```

### 매 회차 지켜야 할 것

| 규칙 | 이유 |
|---|---|
| **워밍업 30초 후부터 집계** | JIT 컴파일·커넥션 풀 초기화 전 구간은 느리다. 포함하면 p95가 왜곡된다 |
| 회차 간 DB 초기화 | 데이터 누적으로 회차마다 조건이 달라진다 |
| 부하 생성기와 서버 분리 권장 | 같은 머신이면 Gatling이 CPU를 먹어 **서버가 아니라 클라이언트가 먼저 포화**된다. 분리가 어려우면 최소한 CPU 사용률을 함께 기록해 클라이언트 포화를 배제 |
| Docker Desktop 리소스 확인 | 기본 할당(CPU 2코어 등)이 상한을 만든다. 측정 전 설정값을 리포트에 기록 |
| 1회차는 버린다 | 캐시·페이지 캐시가 비어 있어 항상 느리다 |

---

## 7. 결과 판독 — 증상별 원인

| 증상 | 유력 원인 | 확인 방법 | 대응 |
|---|---|---|---|
| TPS가 특정 지점에서 평평 + 응답시간만 증가 | 커넥션 풀 포화 | `hikaricp.connections.pending` > 0 | 풀 크기 조정, 트랜잭션 구간 단축 |
| 특정 엔드포인트만 느림 | N+1 또는 인덱스 누락 | hibernate 쿼리 수, `pg_stat_statements` | Fetch Join / `@EntityGraph` / 인덱스 |
| 쓰기 p99만 급등 | 락 경합 | `pg_locks`, 락 대기 시간 | 락 구간 축소, 경합 분산 |
| 시간이 갈수록 느려짐 (Soak) | 메모리·커넥션 누수 | `jvm.memory.used` 우상향, GC 후 미회복 | 힙 덤프 분석 |
| 주기적 스파이크 | 스케줄러 간섭 | 스파이크 주기와 스케줄러 주기 대조 | 배치 분리, 실행 시각 분산 |
| 에러율만 급증, 응답은 빠름 | 커넥션/스레드 고갈 후 즉시 거절 | 5xx 종류 확인 | 타임아웃·큐 설정 |

**클라이언트 포화를 항상 먼저 의심한다.** 부하 생성기가 한계면 서버는 멀쩡한데 숫자만 나빠진다.

---

## 8. Phase 3 — 최적화 루프

```
측정 → 병목 1개 특정 → 1개만 수정 → 재측정 → 비교 기록
```

| 규칙 | 이유 |
|---|---|
| **한 번에 하나만 바꾼다** | 두 개를 바꾸면 어느 쪽이 효과였는지 영원히 모른다 |
| 개선 전/후 숫자를 같은 리포트에 남긴다 | "빨라진 것 같다"는 근거가 아니다 |
| 효과 없는 변경은 되돌린다 | 근거 없는 복잡도는 부채다 |
| 측정 없이 최적화하지 않는다 | 추측한 병목은 대체로 틀리다 |

예상 개선 후보 (측정으로 확인하기 전까지는 **가설일 뿐**):

- 맵 조회 응답 캐싱 (Redis) — 읽기 비중이 압도적이면
- 영토 목록 조회 Fetch Join — N+1이 확인되면
- 채팅 메시지 인덱스 — 페이지네이션이 느리면
- Simple Broker → Redis Pub-Sub — 브로드캐스트 지연이 SLO를 넘으면
- HikariCP 풀 크기 — pending이 지속 발생하면

---

## 9. 프론트엔드 반응 성능

백엔드와 별개로 측정한다. 도구는 Gatling이 아니다.

| 대상 | 도구 | 기준 |
|---|---|---|
| 초기 로딩 | Lighthouse (Chrome DevTools) | LCP < 2.5s, TBT < 200ms |
| 번들 크기 | `npm run build` 출력 | vendor 청크별 gzip 크기 추이 관리 |
| 렌더 병목 | React DevTools Profiler | 단일 상호작용 커밋 > 16ms 구간 |
| 실시간 갱신 | 수동 + Profiler | STOMP 수신 시 불필요 리렌더 |

**최우선 확인 대상은 50×50 그리드 렌더.** 2,500개 셀을 DOM으로 그리면 상호작용마다 리렌더 비용이 발생한다.
`React.memo` / 가상화 / Canvas 전환 중 무엇이 필요한지는 Profiler 수치로 판단한다.

---

## 10. 리포트 규칙

```
report/load/YYYY-MM-DD-{scope}-{slug}.md
report/perf/YYYY-MM-DD-{scope}-{slug}.md
```

### 템플릿

```markdown
# {도메인} 부하 테스트 — {유형}

## 환경
- 커밋: {sha}
- 하드웨어 / Docker 할당 리소스
- 시드 볼륨 (users, auctions, ...)

## 시나리오
- 부하 형태, VU 수, 지속 시간

## 결과
| 지표 | 값 | SLO | 판정 |
|---|---|---|---|
| p95 | | < 200ms | ✅/❌ |
| p99 | | < 1s | |
| TPS | | | |
| 에러율 | | < 1% | |

## 서버 지표
- HikariCP pending / JVM heap / GC / 쿼리 수

## 병목 분석
- 특정된 병목 1개와 근거

## 조치 및 재측정
| 변경 | 전 | 후 |
```

**환경 정보 없는 리포트는 재현이 불가능하다** — 커밋 SHA와 리소스 할당은 반드시 기록한다.

---

## 11. 완료 기준 (MSA 분리 착수 조건)

- [ ] 우선순위 1~3 시나리오의 베이스라인 리포트 확보
- [ ] 한계 TPS와 붕괴 지점 파악 (Stress)
- [ ] Soak 1시간에서 누수 없음 확인
- [ ] SLO 미달 병목에 대해 최적화 후 재측정 완료
- [ ] 도메인별 부하 특성 비교표 작성 → **첫 분리 대상 도메인 결정**

마지막 항목이 이 전체 작업의 산출물이다. 나머지는 그 근거다.

---

## 관련 문서

- 단위·프론트 테스트 전략 → [테스트 전략](./testing.md)
- 브로커 전환 전략 → [chat-broker-strategy.md](./chat-broker-strategy.md)
- 아키텍처·MSA 계획 → [시스템 아키텍처](./architecture.md)
