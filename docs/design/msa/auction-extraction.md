# MSA 1단계 — Auction Service 추출 가이드

> **이 문서는 "직접 구현하기 위한 가이드"다.** 코드 완제품이 아니라, auction 도메인을 어떻게 떼어낼지 — 무엇을 소유/참조로 나누고, 끊어지는 결합을 어떤 통신으로 대체하고, 어떤 순서로 옮길지 — 판단 기준과 계약(contract)을 정리한다. 실제 Java 구현은 읽는 사람이 이 가이드를 따라 작성한다.
>
> 전제: [Strangler 전략, 서비스당 DB](./local-run.md) · [CI/CD·검증 정책](../../operations/ci-cd-policy.md) · [아키텍처 경계](../architecture.md). auction을 첫 추출 대상으로 삼은 근거는 [부하 테스트 결론](../testing.md)(단일 인기 경매의 지속 경합 한계).

> **📌 territory는 외부 참조 (map은 독립 서비스)** — [목표 토폴로지](./README.md)에서 **map은 독립 `map-service`**다(territory를 8개 도메인이 참조하는 공유 커널이라 auction에 병합하지 않음). 따라서 auction은 territory를 **항상 외부 참조**한다 — 아래 territory 관련 서술(ID+스냅샷·`/internal/territories/*`·읽기 모델)은 임시 배관이 아니라 **최종 형태**다.
> - territory 소유자는 전환 내내 **모놀리식**(map 잔류)이다 → auction은 모놀리식의 territory API를 호출(DB 공유 아님), 표시용 값은 스냅샷으로 복사.
> - 전환 **최후에 map이 map-service로 분리**되면, auction의 호출 대상만 모놀리식 → map-service로 바뀐다(계약 형태는 동일).

---

## 0. 왜 auction부터, 무엇을 조심하나

auction은 **쓰기 경합이 가장 심한 도메인**(입찰)이라 분리 1순위다. 동시에, 아래에서 보듯 **결합이 얕지 않다** — 그래서 "단순히 패키지를 옮기면 되는" 작업이 아니다. 이 가이드의 목적은 그 결합을 하나씩 끊는 방법을 정하는 것이다.

핵심 난이도 한 줄 요약: **지금은 입찰·정산이 단일 DB·단일 트랜잭션 안에서 map·user·building·season을 자유롭게 조인/수정하는데, 서비스가 갈리면 그게 전부 불가능해진다.**

---

## 1. 현재 결합 지도 (As-Is, 코드 근거)

`domain/auction` 이 실제로 의존하는 타 도메인 (import 빈도 기준):

| 대상 도메인 | 무엇을 | 어디서 | 성격 |
|---|---|---|---|
| **user** | `User`, `Wallet`, `UserRepository`, `WalletRepository` | 입찰(AP 잠금/환불), 정산(AP 소비), 닉네임 표시 | **쓰기+읽기, 비관적 락** |
| **map** | `Territory`, `Continent`, `TerritoryRepository` | 정산(영토 점유), 목록/상세(좌표·대륙·등급 표시) | **쓰기+읽기** |
| **building** | `BuildingInstance`, `BuildingType` (+Repo) | 정산 시 초기 성 생성(`createInitialCastle`) | **쓰기** |
| **season** | `Season`, `SeasonRepository` | 정산 시 활성 시즌 조회·기록 | **읽기** |
| **notification** | `NotificationService` | 입찰 밀림/낙찰/유찰 알림 | **쓰기(호출)** |
| **military** | `TerritoryLostEvent` | 성 파괴로 영토 상실 시 auction이 수신 | **인바운드 이벤트** |
| global | `DistributedLock`(Redisson), `SimpMessagingTemplate`(STOMP), `ApiResponse`/`ErrorCode` | 입찰 직렬화, 실시간 브로드캐스트 | 공통 인프라 |

auction이 **소유**하는 것: `Auction`, `AuctionBid`, `AuctionHistory`, `AuctionStatus`, `AuctionPolicy`.

### 결정적 코드 지점 2개

**① 입찰 — `AuctionService.placeBid()`** (동기·원자·비관적 락)
- 신규 입찰자 + 이전 최고 입찰자의 **두 지갑을 순서대로 비관적 락**(`lockWallets`, 데드락 회피용 id 정렬)
- 이전 입찰자 잠금 AP **환불**(`refundLockedAp`) + 신규 입찰자 AP **잠금**(`lockAp`) — **한 트랜잭션 안에서**
- AP는 **차감이 아니라 잠금(escrow)** 이다. 밀려난 입찰자는 즉시 환불, 최종 승자만 정산 때 소비.

**② 정산 — `AuctionLifecycleService.settleAuction()`** (단일 트랜잭션이 4개 도메인 관통)
- `territory.occupy(winner, occupiedUntil, protectedUntil)` — map 쓰기
- `createInitialCastle(territory)` — building 쓰기
- `wallet.consumeLockedAp(finalPrice)` — user 쓰기(비관적 락)
- `seasonRepository.findActiveSeason` — season 읽기
- `AuctionHistory` 저장 — auction 쓰기
- `eventPublisher.publishEvent(AuctionSettledEvent / TerritoryHoldStartedEvent)` — **이미 이벤트 seam**
- 알림 + STOMP(`/sub/user/../auction-result`, `/sub/map/update`) — afterCommit

> 좋은 소식: 정산 일부는 **이미 `ApplicationEvent`로 발행**된다. 이건 서비스 간 메시지로 바꾸기 가장 쉬운 지점이다. 나쁜 소식: territory 점유·성 생성·AP 소비는 **직접 호출/직접 쓰기**라 여기가 진짜 분리 대상이다.

---

## 2. 소유 vs 참조 경계 (Auction Service가 가질 것)

원칙: **auction-DB는 auction이 소유한 테이블만.** 타 도메인 데이터는 **ID로만 참조**하고, 표시용으로 필요한 값은 **입찰/정산 시점의 스냅샷으로 비정규화**해 저장한다(읽기 시점 조인 제거).

| 엔티티 | 소유? | 타 도메인 FK 제거 후 |
|---|---|---|
| `Auction` | ✅ | `territory` 관계 → `territoryId:Long` + 스냅샷(`coordX`,`coordY`,`continentName`,`grade`) / `currentBidder` → `currentBidderId:Long` + `currentBidderNickname` |
| `AuctionBid` | ✅ | `bidder` → `bidderId:Long` + `bidderNickname`(스냅샷) |
| `AuctionHistory` | ✅ | `territory`/`winner`/`season` → `territoryId`/`winnerId`/`seasonId` + 이미 저장 중인 `finalPrice`·`wonAt` |

**왜 스냅샷인가:** `getAuctions`/`getAuctionDetail`/`getMyBids`가 지금은 `auction.getTerritory().getContinent().getDisplayName()`, `bidder.getNickname()` 같은 **JPA 관계 네비게이션**으로 표시 데이터를 얻는다. 서비스가 갈리면 이 조인이 불가능하다. 선택지:
- **(A) 스냅샷 비정규화 (권장)** — 경매 생성/입찰 시 좌표·등급·닉네임을 auction-DB에 복사. 읽기가 자기 DB로 끝나 빠르고 단순. 대신 원본 변경(닉네임 수정 등) 반영이 지연됨 → auction 표시엔 허용 가능.
- (B) API 컴포지션 — 조회 때 map/user-service 호출로 조합. 데이터 최신이지만 읽기마다 네트워크 + 장애 전파.
- (C) 게이트웨이 조합 — 진입점에서 합침. 복잡.

> 표시 정확도가 치명적이지 않은 경매 목록/상세는 **(A)** 로 시작한다. 닉네임 변경 같은 드문 갱신은 user-service가 발행하는 이벤트로 auction 스냅샷을 나중에 동기화할 수도 있으나, **1단계에선 하지 않는다**(과설계).

---

## 3. 네 개의 분리 난제와 해법

### 난제 1 — 입찰 AP 잠금/환불 (동기·원자 복합 연산)

`placeBid`의 지갑 조작은 **두 지갑에 대한 원자적 {이전 환불 + 신규 잠금}**이다. 이건 user-service가 자기 DB·자기 락으로 처리해야 한다. auction이 쪼개서 호출하면 원자성이 깨진다.

**해법:** user-service에 **복합 원자 엔드포인트** 하나를 둔다 — auction이 동기 호출.

```
POST /internal/wallets/bid-escrow            (user-service, 내부 전용)
body: {
  bidderId, bidAmount,                       # 신규 입찰자 잠금액
  previousBidderId?, previousAmount?          # 밀려난 입찰자 환불액 (없으면 시작가 입찰)
}
→ 200 {ok:true}                              # 두 지갑을 한 트랜잭션·비관적 락으로 처리
→ 409 {code: INSUFFICIENT_AP}                # 잔액 부족 → auction이 입찰 거부
```

user-service가 `lockWallets`(id 정렬 데드락 회피) + `refundLockedAp` + `lockAp`를 **자기 트랜잭션 안에서** 그대로 수행한다. auction은 결과 코드만 본다.

**남는 문제(미니 사가):** escrow 성공 → auction이 bid 저장 실패하면 escrow가 붕 뜬다. 처리:
1. **순서**: escrow(user) 성공 → auction bid 저장. 저장 실패 시 auction이 **보상 호출** `POST /internal/wallets/bid-escrow/cancel`로 방금 escrow를 되돌린다.
2. 또는 **idempotency key**(=auctionId+bidSeq)를 escrow에 실어, 재시도·중복을 user-service가 흡수.

> 1단계 권장: 동기 호출 + 실패 시 보상 호출 + idempotency key. 완전한 2PC는 도입하지 않는다.

### 난제 2 — 낙찰 정산 (분산 트랜잭션 → Saga)

`settleAuction`의 단일 트랜잭션을 **여러 서비스에 걸친 사가(saga)**로 바꾼다. auction이 오케스트레이터가 된다(정산을 촉발하는 주체이므로).

권장 흐름 (**오케스트레이션 사가**, 동기 명령 + 비동기 통지 혼합):

```
auction: 경매 종료 감지 → 자기 DB에 Auction.status=SETTLING 기록(사가 시작)
  1) → user-service   [동기] POST /internal/wallets/consume-locked  {winnerId, finalPrice}
  2) → map-service    [동기] POST /internal/territories/{id}/occupy {winnerId, occupiedUntil, protectedUntil}
  3) → building-service(=현재 building 도메인) [동기 or 이벤트] 초기 성 생성
  4) auction: AuctionHistory 저장 + status=SETTLED
  5) → [비동기 이벤트] auction.settled 발행
        · season/ranking 소비 (기존 AuctionSettledEvent / TerritoryHoldStartedEvent 대체)
        · notification 소비 (WIN/LOSE)
        · map broadcast, user auction-result (STOMP)
```

- **동기(1~3)**: 정합성이 중요한 상태 변경(AP 소비·영토 점유). 하나라도 실패하면 **보상**: 이미 성공한 단계 역연산(예: 2 실패 시 1의 AP 소비를 되돌리는 `refund-consumed`), auction status=SETTLE_FAILED로 두고 재시도 큐/관리자 개입.
- **비동기(5)**: 지연 허용(알림·랭킹·브로드캐스트). Redis pub/sub 이벤트로 발행, 소비 서비스가 각자 처리. 실패해도 정산 자체는 성립.

> 어디까지 동기로 할지가 설계의 핵심. **돈·소유권(AP·영토)은 동기+보상**, **파생·통지(랭킹·알림·실시간)는 비동기**로 가르는 게 기준이다.

### 난제 3 — 읽기 모델 (목록/상세의 조인)

[2절](#2-소유-vs-참조-경계-auction-service가-가질-것)의 **스냅샷 비정규화(A)** 로 해결. auction-DB만으로 `getAuctions`/`getAuctionDetail`/`getMyBids`가 완결되게 한다. 타 서비스 조회 없이 읽기가 끝나야 경합·장애 격리 이점이 산다.

### 난제 4 — 인바운드 이벤트 & STOMP 소유권

- **military → auction**: 현재 `TerritoryLostEvent`를 auction이 in-process로 수신(성 파괴 → 재경매 트리거 등). 이건 **서비스 간 이벤트 구독**으로 바뀐다 — military(현재 모놀리식)가 Redis로 발행, auction이 구독.
- **STOMP 브로드캐스트**: `/sub/auction/{id}`(입찰), `/sub/user/{id}/auction-result`(정산)는 auction이 소유·발행한다. 단, 로컬 SimpleBroker는 **인스턴스 로컬**이라 서비스가 갈리면 브로커 공유가 안 된다 → [chat-broker-strategy](../chat-broker-strategy.md)의 Redis STOMP relay 전환이 이 시점에 필요해질 수 있다(1단계에선 auction이 자기 STOMP 엔드포인트를 갖는 선에서 시작, 크로스 서비스 브로드캐스트가 필요해지면 relay 도입).

---

## 4. 통신 계약 초안 (구체 스펙)

> `/internal/*` 는 **서비스 간 내부 전용** — 게이트웨이/외부에 노출 금지. 인증은 서비스 간 신뢰(네트워크 격리 + 공유 시크릿 헤더)로 단순화. 아래는 **계약의 형태**이며, 필드는 구현하며 확정한다.

### 동기 REST (auction → 모놀리식)

| 목적 | 엔드포인트 | 요청 | 성공/실패 |
|---|---|---|---|
| 입찰 escrow | `POST /internal/wallets/bid-escrow` | bidderId, bidAmount, previousBidderId?, previousAmount?, idempotencyKey | 200 / 409 INSUFFICIENT_AP |
| escrow 취소(보상) | `POST /internal/wallets/bid-escrow/cancel` | idempotencyKey | 200 |
| 낙찰 AP 소비 | `POST /internal/wallets/consume-locked` | winnerId, finalPrice, auctionId | 200 / 409 |
| 소비 보상 | `POST /internal/wallets/refund-consumed` | winnerId, finalPrice, auctionId | 200 |
| 영토 점유 | `POST /internal/territories/{id}/occupy` | winnerId, occupiedUntil, protectedUntil | 200 / 409 |
| 점유 보상 | `POST /internal/territories/{id}/release` | auctionId | 200 |

### 비동기 이벤트 (Redis pub/sub)

| 이벤트 | 발행 | 구독 | 페이로드(초안) |
|---|---|---|---|
| `auction.settled` | auction | season·ranking·notification | auctionId, winnerId, seasonId, finalPrice, territoryId, grade, wonAt |
| `auction.settled.map` | auction | (STOMP relay) | territoryId, coordX, coordY, winnerId, winnerNickname |
| `territory.lost` | military | auction | territoryId, reason, at |

기존 in-process `AuctionSettledEvent`·`TerritoryHoldStartedEvent`가 `auction.settled`로 승격된다. [websocket 카탈로그](../../../frontend/.claude/rules/websocket.md)와 짝을 유지할 것.

---

## 5. 트랜잭션·동시성 재설계

| 현재(모놀리식) | 분리 후 |
|---|---|
| `@DistributedLock(lock:auction:{id})` — 입찰 직렬화 | **유지**(auction-service 내부, Redis 공유). 락 범위는 auction 자기 상태 + escrow 호출까지 |
| Wallet 비관적 락(`findByIdWithLock`) — auction 트랜잭션 안 | **user-service로 이동**. escrow/consume 엔드포인트 **내부**에서 락 |
| 정산 단일 `@Transactional` | **auction 로컬 트랜잭션 + 사가**. 각 서비스가 자기 로컬 트랜잭션만 보장, 전체는 보상으로 정합 |

주의: 분산락(Redisson)과 user-service의 Wallet 비관적 락을 **이중으로 같은 것을 직렬화하지 않기**([concurrency 규칙](../../../backend/.claude/rules/concurrency.md)). 분산락은 "동일 경매 입찰 직렬화", Wallet 락은 "동일 지갑 잔액 정합" — 충돌 단위가 다르다.

---

## 6. 구현 순서 (이 순서로 진행 권장)

Strangler라 **모놀리식은 계속 돌아간다**. auction만 떼되, 각 단계 끝마다 검증한다.

1. **스캐폴딩** → `services/auction-service/` Spring Boot 프로젝트 + Dockerfile + 자기 `build.gradle`(jacoco 포함). `docker-compose.msa.yml`에 `auction-service` + `auction-postgres` 추가.
   - 검증: 빈 서비스가 `/actuator/health` 뜸.
2. **코드 이동** → `domain/auction` 소스를 서비스로 복사. 타 도메인 참조를 **컴파일 에러로 드러나게** 두고, 하나씩 [2절] 경계대로 ID+스냅샷으로 치환.
   - 검증: auction-service 단독 컴파일 통과.
3. **DB 분리** → auction 테이블만 auction-DB로. FK 제거, 스냅샷 컬럼 추가(Flyway 마이그레이션 auction-service 소유).
   - 검증: auction-service가 auction-postgres에만 붙어 기동.
4. **입찰 경로 치환** → Wallet 직접 접근 제거 → `/internal/wallets/bid-escrow` 동기 호출 + 보상. 모놀리식(user)에 해당 엔드포인트 구현.
   - 검증: 격리 테스트(escrow 목킹) + **계약 테스트**([9절]).
5. **정산 경로 치환** → 단일 트랜잭션 → 사가(동기 명령 + 이벤트). 모놀리식에 occupy/consume 엔드포인트 + Redis 구독자.
   - 검증: 계약 테스트 + 로컬 풀스택 스모크(경매 1건 낙찰 e2e).
6. **인바운드 이벤트/STOMP** → military `TerritoryLostEvent` → Redis 구독으로 전환. auction STOMP 엔드포인트 정리.
7. **모놀리식에서 auction 제거** → 프론트/게이트웨이 라우팅을 `/api/v1/auctions/**` → auction-service로. 모놀리식의 auction 패키지 삭제.
   - 검증: 회귀(경매 목록·입찰·정산·내 입찰) 전 경로 수동 QA + 스모크.

각 단계는 **독립 PR**로. 브랜치/커밋은 `[TYPE][auction]` 도메인 스코프([git 규칙](../../../.claude/rules/git.md)).

---

## 7. 계약 테스트 도구 — Spring Cloud Contract (권장)

| | Spring Cloud Contract | Pact |
|---|---|---|
| 스택 적합 | ✅ 양쪽 다 Spring/JVM, Gradle 통합 자연스러움 | 폴리글랏일 때 강점(현재 불필요) |
| 흐름 | provider가 계약 정의 → consumer 스텁 자동 생성 | consumer 주도 계약 |
| 결론 | **채택** — 모두 Spring이므로 도구 일관성·러너 통합이 이득 | 폴리글랏 확장 시 재검토 |

계약 테스트가 검증하는 것: auction(consumer)이 기대하는 `/internal/wallets/bid-escrow` 요청/응답 형태를 **모놀리식(provider)을 띄우지 않고** CI에서 검증. [정책 5.4](../../operations/ci-cd-policy.md#54-msa-검증-전략-테스트-피라미드)의 "계약" 계층이 이것.

---

## 8. 검증 매핑 (CI/CD 정책과 연결)

| 계층 | auction 추출에서 | 도구 |
|---|---|---|
| 격리 | auction-service 서비스 로직(escrow/사가 호출은 목킹) | JUnit + auction-postgres(Testcontainers) |
| 계약 | escrow/occupy/consume 엔드포인트 요청·응답 형태 | Spring Cloud Contract |
| 풀스택 스모크 | 경매 1건 입찰→낙찰 e2e (compose up) | 러너 or 로컬 [local-msa](./local-run.md) |

---

## 9. 열린 결정 / 리스크

| 항목 | 상태 | 메모 |
|---|---|---|
| STOMP 크로스 서비스 브로드캐스트 | 미정 | SimpleBroker는 인스턴스 로컬. 필요 시 [Redis relay](../chat-broker-strategy.md) |
| 사가 실패 복구 | 초안 | 동기 단계 보상 + status=SETTLE_FAILED + 재시도. 관리자 강제정산(`forceSettle`) 경로도 이관 필요 |
| 서비스 간 인증 | 미정 | `/internal/*` 공유 시크릿 헤더 vs mTLS. 로컬은 공유 헤더로 시작 |
| 스냅샷 최신성 | 허용 | 닉네임 등 변경 반영 지연 감수. 필요 시 후속 이벤트 동기화 |
| building 분리 시점 | 후속 | `createInitialCastle`는 현재 building 도메인. 1단계엔 모놀리식에 두고 동기 호출 |

---

## 관련 문서
- [로컬 MSA 구동](./local-run.md) · [CI/CD·검증 정책](../../operations/ci-cd-policy.md)
- [아키텍처·MSA 경계](../architecture.md) · [브로커 전략](../chat-broker-strategy.md)
- [동시성 규칙](../../../backend/.claude/rules/concurrency.md)
