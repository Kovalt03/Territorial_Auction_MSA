# Military API

> Notion 상세 기능 명세: [F-11 전투/공성 시스템](https://www.notion.so/Functional-Specification-Access-Control-Matrix-3332efa4278d804e8ccfdb31151e9943), [F-12 공격권](https://www.notion.so/Functional-Specification-Access-Control-Matrix-3332efa4278d804e8ccfdb31151e9943)  
> 구현 상태: 🔲 미구현

---

## 공격권 보유 조회

**GET** `/api/v1/military/attack-tokens`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "normalCount": 3,
    "precisionCount": 1
  }
}
```

| field | 설명 |
|---|---|
| `normalCount` | 일반 공격권 (랜덤 건물 피해) |
| `precisionCount` | 정밀 공격권 (목표 건물 지정) |

출처: `attack_tokens`

---

## 유닛 종류 카탈로그 조회

**GET** `/api/v1/military/unit-types`

**Authorization**: Bearer `{{accessToken}}` (필수)

훈련 가능한 **전체 유닛 종류**를 보유 여부와 무관하게 반환한다. 생산 UI가 첫 유닛도 선택할 수 있게 하는 소스 — 보유 유닛(`GET /units`)만으로는 미보유 종류를 훈련할 수 없다.

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "unitTypeId": 1,
      "name": "INFANTRY",
      "displayName": "보병",
      "icon": "🗡",
      "colorHex": "#00f5ff",
      "attackPower": 10,
      "defensePower": 8,
      "costGp": 100,
      "foodCost": 2,
      "buildingDamage": 0,
      "requiredBarracksLevel": 1
    }
  ]
}
```

결과가 없으면 빈 배열 `[]`.

---

## 유닛 생산

**POST** `/api/v1/military/units`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request

```json
{
  "unitTypeId": 1,
  "quantity": 10,
  "level": 2,
  "locationId": 10,
  "locationType": "TERRITORY"
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `unitTypeId` | Long | Y | |
| `quantity` | Integer | Y | |
| `level` | Integer | N | 생산할 유닛 레벨. 생략 시 1. 연구로 해금된 레벨 이하만 가능 |
| `locationId` | Long | Y | 생산 위치(영토 또는 섬) ID |
| `locationType` | Enum | Y | `TERRITORY` / `ISLAND` |

### 비즈니스 규칙
- 해당 위치에 병영(BARRACKS)이 있어야 하며, 레벨별 `required_barracks_level` 이상이어야 한다
- GP `unit_types.cost_gp × quantity` 를 **해당 위치 저장소**에서 차감 (금고 아님)
- 식량은 레벨별 `train_cost_food × quantity` 를 해당 위치 저장소에서 차감
- 생산 결과는 (유저 × 유닛종류 × 레벨 × 귀속위치 × 배치) 스택으로 합산

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "unitTypeId": 1,
    "unitName": "INFANTRY",
    "quantity": 10,
    "gpRemaining": 7000
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | NO_BARRACKS | 병영 없음 |
| 400 | INSUFFICIENT_GP | GP 부족 |

---

## 병력 배치

**POST** `/api/v1/military/units/deploy`

**Authorization**: Bearer `{{accessToken}}` (필수)

점유 중인 영토에 유닛을 방어 배치합니다.

### Request

```json
{
  "territoryId": 5,
  "unitTypeId": 1,
  "quantity": 20
}
```

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "deployedCount": 20,
    "territoryId": 5
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 403 | NOT_TERRITORY_OWNER | 배치 권한 없음 |
| 400 | INSUFFICIENT_UNITS | 보유 유닛 부족 |

---

## 병력 교환 (회수)

**POST** `/api/v1/military/units/recall`

**Authorization**: Bearer `{{accessToken}}` (필수)

배치된 유닛을 회수합니다.

### Request

```json
{
  "territoryId": 5,
  "unitTypeId": 1,
  "quantity": 10
}
```

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "recalledCount": 10,
    "remainingDeployed": 10
  }
}
```

---

## 유닛 위치 간 이동
**POST** `/api/v1/military/units/move`

**Authorization**: Bearer `{{accessToken}}` (필수)

대기 유닛을 다른 위치(영토/섬)로 이동시킵니다. 출발지 저장소에서 `UNIT_MOVE_COST_GP × 수량` 차감, `UNIT_MOVE_MINUTES` 후 도착. 도착 전까지 이동 중 상태(방어·배치·재이동 불가).

### Request

```json
{
  "unitTypeId": 1,
  "quantity": 10,
  "sourceLocationId": 5,
  "sourceLocationType": "TERRITORY",
  "destLocationId": 1,
  "destLocationType": "ISLAND"
}
```

- `sourceLocationType` / `destLocationType`: `TERRITORY` | `ISLAND`
- 도착지 수용량 초과 시 `UNIT_CAPACITY_EXCEEDED`, 출발지 GP 부족 시 `INSUFFICIENT_GP`

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "movedCount": 10,
    "gpRemaining": 320,
    "moveCompleteAt": "2026-04-08T12:10:00Z"
  }
}
```

---

## 공격 선언

**POST** `/api/v1/military/siege`

**Authorization**: Bearer `{{accessToken}}` (필수, 공격권 보유자)

### Request

```json
{
  "targetTerritoryId": 10,
  "targetBuildingId": null,
  "attackZone": 3,
  "forces": [
    { "unitTypeId": 1, "quantity": 30, "level": 1 },
    { "unitTypeId": 4, "quantity": 2, "level": 2 }
  ],
  "structures": [
    { "type": "STAGING", "coordX": 12, "coordY": 8 },
    { "type": "TOWER", "coordX": 13, "coordY": 8 }
  ]
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `targetTerritoryId` | Long | Y | 공격 대상 영토 |
| `targetBuildingId` | Long | N | 정밀 공격권 사용 시 목표 건물 |
| `attackZone` | Integer | Y | 1/2/3 |
| `forces` | Array | Y | 투입 병력. `unitTypeId`, `quantity`, `level`(생략 시 1) |
| `structures` | Array | Y | 공성 건물. `type`(STAGING/TOWER/SUPPLY), `coordX`, `coordY` |

### 비즈니스 규칙
- 공격권 1개 자동 소모
- Zone 순서 제약: 이전 Zone 미클리어 시 다음 Zone 공격 불가
- **공성 건물**: STAGING 1개 필수, 나머지는 STAGING 기준 체비셰프 거리 1 인접, 좌표 중복 불가, 총 8개 이하. 건설비는 금고 GP에서 차감
- **투입 상한**: 총 병력 ≤ STAGING 레벨당 수용량(10)
- TOWER는 공격 전력 보너스, SUPPLY는 공격 쿨다운 단축. 정산 후 공성 건물은 삭제
- 투입 병력은 선언 시점에 `siege_forces`로 커밋되어 잠기고, 정산 후 생존분만 복귀
- `SIEGE_COUNTDOWN_MINUTES`(30분) 후 자동 전투 계산
- 방어자에게 즉시 WebSocket 알림 발송

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "siegeId": 1,
    "resolveAt": "2026-04-27T15:30:00Z",
    "attackTokenRemaining": 2
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | NO_ATTACK_TOKEN | 공격권 없음 |
| 400 | ZONE_NOT_CLEARED | 이전 Zone 미클리어 |
| 400 | TERRITORY_INVINCIBLE | 무적 상태 영토 |
| 400 | ATTACK_COOLDOWN | 공격 쿨다운 중 |

---

## 공성전 결과 조회

**GET** `/api/v1/military/siege/{siegeId}/result`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "siegeId": 1,
    "isAttackerWin": true,
    "attackerUnitsLost": 5,
    "defenderUnitsLost": 20,
    "lootedGp": 500,
    "resultType": "LOOT",
    "resolvedAt": "2026-04-27T15:30:00Z"
  }
}
```

| `resultType` | 설명 |
|---|---|
| `LOOT` | GP 약탈 (공격자 금고 적립) |
| `DEBUFF` | 건물 피해 (생산/병영 디버프) |
| `AUCTION` | Zone 1 공격 성공. 성 파괴 시 **공격자 즉시 인계**(경매 아님). enum 이름은 이력 호환상 유지 |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | SIEGE_NOT_FOUND | 존재하지 않는 공성전 |
| 403 | FORBIDDEN | 관계없는 유저 조회 시도 |

---

## 유닛 현황 조회

**GET** `/api/v1/military/units`

**Authorization**: Bearer `{{accessToken}}` (필수)

사용 페이지: 영토 관리 화면

사용자가 보유한 군사 유닛 목록과 수량을 반환합니다.

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "units": [
      {
        "unitTypeId": 1,
        "name": "INFANTRY",
        "quantity": 50,
        "deployedCount": 20,
        "idleCount": 30,
        "attackPower": 10,
        "defensePower": 8,
        "foodCost": 1
      },
      {
        "unitTypeId": 2,
        "name": "ARCHER",
        "quantity": 30,
        "deployedCount": 0,
        "idleCount": 30,
        "attackPower": 15,
        "defensePower": 5,
        "foodCost": 2
      }
    ],
    "availableFood": 480
  }
}
```

| field | 설명 |
|---|---|
| `deployedCount` | `deployed_territory_id`가 NOT NULL인 유닛 수 |
| `idleCount` | 대기 중인 유닛 수 |
| `foodCost` | 유닛 타입 1회 생산 식량 비용 (시간당 소모 아님) |
| `availableFood` | 그 위치 저장소 식량 잔액 (`building_instances.stored_food` 합) |

출처: `unit_instances` JOIN `unit_types`, 위치별 저장 식량은 `building_instances`(성+Storage)에서 집계

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 인증 실패 |

---

## 공성 이벤트 조회

**GET** `/api/v1/siege/events?page={0}&size={20}&status={PENDING}`

- 인증 불필요

사용 페이지: 메인 맵, 공성전 화면

현재 진행 중이거나 최근 종료된 공성전 목록을 반환합니다. 메인 맵에서 진행 중인 공성전 좌표 하이라이트에 활용됩니다.

### Query Parameters

| parameter | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | Integer | N | 0 | 페이지 번호 |
| `size` | Integer | N | 20 | 페이지 크기 |
| `status` | String | N | PENDING | PENDING / RESOLVED |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "totalCount": 5,
    "sieges": [
      {
        "siegeId": 44,
        "status": "PENDING",
        "attacker": { "userId": 3, "nickname": "공격자" },
        "defender": { "userId": 8, "nickname": "방어자" },
        "targetTerritory": { "id": 12, "coordX": 4, "coordY": 6 },
        "siegeStartAt": "2026-04-27T14:00:00Z",
        "resolveAt": "2026-04-27T14:30:00Z"
      }
    ]
  }
}
```

출처: `siege_events` (PENDING 상태는 Redis `siege:active:{siegeId}` 우선 조회)

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | INVALID_PARAM | 잘못된 쿼리 파라미터 |

### 남은작업
- 서비스 구현
- Redis `siege:active:{siegeId}` 연동

---

## 나의 공성전 이력

**GET** `/api/v1/siege/my-history?page={0}&size={20}&result={ALL}`

**Authorization**: Bearer `{{accessToken}}` (필수)

내가 공격자 또는 방어자로 참여한 공성전 이력을 조회합니다.

### Query Parameters

| parameter | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | Integer | N | 페이지 번호 (기본 0) |
| `size` | Integer | N | 페이지당 항목 수 (기본 20) |
| `result` | String | N | 결과 필터 (WIN / LOSE / ALL, 기본 ALL) |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "totalCount": 14,
    "wins": 9,
    "losses": 5,
    "history": [
      {
        "siegeId": 301,
        "territoryId": 28,
        "territoryGrade": "A",
        "role": "ATTACKER",
        "result": "WIN",
        "attackerMilitary": 200,
        "defenderMilitary": 150,
        "occurredAt": "2026-04-25T18:00:00Z"
      }
    ]
  }
}
```

| field | 설명 |
|---|---|
| `role` | `ATTACKER` (공격자) / `DEFENDER` (방어자) |
| `result` | `WIN` / `LOSE` |

출처: `siege_events` JOIN `siege_results`

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | UNAUTHORIZED | 인증 실패 |

### 남은작업
- 서비스 구현

---

## 영토 정찰

**POST** `/api/v1/military/scout/{territoryId}`

**Authorization**: Bearer `{{accessToken}}` (필수)

정찰로 얻는 정보는 **방어 총 병력 수뿐**이다. 유닛 종류·Zone 분포·건물 배치는 공개하지 않는다(정보 비대칭).

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "territoryId": 10,
    "defenderTotalUnits": 42
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | `TERRITORY_NOT_FOUND` | 영토 없음 |
| 400 | `SCOUT_INVALID_TARGET` | 정찰할 수 없는 영토 (자기 영토·미점유 등) |
| 400 | `SCOUT_UNIT_REQUIRED` | 정찰 유닛 없음 — 정찰 1회당 1기 소모 |

---

## 영토 주둔 병력 조회

**GET** `/api/v1/military/territory/{territoryId}/garrison`

**Authorization**: Bearer `{{accessToken}}` (필수)

해당 영토에 배치된 **내 유닛**의 타입별 합계. 회수 UI가 이 목록으로 회수 대상을 표시한다.
소유권 검증은 하지 않는다 — 조회자 본인의 배치 유닛만 집계하므로 타인 병력은 노출되지 않는다.

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": [
    {
      "unitTypeId": 1,
      "name": "INFANTRY",
      "displayName": "보병",
      "icon": "🗡",
      "colorHex": "#00f5ff",
      "deployedCount": 30
    }
  ]
}
```

결과가 없으면 빈 배열 `[]`.

---

## 연구 현황 조회

**GET** `/api/v1/military/research`

**Authorization**: Bearer `{{accessToken}}` (필수)

계정 단위 유닛 연구 현황. `researchLabLevel`이 연구 가능 상한(`researchLabLevel + 1`)을 결정한다.
완료 시각이 지난 연구는 이 조회 시점에 지연 반영(lazy completion)된다.

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "researchLabLevel": 1,
    "units": [
      {
        "unitTypeId": 1,
        "name": "INFANTRY",
        "displayName": "보병",
        "icon": "🗡",
        "colorHex": "#00f5ff",
        "researchedLevel": 1,
        "maxLevel": 3,
        "pendingLevel": 2,
        "researchCompleteAt": "2026-07-24T15:30:00",
        "nextCostGp": 4000
      }
    ]
  }
}
```

---

## 연구 시작

**POST** `/api/v1/military/research/{unitTypeId}`

**Authorization**: Bearer `{{accessToken}}` (필수)

### 비즈니스 규칙
- 목표 레벨 = 현재 `researchedLevel + 1`
- 필요 연구소(RESEARCH_LAB) 레벨 = `목표 레벨 − 1`
- 비용 `2000 × 목표 레벨` GP — **금고(GlobalVault)**에서 차감
- 소요 시간 `30분 × 목표 레벨`
- 유닛당 동시 1건만 진행 가능

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "unitTypeId": 1,
    "pendingLevel": 2,
    "researchCompleteAt": "2026-07-24T15:30:00",
    "vaultGpRemaining": 1000
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | `RESEARCH_MAX_REACHED` | 이미 최대 레벨 |
| 409 | `RESEARCH_IN_PROGRESS` | 해당 유닛 연구 진행 중 |
| 400 | `RESEARCH_LAB_LEVEL_INSUFFICIENT` | 연구소 레벨 부족 |
| 400 | `RESEARCH_SPEC_NOT_FOUND` | 목표 레벨의 유닛 스펙 미등록 (관리자 설정 필요) |
| 400 | `INSUFFICIENT_GP` | 금고 GP 부족 |
| 404 | `UNIT_TYPE_NOT_FOUND` | 유닛 종류 없음 |

---

## 전투 계산 공식 (참고)

> Notion F-11.4 — 구현 시 참고용

공격 선언 후 `SIEGE_COUNTDOWN_MINUTES`(config) 경과 시 아래 공식으로 전투 자동 계산.

### 전력 계산

```
ATK = Σ(파견 유닛 attack_power × 수량)
DEF = Σ(방어 유닛 defense_power × 수량) + Σ(해당 Zone 방어 건물 defense_power)
```

### 성공 판정

- `ATK > DEF` → 공격 성공
- `ATK ≤ DEF` → 공격 실패

### Zone 클리어 판정

- `Σ(Zone 방어 건물 hp) / Σ(Zone 방어 건물 max_hp) < (1 − ZONE_CLEAR_THRESHOLD)` → 클리어 (별도 컬럼 불필요, 동적 계산)

### 건물 HP 감소 (공격 성공 시)

- 잉여 공격력 `(ATK − DEF)`를 해당 Zone 방어 건물들에게 현재 HP 비율로 분산 적용

### 유닛 손실

| 상황 | 손실 공식 |
|---|---|
| 공격자 (성공) | `파견 수 × (DEF / ATK) × ATTACKER_LOSS_RATE` (기본 0.3) |
| 공격자 (실패) | `파견 수 × ATTACKER_FAIL_LOSS_RATE` (기본 0.5) |
| 방어자 (공격 성공 시) | `방어 수 × (ATK / DEF) × DEFENDER_LOSS_RATE` (기본 0.3) |
| 방어자 (공격 실패 시) | 피해 없음 |

### 전투 결과 처리 (F-11.5)

| 결과 | 처리 |
|---|---|
| Storage 파괴 | 영토 저장소 자원 N% 약탈 (DB 트랜잭션 보장) |
| Workshop 파괴 | 생산량 제로 디버프 T시간 |
| Barracks 파괴 | 유닛 생산 중단 T시간 |
| Castle 파괴 | **공격자 즉시 인계**: 저장 GP 80% 공격자 금고·나머지·식량 소멸·방어 유닛 전멸·영토 점유 이전 + 보호 기간 재시작 |
| 공격 실패 | 공격 유닛 일부 손실, 전투 리포트 양측 발송 |

---

## 공격 쿨다운 및 보호 기간

> Notion F-11.6, F-11.7

### 공격 쿨다운 (F-11.6)

- 공격 실패 후 `ATTACK_COOLDOWN_HOURS`(config) 동안 같은 영토 재공격 불가
- `siege_events`의 실패 기록으로 쿨다운 계산

### 보호 기간 (F-11.7)

- 경매 낙찰 후 `PROTECTION_DURATION_HOURS`(config) 동안 공격 수신 불가
- Castle 파괴 → 공격자 즉시 인계 시 인계받은 공격자에게 보호 기간 재시작
- 보호 기간 중 공격 선언 시 → `TERRITORY_PROTECTED` 에러 반환
- 보호 기간(`territories.protected_until`)은 점유 기간(`occupied_until`)과 분리된 별도 컬럼이다 — 보호 만료 후에도 점유는 유지되며, 그 구간에서만 공성이 성립한다.
