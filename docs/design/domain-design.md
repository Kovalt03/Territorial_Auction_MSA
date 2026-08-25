# 도메인 상세 설계

> Notion 원본: https://www.notion.so/3332efa4278d806697ccef749a8eda2c

---

## 1. 도메인 경계 (Bounded Context)

서비스는 10개의 독립된 영역으로 나뉩니다. 각 경계는 나중에 Microservice 단위가 됩니다.

1. **User Domain**: 사용자 정보, 자산(AP/GP), 글로벌 금고, 알림 설정, 프로필 이미지 관리
2. **Item Domain**: 아이템 종류 정의 및 구매 이력 관리
3. **Season Domain**: 시즌, 시즌 패스, 트로피, 리그, 시즌 랭킹 관리
4. **Map Domain**: 지도 물리 구조(영토, 대륙, 좌표, 등급, 토지세) 관리
5. **Auction Domain**: 경매 프로세스, 입찰 규칙, 가격 변동 이력 관리
6. **Social Domain**: 실시간 채팅, 길드 조직 및 멤버십 관리
7. **Notification Domain**: 이벤트 기반 알림 발송 및 수신 이력 관리
8. **Building Domain**: 영토 내 건물 배치, HP, GP 저장 관리
9. **Island Domain**: 유저 귀속 항시 보유 섬 관리 (공격 불가, 신규 유저 보호 거점)
10. **Military Domain**: 유닛 보유/배치, 공성전 이벤트 및 결과 관리

> **Auth**는 JWT 인증/인가 횡단 관심사로 독립 패키지로 관리하되 별도 Microservice로 분리 가능

---

## 2. 핵심 엔티티 및 속성

### 1. User Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **User** | Entity | 사용자 본체 | `id`, `username`, `password`, `created_at` |
| **UserProfile** | Entity | 프로필 이미지 (1:1) | `user_id`, `profile_image_url`, `updated_at` |
| **Wallet** | Entity | AP 자산 (GP·식량은 위치 저장소·금고로 분리) | `available_ap`, `locked_ap` |
| **GlobalVault** | Entity | 유저 개인 GP 금고 | `user_id`, `stored_gp`, `capacity`, `last_transfer_at` |
| **NotificationSetting** | Entity | 알림 수신 설정 | `user_id`, `is_outbid_enabled`, `is_auction_start_enabled`, `is_marketing_enabled` |

> **포인트 락(Lock) 전략**: 입찰 시 `available_ap` → `locked_ap`로 이동. 낙찰 시 최종 차감, 찬탈 시 복구.

### 2. Item Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **Item** | Entity | 아이템 종류 정의 | `id`, `name`, `item_type`, `cost_ap`, `cost_gp`, `daily_limit` |
| **ItemPurchase** | Entity | 아이템 구매 이력 | `id`, `user_id`, `item_id`, `quantity`, `purchased_at` |

`item_type`: INVINCIBILITY / ATTACK_NORMAL / ATTACK_PRECISION / GP_PURCHASE

### 3. Season Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **Season** | Entity | 시즌 정의 | `id`, `season_number`, `started_at`, `ended_at` |
| **SeasonPass** | Entity | 시즌 패스 단계 정의 | `id`, `name`, `cost_ap`, `island_production_bonus_pct`, `extra_builders`, `tax_exempt_bonus` |
| **UserSeasonPass** | Entity | 유저 패스 보유 현황 | `id`, `user_id`, `season_pass_id`, `started_at`, `expires_at`, `is_active` |
| **UserTrophy** | Entity | 유저 트로피 현황 | `user_id`, `score`, `league`, `season_id`, `updated_at` |
| **TrophyLog** | Entity | 트로피 변동 이력 | `id`, `user_id`, `season_id`, `delta`, `reason`, `score_after`, `created_at` |
| **SeasonReward** | Entity | 시즌 종료 보상 이력 | `id`, `user_id`, `season_id`, `league`, `gp_reward`, `created_at` |

### 4. Map Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **Territory** | Entity | 10×10 영토 단위 | `id`, `coordinate(VO)`, `continent_id`, `grade_id`, `owner_id`, `current_color`, `occupied_until` |
| **TerritoryGrade** | Entity | 등급 정의 | `id`, `grade`, `production_multiplier`, `auction_price_multiplier`, `pre_built_count`, `spawn_rate`, `grid_size` |
| **Continent** | Entity | 영토 묶음(대륙) | `id`, `name`, `theme_color` |
| **BonusTile** | Entity | 추가 생산 배율 적용 특수 영토 | `id`, `territory_id`, `multiplier`, `description` |
| **LandTaxLog** | Entity | 토지세 납세 이력 | `id`, `user_id`, `territory_count`, `gp_charged`, `status`, `charged_at` |
| **Coordinate** | VO | 좌표 정보 | `x`, `y` |

등급별 내부 그리드: S: 12×12, A: 10×10, B: 8×8, C: 6×6 (`TerritoryGrade.grid_size`로 관리)

### 5. Auction Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **Auction** | Entity | 진행 중인 경매 | `id`, `territory_id`, `current_bidder_id`, `current_price`, `end_at` |
| **AuctionHistory** | Entity | 역대 낙찰 이력 | `id`, `auction_id`, `territory_id`, `winner_id`, `final_price`, `won_at` |
| **ColorHistory** | Entity | 색상 변경 이력 | `id`, `territory_id`, `user_id`, `color_code`, `created_at` |
| **BidRule** | VO | 입찰 정책 | `min_increase_rate`(5%), `min_increase_point`(10) |

### 6. Social Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **ChatRoom** | Entity | 채팅방 | `id`, `type`(WORLD/CONTINENT/GUILD), `target_id` |
| **ChatMessage** | Entity | 메시지 로그 | `id`, `room_id`, `sender_id`, `content`, `sent_at` |
| **Guild** | Entity | 길드 | `id`, `name`, `description`, `master_id`, `max_members`, `created_at` |
| **GuildMember** | Entity | 길드 멤버십 | `guild_id`, `user_id`, `role`(MASTER/MEMBER), `joined_at` |
| **GuildApplication** | Entity | 가입 신청 | `id`, `guild_id`, `applicant_id`, `status`(PENDING/APPROVED/REJECTED), `applied_at` |

**채팅방 타입 정의:**
- `WORLD`: 전체 유저 참여 가능한 월드 채팅
- `CONTINENT`: 해당 대륙 클릭 시 자동 입장하는 대륙 채팅
- `GUILD`: 길드 생성 시 자동 생성되는 길드 채팅 (멤버만 입장 가능)

### 7. Notification Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **InterestGroup** | Entity | 알림 설정 그룹 | `id`, `user_id`, `continent_id` |
| **NotificationLog** | Entity | 발송된 알림 내역 | `id`, `user_id`, `type`, `message`, `is_read` |

### 8. Building Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **BuildingType** | Entity | 건물 종류 정의 | `id`, `name`, `width`, `height`, `max_hp`, `base_cost_gp`, `zone_restriction` |
| **BuildingInstance** | Entity | 영토 내 배치된 건물 | `id`, `territory_id`, `building_type_id`, `pos_x`, `pos_y`, `hp`, `level`, `zone`, `is_destroyed` |
| **BuildingEffect** | VO | 건물 효과 정의 | `production_rate`, `storage_capacity`, `defense_power`, `attack_power` |

**건물 로직 규칙:**
- Castle은 Zone 1에만 배치 가능, 영토당 반드시 1개
- 건물 파괴(`is_destroyed=true`) 시 효과 정지, GP 소비 후 재건 가능
- 같은 셀 중복 배치 불가

### 9. Island Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **HomeIsland** | Entity | 유저 귀속 항시 보유 섬 | `id`, `user_id`, `grid_size`, `created_at` |
| **IslandBuildingInstance** | Entity | 섬 내 배치된 건물 | `id`, `island_id`, `building_type_id`, `pos_x`, `pos_y`, `hp`, `level` |

### 10. Military Context

| 객체명 | 타입 | 설명 | 핵심 속성 |
|---|---|---|---|
| **UnitType** | Entity | 유닛 종류 정의 | `id`, `name`, `attack_power`, `defense_power`, `cost_gp`, `food_cost`(1회 소모), `level`(필요 병영 레벨) |
| **UnitInstance** | Entity | 유저 보유 유닛 스택 | `id`, `user_id`, `unit_type_id`, `level`, `quantity`, `home_territory_id`/`home_island_id`, `deployed_territory_id` |
| **UnitTypeLevelSpec** | Entity | 유닛 레벨별 스펙 (관리자 편집) | `unit_type_id`, `level`, `attack_power`, `defense_power`, `train_cost_food`, `required_barracks_level` |
| **UnitResearch** | Entity | 계정 단위 유닛 연구 진행 | `user_id`, `unit_type_id`, `researched_level`, `pending_level`, `research_complete_at` |
| **AttackToken** | Entity | 공격권 보유 현황 | `user_id`(PK), `normal_count`, `precision_count` |
| **SiegeEvent** | Entity | 공성 이벤트 | `id`, `attacker_id`, `defender_id`, `target_territory_id`, `attack_zone`, `status`, `resolve_at` |
| **SiegeForce** | Entity | 공성 투입 병력 스냅샷 | `siege_id`, `unit_type_id`, `level`, `quantity` |
| **SiegeStructure** | Entity | 공성 전용 건물 (정산 후 삭제) | `siege_id`, `type`(STAGING/TOWER/SUPPLY), `coord_x`, `coord_y` |
| **SiegeResult** | Entity | 전투 결과 로그 | `id`, `siege_id`, `winner`, `attacker_units_lost`, `defender_units_lost`, `looted_amount`, `result_type` |

---

## 3. 핵심 비즈니스 로직

### 3.1 포인트 락(Lock) & 원자적 환불

- 입찰 시: `available_ap` → `locked_ap` (DB 트랜잭션 보장)
- 찬탈 시: 기존 입찰자의 `locked_ap` → `available_ap` 복구
- 낙찰 시: 최고 입찰자의 `locked_ap` 영구 차감

### 3.2 경매 진행 규칙

- 상회 입찰 조건: `Next ≥ Current × 1.05` AND `Next ≥ Current + 10`
- **재입찰 제한**: 현재 `current_bidder_id`와 동일한 유저는 입찰 불가 — 찬탈 후에만 재입찰 가능
- Anti-Sniping: 종료 1분 전 입찰 시 30초 연장 (최대 연장 한도 초과 불가)
- `Territory.occupied_until` 만료 + 유예시간(Idle) 이후 새 `Auction` 자동 생성

### 3.3 도메인 간 협력 (Event-Driven)

1. **Auction Domain**: 입찰 성공 → `OUTBID` 이벤트 발행
2. **User Domain**: 해당 유저의 `NotificationSetting` 확인
3. **Notification Domain**: 설정이 `true`라면 알림 생성 후 발송
4. **Military Domain**: Castle HP 0 도달 → `CastleDestroyedEvent` 발행 → **Auction Domain**: `Territory.release()` 후 `Auction` 즉시 생성

> 도메인 간 Service 직접 주입 금지 규칙에 따라, Military → Auction 협력은 `ApplicationEventPublisher`를 통해 이벤트로 처리한다.

### 3.4 포인트 생산 규칙 (Passive Income)

- 최종 생산량: `base_rate × 인접_보너스_배율 × 보너스칸_배율`
- 인접 보너스 배율: `1 + (인접 점유 수 × 0.1)`
- 생산 중단: `Territory.occupied_until` 만료 시 즉시 중단
- 비동기 처리: 생산 적립은 경매 로직과 독립된 스케줄러 담당

**GP 저장 흐름 (Territory-Scoped GP)**:
1. Workshop 생산 → 해당 위치(영토/섬)의 **저장소(성+Storage) `stored_gp`**에 적립 (Storage는 공격 시 약탈 대상)
2. 위치 저장소 GP는 그 위치 내 건설·업그레이드·유닛 생산에 직접 사용
3. 일부를 **Global Vault(`global_vaults.stored_gp`)**로 이전 → 어디서든 사용 가능 (이전 쿨다운 적용). 계정 단위 보상 GP(아이템·시즌·시즌패스)와 상실 환수 GP도 금고로 들어간다
4. 지갑에는 GP가 없다 — GP 잔고 표시는 **금고 + 위치 저장소 합**

### 3.5 전투 계산

- ATK = Σ(파견 유닛 `attack_power` × 수량) — 레벨 2 이상은 `unit_type_level_specs`의 레벨별 스펙 사용
- DEF = Σ(방어 유닛 `defense_power` × 수량) + Σ(해당 Zone 방어 건물 `defense_power`) — 방어 유닛도 레벨별 스펙 적용
- 공성 건물: TOWER는 ATK 보너스, SUPPLY는 공격 쿨다운 단축. STAGING 수용량이 투입 병력 상한
- 성공 판정: ATK > DEF
- Zone 클리어: `Σ(Zone 방어 건물 hp) / Σ(Zone 방어 건물 max_hp) < (1 − ZONE_CLEAR_THRESHOLD)`

**Zone별 전투 결과 (공격 성공 시)**:

| Zone | 주요 건물 | 결과 유형 | 효과 |
|---|---|---|---|
| Zone 3 | Storage | `LOOT` | 공격자 **금고** `stored_gp += lootedGp`, Storage `storedGp -= lootedGp` |
| Zone 2 | Workshop / Tower | `DEBUFF` | 건물 HP 감소 → 0이면 `isDestroyed=true` |
| Zone 1 | Castle | `AUCTION`* | Castle HP 0 → **공격자 즉시 인계**(경매 없음): 저장 GP 80% 공격자 금고·나머지·식량 소멸, 방어 유닛 전멸, 영토 점유 이전. (\*enum 이름은 이력 호환상 `AUCTION` 유지) |

**유닛 손실** (`MilitaryPolicy` 상수 기준):
- 공격 성공: 공격자 `ATTACKER_LOSS_RATE(30%)`, 방어자 `DEFENDER_LOSS_RATE(30%)`
- 공격 실패: 공격자 `ATTACKER_FAIL_LOSS_RATE(50%)`

**스케줄러**: `SiegeScheduler` 1분 주기 polling. `resolveAt <= now`인 PENDING 이벤트를 일괄 처리한다.

### 3.6 유닛 연구 (계정 단위)

1. 연구소(`RESEARCH_LAB`) 건물 레벨이 연구 상한을 결정 — 목표 레벨 = 현재 + 1, 필요 연구소 레벨 = 목표 − 1
2. 비용 `2000 × 목표레벨` GP를 **금고(GlobalVault)**에서 차감, 소요 `30분 × 목표레벨`
3. 완료 처리는 스케줄러가 아닌 **조회 시점 지연 반영**(`applyCompletionIfDue`) — 배치 부하 없이 정합성 유지
4. 연구 상태는 계정 단위(`user_id` × `unit_type_id`)라 모든 위치의 생산에 공통 적용
5. 생산 시 해금된 레벨 이하를 선택하며, 레벨은 유닛 스택 식별자에 포함된다

### 3.5 토지세 미납 처리

1. 매일 자정 GP 잔액 부족 → 경고 알림 발송
2. 유예기간(config) 내 납부 없을 시: **최저 등급(D→C→B→A→S) 영토부터 순차 강제 경매 전환**
3. 강제 경매 낙찰 대금 합계가 미납 세금 이상 되면 **즉시 처분 중단**
4. 강제 처분 시 **무적 상태·보호 기간 무시** (유일한 무적/보호 우회 예외)
5. 처분·점유 만료 영토마다 상실 정산: 저장 GP 80% 원소유자 금고 환수(20%·식량 소멸), 방어 유닛 홈 아일랜드 퇴각(섬 수용량 초과분 소멸) — `TerritoryLostEvent`

### 3.6 시즌 관리

- **반자동 운영**: 관리자가 DB에 시즌 `started_at`·`ended_at`을 설정하면 스케줄러가 자동 처리
- 시즌 종료 시: 리그별 보상 지급 → 트로피 50% 소프트 리셋 → 신규 시즌 레코드 생성
- 시즌 1은 전체 구현 완료 후 관리자가 수동으로 시작

### 3.7 실시간 채팅

- WORLD 채팅은 누구나 참여, CONTINENT 채팅은 해당 대륙 클릭 시 자동 입장, GUILD 채팅은 멤버만 접근
- 구현: REST API가 아닌 **WebSocket + STOMP** 프로토콜 사용
- 다중 서버 확장 시 Redis Pub-Sub 브로커 사용
- 채팅 로그는 최신 100건만 유지 (V1 정책)

### 3.8 알림 유형

- **상회 입찰(Outbid)**: 내가 최고 입찰자인 경매에서 상회 입찰 시 즉시 발송 (최우선순위)
- **관심 그룹 경매 시작(Auction Start)**: 구독한 `InterestGroup` 내 영토 BIDDING 전환 시
- **낙찰 결과(Auction Result)**: 경매 종료 시 최종 낙찰자 및 참여자에게 결과 알림
