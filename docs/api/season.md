# Season Pass API

> Notion 상세 기능 명세: [F-16 시즌 패스](https://www.notion.so/Functional-Specification-Access-Control-Matrix-3332efa4278d804e8ccfdb31151e9943)  
> 구현 상태: 🔄 일부 완료

## 목차

| Method | Endpoint | 기능 | 구현 |
|---|---|---|---|
| GET | `/api/v1/season-pass/me` | [시즌 패스 상태 조회](#시즌-패스-상태-조회) | ✅ |
| GET | `/api/v1/season-pass` | [시즌 패스 현황 조회](#시즌-패스-현황-조회) | 🔄 |
| POST | `/api/v1/season-pass/purchase` | [시즌 패스 구매](#시즌-패스-구매) | ✅ |

---

## 시즌 패스 상태 조회

**GET** `/api/v1/season-pass/me`

**Authorization**: Bearer `{{accessToken}}` (필수)

현재 시즌 패스 보유 여부, 만료일, 적용 중인 혜택을 반환합니다. (Notion F-16.2, F-16.3)

### Response (200 OK)

```json
{
  "hasSeasonPass": true,
  "seasonPass": {
    "seasonPassId": 1,
    "name": "시즌 패스 Vol.1",
    "startedAt": "2026-04-08T12:00:00Z",
    "expiresAt": "2026-05-08T12:00:00Z",
    "daysRemaining": 29,
    "benefits": {
      "islandBonusPct": 50,
      "extraBuilders": 1,
      "taxExemptBonus": 2
    }
  }
}
```

> 미보유 시: `{ "hasSeasonPass": false, "seasonPass": null }`

| field                                | 타입 | 설명 | 출처 |
|--------------------------------------|---|---|---|
| `hasSeasonPass`                      | Boolean | 시즌 패스 보유 여부 | `user_season_passes.is_active` AND `expires_at > now()` |
| `seasonPass.seasonPassId`           | Long (nullable) | 패스 ID | `user_season_passes.season_pass_id` |
| `seasonPass.name`                    | String (nullable) | 패스 이름 | `season_passes.name` |
| `seasonPass.startedAt`               | DateTime (nullable) | 패스 시작 시각 | `user_season_passes.started_at` |
| `seasonPass.expiresAt`               | DateTime (nullable) | 패스 만료 시각 | `user_season_passes.expires_at` |
| `seasonPass.daysRemaining`           | Integer (nullable) | 남은 일수 | `expires_at - now()` 계산값 |
| `seasonPass.benefits.islandBonusPct` | Integer (nullable) | 섬 GP 생산 보너스 (%) | `season_passes.island_bonus_pct` |
| `seasonPass.benefits.extraBuilders`  | Integer (nullable) | 추가 건설 슬롯 수 | `season_passes.extra_builders` |
| `seasonPass.benefits.taxExemptBonus` | Integer (nullable) | 세금 면제 보너스 영토 수 | `season_passes.tax_exempt_bonus` |

### 비즈니스 규칙

- `is_active = true` AND `expires_at > now()` 조건으로 유효 패스 조회
- Redis `season_pass:my:{userId}` 캐시 우선 조회 → 미존재 시 DB 조회 후 캐싱 (TTL 30분)
- **만료 알림 (F-16.4)**: `expires_at` 3일 전 및 당일 08:00에 알림 발송 (스케줄러 처리)
- **패스 만료 후 (F-16.5)**: 다음 토지세 징수 시점부터 `LAND_TAX_EXEMPT_COUNT` 기본값으로 복구

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |

### 남은 작업
- ✅ `SeasonPassService.getMyPass()` 구현
- ✅ Redis `season_pass:my:{userId}` 캐시 (TTL 30분)
- ⬜ 만료 알림 스케줄러 구현 (F-16.4)

---

## 시즌 패스 현황 조회

**GET** `/api/v1/season-pass`

**Authorization**: Bearer `{{accessToken}}` (필수)

현재 시즌의 패스 등급과 보상 수령 여부를 조회합니다.

### Response (200 OK)

```json
{
  "seasonId": 3,
  "seasonName": "Season 3",
  "passType": "PREMIUM",
  "currentLevel": 18,
  "currentXp": 420,
  "nextLevelXp": 1000,
  "rewards": [
    {
      "level": 15,
      "rewardName": "병력 증강제 x3",
      "isClaimed": true
    },
    {
      "level": 20,
      "rewardName": "전설 영토 스킨",
      "isClaimed": false
    }
  ],
  "seasonEndsAt": "2026-05-31T23:59:59Z"
}
```

| field                  | 타입 | 설명 | 출처 |
|------------------------|---|---|---|
| `seasonId`             | Long | 시즌 ID | `seasons.id` |
| `seasonName`           | String | 시즌 이름 | `"Season " + seasons.season_number` |
| `passType`             | String | `FREE` / `PREMIUM` | 유효한 `user_season_passes` 보유 여부 |
| `currentLevel`         | Integer | 현재 시즌패스 레벨 | `season_pass_progress.level` (없으면 1) |
| `currentXp`            | Integer | 현재 레벨 내 경험치 | `season_pass_progress.xp` (없으면 0) |
| `nextLevelXp`          | Integer | 다음 레벨까지 필요 경험치 | 고정 1,000 XP/레벨 |
| `rewards[].level`      | Integer | 보상 해금 레벨 | `season_pass_level_rewards.level` |
| `rewards[].rewardName` | String | 보상 이름 | `season_pass_level_rewards.reward_name` |
| `rewards[].isClaimed`  | Boolean | 보상 수령 여부 | `season_pass_reward_claims` |
| `seasonEndsAt`         | DateTime | 시즌 종료 시각 | `seasons.ended_at` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 401 | `UNAUTHORIZED` | 인증 실패 |
| 404 | `SEASON_NOT_FOUND` | 진행 중인 시즌 없음 |

### XP 적립 규칙

레벨당 `XP_PER_LEVEL = 1,000` XP 필요. 최대 레벨 제한 없음 (MVP).  
XP 적립 후 Redis `season_pass:progress:{userId}` 캐시를 무효화한다.

#### XP 트리거 및 적립량

| 이벤트 | XP | 이벤트 클래스 | 처리 방식 |
|---|---|---|---|
| 경매 낙찰 | +100 | `AuctionSettledEvent` (기존) | `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` |
| 공성전 승리 | +50 | `SiegeVictoryEvent` (신규) | `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` |

#### 레벨업 정책

- XP 누적이 `1,000` 이상이 되면 `SeasonPassProgress.addXp(int amount, int xpPerLevel)` 도메인 메서드가 자동 레벨업 처리
- 레벨업 후 잔여 XP는 이월됨 (예: 레벨 N에서 XP 950 보유 중 +100 적립 → 레벨 N+1, XP 50)

#### 정책 상수 (`SeasonPassPolicy`)

| 상수 | 값 | 설명 |
|---|---|---|
| `XP_AUCTION_WIN` | 100 | 경매 낙찰 시 획득 XP |
| `XP_SIEGE_VICTORY` | 50 | 공성전 승리 시 획득 XP |
| `XP_PER_LEVEL` | 1,000 | 레벨업에 필요한 XP |

### 시드 데이터 (`season-pass-rewards.yml`)

활성 시즌에 `season_pass_level_rewards` 레코드가 없으면 `SeasonPassLevelRewardSeeder`가 YAML에서 삽입한다.

| 레벨 | 보상 이름 |
|---|---|
| 5 | 병력 증강제 x1 |
| 10 | 공격권 x2 |
| 15 | 병력 증강제 x3 |
| 20 | 전설 영토 스킨 |
| 25 | 무적 방어막 x1 |
| 30 | 시즌 챔피언 칭호 |

### 남은 작업
- ✅ `SeasonPassService.getProgress()` 구현
- ✅ Redis `season_pass:progress:{userId}` 캐시 (TTL 30분)
- ⬜ `SeasonPassPolicy` 정책 상수 클래스 생성 (`XP_AUCTION_WIN=100`, `XP_SIEGE_VICTORY=50`, `XP_PER_LEVEL=1000`)
- ⬜ `SeasonPassProgress.addXp(int amount, int xpPerLevel)` 도메인 메서드 추가
- ⬜ `SeasonXpService`: `AuctionSettledEvent` 구독 → XP +100 적립 (`@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`)
- ⬜ `SiegeVictoryEvent` 신규 이벤트 클래스 생성 (필드: `userId`, `seasonId`)
- ⬜ `SeasonXpService`: `SiegeVictoryEvent` 구독 → XP +50 적립
- ⬜ XP 적립 후 Redis `season_pass:progress:{userId}` 캐시 무효화
- ⬜ `SeasonPassLevelRewardSeeder`: 활성 시즌에 레코드 없으면 `season-pass-rewards.yml`에서 삽입

---

## 시즌 패스 구매

**POST** `/api/v1/season-pass/purchase`

**Authorization**: Bearer `{{accessToken}}` (필수)

`SEASON_PASS_AP_COST`(config, 기본 1,000) AP를 소모하여 `SEASON_PASS_DURATION_DAYS`(config, 기본 30)일간 시즌 패스를 활성화합니다. (Notion F-16.1)

### Request Body

없음

### 비즈니스 규칙

- `wallets.available_ap` `SEASON_PASS_AP_COST` 차감 후 `user_season_passes` INSERT
- **중복 구매 시 기간 누적**: 기존 패스 보유 중 재구매 가능. 기존 `expires_at`에서 `SEASON_PASS_DURATION_DAYS`만큼 추가 연장
- 구매 즉시 혜택 적용 (섬 GP 보너스 `+SEASON_PASS_ISLAND_BONUS_PCT`%, 일꾼 `+SEASON_PASS_EXTRA_BUILDERS`, 세금 면제 `+SEASON_PASS_TAX_EXEMPT_BONUS`개)
- 구매 후 Redis `season_pass:my:{userId}` 캐시 갱신, `season_pass:progress:{userId}` 캐시 삭제

### 혜택 상세

| benefit | 설명 |
|---|---|
| `islandBonusPct` | Home Island GP 생산량 `+SEASON_PASS_ISLAND_BONUS_PCT`% (기본 +50%) |
| `extraBuilders` | 건설 일꾼 `+SEASON_PASS_EXTRA_BUILDERS`명 (기본 +1, 동시 건설 2개) |
| `taxExemptBonus` | 토지세 면제 구간 `+SEASON_PASS_TAX_EXEMPT_BONUS`개 (기본 +2, 3개 → 5개) |

### Response (201 Created)

```json
{
  "seasonPassId": 1,
  "name": "시즌 패스 Vol.1",
  "startedAt": "2026-04-08T12:00:00Z",
  "expiresAt": "2026-05-08T12:00:00Z",
  "costAP": 1000,
  "remainingAP": 500,
  "benefits": {
    "islandBonusPct": 50,
    "extraBuilders": 1,
    "taxExemptBonus": 2
  }
}
```

| field                     | 타입 | 설명 | 출처 |
|---------------------------|---|---|---|
| `seasonPassId`            | Long | 구매한 시즌 패스 ID | `season_passes.id` |
| `name`                    | String | 시즌 패스 이름 | `season_passes.name` |
| `startedAt`               | DateTime | 패스 시작 시각 | `user_season_passes.started_at` |
| `expiresAt`               | DateTime | 패스 만료 시각 | `user_season_passes.expires_at` |
| `costAP`                  | Integer | 차감된 AP | `season_passes.cost_ap` |
| `remainingAP`             | Integer | 구매 후 잔여 AP | `wallets.available_ap` |
| `benefits.islandBonusPct` | Integer | 섬 GP 생산 보너스 (%) | `season_passes.island_bonus_pct` |
| `benefits.extraBuilders`  | Integer | 추가 건설 슬롯 수 | `season_passes.extra_builders` |
| `benefits.taxExemptBonus` | Integer | 세금 면제 보너스 영토 수 | `season_passes.tax_exempt_bonus` |

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | `INSUFFICIENT_AP` | AP 잔액 부족 |
| 404 | `SEASON_PASS_NOT_FOUND` | 활성 시즌 패스 없음 |
| 404 | `USER_NOT_FOUND` | 사용자 없음 |

### 남은 작업
- ✅ `SeasonPassService.purchase()` 구현
- ✅ Redis `season_pass:my:{userId}` 캐시 갱신 (TTL 30분)
- ✅ Redis `season_pass:progress:{userId}` 캐시 무효화
