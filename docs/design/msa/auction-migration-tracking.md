# Auction 추출 — 이관 추적 체크리스트

> auction을 분리하며 **다른 서비스로 옮긴 동작**과 **아직 그쪽에 구현되지 않은 것**을 추적한다. "코드를 주석으로 남기기"의 대체물 — 기능이 조용히 사라지지 않게 하는 살아있는 등록부다. 각 항목이 실제 구현되면 체크한다.

관련: [auction-extraction.md](./auction-extraction.md) · 서비스 간 계약 [internal.md](../../api/internal.md)

> **✅ 추출 완료 (2026-08).** 아래 §1~§4의 이관 항목은 §3(saga 보상)만 남기고 모두 구현됐고, **모놀리식 auction 도메인은 삭제**됐다. 부하 실측: 경매 쓰기 경합 하 **맵 그리드 조회 p99 26.6ms→2.8ms(~10배 개선)** — 그리드가 auction 테이블 대신 로컬 프로젝션을 읽어 경매 쓰기 경합에서 격리됨.

---

## 1. `auction.settled` 이벤트 소비자 (auction 발행 → 각 서비스 구현 필요)

정산 시 auction이 `auction.settled` 이벤트를 발행한다. 아래 동작들은 **auction에서 제거됐고, 이 이벤트를 받아 각 소비자가 재현**해야 한다.

이벤트 페이로드: `auctionId, territoryId, coordX, coordY, winnerId, winnerNickname, finalPrice, grade, runnerUpIds`

현재(스트랭글러) 소비자는 전부 **모놀리식**이다: WS push·알림·map은 `realtime/AuctionRealtimeSubscriber`, 랭킹·시즌 귀속은 `ranking/AuctionSettledRelaySubscriber`(→ `AuctionSettlementRelayService`가 인프로세스 Spring 이벤트로 중계). 향후 각 서비스로 분리 시 이 소비자들을 이동한다.

| 소비자(현행) | 구현한 동작 | 원본(모놀리식 참조) | 상태 |
|---|---|---|---|
| realtime 허브 | 낙찰자에 `AUCTION_WIN` 알림 | `notifyAuctionResult` | ✅ |
| realtime 허브 | runnerUpIds 전원에 `AUCTION_LOSE` 알림 | `notifyAuctionResult` | ✅ |
| realtime 허브 | `/sub/map/update`에 `OCCUPIED` 브로드캐스트 | settleAuction afterCommit | ✅ |
| realtime 허브 | `/sub/user/{winnerId}/auction-result` WIN, runnerUp들에 LOSE | settleAuction afterCommit | ✅ |
| ranking 브리지 | Spring `AuctionSettledEvent`(winnerId, seasonId, finalPrice) — 경매 소비 랭킹·시즌 XP·미션. seasonId는 모놀리식이 현재 활성 시즌 조회로 채움 | `publishSettlementEvents` | ✅ |
| ranking 브리지 | Spring `TerritoryHoldStartedEvent`(winnerId, seasonId, territoryId, grade, now) — 영토 보유 트로피 | `publishSettlementEvents` | ✅ |

> ⚠️ 이벤트에 **`grade`** 필수(ranking이 씀). ✅ 페이로드에 포함됨.
> ⚠️ 랭킹/시즌 리스너는 `@TransactionalEventListener(AFTER_COMMIT)`라, 브리지는 반드시 **활성 트랜잭션 안**에서 Spring 이벤트를 발행해야 한다(`AuctionSettlementRelayService`가 `@Transactional`).

**`auction.bid` 이벤트** — 입찰 시 발행(`AuctionBidBroadcast`, `previousBidderId`·coord 포함). auction-service엔 WebSocket이 없으므로 소비 서비스가 담당:

| 소비자(현행) | 동작 | 상태 |
|---|---|---|
| realtime 허브 | `/sub/auction/{auctionId}`에 입찰 브로드캐스트 | ✅ |
| realtime 허브 | 이전 최고 입찰자에게 `OUTBID` 알림("입찰이 밀렸습니다") | ✅ |

## 2. 모놀리식 `/internal/*` 엔드포인트 (auction이 동기 호출 → 모놀리식 구현 필요, Part B)

| 엔드포인트 | 하는 일 | 호출부 | 상태 |
|---|---|---|---|
| `POST /internal/wallets/bid-escrow` | 이전 입찰자 환불 + 신규 입찰자 잠금(원자적), 닉네임 반환 | `WalletClient.bidEscrow` | ✅ |
| `POST /internal/wallets/bid-escrow-compensate` | escrow 성공 후 auction 저장 롤백 시 역연산 | `WalletClient.compensateBidEscrow` | ✅ |
| `POST /internal/wallets/consume-locked` | 낙찰자 lockedAp 소비 | `WalletClient.consumeLocked` | ✅ |
| `POST /internal/wallets/refund-locked` | 잠금 AP 환불(관리자 강제 취소) | `WalletClient.refundLocked` | ✅ |
| `POST /internal/territories/{id}/occupy` | 영토 점유(winner, occupiedUntil, protectedUntil) | `TerritoryClient.occupy` | ✅ |
| `POST /internal/territories/{id}/release` | 영토 IDLE 복귀(nextAuctionAt) | `TerritoryClient.release` | ✅ |
| `POST /internal/buildings/initial-castle` | 낙찰 영토에 초기 성 생성 | `BuildingClient.createInitialCastle` | ✅ |

관리자 경매 관리(모놀리식 `/api/v1/admin/auctions` → auction-service `/internal/auctions/*`): 목록·강제정산·강제취소·유저 입찰내역·활성 경매 카운트 모두 ✅. 인증·감사 로그는 모놀리식 유지.

**실패 응답 계약** — 상태코드는 모놀리식 **GlobalExceptionHandler 단일 소스**(ErrorCode.httpStatus)를 따른다. 내부 전용 로컬 핸들러를 두지 않는다. `INSUFFICIENT_AP`은 400→**409(CONFLICT)**로 변경(검증 400과 겹치지 않게, 모놀리식·auction 양쪽 ErrorCode 일치).

| 엔드포인트 | 에러 | HTTP(글로벌) |
|---|---|---|
| bid-escrow | INSUFFICIENT_AP / USER_NOT_FOUND | 409 / 404 |
| consume-locked | INSUFFICIENT_AP / USER_NOT_FOUND | 409 / 404 |
| territories/occupy·release | TERRITORY_NOT_FOUND | 404 |
| buildings/initial-castle | BUILDING_TYPE_NOT_FOUND | 404 (성 존재 시 idempotent→200) |

## 3. 보상(Saga) — 미구현

| 항목 | 내용 | 상태 |
|---|---|---|
| 입찰 escrow 보상 | escrow 성공 후 auction 트랜잭션 롤백 시 `bid-escrow-compensate` 호출 | ✅ |
| 정산 보상 | occupy 성공 후 consume/castle 실패 시 occupy 되돌리기(release) | ⬜ |

## 4. auction에서 **제거**하고 다른 곳으로 넘긴 흐름

| 흐름 | 원래 위치 | 어디로 | 상태 |
|---|---|---|---|
| 경매 **생성** | LifecycleService | ✅ map `territory.auction-ready` 이벤트 발행(모놀리식) → auction 구독 생성(JSON). 모놀리식 createPendingAuctions 비활성 | ✅ |
| 영토 **점유 만료**(releaseExpiredTerritories) | LifecycleService | ✅ map `TerritoryExpiryService`(@Scheduled) 소유. `TerritoryLostEvent`·`TerritoryHoldClosedEvent`도 여기서 발행 | ✅ |
| 상회입찰(OUTBID) 알림 | AuctionService.notifyOutbid | ✅ auction.bid에 previousBidderId 실어 realtime 허브가 알림 | ✅ |
| 관리자 경매 관리(목록·강제정산·강제취소) | LifecycleService+AuctionAdminController | ✅ auction-service `/internal` + 모놀리식 admin 프록시 | ✅ |
| auction 도메인 삭제 | 모놀리식 domain/auction | ✅ 전체 삭제(테이블은 stale 유지) | ✅ |

**단위 테스트**: auction-service `AuctionServiceTest`(입찰 검증 5) + `AuctionLifecycleServiceTest`(강제정산·취소 4) 이식 완료.
| 관리자 강제 정산·취소·목록 | LifecycleService + AuctionAdminController | 모놀리식 잔류(또는 auction 자체 admin 후속) | ⬜ |
| 인증(@AuthenticationPrincipal) | 컨트롤러 | 게이트웨이 X-User-Id 헤더 | 🔄 임시(헤더) |

## 5. 알려진 임시/제약

- **실시간 아키텍처 결정: (A) 이벤트 + realtime 허브** — 비즈니스 서비스는 WS를 모르고 이벤트만 발행, 클라이언트 WS를 소유한 realtime 서비스가 `auction.bid`·`auction.settled`를 구독해 push. (대안 (B) 공유 STOMP relay는 미채택.) → §1의 realtime push 소비자 = 이 허브.
- 계약 테스트(Spring Cloud Contract)로 §2 엔드포인트 계약 고정 예정.
- **버그**: `Auction.currentBidderId`에 `@Column(nullable = false)`가 있어 current_bidder_id가 NOT NULL — 입찰 없는 경매 저장 불가. 입찰 전엔 null이어야 하므로 nullable로 수정 필요.
- `RedisEventPublisher`: `AuctionSettledEvent`(record) 직렬화 코덱 확인 필요 — 기본 코덱에서 record가 안 풀리면 토픽에 `JsonJacksonCodec` 지정. 소비자와 코덱 일치해야 함.
