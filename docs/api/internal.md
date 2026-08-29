# Internal API — 서비스 간 계약

> **⚙️ MSA 전용.** 서비스 간 동기 호출과 비동기 이벤트의 계약을 정의한다. 공개 API가 아니다.
>
> - **게이트웨이 우회 + 내부 토큰**: `/internal/**`는 외부에 노출하지 않고, 호출자는 `X-Internal-Service-Token`에 `INTERNAL_API_SECRET`을 담는다. 수신 서비스는 고정 시간 비교로 검증한다.
> - **응답 래핑 없음**: 성공 시 `ApiResponse` 래핑 없이 원시 DTO(또는 204/200)를 반환한다. 오류만 각 서비스의 `GlobalExceptionHandler`가 `ApiResponse.error`로 감싼다.
> - **상태코드 단일 소스**: 각 서비스의 `ErrorCode.httpStatus`를 따른다. `INSUFFICIENT_AP`은 **409**(검증 400과 구분).

관련: [MSA 전환 허브](../design/msa/README.md) · [이관 추적](../design/msa/auction-migration-tracking.md)

---

## 1. user-service `/internal/wallets/*` (auction-service가 호출)

지갑(AP·GP)은 user-service가 소유한다.

### 지갑 (user 도메인)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/wallets/bid-escrow` | `{auctionId, bidderId, bidAmount, previousBidderId?, previousAmount?}` | `{bidderNickname}` | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP |
| POST | `/internal/wallets/consume-locked` | `{winnerId, finalPrice, auctionId}` | 200 | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP / WALLET_COMMAND_CONFLICT |
| POST | `/internal/wallets/refund-locked` | `{bidderId, amount, auctionId}` | 200 | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP / WALLET_COMMAND_CONFLICT |

- **bid-escrow**: 이전 최고 입찰자 잠금 AP **환불** + 신규 입찰자 AP **잠금**을 한 트랜잭션에서. 두 지갑을 **id 오름차순 비관적 락**(데드락 회피). 잔액 부족 시 이전 입찰자는 환불되지 않는다.
- **consume-locked**: 낙찰자 잠금 AP 소비(정산). `auctionId`는 추적용.
- **refund-locked**: 관리자 강제 취소 시 현재 입찰자 잠금 AP 환불.
- 세 명령은 `auctionId` 기반 command key와 request fingerprint를 User DB에 기록한다. 동일 key·동일 payload 재시도는 200으로 멱등 처리하고, 동일 key·다른 payload는 409 `WALLET_COMMAND_CONFLICT`로 거절하며 금액은 양수여야 한다.

## 2. 모놀리식 `/internal/*` (auction-service가 호출)

영토·건물 상태는 아직 모놀리식이 소유한다.

### 영토 (map 도메인)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/territories/{id}/occupy` | `{winnerId, occupiedUntil, protectedUntil}` | 200 | 404 TERRITORY_NOT_FOUND |
| POST | `/internal/territories/{id}/release` | `{nextAuctionAt}` | 200 | 404 TERRITORY_NOT_FOUND |

### 건물 (building 도메인)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/buildings/initial-castle` | `{territoryId}` | 200 | 404 TERRITORY_NOT_FOUND / BUILDING_TYPE_NOT_FOUND |

- **initial-castle**: 낙찰 영토 Zone1 중심에 초기 성 생성. 이미 성이 있으면 스킵(**idempotent** → 200).

---

## 3. auction-service `/internal/auctions/*` (모놀리식 admin이 호출)

관리자 경매 관리. 모놀리식이 인증·감사 로그를 유지하고 데이터·작업만 위임한다.

| Method | Path | 응답 | 오류 |
|---|---|---|---|
| GET | `/internal/auctions/active-count` | `long` (진행 중 경매 수) | — |
| GET | `/internal/auctions/active?page&size` | 진행 중 경매 페이지 | — |
| GET | `/internal/auctions/bidders/{bidderId}/bids?page&size` | 유저 입찰 이력 페이지 | — |
| GET | `/internal/auctions/bidders/{bidderId}/active-bids` | 유저 진행 중 입찰 목록 | — |
| POST | `/internal/auctions/{auctionId}/force-settle` | 200 | 404 AUCTION_NOT_FOUND · 409 AUCTION_NO_BIDDER_TO_SETTLE |
| POST | `/internal/auctions/{auctionId}/force-cancel` | 200 | 404 AUCTION_NOT_FOUND · 409 AUCTION_ALREADY_SETTLED |

응답 DTO는 모놀리식 admin 응답과 **필드명이 일치**해 그대로 역직렬화된다(스냅샷 필드만으로 구성). force-cancel은 입찰자 잠금 AP 환불(→ user-service `refund-locked`) + 영토 재경매 예약(→ 모놀리식 `release`) + 종료.

---

## 4. 이벤트 (Redis — 공유 인스턴스)

Auction 이벤트는 Redis pub/sub JSON 문자열로 발행한다. `user.created`는 Redis Stream `stream:user-events`로 전달하고 소비 후 ACK한다. 소비자는 필드명만 일치하는 자체 record로 역직렬화한다(클래스 공유 아님).

| Topic | 발행 | 페이로드 | 소비 (현행) |
|---|---|---|---|
| `territory.auction-ready` | 모놀리식(map) | `{territoryId, coordX, coordY, continentName, continentId, grade}` | auction-service → 경매 생성 |
| `auction.opened` | auction-service | `{auctionId, territoryId, currentPrice, endAt}` | 모놀리식 프로젝션 upsert |
| `auction.bid` | auction-service | `{auctionId, currentPrice, bidderId, bidderNickname, bidAt, endAt, previousBidderId, coordX, coordY}` | 프로젝션 갱신 · realtime 허브(`/sub/auction/{id}` push + OUTBID 알림) |
| `auction.settled` | auction-service | `{auctionId, territoryId, coordX, coordY, winnerId, winnerNickname, finalPrice, grade, runnerUpIds}` | realtime 허브(WIN/LOSE push+알림 · map OCCUPIED) · 랭킹·시즌 브리지 |
| `auction.closed` | auction-service | `{auctionId, territoryId}` | 프로젝션 제거 (낙찰·무낙찰 공통) |
| `user.created` | user-service outbox | `{userId, username, email, nickname}` | 모놀리식 User 읽기 프로젝션·NotificationSetting·UserProfile·HomeIsland·기본 성 생성 |

- `user.created`는 User DB transactional outbox에 가입과 함께 저장하고 발행 성공까지 재시도한다. 소비자는 `userId` 기준으로 멱등 처리한다.

> 정산 시 `grade`는 랭킹이 쓰므로 `auction.settled`에 반드시 포함. 자세한 소비자별 동작은 [이관 추적 §1](../design/msa/auction-migration-tracking.md).

---

## 5. 미구현 (후속)

- **정산 saga 보상**: `occupy` 성공 후 `consume-locked`/`initial-castle` 실패 시 `occupy` 되돌리기(release). 현재는 로깅만.
- **escrow 보상**: escrow 성공 후 경매 트랜잭션 롤백 시 취소.
