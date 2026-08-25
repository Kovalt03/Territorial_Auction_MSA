# Territory Income API

> 브랜치: `feature/be-28-territory-income`  
> 관련 테이블: `territories`, `building_instances` (STORAGE), `territory_production_logs`

---

## 개요: Lazy Evaluation 방식

영토 GP 소득은 **1분 주기 스케줄러 없이**, 이벤트 발생 시점에 `territories.last_produced_at`과 현재 시각의 차이(경과 분)를 곱해 즉시 정산(settle)하는 방식으로 동작한다.

### 정산(settle) 호출 시점

| # | 트리거 | 진입점 |
|---|--------|--------|
| 1 | 유저가 수령 버튼 클릭 | `POST /api/v1/territories/{id}/collect` |
| 2 | 공격자가 Storage Zone 약탈 성공 | `SiegeService` 내부 |
| 3 | 토지세 강제 경매 전환(release) | `LandTaxService` / `AuctionService` 내부 |
| 4 | 일 배치 안전망 (선택) | 별도 `TerritoryIncomeScheduler` |

### 클라이언트 실시간 카운터

프론트엔드는 응답의 `lastProducedAt`과 `productionRatePerMin`을 이용해 카운터를 클라이언트 사이드에서 직접 계산한다. DB 폴링 없이 반응성을 확보한다.

```
현재 적립량 ≈ productionRatePerMin × (now - lastProducedAt) / 1분
```

---

## 생산량 계산 공식

```
effectiveRate = floor(
  territory.baseProductionRate
  × grade.productionMultiplier
  × bonusTileMultiplier          -- BonusTile 없으면 1.0
  × (1 + adjacentOwnedCount × 0.1)   -- ADJACENT_BONUS_RATE = 0.1
)

elapsedMinutes = (now - territory.lastProducedAt) / 60초

accumulatedGp = effectiveRate × elapsedMinutes

creditedGp = min(storageCapacity, storage.storedGp + accumulatedGp) - storage.storedGp
```

- `adjacentOwnedCount`: 현재 영토와 상하좌우 인접한 영토 중, 동일 유저가 소유한 OCCUPIED 영토 수 (최대 4)
- `storageCapacity`: STORAGE 건물 레벨 × 200 GP (정확한 배율 TBD)
- STORAGE 건물이 존재하지 않으면 `creditedGp = 0` (생산 누적 불가)
- STORAGE 건물이 파괴(`is_destroyed = true`)된 경우도 `creditedGp = 0`

---

## GP 수령 API

### `POST /api/v1/territories/{id}/collect`

**Authorization**: Bearer `{{accessToken}}` (필수)  
**소유자 본인만 호출 가능**

#### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `id` | Long | 수령 대상 영토 ID |

#### Request Body

없음

#### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "creditedGp": 48,
    "storedGp": 248,
    "productionRatePerMin": 3,
    "lastProducedAt": "2026-05-20T14:32:00",
    "storageCapacity": 400
  }
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `creditedGp` | int | 이번 수령으로 실제 적립된 GP (용량 초과분 제외) |
| `storedGp` | int | 수령 후 Storage에 남은 총 GP |
| `productionRatePerMin` | int | 현재 기준 분당 유효 생산량 (클라이언트 카운터 계산용) |
| `lastProducedAt` | LocalDateTime (UTC+9, Z 없음) | 이번 settle 완료 시각 (= 수령 요청 시각) |
| `storageCapacity` | int | 현재 Storage 최대 용량 |

#### STORAGE 파괴 시 응답

STORAGE 건물이 존재하지만 파괴(`isDestroyed = true`)된 경우, 정산 없이 즉시 반환한다.

```json
{
  "creditedGp": 0,
  "storedGp": 50,
  "productionRatePerMin": 0,
  "lastProducedAt": "2026-05-20T14:32:00",
  "storageCapacity": 0
}
```

- `creditedGp = 0`: 파괴된 Storage에는 GP를 적립하지 않음
- `storageCapacity = 0`: 파괴 상태이므로 용량 없음
- `storedGp`: 파괴 전에 이미 적립된 GP는 유지됨 (약탈 대상)
- `lastProducedAt`: 정산 미수행이므로 갱신하지 않음

#### 에러

| HTTP | 에러 코드 | 설명 |
|------|-----------|------|
| 404 | `TERRITORY_NOT_FOUND` | 존재하지 않는 영토 |
| 403 | `NOT_TERRITORY_OWNER` | 요청자가 해당 영토의 소유자가 아님 |
| 400 | `TERRITORY_NOT_OCCUPIED` | 영토가 OCCUPIED 상태가 아니거나 점유 기간 만료 |
| 404 | `BUILDING_NOT_FOUND` | STORAGE 건물이 영토에 없는 경우 |

#### 비즈니스 규칙

- 영토 상태가 `OCCUPIED`이고 `occupied_until`이 미래인 경우에만 수령 가능
- 정산 완료 후 `territories.last_produced_at`을 현재 시각으로 갱신
- `territory_production_logs`에 이유별(BASE / ADJACENT_BONUS / BONUS_TILE) 분리 로그 삽입
  - BASE: 기본 생산량 분
  - ADJACENT_BONUS: 인접 보너스로 추가된 분
  - BONUS_TILE: BonusTile 배율로 추가된 분 (해당 영토에 BonusTile이 있는 경우에만)

---

## 영토 상세 조회 — 생산 관련 필드 추가

### `GET /api/v1/map/territories/{territoryId}`

기존 영토 상세 조회 응답에 아래 필드를 추가한다.

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "territoryId": 1,
    "coordX": 0,
    "coordY": 0,
    "continentName": "크리오 행성",
    "grade": "B",
    "productionMultiplier": 1.0,
    "gridSize": 8,
    "status": "OCCUPIED",
    "owner": {
      "userId": 5,
      "nickname": "테스트유저",
      "currentColor": "#FF4444"
    },
    "baseProductionRate": 2,
    "isInvincible": false,
    "buildings": [
      {
        "buildingId": 1,
        "type": "CASTLE",
        "level": 1,
        "hp": 100,
        "maxHp": 100
      }
    ],
    "auction": null,
    "productionRatePerMin": 3,
    "lastProducedAt": "2026-05-20T14:32:00",
    "storedGp": 248,
    "storageCapacity": 400
  }
}
```

#### 추가된 필드

| 필드 | 타입 | 조건 | 설명 |
|------|------|------|------|
| `productionRatePerMin` | int | OCCUPIED이고 STORAGE 존재 시 | 현재 분당 유효 생산량 (클라이언트 카운터 계산용) |
| `lastProducedAt` | ISO 8601 | OCCUPIED이고 STORAGE 존재 시 | 마지막 settle 완료 시각 |
| `storedGp` | int | OCCUPIED이고 STORAGE 존재 시 | 현재 Storage에 적립된 GP |
| `storageCapacity` | int | OCCUPIED이고 STORAGE 존재 시 | 현재 Storage 최대 용량 |

> STORAGE 건물이 없거나 비점유 상태이면 위 4개 필드는 `null` 반환
