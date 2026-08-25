# Item API

> Notion 상세 기능 명세: [F-15 아이템 시스템](https://www.notion.so/Functional-Specification-Access-Control-Matrix-3332efa4278d804e8ccfdb31151e9943)  
> 구현 상태: ✅ 구현 완료

> **도메인 분리 안내**
> - AP 충전: [user.md](./user.md) — `POST /api/v1/users/me/ap/charge`
> - 토지세: [tax.md](./tax.md)
> - 글로벌 금고: [global-vault.md](./global-vault.md)
> - 시즌 패스: [season.md](./season.md)

---

## 목차

| Method | Endpoint | 기능 | 구현 | 남은 작업 |
|---|---|---|---|---|
| GET | `/api/v1/items` | [아이템 목록 조회](#아이템-목록-조회) | ✅ | Redis 캐시 조회 연동, items 시딩 |
| POST | `/api/v1/items/purchase` | [아이템 구매](#아이템-구매) | ✅ | items 시딩 |
| POST | `/api/v1/items/use` | [아이템 사용](#아이템-사용) | ✅ | 공격권 — 공성전 도메인 구현 후 연동 |
| GET | `/api/v1/items/inventory` | [보유 아이템 목록 조회](#보유-아이템-목록-조회) | ✅ | - |

---

## 아이템 목록 조회

**GET** `/api/v1/items`

**Authorization**: Bearer `{{accessToken}}` (필수)

아이템 샵에서 구매 가능한 아이템 목록과 가격을 반환합니다.

### Response (200 OK)

```json
{
  "items": [
    {
      "itemId": 1,
      "name": "무적권",
      "itemType": "INVINCIBILITY",
      "description": "영토에 1시간 동안 무적 상태를 부여합니다.",
      "costAP": 50,
      "costGP": null,
      "dailyLimit": 3,
      "myInventory": 2
    },
    {
      "itemId": 2,
      "name": "일반 공격권",
      "itemType": "ATTACK_NORMAL",
      "description": "대상 영토에 공성전을 선언합니다. 랜덤 건물에 피해.",
      "costAP": 100,
      "costGP": null,
      "dailyLimit": 5,
      "myInventory": 1
    },
    {
      "itemId": 3,
      "name": "GP 구매권",
      "itemType": "GP_PURCHASE",
      "description": "AP 200으로 GP 1,000을 즉시 구매합니다.",
      "costAP": 200,
      "costGP": null,
      "dailyLimit": null,
      "myInventory": 0
    }
  ]
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `items[].itemId` | Long | 아이템 ID | `items.id` |
| `items[].name` | String | 아이템 이름 | `items.name` |
| `items[].itemType` | String | `INVINCIBILITY` / `ATTACK_NORMAL` / `GP_PURCHASE` | `items.item_type` |
| `items[].description` | String | 아이템 설명 | `items.description` |
| `items[].costAP` | Integer (nullable) | AP 구매 비용 — `null`이면 AP로 구매 불가 | `items.cost_ap` |
| `items[].costGP` | Integer (nullable) | GP 구매 비용 — `null`이면 GP로 구매 불가 | `items.cost_gp` |
| `items[].dailyLimit` | Integer (nullable) | 일일 구매 한도 — `null`이면 무제한 | `items.daily_limit` |
| `items[].myInventory` | Integer | 내 보유 수량 | `user_items.quantity` (Redis 캐시 우선) |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |

### 남은 작업
- TODO: `items` 테이블 초기 데이터 시딩 (무적권·공격권·GP구매권 3종)
- TODO: `getItems()` — Redis `user:item:{userId}` 캐시 우선 조회 연동 (현재 DB 직접 조회)

---

## 아이템 구매

**POST** `/api/v1/items/purchase`

**Authorization**: Bearer `{{accessToken}}` (필수)

AP를 소모하여 아이템을 구매합니다. 구매 즉시 인벤토리에 추가됩니다.

### Request

```json
{
  "itemId": 1,
  "quantity": 2
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `itemId` | Long | Y | 아이템 ID |
| `quantity` | Integer | Y | 구매 수량 (1 이상) |

### 비즈니스 규칙
- AP 차감 후 `item_purchases`에 이력 기록, `user_items`에 수량 적립
- `GP_PURCHASE` 타입 구매 시 즉시 **글로벌 금고**(`global_vaults.stored_gp`) 증가
- 일일 한도 초과 체크: `item_purchases` 당일 구매 이력 집계
- 구매 후 Redis `user:item:{userId}` 무효화

### Response (200 OK)

```json
{
  "itemId": 1,
  "itemType": "INVINCIBILITY",
  "purchased": 2,
  "totalOwned": 4,
  "costAP": 100,
  "remainingAP": 200
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `itemId` | Long | 구매한 아이템 ID | `items.id` |
| `itemType` | String | 아이템 종류 | `items.item_type` |
| `purchased` | Integer | 구매 수량 | 요청 `quantity` |
| `totalOwned` | Integer | 구매 후 총 보유 수량 | `user_items.quantity` |
| `costAP` | Integer | 차감된 AP (수량 × 단가) | `items.cost_ap × quantity` |
| `remainingAP` | Integer | 구매 후 잔여 AP | `wallets.available_ap` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | `INVALID_QUANTITY` | 수량 1 미만 |
| 402 | `INSUFFICIENT_AP` | AP 잔액 부족 |
| 404 | `ITEM_NOT_FOUND` | 존재하지 않는 아이템 |
| 429 | `DAILY_LIMIT_EXCEEDED` | 일일 구매 한도 초과 |

### 남은 작업
- TODO: `items` 테이블 초기 데이터 시딩 (무적권·공격권·GP구매권 3종)

---

## 아이템 사용

**POST** `/api/v1/items/use`

**Authorization**: Bearer `{{accessToken}}` (필수)

보유 중인 아이템을 사용합니다. 아이템 종류에 따라 영토 지정이 필요합니다.

### Request

```json
{
  "itemId": 1,
  "targetTerritoryId": 15
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `itemId` | Long | Y | 아이템 ID |
| `targetTerritoryId` | Long | N | 무적권·공격권 사용 시 대상 영토 |

### 비즈니스 규칙
- `INVINCIBILITY`: 대상 영토에 Redis 키 생성 (`invincible:{territoryId}`, TTL=1시간)
- `ATTACK_NORMAL` / `ATTACK_PRECISION`: 공성전 자동 선언 (내부적으로 siege 처리)
- `GP_PURCHASE`: 구매 시점에 이미 GP 지급 완료 → 별도 사용 불필요
- 사용 후 `user_items.quantity` -1, `item_purchases.used_at` 업데이트
- 사용 후 Redis `user:item:{userId}` 무효화

### Response (200 OK)

```json
{
  "itemId": 1,
  "itemType": "INVINCIBILITY",
  "result": {
    "territoryId": 15,
    "invincibleUntil": "2026-04-08T16:00:00Z"
  },
  "remainingCount": 1
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `itemId` | Long | 사용한 아이템 ID | `items.id` |
| `itemType` | String | 아이템 종류 | `items.item_type` |
| `result.territoryId` | Long (nullable) | 적용된 영토 ID | `siege_events.target_territory_id` |
| `result.invincibleUntil` | DateTime (nullable) | 무적 만료 시각 | Redis TTL 기준 |
| `remainingCount` | Integer | 사용 후 보유 수량 | `user_items.quantity` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | `TARGET_REQUIRED` | 무적권·공격권 — 대상 영토 필수 |
| 403 | `NOT_TERRITORY_OWNER` | 무적권 — 본인 영토가 아님 |
| 404 | `ITEM_NOT_FOUND` | 아이템 없음 (보유하지 않음) |
| 409 | `ITEM_OUT_OF_STOCK` | 보유 수량 0 |
| 409 | `ALREADY_INVINCIBLE` | 이미 무적 상태인 영토 |

### 남은 작업
- TODO: `ATTACK_NORMAL` / `ATTACK_PRECISION` 사용 — 공성전(siege) 도메인 구현 후 연동 (현재 `SIEGE_NOT_SUPPORTED` 예외 반환)

---

## 보유 아이템 목록 조회

**GET** `/api/v1/items/inventory?page={0}&size={20}&type={INVINCIBILITY}`

**Authorization**: Bearer `{{accessToken}}` (필수)

현재 로그인한 사용자의 보유 아이템 목록을 조회합니다.

### Query Parameters

| parameter | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | Integer | N | 0 | 페이지 번호 (0-based) |
| `size` | Integer | N | 20 | 페이지당 항목 수 |
| `type` | String | N | - | 아이템 타입 필터 |

### Response (200 OK)

```json
{
  "totalCount": 5,
  "items": [
    {
      "userItemId": 301,
      "itemId": 1,
      "itemName": "무적권",
      "itemType": "INVINCIBILITY",
      "description": "영토에 1시간 동안 무적 상태를 부여합니다.",
      "quantity": 2,
      "acquiredAt": "2026-04-01T12:00:00Z"
    }
  ]
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `totalCount` | Integer | 전체 보유 아이템 수 | `item_purchases` COUNT |
| `items[].userItemId` | Long | 보유 아이템 레코드 ID | `item_purchases.id` |
| `items[].itemId` | Long | 아이템 ID | `items.id` |
| `items[].itemName` | String | 아이템 이름 | `items.name` |
| `items[].itemType` | String | 아이템 종류 | `items.item_type` |
| `items[].description` | String | 아이템 설명 | `items.description` |
| `items[].quantity` | Integer | 보유 수량 | `item_purchases.quantity` |
| `items[].acquiredAt` | DateTime | 아이템 획득 시각 | `item_purchases.purchased_at` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 토큰 없음 또는 만료 |

