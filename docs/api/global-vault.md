# Global Vault API

> Notion 상세 기능 명세: [F-10 이중 저장소](https://www.notion.so/Functional-Specification-Access-Control-Matrix-3332efa4278d804e8ccfdb31151e9943)  
> 구현 상태: ✅ 구현 완료

## 이중 저장소 개요 (F-10)

| 구분 | 영토 저장소 (Territory Storage) | 글로벌 금고 (Global Vault) |
|---|---|---|
| 활성화 | Storage 건물 건설 시 (F-10.1) | 계정 생성 시 자동 생성 (F-10.2) |
| 약탈 위험 | ✅ (Storage 파괴 시 N% 약탈 가능) | ❌ (타 유저 접근 불가) |
| 용량 | 건물 레벨에 비례 | GP/AP로 업그레이드 가능 |
| 이전 | ↔ 글로벌 금고 상호 이전 가능 | ↔ 영토 저장소 상호 이전 가능 |

---

## 목차

| Method | Endpoint | 기능 | 구현 | 남은 작업 |
|---|---|---|---|---|
| GET | `/api/v1/global-vault` | [글로벌 금고 조회](#글로벌-금고-조회) | ✅ | - |
| POST | `/api/v1/global-vault/transfer` | [자원 이전](#자원-이전) | ✅ | - |

> **구현 참고:** 영토 창고는 `building_instances`(성+Storage)의 `stored_gp`에 실제 저장된다. `sourceTerritoryId`로 그 위치 저장소 ↔ 금고 간 GP를 이전한다.

---

## 글로벌 금고 조회

**GET** `/api/v1/global-vault`

**Authorization**: Bearer `{{accessToken}}` (필수)

사용 페이지: 마이페이지, 자산 현황

내 글로벌 금고의 현재 잔액, 용량, 쿨다운 상태를 반환합니다. (Notion F-10.2)

### Response (200 OK)

```json
{
  "storedGP": 8500,
  "capacity": 10000,
  "lastTransferAt": "2026-04-08T12:00:00Z",
  "nextTransferAvailableAt": "2026-04-08T12:10:00Z",
  "isTransferAvailable": false
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `storedGP` | Long | 현재 금고 보관 GP | `global_vaults.stored_gp` |
| `capacity` | Long | 금고 최대 용량 | `global_vaults.capacity` |
| `lastTransferAt` | DateTime (nullable) | 마지막 이전 시각 | `global_vaults.last_transfer_at` |
| `nextTransferAvailableAt` | DateTime (nullable) | 다음 이전 가능 시각 | `last_transfer_at + VAULT_TRANSFER_COOLDOWN` |
| `isTransferAvailable` | Boolean | 현재 이전 가능 여부 | `now() >= nextTransferAvailableAt` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |


---

## 자원 이전

**POST** `/api/v1/global-vault/transfer`

**Authorization**: Bearer `{{accessToken}}` (필수)

점유 중인 영토의 창고에 있는 GP를 글로벌 금고로 이전하거나, 글로벌 금고에서 영토 창고로 역이전합니다. (Notion F-10.3)

영토 창고의 GP는 공격으로 탈취당할 수 있지만, 글로벌 금고에 보관된 GP는 계정에 귀속되어 안전합니다.

> 쿨다운: 이전 후 `VAULT_TRANSFER_COOLDOWN_MINUTES`(config, 기본 10분) 간 재이전 불가

### Request

```json
{
  "direction": "TO_VAULT",
  "sourceTerritoryId": 42,
  "amount": 5000
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `direction` | String | Y | `TO_VAULT` (영토→금고) / `FROM_VAULT` (금고→영토) |
| `sourceTerritoryId` | Long | Y | 이전 대상 영토 ID |
| `amount` | Long | Y | 이전할 GP 수량 (1 이상) |

### 비즈니스 규칙
- 본인 점유 영토(`territories.owner_id = userId`)에서만 이전 가능
- `TO_VAULT`: 영토 저장소 잔여 GP 이상 이전 불가 (그 영토 성+저장소 `building_instances.stored_gp` 합 >= amount)
- `FROM_VAULT`: 금고 잔여 GP 이상 이전 불가 (`global_vaults.stored_gp >= amount`)
- 금고 용량 초과 불가 (`global_vaults.stored_gp + amount <= global_vaults.capacity`)
- 이전 성공 시 양쪽 GP 원자적 차감/증가 (DB 트랜잭션 보장)
- `global_vaults.last_transfer_at` 갱신 → 쿨다운 계산 기준

### Response (200 OK)

```json
{
  "direction": "TO_VAULT",
  "transferredAmount": 5000,
  "sourceTerritoryId": 42,
  "territoryStorageAfter": 3200,
  "vaultStoredAfter": 8500,
  "vaultCapacity": 10000,
  "nextTransferAvailableAt": "2026-04-08T12:10:00Z"
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `direction` | String | 이전 방향 | 요청 `direction` |
| `transferredAmount` | Long | 이번에 이전된 GP 수량 | 요청 `amount` |
| `sourceTerritoryId` | Long | 대상 영토 ID | 요청 `sourceTerritoryId` |
| `territoryStorageAfter` | Long | 이전 후 영토 저장소 잔여 GP | 그 영토 `building_instances.stored_gp` 합 |
| `vaultStoredAfter` | Long | 이전 후 글로벌 금고 잔액 | `global_vaults.stored_gp` |
| `vaultCapacity` | Long | 글로벌 금고 최대 용량 | `global_vaults.capacity` |
| `nextTransferAvailableAt` | DateTime | 다음 이전 가능 시각 | `global_vaults.last_transfer_at + 쿨다운` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | `INVALID_AMOUNT` | 이전 수량 1 미만 |
| 400 | `INSUFFICIENT_GP` | 출처 GP 부족 |
| 403 | `NOT_TERRITORY_OWNER` | 본인 점유 영토가 아님 |
| 404 | `TERRITORY_NOT_FOUND` | 존재하지 않는 영토 |
| 409 | `VAULT_CAPACITY_EXCEEDED` | 금고 용량 초과 |
| 429 | `TRANSFER_COOLDOWN_ACTIVE` | 쿨다운 중 |

