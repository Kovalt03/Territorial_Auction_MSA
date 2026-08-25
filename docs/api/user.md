# User API

> 구현 상태: 🔄 일부 완료

## 목차

| Method | Endpoint | 기능 | 구현 |
|---|---|---|---|
| GET | `/api/v1/users/{userId}` | 유저 프로필 조회 | ✅ |
| GET | `/api/v1/users/me` | 내 프로필 조회 | ✅ (일부 TODO) |
| DELETE | `/api/v1/users/me` | 회원 탈퇴 | ✅ (일부 TODO) |
| GET | `/api/v1/users/me/settings` | 알림 설정 조회 | ✅ |
| PATCH | `/api/v1/users/me/settings` | 알림 수신 설정 변경 | ✅ |
| GET | `/api/v1/users/me/wallet` | GP/AP 잔액 조회 | ✅ (일부 TODO) |
| GET | `/api/v1/users/me/territories` | 나의 영토 목록 조회 | ✅ (일부 TODO) |
| PATCH | `/api/v1/users/me/nickname` | 닉네임 변경 | ✅ (일부 TODO) |
| PATCH | `/api/v1/users/me/password` | 비밀번호 변경 | ✅ |
| POST | `/api/v1/users/me/ap/charge` | AP 충전 | ✅ (PG 연동 TODO) |
| GET | `/api/v1/users/me/wishlist` | 위시리스트 조회 | ⬜ |
| POST | `/api/v1/users/me/wishlist/{territoryId}` | 위시리스트 추가 | ⬜ |
| DELETE | `/api/v1/users/me/wishlist/{territoryId}` | 위시리스트 제거 | ⬜ |

---

## 유저 프로필 조회

**GET** `/api/v1/users/{userId}`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "userId": 1,
    "username": "testuser",
    "nickname": "테스트유저",
    "profileImageUrl": null,
    "joinedAt": "2026-04-01T00:00:00Z"
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | USER_NOT_FOUND | 존재하지 않는 유저 |

---

## 내 프로필 조회

**GET** `/api/v1/users/me`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "userId": 1,
    "username": "testuser",
    "nickname": "테스트유저",
    "profileImageUrl": null,
    "joinedAt": "2026-04-01T00:00:00Z",
    "wallet": {
      "availableAP": 5000,
      "lockedAP": 1000,
      "availableGP": 12000
    },
    "island": {
      "islandId": 1,
      "gridSize": 10,
      "productionRate": 5
    },
    "seasonPass": {
      "isActive": true,
      "expiresAt": "2026-05-01T00:00:00Z"
    }
  }
}
```

> `island.productionRate`: 섬 건물 합산 분당 GP 생산량  
> `seasonPass`: 없으면 null

### 남은작업
- 섬 도메인 연동 (island 필드)
- 시즌 패스 Redis 캐시 연동

---

## 알림 설정 조회

**GET** `/api/v1/users/me/settings`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "isOutbidEnabled": true,
    "isAuctionStartEnabled": true,
    "isMarketingEnabled": false
  }
}
```

| field | 설명 | 출처 |
|---|---|---|
| `isOutbidEnabled` | 상회 입찰 알림 | `notification_settings.is_outbid_enabled` |
| `isAuctionStartEnabled` | 관심 대륙 경매 시작 알림 | `notification_settings.is_auction_start_enabled` |
| `isMarketingEnabled` | 마케팅 알림 | `notification_settings.is_marketing_enabled` |

---

## 알림 수신 설정 변경

**PATCH** `/api/v1/users/me/settings`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request

```json
{
  "isOutbidEnabled": false,
  "isAuctionStartEnabled": true,
  "isMarketingEnabled": false
}
```

요청 body에 포함된 필드만 UPDATE, 나머지는 기존 값 유지

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | NOTIFICATION_SETTING_NOT_FOUND | 설정 레코드 없음 (데이터 정합성 오류) |

---

## GP/AP 잔액 조회

**GET** `/api/v1/users/me/wallet`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "availableGP": 12000,
    "availableAP": 5000,
    "lockedAP": 1000
  }
}
```

> `availableFood` 필드는 미군사 도메인 구현 후 추가 예정

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `availableGP` | int | 사용 가능 Grid Point (**금고 잔액**) | `global_vaults.stored_gp` |
| `availableAP` | int | 사용 가능 Auction Point | `wallets.available_ap` |
| `lockedAP` | int | 진행 중인 경매 입찰로 묶인 AP | `wallets.locked_ap` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | USER_NOT_FOUND | 존재하지 않는 유저 |

---

## 나의 영토 목록 조회

**GET** `/api/v1/users/me/territories`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Query Parameters

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `page` | 0 | 페이지 번호 (0-based) |
| `size` | 10 | 페이지 크기 |
| `sort` | `id,DESC` | 정렬 기준 |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "totalCount": 3,
    "territories": [
      {
        "territoryId": 10,
        "grade": "A",
        "position": { "x": 2, "y": 3 },
        "continentName": "크리오 행성",
        "occupiedAt": null,
        "militaryCount": 0,
        "isInvincible": false
      }
    ]
  }
}
```

> `occupiedAt`, `militaryCount`, `isInvincible`은 군사 도메인 구현 후 연동 예정 (현재 각각 null / 0 / false 반환)

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `totalCount` | int | 보유 영토 전체 개수 | `territories` 집계 |
| `territories[]` | array | 페이지 단위 영토 목록 | - |
| `territories[].territoryId` | Long | 영토 ID | `territories.id` |
| `territories[].grade` | String | 영토 등급 (S/A/B/C/D) | `territory_grades.grade` |
| `territories[].position.x` | int | 그리드 X 좌표 | `territories.coord_x` |
| `territories[].position.y` | int | 그리드 Y 좌표 | `territories.coord_y` |
| `territories[].continentName` | String | 소속 행성 표시명 | `continents.display_name` |
| `territories[].occupiedAt` | String (ISO 8601) | 점령 시각 (미구현, null) | - |
| `territories[].militaryCount` | int | 배치된 유닛 수 (미구현, 0) | - |
| `territories[].isInvincible` | boolean | 무적 상태 여부 (미구현, false) | - |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 인증 토큰 없음 또는 만료 |

---

## 회원 탈퇴

**DELETE** `/api/v1/users/me`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request Body

```json
{ "password": "mypassword" }
```

### Response (200 OK)

```json
{
  "status": 200,
  "message": "회원 탈퇴가 완료되었습니다.",
  "data": null
}
```

> TODO: 탈퇴 후 JWT 토큰 무효화 미구현 (Redis 블랙리스트 등록 필요)

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | INVALID_PASSWORD | 비밀번호 불일치 |
| 404 | USER_NOT_FOUND | 존재하지 않는 유저 |

---

## 닉네임 변경

**PATCH** `/api/v1/users/me/nickname`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request Body

```json
{ "nickname": "새닉네임" }
```

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "userId": 1,
    "nickname": "새닉네임",
    "updatedAt": "2026-04-27T13:00:00"
  }
}
```

> TODO: `updatedAt`은 현재 `LocalDateTime.now()` 반환. User 엔티티에 `updatedAt` 감사 필드 추가 후 교체 권장

| field | 타입 | 설명 |
|---|---|---|
| `userId` | Long | 유저 ID |
| `nickname` | String | 변경된 닉네임 |
| `updatedAt` | String (ISO 8601) | 변경 시각 |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 409 | DUPLICATE_NICKNAME | 이미 사용 중인 닉네임 |
| 404 | USER_NOT_FOUND | 존재하지 않는 유저 |

---

## 비밀번호 변경

**PATCH** `/api/v1/users/me/password`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request Body

```json
{
  "currentPassword": "oldpassword",
  "newPassword": "newpassword"
}
```

### Response (200 OK)

```json
{
  "status": 200,
  "message": "비밀번호가 성공적으로 변경되었습니다.",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | INVALID_PASSWORD | 현재 비밀번호 불일치 |
| 404 | USER_NOT_FOUND | 존재하지 않는 유저 |

---

## AP 충전

**POST** `/api/v1/users/me/ap/charge`

**Authorization**: Bearer `{{accessToken}}` (필수)

외부 결제(PG)를 통해 AP 포인트를 충전합니다.

### Request

```json
{
  "amount": 1000,
  "paymentKey": "tgen_20260408...",
  "orderId": "order_user1_1744113600"
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `amount` | Integer | Y | 충전할 AP 수량 |
| `paymentKey` | String | Y | PG사 결제 키 (검증용) |
| `orderId` | String | Y | 주문 ID (멱등성 보장) |

### 비즈니스 규칙
- PG사 API 검증 후 `wallets.available_ap` 원자적 증가
- `orderId` 기반 멱등성 처리 (중복 요청 방지)
- AP:원화 환율은 서버 config 기준

### Response (200 OK)

```json
{
  "availableAP": 1300,
  "chargedAmount": 1000,
  "chargedAt": "2026-04-08T12:00:00Z"
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `availableAP` | Integer | 충전 후 사용 가능 AP 잔액 | `wallets.available_ap` |
| `chargedAmount` | Integer | 이번에 충전된 AP 수량 | 요청 `amount` |
| `chargedAt` | DateTime | 충전 완료 시각 | `wallets.updated_at` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | `INVALID_PAYMENT` | PG 검증 실패 |
| 409 | `DUPLICATE_ORDER` | 중복 주문 ID |
| 422 | `PAYMENT_AMOUNT_MISMATCH` | 결제 금액 불일치 |

### 남은 작업
- TODO: PG 연동 (Toss Payments 등) — 현재 `validatePayment()` 는 stub 구현

---

## 위시리스트 조회

**GET** `/api/v1/users/me/wishlist`

**Authorization**: Bearer `{{accessToken}}` (필수)

로그인 유저의 위시리스트에 등록된 영토 ID 목록을 반환합니다.

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "territoryIds": [1, 5, 12]
  }
}
```

> 위시리스트가 비어 있으면 `"territoryIds": []` 반환. null 반환 금지.

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `territoryIds` | Long[] | 위시리스트에 등록된 영토 ID 목록 | `wishlists.territory_id` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 인증 토큰 없음 또는 만료 |

---

## 위시리스트 추가

**POST** `/api/v1/users/me/wishlist/{territoryId}`

**Authorization**: Bearer `{{accessToken}}` (필수)

지정한 영토를 위시리스트에 추가합니다.

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `territoryId` | Long | 추가할 영토 ID |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | TERRITORY_NOT_FOUND | 존재하지 않는 영토 |
| 409 | WISHLIST_ALREADY_EXISTS | 이미 위시리스트에 등록된 영토 |

---

## 위시리스트 제거

**DELETE** `/api/v1/users/me/wishlist/{territoryId}`

**Authorization**: Bearer `{{accessToken}}` (필수)

지정한 영토를 위시리스트에서 제거합니다.

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `territoryId` | Long | 제거할 영토 ID |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | WISHLIST_NOT_FOUND | 위시리스트에 등록되지 않은 영토 |
