# 성능 · 부하 테스트 가이드 (MSA)

> ✅ **MSA 전환 완료 기준.** 부하 대상은 **게이트웨이(`docker-compose.msa.yml`, :8090)** 와 개별 서비스다. 구 모놀 부하 스택(`docker-compose.loadtest.yml`)·`LoadTestSeeder`·`backend/src/gatling/`는 모놀리식과 함께 제거됐다 — 아래 방법론(측정 → 판단 → 1변경 최적화)은 그대로 재사용하되, 실행 경로는 MSA 토폴로지 기준으로 재구성한다.
>
> 부하 시나리오 모듈은 아직 재구성되지 않았다([3. 도구·배치](#3-도구--배치-현행) 참고). 이 문서는 **판단 기준을 먼저 세우고 측정하는 절차**를 정의한다 — 합격 기준 없이 돌린 부하 테스트는 숫자만 남고 결론이 없다.

---

## 0. 전제: MSA에서 무엇을 측정하는가

모놀리식 시절의 질문("어디를 먼저 떼어낼까")은 끝났다. MSA에서 부하 테스트가 답해야 하는 질문은 다르다.

| 질문 | 무엇을 본다 |
|---|---|
| 각 서비스가 자기 SLO를 지키나 | 서비스별 격리 부하 — 자기 DB만 붙은 상태의 상한 |
| **서비스 간 동기 호출**이 병목인가 | auction→user(지갑 에스크로)·auction→map(점유)·combat→season 등 `/internal/**` 왕복 지연 |
| 이벤트 전파가 실시간을 따라오나 | Kafka consumer lag, Redis pub/sub → realtime-service WS 반영 지연 |
| 게이트웨이가 포화 지점인가 | 전 트래픽이 통과하는 단일 진입점(:8090)의 라우팅·필터 오버헤드 |

**분산 시스템의 지연은 합산된다.** 단일 요청이 여러 서비스를 거치면 각 홉의 p99가 곱이 아니라 꼬리로 누적된다 — 격리 측정만으로는 사용자 체감을 알 수 없어, 게이트웨이 경유 end-to-end 측정을 반드시 함께 한다.

---

## 1. 4단계 진행 순서

```
Phase 0  계측 준비   → 각 서비스가 자기 상태를 말할 수 있게 만든다
Phase 1  베이스라인 → 서비스별·end-to-end 성능을 숫자로 고정한다
Phase 2  부하 테스트 → 한계점과 붕괴 지점(어느 서비스가 먼저 무너지나)을 찾는다
Phase 3  최적화 루프 → 1회 1변경, 매번 재측정
```

**Phase 0을 건너뛰지 않는다.** 계측 없이 부하만 주면 "느리다"는 것만 알고 "어느 서비스가 왜 느린지"는 모른다 — 서비스가 늘수록 이 비용이 커진다.

---

## 2. Phase 0 — 계측 준비

### 2.1 서버 지표 노출 (서비스별 actuator)

각 Spring Boot 서비스에 actuator + micrometer-prometheus를 노출한다(공통 `build.gradle` 또는 `libs/common`).

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

```yaml
# 각 서비스 application-loadtest.yml
management:
  endpoints.web.exposure.include: health,metrics,prometheus
  metrics.distribution.percentiles-histogram.http.server.requests: true
```

서비스마다 확인할 지표:

| 지표 | 의미 |
|---|---|
| `http.server.requests` | 엔드포인트별 응답시간 분포 (서비스별로 분리 수집) |
| `hikaricp.connections.pending` | **그 서비스 DB 커넥션 대기 — 가장 흔한 병목 신호** |
| `http.client.requests` (또는 WebClient 타이머) | **서비스 간 동기 호출 지연** — 홉별로 어디서 시간이 새는지 |
| `spring.kafka.listener` / consumer lag | 이벤트 소비 지연 (map·season·notification·ranking·realtime) |
| `jvm.gc.pause`, `jvm.memory.used` | GC 정지·힙 |

> Prometheus + Grafana 상시 스택은 **단발 측정에는 붙이지 않는다** — `/actuator/metrics` 폴링으로 충분하다. 다만 MSA는 지표가 서비스마다 흩어지므로, 여러 회차를 반복하거나 상시 관측이 필요해지면 Prometheus scrape + Grafana 대시보드 도입이 모놀 때보다 이르게 정당화된다.

### 2.2 느린 쿼리 가시화 (서비스 DB별)

```yaml
# 대상 서비스 application-loadtest.yml
spring.jpa.properties.hibernate:
  generate_statistics: true
  session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 100
logging.level.org.hibernate.stat: DEBUG
```

PostgreSQL은 **서비스별 DB 컨테이너 각각에서** 확인한다:

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
SELECT calls, mean_exec_time, total_exec_time, query
FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20;
```

N+1은 `generate_statistics`의 쿼리 실행 횟수로 잡는 게 가장 빠르다.

### 2.3 부하 전용 환경

**개발 볼륨에 부하를 주지 않는다.** `docker-compose.msa.yml`을 loadtest 오버라이드로 기동한다:

- 서비스별 PG/Redis/Kafka를 별도 볼륨(예: compose override 파일)로 격리
- 각 서비스 `SPRING_PROFILES_ACTIVE=loadtest`
- 회차 간 초기화는 볼륨 폐기(`down -v`) 또는 서비스별 `ddl-auto: create`로 — **각 서비스 DB를 독립적으로** 리셋한다

### 2.4 부하용 시드 데이터 (서비스별 시더)

빈 DB 대상 측정은 의미가 없다 — 인덱스 효과가 드러나지 않는다. 시드는 **각 서비스가 자기 DB에** 넣는다(다른 서비스 DB를 직접 채우지 않는다). 서비스 경계를 넘는 참조는 ID 값·이벤트로 채운다.

목표 볼륨 (초기 기준, 필요 시 조정):

| 데이터 | 소유 서비스 | 건수 | 이유 |
|---|---|---|---|
| users | user-service | 1,000 | 동시 접속 상한의 10배 |
| territories | map-service | 2,500 (고정) | 50×50 맵 전체 |
| 점유된 영토 | map-service | 1,500 | 맵 조회 응답 크기를 실사용에 근접 |
| auctions (진행 중) | auction-service | 200 | 입찰 경합 대상 |
| building_instances | combat-service | 5,000 | 영토 상세·생산 스케줄러 부하 |
| unit_instances | combat-service | 10,000 | 공성 정산 시 스택 조회 부하 |
| chat_messages | social-service | 100,000 | 페이지네이션·인덱스 검증 |

각 시더는 `@Profile("loadtest")`로 작성한다 — 운영 프로파일에 절대 실리지 않게.

### 2.5 인증 토큰 준비

모든 보호 엔드포인트가 JWT를 요구한다. 시나리오마다 로그인부터 하면 **user-service가 병목처럼 보이는 착시**가 생긴다. 사전에 N명 로그인해 토큰을 CSV로 덤프하고 Gatling `feeder`로 주입한다. 게이트웨이 경유 시에도 발급 토큰을 그대로 `Authorization: Bearer`로 쓴다.

---

## 3. 도구 · 배치 (현행)

**Gatling (Java DSL)** 을 사용한다 — 백엔드와 같은 Java 17, Gradle 플러그인으로 `./gradlew gatlingRun` 일원화, HTML 리포트 기본.

| 후보 | 판단 |
|---|---|
| **Gatling Java DSL** | ✅ 채택 |
| Gatling Scala DSL | ❌ Scala 도입 비용만 추가 |
| k6 | 대안(JS). 별도 바이너리·리포트 파이프라인 |
| JMeter | ❌ GUI 중심, Git 관리 나쁨 |

**⚠️ 시나리오 모듈은 재구성이 필요하다.** 구 `backend/src/gatling/`은 제거됐다. MSA에서는 두 배치 전략 중 하나를 택한다(착수 시 확정):

1. **전용 부하 모듈** `loadtest/`(Gradle 서브프로젝트) — 게이트웨이(:8090) end-to-end 시나리오를 한곳에 모은다. 분산 사용자 여정(로그인→맵→입찰)을 실제 라우팅대로 검증하기 좋다.
2. **서비스별 `src/gatling/`** — 개별 서비스 격리 부하(자기 DB만). 서비스 단위 회귀·용량 산정에 적합.

권장: 두 층을 병행 — 서비스 격리는 (2), 사용자 체감·홉 누적은 (1)로 게이트웨이를 때린다.

---

## 4. 테스트 유형과 합격 기준

### 4.1 유형별 목적

| 유형 | 부하 형태 | 목적 | 언제 |
|---|---|---|---|
| **Smoke** | 1~5 VU, 1분 | 시나리오 자체가 맞는지 검증 | 시나리오 작성 직후 매번 |
| **Load** | 목표 부하 유지, 10분 | 정상 부하에서 SLO 충족 여부 | 베이스라인·회귀 확인 |
| **Stress** | 포화까지 단계 증가 | **한계 TPS와 붕괴 지점**(먼저 무너지는 서비스) | 용량 산정 |
| **Spike** | 순간 10배 급증 | 급증 후 회복 여부 | 경매 마감 직전 상황 재현 |
| **Soak** | 중간 부하, 1시간+ | 메모리·커넥션·**consumer lag** 누적 | 최적화 완료 후 1회 |

**Smoke를 먼저 통과시키지 않은 시나리오는 실행하지 않는다.** 잘못된 시나리오로 얻은 숫자가 가장 비싸다.

### 4.2 SLO (합격 기준)

게이트웨이 경유 end-to-end 기준. 서비스 격리 측정은 홉이 하나 빠지므로 더 빡빡하게 잡는다.

| 지표 | 기준(게이트웨이 경유) | 근거 |
|---|---|---|
| p95 응답시간 (조회) | < 250ms | 게이트웨이 1홉 오버헤드 포함, 체감 즉시성 경계 |
| p95 응답시간 (쓰기) | < 600ms | 락 경합 + 서비스 간 동기 호출 1~2홉 허용 |
| p99 응답시간 | < 1.2s | 꼬리 지연 상한(분산 누적 고려) |
| 이벤트 반영 지연 (쓰기→WS/프로젝션) | p95 < 2s | Kafka/Redis 전파 + realtime push |
| 에러율 | < 1% | 4xx 비즈니스 예외는 제외하고 집계 |
| 목표 동시 사용자 | 100 VU | 개인 프로젝트 규모 기준 |

Gatling assertion으로 코드에 박아 CI/야간 워크플로우에서도 판정 가능하게 한다:

```java
setUp(scn.injectOpen(rampUsers(100).during(60)))
    .protocols(httpProtocol)   // baseUrl = http://localhost:8090 (gateway)
    .assertions(
        global().responseTime().percentile(95).lt(250),
        global().failedRequests().percent().lt(1.0)
    );
```

> ⚠️ **4xx를 실패로 세지 않도록 주의.** 입찰 시나리오는 "입찰가 부족"(400)이 정상적으로 대량 발생한다. `check(status().in(200, 400))`으로 기대 상태를 명시하고 5xx만 실패로 잡는다.

---

## 5. 시나리오 우선순위

부하 특성이 다른 것부터 만든다. 전 서비스를 다 만들 필요 없다. **경로는 게이트웨이(:8090) 기준**으로 적는다.

### 우선순위 1 — 경매 입찰 (auction-service, + user·map 동기 호출)

**가장 중요.** 분산락(Redisson `auction:lock:{id}`)이 걸린 고경합 쓰기 경로이자, **서비스 간 동기 호출이 엮인 대표 경로**다.

```
POST /api/v1/auctions/{id}/bid   → auction-service → user-service(지갑 에스크로)
```

- 100 VU가 **동일 경매 1건**에 동시 입찰 → 분산락 직렬화 한계 측정
- 200개 경매에 분산 입찰 → 락 없는 상태의 상한 측정 (두 숫자의 차 = 락 비용)
- **user-service 왕복 지연**이 p99에 얼마나 실리는지 홉별로 분리
- 검증: 최종 `current_price`가 입찰 성공 건수와 정합한지(lost update 부재), 지갑 잠금 AP 정합

### 우선순위 2 — 맵 조회 (map-service)

**최대 트래픽 경로.** 비로그인 포함 전원이 진입 시 호출.

```
GET /api/v1/map/grid
GET /api/v1/map/territories/{id}
```

- 2,500개 영토 응답 직렬화 비용 + N+1 유무
- '경매중' 뱃지는 map-service 로컬 read-model(`territory_auction_status`)에서 읽는다 — **auction-service를 때리지 않는지** 확인(핫패스 격리 검증)
- Redis 캐시(`adjacent_bonus`) 히트율, 캐시 on/off 비교

### 우선순위 3 — 자산 쓰기 (combat-service)

분산락/금고 차감 경합 확인.

```
POST /api/v1/military/units          (생산 — 위치 저장소 차감)
POST /api/v1/military/research/{id}  (연구 — 금고 차감)
POST /api/v1/vault/transfer          (금고 이전)
```

- 같은 유저 동시 요청 → 데드락·타임아웃 여부
- **락 대기 시간**이 p99를 얼마나 끌어올리는지

### 우선순위 4 — 실시간 브로드캐스트 (realtime-service)

fan-out 특성이 REST와 완전히 다르다. WS는 realtime-service가 소유한다.

```
WS  /ws  → SUBSCRIBE /sub/map/update, /sub/auction/{id}
```

- 100 커넥션 구독 상태에서 쓰기 이벤트 1건 발생 → **이벤트 발생(소유 서비스) → Kafka/Redis → realtime push** 까지의 지연
- realtime-service SimpleBroker(단일 인스턴스) 한계 확인 → 다중 인스턴스 + Redis relay 전환 판단 근거([chat-broker-strategy.md](./chat-broker-strategy.md))

> ⚠️ SockJS + STOMP는 Gatling 작성 난도가 높다. REST 3종을 먼저 끝내고 착수한다. 여의치 않으면 별도 Java WS 클라이언트로 대체 — 목적은 전파 지연 측정이지 도구 통일이 아니다.

### 스케줄러·이벤트 간섭 (별도)

스케줄러는 이제 **소유 서비스 안에** 있다 — combat-service `SiegeScheduler`(60초)·생산, map-service 토지세/생산 배치 등. 부하와 **동시에** 돌려야 실제 상황이 된다. 배치가 도는 순간 그 서비스 p99가 튀는지, Kafka consumer lag가 벌어지는지 확인한다.

---

## 6. 실행 절차

```bash
# 1. 부하 환경 기동 (loadtest 오버라이드로 전 스택 또는 대상 서비스만)
docker compose -f docker-compose.msa.yml -f docker-compose.loadtest.yml up -d --build

# 2. 서비스별 시드 주입 (각 서비스 loadtest 프로파일)
#    각 서비스가 자기 DB에 @Profile("loadtest") 시더로 적재

# 3. 토큰 덤프 (user-service 로그인 → CSV)
./scripts/dump-tokens.sh > loadtest/resources/tokens.csv

# 4. Smoke (게이트웨이 대상)
./gradlew :loadtest:gatlingRun --simulation=com.territorial.load.AuctionSmokeSimulation

# 5. 본 측정
./gradlew :loadtest:gatlingRun --simulation=com.territorial.load.AuctionLoadSimulation

# 6. 리포트: build/reports/gatling/{simulation}-{timestamp}/index.html
```

> `docker-compose.loadtest.yml`(오버라이드)과 `loadtest/` 모듈은 재구성 대상이다([3절](#3-도구--배치-현행)). 경로는 확정 시 이 문서에 반영한다.

### 매 회차 지켜야 할 것

| 규칙 | 이유 |
|---|---|
| **워밍업 30초 후부터 집계** | JIT·커넥션 풀·**consumer 리밸런스** 초기 구간은 느리다 |
| 회차 간 DB 초기화 | 데이터 누적으로 조건이 달라진다(서비스별로 각각) |
| 부하 생성기와 스택 분리 권장 | 같은 머신이면 Gatling이 CPU를 먹어 **클라이언트가 먼저 포화**된다 |
| Docker Desktop 리소스 확인 | 전 스택 기동은 무겁다 — 할당 CPU/메모리를 리포트에 기록 |
| 1회차는 버린다 | 캐시·페이지 캐시가 비어 항상 느리다 |

---

## 7. 결과 판독 — 증상별 원인

| 증상 | 유력 원인 | 확인 방법 | 대응 |
|---|---|---|---|
| TPS 평평 + 응답시간만 증가 | 커넥션 풀 포화 | `hikaricp.connections.pending` > 0 (**어느 서비스인지**) | 풀 크기, 트랜잭션 구간 단축 |
| 특정 엔드포인트만 느림 | N+1·인덱스 누락 | hibernate 쿼리 수, `pg_stat_statements` | Fetch Join / `@EntityGraph` / 인덱스 |
| **홉 하나에서만 지연** | 서비스 간 동기 호출 병목 | `http.client.requests` 타이머, 호출 대상 서비스 지표 | 호출 축소·캐시·비동기화 |
| 쓰기 p99만 급등 | 분산락 경합 | Redisson 락 대기, `pg_locks` | 락 구간 축소, 경합 분산 |
| WS 반영이 느림 | consumer lag·relay 병목 | Kafka lag, Redis pub/sub 지연 | 파티션/컨슈머 증설, relay 전환 |
| 갈수록 느려짐 (Soak) | 메모리·커넥션·lag 누수 | `jvm.memory.used` 우상향, lag 우상향 | 힙 덤프, 컨슈머 처리량 점검 |
| 에러율만 급증, 응답 빠름 | 커넥션/스레드 고갈 후 즉시 거절 | 5xx 종류·게이트웨이 타임아웃 | 타임아웃·큐·회로차단 |

**클라이언트 포화를 항상 먼저 의심한다.** 그리고 MSA에서는 **"어느 서비스"** 를 항상 붙여 읽는다 — 병목은 요청이 통과한 여러 서비스 중 하나다.

---

## 8. Phase 3 — 최적화 루프

```
측정 → 병목 1개 특정(어느 서비스·어느 홉) → 1개만 수정 → 재측정 → 비교 기록
```

| 규칙 | 이유 |
|---|---|
| **한 번에 하나만 바꾼다** | 두 개를 바꾸면 어느 쪽이 효과였는지 모른다 |
| 개선 전/후 숫자를 같은 리포트에 남긴다 | "빨라진 것 같다"는 근거가 아니다 |
| 효과 없는 변경은 되돌린다 | 근거 없는 복잡도는 부채다 |
| 측정 없이 최적화하지 않는다 | 추측한 병목은 대체로 틀리다 |

예상 개선 후보 (측정 전까지는 **가설**):

- 맵 조회 응답 캐싱(Redis) — 읽기 비중이 압도적이면
- 서비스 간 동기 호출 축소·캐시 — 홉 지연이 크면
- 영토/유닛 목록 Fetch Join — N+1이 확인되면
- realtime-service 다중 인스턴스 + Redis relay — 브로드캐스트 지연이 SLO를 넘으면
- Kafka 파티션·컨슈머 증설 — consumer lag가 지속되면
- HikariCP 풀 크기(서비스별) — pending이 지속되면

---

## 9. 프론트엔드 반응 성능

백엔드와 별개로 측정한다. 도구는 Gatling이 아니다.

| 대상 | 도구 | 기준 |
|---|---|---|
| 초기 로딩 | Lighthouse | LCP < 2.5s, TBT < 200ms |
| 번들 크기 | `npm run build` 출력 | vendor 청크별 gzip 크기 추이 |
| 렌더 병목 | React DevTools Profiler | 단일 상호작용 커밋 > 16ms 구간 |
| 실시간 갱신 | 수동 + Profiler | STOMP 수신 시 불필요 리렌더 |

**최우선은 50×50 그리드 렌더.** 2,500개 셀을 DOM으로 그리면 상호작용마다 리렌더 비용이 든다. `React.memo` / 가상화 / Canvas 전환 중 무엇이 필요한지는 Profiler 수치로 판단한다.

---

## 10. 리포트 규칙

```
report/load/YYYY-MM-DD-{scope}-{slug}.md
report/perf/YYYY-MM-DD-{scope}-{slug}.md
```

### 템플릿

```markdown
# {서비스/여정} 부하 테스트 — {유형}

## 환경
- 커밋: {sha}
- 하드웨어 / Docker 할당 리소스
- 대상: 게이트웨이 경유 / 서비스 격리 (명시)
- 기동 서비스 목록 + 시드 볼륨

## 시나리오
- 부하 형태, VU 수, 지속 시간, 경로

## 결과
| 지표 | 값 | SLO | 판정 |
|---|---|---|---|
| p95 | | < 250ms | ✅/❌ |
| p99 | | < 1.2s | |
| TPS | | | |
| 이벤트 반영 지연 | | < 2s | |
| 에러율 | | < 1% | |

## 서버 지표 (서비스별)
- HikariCP pending / http.client 홉 지연 / Kafka lag / JVM heap / GC / 쿼리 수

## 병목 분석
- 특정된 병목 1개(어느 서비스·어느 홉)와 근거

## 조치 및 재측정
| 변경 | 전 | 후 |
```

**환경 정보 없는 리포트는 재현 불가** — 커밋 SHA, 리소스 할당, 대상(게이트웨이/격리)과 기동 서비스 목록은 반드시 기록한다.

---

## 11. 완료 기준 (성능 안정화)

- [ ] 우선순위 1~3 시나리오의 베이스라인 리포트 확보(게이트웨이 경유 + 서비스 격리)
- [ ] 한계 TPS와 붕괴 지점 파악 — **먼저 무너지는 서비스**와 그 원인
- [ ] 서비스 간 동기 호출 홉별 지연 분해
- [ ] 이벤트 전파 지연(쓰기→프로젝션/WS) SLO 충족 확인
- [ ] Soak 1시간에서 메모리·커넥션·consumer lag 누수 없음 확인
- [ ] SLO 미달 병목에 대해 1변경 최적화 후 재측정 완료

---

## 관련 문서

- 단위·프론트 테스트 전략 → [테스트 전략](./testing.md)
- 브로커 전환 전략 → [chat-broker-strategy.md](./chat-broker-strategy.md)
- CI/CD·부하 워크플로우 분리 → [ci-cd-policy.md](../operations/ci-cd-policy.md)
- 아키텍처·서비스 경계 → [시스템 아키텍처](./architecture.md)
- 로컬 MSA 구동 → [msa/local-run.md](./msa/local-run.md)
