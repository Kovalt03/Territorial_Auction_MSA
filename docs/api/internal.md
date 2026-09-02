# Internal API — 서비스 간 계약

> **⚙️ MSA 전용.** 서비스 간 동기 호출과 비동기 이벤트의 계약을 정의한다. 공개 API가 아니다.
>
> - **게이트웨이 우회 + 내부 토큰**: `/internal/**`는 외부에 노출하지 않고, 호출자는 `X-Internal-Service-Token`에 `INTERNAL_API_SECRET`을 담는다. 수신 서비스는 고정 시간 비교로 검증한다.
> - **응답 래핑 없음**: 성공 시 `ApiResponse` 래핑 없이 원시 DTO(또는 204/200)를 반환한다. 오류만 각 서비스의 `GlobalExceptionHandler`가 `ApiResponse.error`로 감싼다.
> - **상태코드 단일 소스**: 각 서비스의 `ErrorCode.httpStatus`를 따른다. `INSUFFICIENT_AP`은 **409**(검증 400과 구분).

관련: [MSA 전환 허브](../design/msa/README.md) · [이관 추적](../design/msa/auction-migration-tracking.md)

---

## 1. user-service `/internal/*` (다른 서비스가 호출)

지갑 **AP**와 **신원**(User·Wallet)은 user-service가 소유한다. GP/금고(GlobalVault)는 building/모놀리식 소유.

### 1-1. 지갑 — 경매 escrow (auction-service가 호출)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/wallets/bid-escrow` | `{auctionId, bidderId, bidAmount, previousBidderId?, previousAmount?}` | `{bidderNickname}` | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP |
| POST | `/internal/wallets/bid-escrow-compensate` | `{auctionId, bidderId, bidAmount, previousBidderId?, previousAmount?}` | 200 | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP / WALLET_COMMAND_CONFLICT |
| POST | `/internal/wallets/consume-locked` | `{winnerId, finalPrice, auctionId}` | 200 | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP / WALLET_COMMAND_CONFLICT |
| POST | `/internal/wallets/refund-locked` | `{bidderId, amount, auctionId}` | 200 | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP / WALLET_COMMAND_CONFLICT |

- **bid-escrow**: 이전 최고 입찰자 잠금 AP **환불** + 신규 입찰자 AP **잠금**을 한 트랜잭션에서. 두 지갑을 **id 오름차순 비관적 락**(데드락 회피). 잔액 부족 시 이전 입찰자는 환불되지 않는다.
- **bid-escrow-compensate**: bid-escrow의 **역전**(saga 보상). 신규 입찰자 잠금 해제 + 이전 입찰자 재잠금. auction-service 입찰 로컬 트랜잭션이 롤백되면(`afterCompletion(STATUS_ROLLED_BACK)`) 동일 요청으로 호출한다.
- **consume-locked**: 낙찰자 잠금 AP 소비(정산). `auctionId`는 추적용.
- **refund-locked**: 관리자 강제 취소 시 현재 입찰자 잠금 AP 환불.
- 이 명령들은 command key와 request fingerprint를 User DB에 기록한다. 동일 key·동일 payload 재시도는 멱등 처리하고, 동일 key·다른 payload는 409 `WALLET_COMMAND_CONFLICT`로 거절하며 금액은 양수여야 한다.

### 1-2. 지갑 — 일반 AP 명령 (모놀리식이 호출)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/wallets/spend` | `{userId, amount, commandKey}` | `{availableAp, lockedAp}` | 400 INVALID_WALLET_AMOUNT · 404 USER_NOT_FOUND · 409 INSUFFICIENT_AP / WALLET_COMMAND_CONFLICT |
| POST | `/internal/wallets/credit` | `{userId, amount, commandKey}` | `{availableAp, lockedAp}` | 400 · 404 · 409 WALLET_COMMAND_CONFLICT |
| POST | `/internal/wallets/adjust` | `{userId, delta, commandKey}` | `{availableAp, lockedAp}` | 404 · 409 INSUFFICIENT_AP / WALLET_COMMAND_CONFLICT |
| GET | `/internal/wallets/{userId}` | — | `{availableAp, lockedAp}` | 404 USER_NOT_FOUND |
| GET | `/internal/wallets/sum-available` | — | `<long>` | — |

- **spend**: 건물·아이템·시즌 등 일반 AP 소비. 모놀리식은 "로컬 작업 먼저 → spend 마지막" 순으로 호출해, spend 실패 시 로컬 `@Transactional`이 함께 롤백되게 한다.
- **credit**: 앞선 소비를 되돌리는 보상(결제 충전 포함). `commandKey`로 멱등.
- **adjust**: 관리자 AP 증감(delta). 결과가 음수면 409.

### 1-3. 신원 — OAuth 프로비저닝 (모놀리식이 호출)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/users/provision-oauth` | `{username, email, nickname}` | `{userId, username, nickname, email}` | 409 DUPLICATE_* |
| POST | `/internal/users/{userId}/status` | `{status}` | 200 | 404 USER_NOT_FOUND |

- OAuth 신규 유저의 신원(User 무비번 + Wallet)을 user-service가 소유(**역전**)한다. 모놀리식 `CustomOAuth2UserService`가 동기 호출로 발급 ID를 받아 로컬 프로젝션(섬·성)을 만든다.
- `username`(=`provider:providerId`) 기준 **멱등**. 생성 시 `UserCreatedEvent` outbox를 발행한다.
- **status 소유**: 유저 상태(ACTIVE/SUSPENDED/WITHDRAWN)는 user-service가 소유한다(로그인 차단이 여기서 먹힌다). 셀프 탈퇴는 `DELETE /api/v1/users/me`(게이트웨이→user-service), admin 정지/탈퇴는 모놀리식 admin이 이 `status` 엔드포인트를 호출한다. 변경은 `user.status-changed`로 프로젝션에 전파.
- **토큰 무효화**: 탈퇴 시 access token을 공유 Redis 블랙리스트(`jwt:blacklist:<token>`)에 넣고, 모놀리식·user-service JWT 필터가 모두 확인해 즉시 무효화한다.

## 2. 모놀리식 `/internal/*` (auction/combat-service가 호출)

영토·시즌 상태는 아직 모놀리식이 소유한다.

### 영토 (map 도메인)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/territories/{id}/occupy` | `{winnerId, occupiedUntil, protectedUntil}` | 200 | 404 TERRITORY_NOT_FOUND |
| POST | `/internal/territories/{id}/release` | `{nextAuctionAt}` | 200 | 404 TERRITORY_NOT_FOUND |
| GET | `/internal/territories/{id}/combat-context` | — | `{territoryId, ownerId, coordX, coordY, status, protectedUntil, gridSize, zone1Radius, zone2Radius}` | 404 TERRITORY_NOT_FOUND |
| GET | `/internal/territories/owners/{userId}/combat-contexts` | — | 위 DTO 배열 | — |

### 시즌 (season 도메인)

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| GET | `/internal/seasons/users/{userId}/combat-benefit` | — | `{buildTimeReductionPct, extraBuilders}` | — |

- combat-context는 combat-service의 건물·유닛·공성 port가 함께 쓰는 영토 스냅샷이다.
- combat-benefit은 활성 시즌 패스가 없으면 두 값 모두 0이다.

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

## 4. combat-service `/internal/*`

### 경매 정산 후 초기 성

| Method | Path | Body | 응답 | 오류 |
|---|---|---|---|---|
| POST | `/internal/buildings/initial-castle` | `{territoryId}` | 200 | 404 TERRITORY_NOT_FOUND / BUILDING_TYPE_NOT_FOUND |

- auction-service가 직접 호출한다. 낙찰 영토 Zone1 중심에 초기 성을 만들며 이미 성이 있으면 200으로 멱등 처리한다.

### 관리자 combat 위임

모놀리식이 `/api/v1/admin/**` 인증과 감사 로그를 유지하고 아래 원시 DTO 계약으로 데이터 작업만 위임한다.

| Method | Path | 용도 |
|---|---|---|
| GET/POST | `/internal/admin/combat/building-types` | 건물 타입 목록·생성 |
| PATCH/DELETE | `/internal/admin/combat/building-types/{id}` | 건물 타입 수정·삭제 |
| GET/PATCH | `/internal/admin/combat/building-types/{id}/level-specs` | 건물 레벨 스펙 |
| GET/PATCH | `/internal/admin/combat/building-types/{id}/castle-limits` | 성 레벨별 건물 제한 |
| GET | `/internal/admin/combat/unit-types` | 유닛 타입 목록 |
| PATCH | `/internal/admin/combat/unit-types/{id}` | 유닛 타입 수정 |
| GET/PATCH | `/internal/admin/combat/unit-types/{id}/level-specs` | 유닛 연구 스펙 |
| GET | `/internal/admin/combat/resources/total-gp` | 전체 금고·저장소 GP 합계 |
| GET | `/internal/admin/combat/users/{userId}/resources?territoryIds=...` | 사용자 금고 GP·저장 식량 |
| POST | `/internal/admin/combat/resources/gp-adjustments` | `{userId, delta, commandKey}` 관리자 GP 조정 |

- GP 조정은 `combat_commands`에 command key와 fingerprint를 기록한다. 같은 key·같은 요청은 한 번만 반영하고 다른 요청은 409로 거절한다.
- 모든 요청은 gateway에 노출되지 않으며 `X-Internal-Service-Token`이 필수다.

---

## 5. 이벤트 (Kafka durable + Redis realtime)

상태 반영·재처리가 필요한 이벤트는 Kafka로 전달한다. 논리 이벤트 종류는 `event-topic` header에 담고, payload는 JSON 문자열로 직렬화한다. 소비자는 필드명만 일치하는 자체 record로 역직렬화하며 Java 클래스를 공유하지 않는다.

- `territory-auction-ready`: map → auction-service 경매 생성 trigger
- `auction-events`: auction-service → map projection·랭킹/시즌 bridge. record key는 논리 이벤트명이며 `event-topic` header도 함께 사용한다.
- `user-events`: user-service transactional outbox → monolith user projection·HomeIsland bootstrap. record key는 outbox event ID다.
- `combat-events`: combat-service transactional outbox → map·season·notification/realtime bridge. record key는 aggregate ID다.
- `territory-events`: 모놀리식 map → combat-service 영토 상실 처리. record key는 territory ID다.
- Redis pub/sub `auction.bid`, `auction.settled`: 모놀리식 realtime hub의 WebSocket 저지연 전달 전용. durable 소비자는 Kafka를 사용한다.

| Kafka topic / event-topic | 발행 | 페이로드 | 소비 (현행) |
|---|---|---|---|
| `territory-auction-ready` | 모놀리식(map) | `{territoryId, coordX, coordY, continentName, continentId, grade}` | auction-service `auction-territory-ready` group → 경매 생성 |
| `auction-events` / `auction.opened` | auction-service | `{auctionId, territoryId, currentPrice, endAt}` | `backend-map-projection` → 프로젝션 upsert |
| `auction-events` / `auction.bid` | auction-service | `{auctionId, currentPrice, bidderId, bidderNickname, bidAt, endAt, previousBidderId, coordX, coordY}` | `backend-map-projection` 갱신. Redis realtime hub도 동시 수신 |
| `auction-events` / `auction.settled` | auction-service | `{auctionId, territoryId, coordX, coordY, winnerId, winnerNickname, finalPrice, grade, runnerUpIds}` | `backend-ranking-relay` → 랭킹·시즌 bridge. Redis realtime hub도 동시 수신 |
| `auction-events` / `auction.closed` | auction-service | `{auctionId, territoryId}` | `backend-map-projection` → 프로젝션 제거 |
| `user-events` / `user.created` | user-service outbox | `{userId, username, email, nickname}` | `backend-user-projection` → User 읽기 프로젝션·NotificationSetting·UserProfile·HomeIsland·기본 성 생성 |
| `user-events` / `user.updated` | user-service outbox | `{userId, nickname}` | `backend-user-projection` → User 프로젝션 nickname 갱신 |
| `user-events` / `user.status-changed` | user-service outbox | `{userId, status}` | `backend-user-projection` → User 프로젝션 status 갱신 |
| `combat-events` / `combat.siege.declared` | combat-service outbox | `{siegeId, territoryId, coordX, coordY, attackZone, attackerId, attackerNickname, defenderId, defenderNickname, resolveAt}` | `backend-combat-notification` → 알림·WebSocket |
| `combat-events` / `combat.siege.resolved` | combat-service outbox | `{siegeId, territoryId, coordX, coordY, attackZone, attackerId, attackerNickname, defenderId, defenderNickname, isAttackerWin, resultType, attackerUnitsLost, defenderUnitsLost, lootedGp, resolvedAt}` | `backend-combat-notification` → 결과 알림·WebSocket |
| `combat-events` / `combat.territory.takeover-requested` | combat-service outbox | `{siegeId, territoryId, newOwnerId, formerOwnerId, recoveredGp}` | `backend-combat-map` → 영토 인계 |
| `combat-events` / `combat.siege.victory` | combat-service outbox | `{siegeId, attackerId}` | `backend-combat-season` → 시즌 XP |
| `combat-events` / `combat.island.expanded` | combat-service outbox | `{userId, newLevel}` | `backend-combat-notification` → 섬 확장 알림 |
| `territory-events` / `territory.lost` | 모놀리식 map | `{territoryId, formerOwnerId}` | `combat-territory-loss` → 저장 자원 환수·주둔 유닛 퇴각 |

- `user.created`·`user.updated`·`user.status-changed`는 User DB transactional outbox에서 Kafka `user-events`로 발행한다. 모놀리식 구독자는 `event-topic` header로 분기하고 소비자는 `userId` 기준으로 멱등 처리한다.
- combat 공성 이벤트는 Combat DB 변경과 같은 트랜잭션에서 `combat_outbox`에 적재하고 Kafka `combat-events`로 발행한다. `event-topic` header로 논리 이벤트를 구분하며, 발행 성공 전에는 `published_at`을 기록하지 않아 재시도할 수 있다.
- combat outbox 발행기는 `event-id` header도 전송한다. 모놀리식은 `combat_event_receipts`에 `consumerGroup:eventId`를 저장해 알림·시즌·map 부작용을 중복 적용하지 않는다.
- **프로필 쓰기 소유**: 신원 프로필(닉네임·비밀번호)은 user-service가 소유한다. 게이트웨이가 `PATCH /api/v1/users/me/{nickname,password}`만 user-service로 라우팅하고, 나머지 `/api/v1/users/**`(프로필·지갑 조회, AP 충전)는 모놀리식이 서빙한다. 닉네임 변경은 `user.updated`로 프로젝션에 전파한다.

> 정산 시 `grade`는 랭킹이 쓰므로 `auction.settled`에 반드시 포함. 자세한 소비자별 동작은 [이관 추적 §1](../design/msa/auction-migration-tracking.md).

---

## 6. 미구현 (후속)

- **정산 saga 보상**: `occupy` 성공 후 `consume-locked`/`initial-castle` 실패 시 `occupy` 되돌리기(release). 현재는 로깅만.
