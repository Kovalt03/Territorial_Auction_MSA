# Land Tax API

> Notion 상세 기능 명세: [F-14 토지세 시스템](https://www.notion.so/Functional-Specification-Access-Control-Matrix-3332efa4278d804e8ccfdb31151e9943)  
> 구현 상태: 🔄 일부 완료

## 목차

| Method | Endpoint | 기능 | 구현 |
|---|---|---|---|
| GET | `/api/v1/land-tax/status` | [토지세 현황 조회](#토지세-현황-조회) | ✅ |
| GET | `/api/v1/land-tax/logs` | [납세 내역 조회](#납세-내역-조회) | ✅ |

---

## 토지세 현황 조회

**GET** `/api/v1/land-tax/status`

**Authorization**: Bearer `{{accessToken}}` (필수)

사용 페이지: 마이페이지

현재 보유 영토 수에 따른 일일 토지세 예상액, 면제 구간, 다음 납부일을 반환합니다. (Notion F-14.1, F-14.2)

### Response (200 OK)

```json
{
  "territoryCount": 8,
  "taxBreakdown": {
    "exemptCount": 3,
    "taxableCount": 5,
    "dailyGP": 250
  },
  "seasonPassExemptBonus": 2,
  "effectiveExemptCount": 5,
  "finalDailyGP": 150,
  "nextChargeAt": "2026-04-28T00:00:00"
}
```

> `nextChargeAt`은 `LocalDateTime`(KST, UTC 오프셋 `Z` 없음)으로 직렬화된다. 프론트는 로컬(KST) 시각으로 해석한다.

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `territoryCount` | Integer | 현재 보유 영토 수 (Home Island 제외) | `territories` COUNT |
| `taxBreakdown.exemptCount` | Integer | 기본 면제 구간 내 영토 수 (최대 `LAND_TAX_EXEMPT_COUNT`) | 누진 정책 기준 |
| `taxBreakdown.taxableCount` | Integer | 과세 영토 수 | `territoryCount - exemptCount` |
| `taxBreakdown.dailyGP` | Integer | 시즌패스 미적용 일일 세금 | 누진 정책 계산값 |
| `seasonPassExemptBonus` | Integer | 시즌패스 추가 면제 구간 (보유 시 2, 미보유 시 0) | `season_passes.tax_exempt_bonus` |
| `effectiveExemptCount` | Integer | 실제 면제 영토 수 (`exemptCount + seasonPassExemptBonus`) | 계산값 |
| `finalDailyGP` | Integer | 최종 일일 토지세 (GP) | 누진 정책 재계산값 |
| `nextChargeAt` | DateTime | 다음 세금 납부 시각 | 매일 `LAND_TAX_COLLECTION_HOUR`(config) KST |

### 토지세 누진 구조

| 과세 영토 수 (taxableCount) | 일일 세금 |
|---|---|
| 0 | 면제 |
| 1~3개 | 50 GP/일 |
| 4~7개 | 150 GP/일 |
| 8개↑ | 400 GP/일 |

> 시즌 패스 보유 시 면제 기준 +`SEASON_PASS_TAX_EXEMPT_BONUS`개 적용 (기본 +2)

### 징수 규칙 (F-14.1)

- 매일 `LAND_TAX_COLLECTION_HOUR`(config) 스케줄러가 실제 점유 영토 수를 집계하여 GP를 일괄 차감 — **금고(`global_vaults.stored_gp`) 우선 → 부족 시 영토 저장소(`building_instances.stored_gp`)** 순 (세금 회피 방지를 위한 위치별 GP 원칙의 유일한 예외)
- **Home Island는 영토 수 집계에서 제외** (F-14.6)

### GP 부족 처리 (F-14.3)

- GP 잔액 부족으로 차감 실패 시: 주의 알림 발송 + `LAND_TAX_GRACE_PERIOD_HOURS`(config) 유예 기간 부여
- `land_tax_logs`에 `FAILED` 상태로 기록

### 미납 강제 처리 (F-14.4)

- 유예 기간 내 납세 실패 시: 보유 영토 중 **가장 낮은 등급(D → C → B → A → S 순)부터 순차 강제 경매 전환**
- 강제 경매 전환된 영토는 즉시 `BIDDING` 상태로 변경
- 처분되는 영토마다 상실 정산: 저장 GP의 **80%를 원소유자 금고로 환수**(나머지 20%·저장 식량 소멸), 방어 유닛은 홈 아일랜드로 퇴각(섬 수용량 초과분 소멸) — `TerritoryLostEvent`
- 낙찰 대금 합산이 미납 세금 이상이 되면 **즉시 처분 중단** (세금 초과분은 다음 납부에 이월하지 않음)
- **무적 상태(`invincible:{territoryId}`)·보호 기간(`occupied_until`)이 남아 있어도 강제 처분 대상에서 제외되지 않음** — 토지세 강제 처분이 유일한 예외

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |

### 남은 작업
- ✅ `LandTaxService.getLandTaxStatus()` 구현
- ⬜ Redis `land_tax:expected:{userId}` 캐시 연동 (TTL: 자정까지)

---

## 납세 내역 조회

**GET** `/api/v1/land-tax/logs?page={0}&size={10}&status={PAID|FAILED|EXEMPT|EVICTED}`

**Authorization**: Bearer `{{accessToken}}` (필수)

토지세 부과 및 납부 이력을 페이징하여 반환합니다. (Notion F-14.5)

### Query Parameters

| parameter | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | Integer | N | 0 | 페이지 번호 (0-based) |
| `size` | Integer | N | 10 | 페이지 크기 |
| `status` | String | N | (전체) | `PAID` / `FAILED` / `EXEMPT` / `EVICTED` — 생략 시 전체 조회 |

### Response (200 OK)

```json
{
  "totalCount": 15,
  "logs": [
    {
      "logId": 33,
      "chargedAt": "2026-04-08T00:00:00",
      "territoryCount": 8,
      "gpCharged": 150,
      "status": "PAID"
    },
    {
      "logId": 30,
      "chargedAt": "2026-04-07T00:00:00",
      "territoryCount": 3,
      "gpCharged": 0,
      "status": "EXEMPT"
    }
  ]
}
```

| field | 타입 | 설명 | 출처 |
|---|---|---|---|
| `totalCount` | Long | 전체 납세 이력 수 | `land_tax_logs` COUNT |
| `logs[].logId` | Long | 납세 이력 ID | `land_tax_logs.id` |
| `logs[].chargedAt` | DateTime | 세금 부과 시각 (`LocalDateTime`, KST, `Z` 없음) | `land_tax_logs.charged_at` |
| `logs[].territoryCount` | Integer | 부과 시점 보유 영토 수 | `land_tax_logs.territory_count` |
| `logs[].gpCharged` | Integer | 차감된 GP (면제 시 0) | `land_tax_logs.gp_charged` |
| `logs[].status` | String | `PAID` / `FAILED` / `EXEMPT` / `EVICTED` | `land_tax_logs.status` |

> 상태 의미 — `PAID`: 정상 납부 / `FAILED`: GP 부족으로 차감 실패 → 유예 기간 시작 / `EXEMPT`: 과세액 0(면제 구간) / `EVICTED`: 유예 기간 만료로 강제 경매 전환  
> `charged_at` 내림차순 정렬

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |

### 남은 작업
- ✅ `LandTaxService.getLandTaxLogs()` 구현
- ⬜ 세금 납부/환수 스케줄러 구현 (`processAllUsersTax`, `processUserTax`, `enforceEviction`)
- ⬜ GP 부족 처리 및 유예 기간 로직 구현 (F-14.3, F-14.4)
- ⬜ Redis `land_tax:expected:{userId}` 캐시 연동 (TTL: 자정까지)
