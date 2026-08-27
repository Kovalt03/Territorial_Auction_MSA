# Auction 추출 — 이관 추적 체크리스트

> auction을 분리하며 **다른 서비스로 옮긴 동작**과 **아직 그쪽에 구현되지 않은 것**을 추적한다. "코드를 주석으로 남기기"의 대체물 — 기능이 조용히 사라지지 않게 하는 살아있는 등록부다. 각 항목이 실제 구현되면 체크한다.

관련: [auction-extraction.md](./auction-extraction.md)

---

## 1. `auction.settled` 이벤트 소비자 (auction 발행 → 각 서비스 구현 필요)

정산 시 auction이 `auction.settled` 이벤트를 발행한다. 아래 동작들은 **auction에서 제거됐고, 이 이벤트를 받아 각 소비자가 재현**해야 한다.

이벤트 페이로드: `auctionId, territoryId, coordX, coordY, winnerId, winnerNickname, finalPrice, grade, runnerUpIds`

| 소비자 | 구현할 동작 | 원본(모놀리식 참조) | 상태 |
|---|---|---|---|
| **notification-service** | 낙찰자에 `AUCTION_WIN` 알림 "({x},{y}) 영토를 낙찰받았습니다! 낙찰가 {finalPrice} AP." | `notifyAuctionResult` | ⬜ |
| **notification-service** | runnerUpIds 전원에 `AUCTION_LOSE` 알림 "({x},{y}) 영토 경매에서 낙찰에 실패했습니다." | `notifyAuctionResult` | ⬜ |
| **map-service** | `/sub/map/update`에 `OCCUPIED` 브로드캐스트 (territoryId, coord, winnerId, winnerNickname) | settleAuction afterCommit | ⬜ |
| **realtime(push)** | `/sub/user/{winnerId}/auction-result` WIN, runnerUp들에 LOSE | settleAuction afterCommit | ⬜ |
| **ranking-service** | `AuctionSettledEvent`(winnerId, seasonId, finalPrice) — **시즌은 ranking이 귀속** | `publishSettlementEvents` | ⬜ |
| **ranking-service** | `TerritoryHoldStartedEvent`(winnerId, seasonId, territoryId, grade) | `publishSettlementEvents` | ⬜ |

> ⚠️ 그래서 이벤트에 **`grade`** 를 반드시 포함해야 한다(ranking이 씀). 빠뜨리면 이 기능이 조용히 죽는다.

**`auction.bid` 이벤트** — 입찰 시 발행(`AuctionBidBroadcast`). auction-service엔 WebSocket이 없으므로 실시간 브로드캐스트를 소비 서비스가 담당:

| 소비자 | 동작 | 상태 |
|---|---|---|
| realtime(push) | `/sub/auction/{auctionId}`에 입찰 브로드캐스트 | ⬜ |

## 2. 모놀리식 `/internal/*` 엔드포인트 (auction이 동기 호출 → 모놀리식 구현 필요, Part B)

| 엔드포인트 | 하는 일 | 호출부 | 상태 |
|---|---|---|---|
| `POST /internal/wallets/bid-escrow` | 이전 입찰자 환불 + 신규 입찰자 잠금(원자적), 닉네임 반환 | `WalletClient.bidEscrow` | ⬜ |
| `POST /internal/wallets/bid-escrow/cancel` | escrow 보상(롤백) | (보상) | ⬜ |
| `POST /internal/wallets/consume-locked` | 낙찰자 lockedAp 소비 | `WalletClient.consumeLocked` | ⬜ |
| `POST /internal/territories/{id}/occupy` | 영토 점유(winner, occupiedUntil, protectedUntil) | `TerritoryClient.occupy` | ⬜ |
| `POST /internal/territories/{id}/release` | 영토 IDLE 복귀(nextAuctionAt) | `TerritoryClient.release` | ⬜ |
| `POST /internal/buildings/initial-castle` | 낙찰 영토에 초기 성 생성 | `BuildingClient.createInitialCastle` | ⬜ |

**실패 응답 계약** (auction 클라이언트가 이 상태코드로 도메인 예외를 매핑함 — Part B는 반드시 지킬 것):

| 엔드포인트 | 404 | 409 |
|---|---|---|
| bid-escrow | USER_NOT_FOUND | INSUFFICIENT_AP |
| consume-locked | USER_NOT_FOUND | INSUFFICIENT_AP |
| territories/occupy·release | TERRITORY_NOT_FOUND | — |
| buildings/initial-castle | BUILDING_TYPE_NOT_FOUND | (idempotent, 200 처리) |

## 3. 보상(Saga) — 미구현

| 항목 | 내용 | 상태 |
|---|---|---|
| 입찰 escrow 보상 | escrow 성공 후 auction 트랜잭션 롤백 시 escrow 취소 | ⬜ |
| 정산 보상 | occupy 성공 후 consume/castle 실패 시 occupy 되돌리기(release) | ⬜ |

## 4. auction에서 **제거**하고 다른 곳으로 넘긴 흐름

| 흐름 | 원래 위치 | 어디로 | 상태 |
|---|---|---|---|
| 경매 **생성**(createAuction/createPendingAuctions/createInitialCastle 트리거) | LifecycleService | map "territory-ready" 이벤트 구독으로 재설계(#3) | ⬜ |
| 영토 **점유 만료**(releaseExpiredTerritories) | LifecycleService | map-service 소유 | ⬜ |
| 관리자 강제 정산·취소·목록 | LifecycleService + AuctionAdminController | 모놀리식 잔류(또는 auction 자체 admin 후속) | ⬜ |
| 인증(@AuthenticationPrincipal) | 컨트롤러 | 게이트웨이 X-User-Id 헤더 | 🔄 임시(헤더) |

## 5. 알려진 임시/제약

- STOMP: SimpleBroker는 인스턴스 로컬 → 크로스서비스 실시간은 Redis relay 필요([chat-broker-strategy](./../chat-broker-strategy.md)). 그래서 브로드캐스트를 event로 넘김.
- 계약 테스트(Spring Cloud Contract)로 §2 엔드포인트 계약 고정 예정.
- **버그**: `Auction.currentBidderId`에 `@Column(nullable = false)`가 있어 current_bidder_id가 NOT NULL — 입찰 없는 경매 저장 불가. 입찰 전엔 null이어야 하므로 nullable로 수정 필요.
- `RedisEventPublisher`: `AuctionSettledEvent`(record) 직렬화 코덱 확인 필요 — 기본 코덱에서 record가 안 풀리면 토픽에 `JsonJacksonCodec` 지정. 소비자와 코덱 일치해야 함.
