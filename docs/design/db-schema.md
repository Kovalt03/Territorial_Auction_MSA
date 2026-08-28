# DB 설계

> Notion 원본: https://www.notion.so/DB-3332efa4278d81c7b707e4456b25774e

---

## PostgreSQL 테이블 목록

### 👤 User Domain

#### users

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `username` | `VARCHAR(50)` | NOT NULL, UNIQUE | |
| `email` | `VARCHAR(100)` | NOT NULL, UNIQUE | |
| `password_hash` | `TEXT` | NOT NULL | |
| `nickname` | `VARCHAR(30)` | NOT NULL, UNIQUE | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |
| `status` | `VARCHAR(10)` | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / WITHDRAWN / SUSPENDED |
| `role` | `VARCHAR(10)` | NOT NULL, DEFAULT 'USER' | USER / ADMIN — 관리자 페이지 접근 권한 |

#### wallets

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `user_id` | `BIGINT` | PK, FK → users.id | |
| `available_ap` | `INTEGER` | NOT NULL, DEFAULT 0 | 사용 가능 Auction Point |
| `locked_ap` | `INTEGER` | NOT NULL, DEFAULT 0 | 입찰 중 잠금 AP |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | |

> 지갑은 **AP만** 보유한다. GP는 위치 저장소(`building_instances.stored_gp`) + 글로벌 금고(`global_vaults.stored_gp`), 식량은 위치 저장소(`building_instances.stored_food`)로 일원화됐다. 옛 `available_gp`/`available_food` 컬럼은 자원 스코프 전환에서 DROP.

#### notification_settings

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `user_id` | `BIGINT` | PK, FK → users.id | |
| `is_outbid_enabled` | `BOOLEAN` | DEFAULT true | 상회 입찰 알림 |
| `is_auction_start_enabled` | `BOOLEAN` | DEFAULT true | 관심 지역 경매 시작 알림 |
| `is_marketing_enabled` | `BOOLEAN` | DEFAULT false | 마케팅 알림 |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | |

#### user_profiles

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `user_id` | `BIGINT` | PK, FK → users.id | |
| `profile_image_url` | `VARCHAR(255)` | NULL 허용 | S3 오브젝트 URL |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | |

---

### 📦 Item Domain

#### items

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(50)` | NOT NULL | |
| `item_type` | `VARCHAR(20)` | NOT NULL | INVINCIBILITY / ATTACK_NORMAL / ATTACK_PRECISION / GP_PURCHASE |
| `cost_ap` | `INTEGER` | NULL | NULL이면 AP로 구매 불가 |
| `cost_gp` | `INTEGER` | NULL | NULL이면 GP로 구매 불가 |
| `daily_limit` | `INTEGER` | NULL | NULL이면 무제한 |

#### item_purchases

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `item_id` | `BIGINT` | FK → items.id | |
| `quantity` | `INTEGER` | NOT NULL, DEFAULT 1 | |
| `target_territory_id` | `BIGINT` | FK, NULL | 무적권 사용 시 대상 영토 |
| `purchased_at` | `TIMESTAMPTZ` | NOT NULL | |

---

### 🏆 Season Domain

#### seasons

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `season_number` | `INTEGER` | NOT NULL, UNIQUE | |
| `started_at` | `TIMESTAMPTZ` | NOT NULL | |
| `ended_at` | `TIMESTAMPTZ` | NULL | NULL이면 진행 중 |
| `processed_at` | `TIMESTAMPTZ` | NULL | 시즌 종료 배치 완료 시각. NULL이면 미처리 |

#### season_passes

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(50)` | NOT NULL | |
| `cost_ap` | `INTEGER` | NOT NULL | |
| `duration_days` | `INTEGER` | NOT NULL, DEFAULT 30 | |
| `island_bonus_pct` | `INTEGER` | NOT NULL | 섬 생산량 보너스 % |
| `extra_builders` | `INTEGER` | NOT NULL, DEFAULT 1 | 추가 건설 일꾼 |
| `tax_exempt_bonus` | `INTEGER` | NOT NULL, DEFAULT 2 | 토지세 면제 추가 구간 |

#### user_season_passes

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `season_pass_id` | `BIGINT` | FK | |
| `started_at` | `TIMESTAMPTZ` | NOT NULL | |
| `expires_at` | `TIMESTAMPTZ` | NOT NULL | 만료 시각 |
| `is_active` | `BOOLEAN` | NOT NULL, DEFAULT true | |

#### user_trophies

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `user_id` | `BIGINT` | PK, FK | |
| `score` | `INTEGER` | NOT NULL, DEFAULT 0 | 현재 트로피 |
| `league` | `VARCHAR(10)` | NOT NULL, DEFAULT 'BRONZE' | BRONZE / SILVER / GOLD / DIAMOND / CHAMPION |
| `season_id` | `BIGINT` | FK | 현재 시즌 |
| `last_reset_season_id` | `BIGINT` | FK, NULL | 마지막 리셋이 적용된 시즌 ID (멱등성 체크용) |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | |

#### trophy_logs

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `season_id` | `BIGINT` | FK | |
| `siege_id` | `BIGINT` | FK, NULL | 연관 전투 |
| `delta` | `INTEGER` | NOT NULL | 변동량 (+/-) |
| `reason` | `VARCHAR(30)` | NOT NULL | ATK_WIN_CASTLE / ATK_FAIL / DEF_WIN 등 |
| `score_after` | `INTEGER` | NOT NULL | 변동 후 트로피 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |

#### season_rewards

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `season_id` | `BIGINT` | FK | |
| `league` | `VARCHAR(10)` | NOT NULL | |
| `gp_reward` | `INTEGER` | NOT NULL | |
| `attack_token_normal` | `INTEGER` | DEFAULT 0 | |
| `attack_token_precision` | `INTEGER` | DEFAULT 0 | |
| `title_reward` | `VARCHAR(30)` | NULL | Champion 전용 칭호 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |

#### season_territory_holds (시즌 영토 등급 보유 집계)

영토 낙찰 / 점유 종료 이벤트마다 누적. 주기적 배치로 랭킹 점수를 계산한다.

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `season_id` | `BIGINT` | FK → seasons.id | |
| `user_id` | `BIGINT` | FK → users.id | |
| `territory_id` | `BIGINT` | FK → territories.id | |
| `grade` | `VARCHAR(1)` | NOT NULL | S / A / B / C / D |
| `held_from` | `TIMESTAMPTZ` | NOT NULL | 점유 시작 (낙찰 시각) |
| `held_until` | `TIMESTAMPTZ` | NULL 허용 | 점유 종료. NULL이면 현재 보유 중 |

INDEX: `(season_id, user_id)` — 랭킹 집계 최적화

---

### 🗺️ Map Domain

#### continents

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(50)` | NOT NULL | 내부 지리명 (마이그레이션 키로만 사용) |
| `theme_color` | `VARCHAR(7)` | NOT NULL | HEX 색상 코드 |
| `display_name` | `VARCHAR(50)` | NULL 허용 | 표시 행성명 (크리오 행성 등) |
| `grade` | `VARCHAR(2)` | NULL 허용 | 최고 영토 등급 (S / A / B / C) |
| `min_trophy_required` | `INTEGER` | NULL 허용 | 진입 트로피 조건. NULL = 자유 입장 |
| `description` | `VARCHAR(100)` | NULL 허용 | 행성 설명 문구 |

> 초기 데이터는 `src/main/resources/continents.yml`에서 `ContinentSeeder`가 앱 시작 시 자동 삽입한다.  
> `name` 컬럼은 기존 DB 마이그레이션 판별 키로만 사용되며, 화면 표시에는 `display_name`을 사용한다.

#### territory_grades

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `grade` | `VARCHAR(1)` | NOT NULL, UNIQUE | S / A / B / C / D |
| `production_multiplier` | `NUMERIC(3,1)` | NOT NULL | 생산량 배율 (0.5~2.0) |
| `auction_price_multiplier` | `NUMERIC(3,1)` | NOT NULL | 시작 경매가 배율 |
| `pre_built_count` | `INTEGER` | NOT NULL, DEFAULT 0 | 사전 배치 건물 수 |
| `spawn_rate` | `NUMERIC(4,3)` | NOT NULL | 등장 확률 |
| `grid_size` | `INTEGER` | NOT NULL | S:12 / A:10 / B:8 / C:6 |

#### territories

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `coord_x` | `INTEGER` | NOT NULL | 10×10 그리드 좌표 |
| `coord_y` | `INTEGER` | NOT NULL | |
| `continent_id` | `BIGINT` | FK → continents.id | |
| `owner_id` | `BIGINT` | FK → users.id, NULL 허용 | 현재 점유자 |
| `current_color` | `VARCHAR(7)` | NULL 허용 | HEX 색상 |
| `occupied_until` | `TIMESTAMPTZ` | NULL 허용 | 점유 만료 일시 |
| `status` | `VARCHAR(10)` | NOT NULL | BIDDING / OCCUPIED / IDLE |
| `base_production_rate` | `INTEGER` | NOT NULL, DEFAULT 1 | 분당 기본 생산량(P) |
| `last_produced_at` | `TIMESTAMPTZ` | NULL 허용 | 마지막 생산 시각 |
| `grade_id` | `BIGINT` | FK → territory_grades.id | |

#### bonus_tiles

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `territory_id` | `BIGINT` | FK, UNIQUE | |
| `multiplier` | `NUMERIC(4,2)` | NOT NULL | 생산 배율 (예: 1.5, 2.0) |
| `description` | `VARCHAR(100)` | | UI 표시용 설명 |

#### land_tax_logs

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `territory_count` | `INTEGER` | NOT NULL | 부과 시점 보유 수 |
| `gp_charged` | `INTEGER` | NOT NULL | |
| `status` | `VARCHAR(10)` | NOT NULL | PAID / FAILED / EXEMPT |
| `charged_at` | `TIMESTAMPTZ` | NOT NULL | |

#### territory_production_logs

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `territory_id` | `BIGINT` | FK | |
| `owner_id` | `BIGINT` | FK | |
| `amount` | `INTEGER` | NOT NULL | |
| `reason` | `VARCHAR(30)` | NOT NULL | BASE / ADJACENT_BONUS / BONUS_TILE |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |

#### color_histories

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `territory_id` | `BIGINT` | FK | |
| `user_id` | `BIGINT` | FK | |
| `color_code` | `VARCHAR(7)` | NOT NULL | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |

---

### 🔨 Auction Domain

> **⚙️ MSA — DB 분리**: `auctions`·`auction_bids`·`auction_histories`는 이제 **auction-service 전용 DB(`auction-postgres`)가 소유**한다(Flyway `V1__init_auction.sql`). 관계는 FK가 아니라 **ID + 스냅샷**(coordX·continentName·grade·bidderNickname 등)으로 보관 — 아래 표의 `FK →`는 모놀리식 단일 DB 시절 표기이며, 실제 auction-service에선 값(ID)만 저장하고 조인하지 않는다.
> - 모놀리식 DB에 남은 동명 테이블은 **stale**(추출 이후 미사용) — 하드 컷오버 후 별도 drop 마이그레이션 예정.
> - 맵 그리드 '경매중' 표시는 모놀리식 DB의 **`territory_auction_status` 프로젝션**(아래)에서 읽는다.

#### auctions

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `territory_id` | `BIGINT` | FK → territories.id | |
| `current_bidder_id` | `BIGINT` | FK → users.id, NULL 허용 | |
| `current_price` | `INTEGER` | NOT NULL | |
| `start_at` | `TIMESTAMPTZ` | NOT NULL | |
| `end_at` | `TIMESTAMPTZ` | NOT NULL | |
| `max_extend_until` | `TIMESTAMPTZ` | NOT NULL | 최대 연장 한도 (Anti-Sniping) |

#### auction_bids (입찰 이력 — 가격 변동 그래프용)

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `auction_id` | `BIGINT` | FK → auctions.id | |
| `bidder_id` | `BIGINT` | FK → users.id, NULL | NULL = 시스템(시작가) |
| `price` | `INTEGER` | NOT NULL | |
| `bid_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

INDEX: `(auction_id, bid_at ASC)` — 그래프 조회 최적화

#### auction_histories

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `auction_id` | `BIGINT` | FK | |
| `territory_id` | `BIGINT` | FK | |
| `winner_id` | `BIGINT` | FK → users.id | |
| `final_price` | `INTEGER` | NOT NULL | |
| `won_at` | `TIMESTAMPTZ` | NOT NULL | |
| `season_id` | `BIGINT` | FK → seasons.id, NULL 허용 | 시즌 중 낙찰 시 연결 (경매 AP 소비 랭킹 집계용) |

#### territory_auction_status (⚙️ MSA 읽기 프로젝션 — 모놀리식/map DB)

auction-service의 `auction.opened/bid/closed` 이벤트로만 갱신되는 read-model. 맵 그리드 '경매중' 뱃지·영토 상세 현재가를 auction 테이블 조회 없이 로컬에서 읽기 위함(핫패스 격리). 영토당 활성 경매 1개이므로 `territory_id`가 PK.

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `territory_id` | `BIGINT` | PK | 영토당 1행 (활성 경매) |
| `auction_id` | `BIGINT` | NOT NULL, INDEX | bid 이벤트 매칭용 |
| `current_price` | `INTEGER` | NOT NULL | |
| `end_at` | `TIMESTAMPTZ` | NOT NULL | 조회 시 `end_at > now()`로 활성 판별(누락된 close 이벤트 자가 치유) |

> 행 존재 + `end_at` 미래 ⟺ 경매 진행 중. 소유: 모놀리식 map 도메인(Flyway `V2__territory_auction_status.sql`).

---

### 💬 Social Domain

#### guilds

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(30)` | NOT NULL, UNIQUE | |
| `description` | `VARCHAR(200)` | NULL 허용 | |
| `master_id` | `BIGINT` | FK → users.id | |
| `max_members` | `INTEGER` | NOT NULL, DEFAULT 30 | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

#### guild_members

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `guild_id` | `BIGINT` | PK, FK → guilds.id | |
| `user_id` | `BIGINT` | PK, FK → users.id | |
| `role` | `VARCHAR(10)` | NOT NULL | MASTER / MEMBER |
| `joined_at` | `TIMESTAMPTZ` | NOT NULL | |

#### guild_applications

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `guild_id` | `BIGINT` | FK → guilds.id | |
| `applicant_id` | `BIGINT` | FK → users.id | |
| `status` | `VARCHAR(10)` | NOT NULL, DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| `applied_at` | `TIMESTAMPTZ` | NOT NULL | |

#### chat_rooms

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `type` | `VARCHAR(10)` | NOT NULL | WORLD / CONTINENT / GUILD |
| `target_id` | `BIGINT` | NULL 허용 | 대륙 ID (CONTINENT 타입) 또는 길드 ID (GUILD 타입) |

#### chat_messages

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `room_id` | `BIGINT` | FK | |
| `sender_id` | `BIGINT` | FK → users.id | |
| `content` | `TEXT` | NOT NULL | |
| `sent_at` | `TIMESTAMPTZ` | NOT NULL | |

#### interest_groups

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `continent_id` | `BIGINT` | FK | 관심 대륙 |

---

### 🔔 Notification Domain

#### notification_logs

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `type` | `VARCHAR(20)` | NOT NULL | OUTBID / AUCTION_START / RESULT / INCOME |
| `message` | `TEXT` | NOT NULL | |
| `is_read` | `BOOLEAN` | DEFAULT false | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |

---

### 🏗️ Building Domain

#### building_types

| column | 자료형 | 설명 |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `name` | `VARCHAR(30)` | CASTLE / STORAGE / WORKSHOP / BARRACKS / WALL / TOWER / FARMLAND / RESIDENCE |
| `width` | `INTEGER` | config 대응 |
| `height` | `INTEGER` | config 대응 |
| `max_hp` | `INTEGER` | |
| `base_cost_gp` | `INTEGER` | |
| `zone_restriction` | `INTEGER` NULL | 양수: 해당 Zone 전용 (1 = Zone1 전용 — CASTLE). 음수: \|값\| 이상 Zone만 허용 (-2 = Zone2/3 전용 — FARMLAND) |
| `defense_power` | `INTEGER` NULL | NULL 허용. 방어 건물(WALL, TOWER)만 값 보유. 전투 계산 시 DEF에 합산 |
| `food_production_rate` | `INTEGER` NULL | NULL 허용. FARMLAND만 값 보유. 시간당 식량 생산량 (level 배율 곱함) |
| `unit_capacity_per_level` | `INTEGER` NULL | NULL 허용. RESIDENCE만 값 보유. 레벨당 유닛 슬롯 추가 수 |
| `gp_production_rate` | `INTEGER` NULL | NULL 허용. WORKSHOP만 값 보유. 시간당 GP 생산량 (level 배율 곱함) |

#### building_instances

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `territory_id` | `BIGINT` | FK, NULL 허용 | NULL이면 섬 건물 |
| `island_id` | `BIGINT` | FK, NULL 허용 | NULL이면 영토 건물 |
| `building_type_id` | `BIGINT` | FK | |
| `pos_x` | `INTEGER` | NOT NULL | |
| `pos_y` | `INTEGER` | NOT NULL | |
| `hp` | `INTEGER` | NOT NULL | |
| `level` | `INTEGER` | NOT NULL, DEFAULT 1 | |
| `zone` | `INTEGER` | NOT NULL | 1/2/3 |
| `is_destroyed` | `BOOLEAN` | DEFAULT false | |
| `owner_id` | `BIGINT` | FK, NULL 허용 | 보관함 상태(territory/island 모두 NULL)일 때 소유자 |
| `stored_gp` | `INTEGER` | NOT NULL, DEFAULT 0 | 성·저장소가 사용. 그 위치 적립 GP (저장소는 약탈 대상) |
| `stored_food` | `INTEGER` | NOT NULL, DEFAULT 0 | 성·저장소가 사용. 그 위치 적립 식량 (약탈·이전 불가) |
| `workshop_debuff_until` | `TIMESTAMPTZ` | NULL 허용 | WORKSHOP 파괴 후 생산 중단 종료 시각 |
| `build_complete_at` | `TIMESTAMPTZ` | NULL 허용 | 건설/업그레이드 완료 예정 시각(NULL=완성) |
| `upgrade_to_level` | `INTEGER` | NULL 허용 | 업그레이드 대기 시 도달 레벨 |

#### global_vaults

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `user_id` | `BIGINT` | PK, FK | |
| `stored_gp` | `INTEGER` | NOT NULL, DEFAULT 0 | 위치와 무관하게 이동·보관되는 GP |
| `capacity` | `INTEGER` | NOT NULL, DEFAULT 10000 | 고정 용량(업그레이드 미구현) |
| `last_transfer_at` | `TIMESTAMPTZ` | NULL | 쿨다운 계산용 |

---

### 🏝️ Island Domain

#### island_grades

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `name` | `VARCHAR(5)` | NOT NULL, UNIQUE | D / B / S |
| `grid_size` | `INTEGER` | NOT NULL | 10 / 15 / 20 |
| `zone1_radius` | `INTEGER` | NOT NULL | Zone1 Chebyshev 반경 |
| `zone2_radius` | `INTEGER` | NOT NULL | Zone2 Chebyshev 반경 |
| `castle_level_required` | `INTEGER` | NOT NULL | 해당 등급 도달에 필요한 성 레벨 (1/2/3) |

#### home_islands

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK, UNIQUE | 1유저 1섬 |
| `level` | `INTEGER` | NOT NULL, DEFAULT 1 | |
| `island_grade_id` | `BIGINT` | FK → island_grades.id | |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | |
| `last_harvest_at` | `TIMESTAMPTZ` | NULL 허용 | 마지막 GP 수확 시각 |

---

### ⚔️ Military Domain

#### unit_types

| column | 자료형 | 설명 |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `name` | `VARCHAR(30)` | INFANTRY / ARCHER / KNIGHT |
| `attack_power` | `INTEGER` | |
| `defense_power` | `INTEGER` | |
| `cost_gp` | `INTEGER` | |
| `food_cost` | `INTEGER` | 유닛 생산 1회 소모 식량 (시간당 소모 아님) |
| `level` | `INTEGER` | 필요 병영 레벨 |

#### unit_instances

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | FK | |
| `unit_type_id` | `BIGINT` | FK | |
| `quantity` | `INTEGER` | NOT NULL | |
| `level` | `INTEGER` | NOT NULL, DEFAULT 1 | 유닛 레벨. 스택 식별자에 포함 (유저×유닛종류×레벨×귀속위치×배치) |
| `home_territory_id` | `BIGINT` | FK, NULL 허용 | 귀속 위치가 영토일 때 (섬과 배타) |
| `home_island_id` | `BIGINT` | FK, NULL 허용 | 귀속 위치가 섬일 때 (영토와 배타) |
| `deployed_territory_id` | `BIGINT` | FK, NULL | 방어 배치된 영토. NULL이면 대기 중 |
| `move_complete_at` | `TIMESTAMPTZ` | NULL 허용 | 위치 간 이동 중이면 도착 예정 시각(도착 전까지 방어·배치·재이동 불가) |

> 유닛은 위치(영토/섬)에 귀속되며, `home_*`(귀속) · `deployed_territory_id`(배치) 두 축으로 관리된다. 위치 간 이동은 `move_complete_at` 도래 시 도착 처리되며 출발지 저장소에서 `UNIT_MOVE_COST_GP` 차감.

#### attack_tokens

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `user_id` | `BIGINT` | PK FK | |
| `normal_count` | `INTEGER` | NOT NULL, DEFAULT 0 | 일반 공격권 |
| `precision_count` | `INTEGER` | NOT NULL, DEFAULT 0 | 정밀 공격권 |

#### siege_events

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `attacker_id` | `BIGINT` | FK | |
| `defender_id` | `BIGINT` | FK | |
| `target_territory_id` | `BIGINT` | FK | |
| `target_building_id` | `BIGINT` | FK, NULL 허용 | Zone 공략형 공성에서는 NULL |
| `attack_zone` | `INTEGER` | NOT NULL | 1/2/3 |
| `status` | `VARCHAR(10)` | NOT NULL | PENDING / RESOLVED |
| `siege_start_at` | `TIMESTAMPTZ` | NOT NULL | |
| `resolve_at` | `TIMESTAMPTZ` | NOT NULL | |

#### siege_results

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `siege_id` | `BIGINT` | FK, UNIQUE | |
| `is_attacker_win` | `BOOLEAN` | NOT NULL | |
| `attacker_units_lost` | `INTEGER` | | |
| `defender_units_lost` | `INTEGER` | | |
| `looted_gp` | `INTEGER` | DEFAULT 0 | 약탈량 |
| `result_type` | `VARCHAR(15)` | | LOOT / DEBUFF / AUCTION |

#### siege_forces

공성 선언 시 투입을 확정한 병력 스냅샷. 선언 시점의 유닛을 잠그고, 정산 후 생존분만 복귀한다.

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `siege_id` | `BIGINT` | NOT NULL, FK → siege_events.id | |
| `unit_type_id` | `BIGINT` | NOT NULL, FK | |
| `quantity` | `INTEGER` | NOT NULL | 투입 수량 |
| `level` | `INTEGER` | NOT NULL, DEFAULT 1 | 투입 유닛 레벨 (레벨별 스펙으로 전력 계산) |

#### siege_structures

공성 선언 시 함께 건설하는 공성 건물. 해당 공성에만 유효하며 정산 후 삭제된다.

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `siege_id` | `BIGINT` | NOT NULL, FK → siege_events.id | |
| `type` | `VARCHAR(10)` | NOT NULL | STAGING(주둔지) / TOWER(공성탑) / SUPPLY(보급소) |
| `coord_x` | `INTEGER` | NOT NULL | 대상 영토 그리드 좌표 |
| `coord_y` | `INTEGER` | NOT NULL | |

> 규칙: STAGING 1개 필수, 나머지는 STAGING 기준 체비셰프 거리 1 인접, 좌표 중복 불가, 총 8개 이하. 투입 병력은 STAGING 수용량(레벨당 10)을 넘을 수 없다. 건설비는 금고(GlobalVault) GP에서 차감.

#### unit_type_level_specs

유닛 레벨별 스펙. 관리자 페이지에서 편집한다.

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `unit_type_id` | `BIGINT` | NOT NULL, FK | UNIQUE(`unit_type_id`, `level`) |
| `level` | `INTEGER` | NOT NULL | 도달 레벨 (2 이상) |
| `attack_power` | `INTEGER` | NOT NULL | |
| `defense_power` | `INTEGER` | NOT NULL | |
| `train_cost_food` | `INTEGER` | NOT NULL | 해당 레벨 생산 시 식량 소모 |
| `required_barracks_level` | `INTEGER` | NOT NULL | 생산에 필요한 병영 레벨 |

#### unit_research

계정 단위 유닛 연구 진행 상태. 연구소(RESEARCH_LAB) 건물이 있어야 진행 가능.

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `user_id` | `BIGINT` | NOT NULL, FK | UNIQUE(`user_id`, `unit_type_id`) |
| `unit_type_id` | `BIGINT` | NOT NULL, FK | |
| `researched_level` | `INTEGER` | NOT NULL, DEFAULT 1 | 현재 해금된 최고 레벨 |
| `pending_level` | `INTEGER` | NULL 허용 | 연구 진행 중인 목표 레벨 |
| `research_complete_at` | `TIMESTAMPTZ` | NULL 허용 | 완료 예정 시각. 도래 시 조회 시점에 지연 반영 |

> 비용 `2000 × 목표레벨` GP(금고 차감), 소요 `30분 × 목표레벨`, 필요 연구소 레벨 = `목표레벨 − 1`. 생산 시 `researched_level` 이하의 레벨만 선택 가능.

---

### 🛡️ Admin Domain

#### admin_audit_logs

관리자의 모든 쓰기 작업 이력. 설계: [admin-dashboard](./admin-dashboard.md).

| column | 자료형 | 조건 | 설명 |
|---|---|---|---|
| `id` | `BIGSERIAL` | PK | |
| `admin_user_id` | `BIGINT` | NOT NULL, FK → users.id | 작업 관리자 |
| `action` | `VARCHAR(50)` | NOT NULL | USER_SUSPEND / WALLET_ADJUST / AUCTION_FORCE_END / SEASON_CREATE / TERRITORY_GRADE_CHANGE 등 |
| `target_type` | `VARCHAR(20)` | NOT NULL | USER / AUCTION / TERRITORY / SEASON / ITEM |
| `target_id` | `BIGINT` | | 대상 엔티티 ID |
| `detail_json` | `TEXT` | | 변경 전/후 값 및 사유(JSON) |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT now() | |

> **경매 비활성화 플래그**: F-17.7 채택 시 `territories`에 `auction_enabled BOOLEAN NOT NULL DEFAULT true` 추가 예정 ([OQ-6](./admin-dashboard.md#10-미결-사항-open-questions)).

---

## Redis 구조

> **⚙️ MSA**: redis는 **공유 인스턴스**(서비스 간 이벤트 버스 `auction.*`/`territory.auction-ready` + 캐시). `auction:lock:{auctionId}`(입찰 분산락)은 이제 **auction-service**가 Redisson으로 잡는다. `ranking:...:auction_spend`는 auction-service의 `auction.settled`를 모놀리식 랭킹 브리지가 받아 갱신한다.

| Key | 타입 | TTL | 역할 |
|---|---|---|---|
| `session:jwt_refresh:{user_id}` | String | 14일 | JWT Refresh Token |
| `adjacent_bonus:{territory_id}:{user_id}` | Integer | 60초 | 인접 영토 점유 수 캐시 |
| `ranking:season:{seasonId}:territory_hold` | Sorted Set | 시즌 종료까지 | 시즌 영토 등급 보유 랭킹 (score = 가중 보유 시간) |
| `ranking:season:{seasonId}:auction_spend` | Sorted Set | 시즌 종료까지 | 시즌 경매 AP 소비 랭킹 (score = 누적 AP) |
| `auction:bid:{auctionId}` | Hash | 경매 end_at까지 | 입찰 실시간 캐시 |
| `auction:lock:{auctionId}` | String | 500ms | 동시 입찰 분산 락 |
| `invincible:{territoryId}` | String | 무적권 지속 시간 | 공격 선언 전 무적 상태 확인 |
| `global_vault:total` | String (Integer) | 영구 | INCR 원자적 GP 누적 |
| `user:item:{userId}` | Hash | 10분 | 아이템 보유 현황 캐시 |
| `season_pass:{userId}` | Hash | 30분 | 시즌 패스 상태 캐시 |
| `notification:unread:{userId}` | String (Integer) | 영구 | 미읽음 알림 카운터 |
| `siege:active:{siegeId}` | Hash | resolveAt까지 | 공성전 진행 상태 |
| `land_tax:expected:{userId}` | Hash | 자정까지 | 토지세 예상액 캐시 |
| `color:change:{territoryId}:{userId}` | Integer | occupiedUntil까지 | 색상 변경 횟수 카운터 |
