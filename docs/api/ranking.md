# Ranking API

> 구현 상태: ⬜ 미구현

---

## 개요

시즌 기간 동안의 성과를 기준으로 하는 **2개 카테고리** 랭킹.

| 카테고리 | 기준 | 갱신 방식 |
|---|---|---|
| 🏅 시즌 영토 등급 보유 | 시즌 중 높은 등급 영토를 오랫동안 보유한 시간 (등급 가중치 반영) | 주기적 배치 집계 |
| 💸 시즌 경매 소비 | 시즌 중 경매 낙찰에 사용한 총 AP | 낙찰마다 즉시 갱신 |

> 트로피·리그는 전투 보상 계산에만 사용. 별도 랭킹 페이지로 노출하지 않는다.

**등급 가중치** (영토 등급 보유 점수 계산용)

| 등급 | 가중치 |
|---|---|
| S | 5 |
| A | 4 |
| B | 3 |
| C | 2 |
| D | 1 |

점수 = Σ(등급 가중치 × 보유 시간(초))

---

## 공통 응답 형식

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "seasonId": 1,
    "seasonNumber": 1,
    "type": "TERRITORY_HOLD",
    "updatedAt": "2026-05-11T12:00:00Z",
    "myRank": 5,
    "rankings": [
      {
        "rank": 1,
        "userId": 3,
        "nickname": "영토왕",
        "score": 720000
      }
    ]
  }
}
```

> `myRank`: 비로그인 또는 랭킹 밖일 시 `null`

---

## 목차

| Method | Endpoint | 기능 | 구현 |
|---|---|---|---|
| GET | `/api/v1/rankings/territory-hold` | [시즌 영토 등급 보유 랭킹](#시즌-영토-등급-보유-랭킹) | ⬜ |
| GET | `/api/v1/rankings/auction-spend` | [시즌 경매 AP 소비 랭킹](#시즌-경매-ap-소비-랭킹) | ⬜ |
| GET | `/api/v1/rankings/me` | [내 랭킹 조회](#내-랭킹-조회) | ⬜ |

---

## 시즌 영토 등급 보유 랭킹

**GET** `/api/v1/rankings/territory-hold?page={0}&size={50}`

- 인증 선택

현재 진행 중인 시즌 동안 높은 등급의 영토를 오랫동안 보유한 유저 순위.  
`season_territory_holds` 테이블을 집계하여 등급 가중치 × 보유 시간(초)의 합산 점수로 정렬.

### Query Parameters

| parameter | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | Integer | N | 0 | 페이지 번호 (0-based) |
| `size` | Integer | N | 50 | 페이지 크기 (최대 100) |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "seasonId": 1,
    "seasonNumber": 1,
    "type": "TERRITORY_HOLD",
    "updatedAt": "2026-05-11T06:00:00Z",
    "myRank": 12,
    "rankings": [
      {
        "rank": 1,
        "userId": 7,
        "nickname": "영토황제",
        "score": 1440000,
        "gradeBreakdown": {
          "S": 86400,
          "A": 172800,
          "B": 0,
          "C": 0,
          "D": 0
        }
      },
      {
        "rank": 2,
        "userId": 3,
        "nickname": "정복자",
        "score": 960000,
        "gradeBreakdown": {
          "S": 0,
          "A": 259200,
          "B": 86400,
          "C": 0,
          "D": 0
        }
      }
    ]
  }
}
```

| field | 타입 | 설명 |
|---|---|---|
| `seasonId` | Long | 현재 시즌 ID |
| `seasonNumber` | Integer | 시즌 번호 |
| `type` | String | `TERRITORY_HOLD` 고정 |
| `updatedAt` | DateTime | 마지막 배치 집계 시각 |
| `myRank` | Integer | 로그인 유저 순위 (비로그인 또는 미집계 시 null) |
| `rankings[].rank` | Integer | 순위 |
| `rankings[].score` | Long | 총 점수 = Σ(가중치 × 보유 초) |
| `rankings[].gradeBreakdown` | Object | 등급별 보유 시간(초) |

출처: Redis `ranking:season:{seasonId}:territory_hold` Sorted Set  
보조 DB: `season_territory_holds`

### 남은 작업
- `season_territory_holds` 테이블 마이그레이션
- 영토 낙찰/점유 종료 시 `held_from` / `held_until` 기록 로직
- 주기적 배치: `season_territory_holds` → Redis Sorted Set 갱신
- `RankingController`, `RankingService` 구현

---

## 시즌 경매 AP 소비 랭킹

**GET** `/api/v1/rankings/auction-spend?page={0}&size={50}`

- 인증 선택

현재 진행 중인 시즌 동안 경매 낙찰에 가장 많은 AP를 사용한 유저 순위.  
낙찰 시마다 Redis Sorted Set을 즉시 업데이트.

### Query Parameters

| parameter | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | Integer | N | 0 | 페이지 번호 (0-based) |
| `size` | Integer | N | 50 | 페이지 크기 (최대 100) |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "seasonId": 1,
    "seasonNumber": 1,
    "type": "AUCTION_SPEND",
    "updatedAt": "2026-05-11T14:32:11Z",
    "myRank": 8,
    "rankings": [
      {
        "rank": 1,
        "userId": 12,
        "nickname": "큰손",
        "totalSpentAP": 58200
      },
      {
        "rank": 2,
        "userId": 5,
        "nickname": "경매왕",
        "totalSpentAP": 43100
      }
    ]
  }
}
```

| field | 타입 | 설명 |
|---|---|---|
| `type` | String | `AUCTION_SPEND` 고정 |
| `updatedAt` | DateTime | 마지막 갱신 시각 (최근 낙찰 시각) |
| `rankings[].totalSpentAP` | Integer | 시즌 누적 낙찰 AP 합산 |

출처: Redis `ranking:season:{seasonId}:auction_spend` Sorted Set  
보조 DB: `auction_histories.season_id + final_price`

### 남은 작업
- `auction_histories.season_id` 컬럼 추가 마이그레이션
- 경매 낙찰 시 Redis `ZINCRBY ranking:season:{seasonId}:auction_spend {winnerId} {finalPrice}` 호출
- `RankingService` 구현

---

## 내 랭킹 조회

**GET** `/api/v1/rankings/me`

**Authorization**: Bearer `{{accessToken}}` (필수)

현재 로그인 유저의 두 카테고리 랭킹 순위를 함께 반환.

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "seasonId": 1,
    "seasonNumber": 1,
    "territoryHold": {
      "rank": 12,
      "score": 345600,
      "gradeBreakdown": {
        "S": 0,
        "A": 86400,
        "B": 0,
        "C": 0,
        "D": 0
      }
    },
    "auctionSpend": {
      "rank": 8,
      "totalSpentAP": 12400
    }
  }
}
```

| field | 타입 | 설명 |
|---|---|---|
| `territoryHold.rank` | Integer | 영토 등급 보유 랭킹 순위 (미집계 시 null) |
| `territoryHold.score` | Long | 나의 총 점수 |
| `auctionSpend.rank` | Integer | 경매 소비 랭킹 순위 (미참여 시 null) |
| `auctionSpend.totalSpentAP` | Integer | 나의 시즌 누적 낙찰 AP |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |

### 남은 작업
- `RankingService.getMyRanking()` 구현
