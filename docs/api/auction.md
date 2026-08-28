# Auction API

> 구현 상태: ✅ 완료. Redis 분산락 적용됨. 입찰 실시간은 이벤트→realtime 허브 경유(아래).
>
> **⚙️ MSA**: 경매는 이제 **auction-service**가 담당한다. 클라이언트는 **게이트웨이(`/api/v1/auctions/**`)**로 호출하고, 게이트웨이가 auction-service로 라우팅 + JWT의 subject를 `X-User-Id`로 주입한다(경로·요청/응답 스키마는 아래 그대로 불변). 입찰 시 실시간 브로드캐스트는 auction-service가 `auction.bid` 이벤트를 발행하고 모놀리식 realtime 허브가 `/sub/auction/{id}`로 push한다. 서비스 간 계약: [internal.md](./internal.md).

## 목차

| Method | Endpoint | 기능 | 구현 | 남은 작업 |
|---|---|---|---|---|
| GET | `/api/v1/auctions` | [경매 목록 조회](#경매-목록-조회) | ✅ | - |
| GET | `/api/v1/auctions/{auctionId}` | [경매 상세 조회](#경매-상세-조회) | ✅ | Redis 캐시 |
| POST | `/api/v1/auctions/{auctionId}/bids` | [입찰하기](#입찰하기) | ✅ | 분산락·실시간 브로드캐스트 완료 |
| GET | `/api/v1/auctions/{auctionId}/bids` | [가격 변동 그래프 데이터](#가격-변동-그래프-데이터) | ✅ | - |
| GET | `/api/v1/auctions/my-bids` | [내 입찰 내역](#내-입찰-내역-조회) | ✅ | - |
| GET | `/api/v1/auctions/territories/{territoryId}` | [영토 경매 이력](#영토-경매-이력-조회) | ✅ | - |

---

## 경매 목록 조회

**GET** `/api/v1/auctions?page={0}&size={20}&continentId={1}&status={BIDDING}&sort=endAt,asc`

- 인증 불필요

### Query Parameters

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | Integer | N | 0 | 페이지 번호 (0-based) |
| `size` | Integer | N | 20 | 페이지 크기 |
| `sort` | String | N | - | 정렬 기준 (예: `endAt,asc` / `endAt,desc`) |
| `continentId` | Long | N | - | 대륙 필터 (`continents.id`) |
| `status` | AuctionStatus | N | - | 경매 상태 필터: `BIDDING` / `IDLE` |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "totalCount": 100,
    "page": 0,
    "size": 20,
    "auctions": [
      {
        "auctionId": 1,
        "territoryId": 5,
        "coordX": 2,
        "coordY": 3,
        "continentName": "크리오 행성",
        "grade": "A",
        "currentPrice": 2000,
        "currentBidderNickname": "입찰왕",
        "endAt": "2026-04-28T12:00:00Z",
        "status": "BIDDING"
      }
    ]
  }
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `totalCount` | Long | 필터 기준 전체 경매 수 | `auctions` 집계 |
| `page` | Integer | 현재 페이지 번호 | 요청 파라미터 |
| `size` | Integer | 페이지 크기 | 요청 파라미터 |
| `auctions[].auctionId` | Long | 경매 ID | `auctions.id` |
| `auctions[].territoryId` | Long | 영토 ID | `territories.id` |
| `auctions[].coordX` | Integer | 그리드 X 좌표 | `territories.coord_x` |
| `auctions[].coordY` | Integer | 그리드 Y 좌표 | `territories.coord_y` |
| `auctions[].continentName` | String | 소속 행성 표시명 | `continents.display_name` |
| `auctions[].grade` | String | 영토 등급 (S/A/B/C/D) | `territory_grades.grade` |
| `auctions[].currentPrice` | Integer | 현재 최고 입찰가 | `auctions.current_price` |
| `auctions[].currentBidderNickname` | String (nullable) | 현재 최고 입찰자 닉네임 | `users.nickname` (없으면 null) |
| `auctions[].endAt` | DateTime | 경매 종료 시각 | `auctions.end_at` |
| `auctions[].status` | AuctionStatus | 경매 상태 | `auctions.end_at > now()` → `BIDDING`, 이하 → `IDLE` |

### 남은 작업
- ~~서비스 구현~~ ✅

---

## 경매 상세 조회

**GET** `/api/v1/auctions/{auctionId}`

- 인증 불필요

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "auctionId": 1,
    "territoryId": 5,
    "coordX": 2,
    "coordY": 3,
    "grade": "A",
    "currentPrice": 2000,
    "currentBidderNickname": "입찰왕",
    "startAt": "2026-04-27T12:00:00Z",
    "endAt": "2026-04-28T12:00:00Z",
    "recentBids": [
      {
        "bidderNickname": "입찰왕",
        "price": 2000,
        "bidAt": "2026-04-27T15:00:00Z"
      }
    ]
  }
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `auctionId` | Long | 경매 ID | `auctions.id` |
| `territoryId` | Long | 영토 ID | `territories.id` |
| `coordX` | Integer | 그리드 X 좌표 | `territories.coord_x` |
| `coordY` | Integer | 그리드 Y 좌표 | `territories.coord_y` |
| `grade` | String | 영토 등급 (S/A/B/C/D) | `territory_grades.grade` |
| `currentPrice` | Integer | 현재 최고 입찰가 | `auctions.current_price` |
| `currentBidderNickname` | String (nullable) | 최고 입찰자 닉네임 | `users.nickname` (없으면 null) |
| `startAt` | DateTime | 경매 시작 시각 | `auctions.start_at` |
| `endAt` | DateTime | 경매 종료 시각 | `auctions.end_at` |
| `recentBids[]` | array | 최근 입찰 내역 (최대 5건, 최신순) | `auction_bids` |
| `recentBids[].bidderNickname` | String (nullable) | 입찰자 닉네임 | `users.nickname` (null = 시작가) |
| `recentBids[].price` | Integer | 입찰 금액 | `auction_bids.price` |
| `recentBids[].bidAt` | DateTime | 입찰 시각 | `auction_bids.bid_at` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | AUCTION_NOT_FOUND | 존재하지 않는 경매 |

### 남은 작업
- ~~서비스 구현~~ ✅
- ⬜ Redis 캐시 (`auction:bid:{auctionId}`)

---

## 입찰하기

**POST** `/api/v1/auctions/{auctionId}/bids`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request

```json
{
  "bidAmount": 2100
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `bidAmount` | Integer | Y | 입찰 금액 (≥ `currentPrice * 1.05` AND ≥ `currentPrice + 10` 동시 만족) |

### 입찰 검증 규칙
- 현재 최고 입찰자 본인은 재입찰 불가
- 입찰 성공 시 `available_ap` → `locked_ap` 이동
- 기존 최고 입찰자는 `locked_ap` → `available_ap` 자동 환불

### Anti-Sniping
- 경매 종료 1분 전 입찰 시 `end_at` 30초 연장
- `max_extend_until` 초과 불가

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "auctionId": 1,
    "newPrice": 2100,
    "endAt": "2026-04-28T12:00:30Z"
  }
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `auctionId` | Long | 경매 ID | `auctions.id` |
| `newPrice` | Integer | 입찰 후 최고 입찰가 | `auctions.current_price` |
| `endAt` | DateTime | 경매 종료 시각 (anti-sniping 적용 후 갱신될 수 있음) | `auctions.end_at` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | AUCTION_NOT_FOUND | 존재하지 않는 경매 |
| 400 | BID_AMOUNT_TOO_LOW | 입찰 금액 미달 |
| 400 | ALREADY_HIGHEST_BIDDER | 이미 최고 입찰자 |
| 400 | INSUFFICIENT_AP | AP 잔액 부족 |
| 400 | AUCTION_ALREADY_ENDED | 이미 종료된 경매 |

### 남은 작업
- ~~서비스 구현~~ ✅
- ~~AP 락/환불 처리~~ ✅ (`lockAp` / `refundLockedAp`)
- ⬜ Redis 분산락 (`auction:lock:{auctionId}`)
- ⬜ 입찰 성공 시 `/sub/auction/{auctionId}` WebSocket 브로드캐스트 (→ `websocket.md` TODO 2번)

---

## 가격 변동 그래프 데이터

**GET** `/api/v1/auctions/{auctionId}/bids`

- 인증 불필요
- 해당 경매의 전체 입찰 내역을 시간순으로 반환 — 프론트 가격 변동 차트용

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "auctionId": 1,
    "bids": [
      {
        "price": 1000,
        "bidAt": "2026-04-27T12:00:00Z",
        "bidderNickname": null
      },
      {
        "price": 1100,
        "bidAt": "2026-04-27T13:00:00Z",
        "bidderNickname": "입찰왕"
      }
    ]
  }
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `auctionId` | Long | 경매 ID | `auctions.id` |
| `bids[]` | array | 전체 입찰 내역 (시간순 ASC) | `auction_bids` |
| `bids[].price` | Integer | 입찰 금액 | `auction_bids.price` |
| `bids[].bidAt` | DateTime | 입찰 시각 | `auction_bids.bid_at` |
| `bids[].bidderNickname` | String (nullable) | 입찰자 닉네임 | `users.nickname` (null = 시작가 레코드, `auction_bids.bidder_id IS NULL`) |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | AUCTION_NOT_FOUND | 존재하지 않는 경매 |

### 남은 작업
- ~~서비스 구현~~ ✅

---

## 내 입찰 내역 조회

**GET** `/api/v1/auctions/my-bids`

**Authorization**: Bearer `{{accessToken}}` (필수)

- 동일 영토에 여러 경매가 존재해도 **영토별 가장 최근 입찰 1건**만 반환 (중복 제거)
- 페이지네이션 없이 전체 목록 반환

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "totalCount": 12,
    "page": 0,
    "size": 12,
    "bids": [
      {
        "auctionId": 1,
        "territoryId": 5,
        "coordX": 2,
        "coordY": 3,
        "myBidAmount": 2100,
        "currentPrice": 2500,
        "isHighestBidder": false,
        "endAt": "2026-04-28T12:00:00Z",
        "status": "BIDDING",
        "grade": "A",
        "continentName": "크리오 행성"
      }
    ]
  }
}
```

> 출처: `auction_bids` + `auctions`

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `totalCount` | Long | 영토별 중복 제거 후 전체 건수 | `auction_bids` 집계 |
| `page` | Integer | 고정값 0 | - |
| `size` | Integer | `totalCount`와 동일 | - |
| `bids[].auctionId` | Long | 경매 ID | `auction_bids.auction_id` |
| `bids[].territoryId` | Long | 영토 ID | `territories.id` |
| `bids[].coordX` | Integer | 그리드 X 좌표 | `territories.coord_x` |
| `bids[].coordY` | Integer | 그리드 Y 좌표 | `territories.coord_y` |
| `bids[].myBidAmount` | Integer | 영토별 가장 최근 입찰 금액 | `auction_bids.price` |
| `bids[].currentPrice` | Integer | 현재 최고 입찰가 | `auctions.current_price` |
| `bids[].isHighestBidder` | Boolean | 내가 현재 최고 입찰자 여부 | `auctions.current_bidder_id = userId` 파생 |
| `bids[].endAt` | DateTime | 경매 종료 시각 | `auctions.end_at` |
| `bids[].status` | AuctionStatus | 경매 상태 | `auctions.end_at > now()` → `BIDDING`, 이하 → `IDLE` |
| `bids[].grade` | String | 영토 등급 (S/A/B/C/D) | `territory_grades.grade` |
| `bids[].continentName` | String | 소속 행성 표시명 | `continents.display_name` |

### 남은 작업
- ~~서비스 구현~~ ✅

---

## 영토 경매 이력 조회

**GET** `/api/v1/auctions/territories/{territoryId}`

- 인증 불필요
- 해당 영토의 과거 낙찰 이력을 최신순으로 반환

> **URL 설계 근거**: 경매 이력은 Auction 도메인 데이터(`auction_histories`)를 다루므로
> `/map/territories/{territoryId}/auctions` 대신 Auction 네임스페이스 안에 배치.
> AuctionController 단일 진입점 유지.

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "territoryId": 5,
    "histories": [
      {
        "auctionId": 3,
        "winnerNickname": "정복왕",
        "finalPrice": 3500,
        "wonAt": "2026-04-20T18:00:00Z"
      },
      {
        "auctionId": 1,
        "winnerNickname": "입찰왕",
        "finalPrice": 2000,
        "wonAt": "2026-04-14T12:00:00Z"
      }
    ]
  }
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `territoryId` | Long | 영토 ID | `territories.id` |
| `histories[]` | array | 낙찰 이력 목록 (최신순 DESC) | `auction_histories` |
| `histories[].auctionId` | Long | 경매 ID | `auction_histories.auction_id` |
| `histories[].winnerNickname` | String | 낙찰자 닉네임 | `users.nickname` (via `auction_histories.winner_id`) |
| `histories[].finalPrice` | Integer | 최종 낙찰가 | `auction_histories.final_price` |
| `histories[].wonAt` | DateTime | 낙찰 시각 | `auction_histories.won_at` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | TERRITORY_NOT_FOUND | 존재하지 않는 영토 |

### 남은 작업
- ~~서비스 구현~~ ✅
- ~~`AuctionHistoryRepository` 쿼리 추가~~ ✅

---
