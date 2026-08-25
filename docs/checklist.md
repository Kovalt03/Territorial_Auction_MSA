# 구현 체크리스트

> 마지막 갱신: 2026-08-25 (개인섬 연구현황·확장 탭 완료, 생산 랭킹 범위 제외 — 모놀리식 선택 항목 정리 마무리)
> 기준 브랜치: `dev`

범례: ✅ 완료 · 🔄 일부 완료 · ⬜ 미구현

> **2026-06-24 이후 대규모 변경(반영됨):**
> - **자원 스코프 개편**: GP·식량이 **위치(영토/섬) 저장소**로 이동. `wallets.available_gp/food` DROP, 계정 단위는 **금고(GlobalVault)** 만. GP 보유 상한 = 저장소 용량(성·저장소 레벨당 5,000).
> - **공성전 시스템 전면 구현**: 보호/점유 분리, Zone 외곽→중심, 공격 병력 커밋(`SiegeForce`), 유닛 건물 주둔, 성 HP 누적·다회 공성, 정찰(SCOUT)·정보 비대칭, **공성 건물(`SiegeStructure`: 주둔지·타워·보급소)**, 관리자 밸런스(`BalanceConfig`), 판정 스케줄러까지 **선언→판정→약탈 실행 검증 완료**.
> - **관리자 페이지·랭킹**: 실제 구현됨(아래 섹션 갱신).
> - **성능·운영 기준**: 50 VU 1시간 우선순위 혼합 Soak에서 71,665 요청·실패 0건을 확인했다. production profile은 Flyway 스키마 마이그레이션과 Hibernate 검증을 사용하며, 로컬 Docker 운영 절차는 [운영 가이드](./operations/local-production.md)에 정리했다.

---

## REST API

### Auth
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 회원가입 | `POST /api/v1/auth/signup` | |
| ✅ | 로그인 | `POST /api/v1/auth/login` | |
| ✅ | 로그아웃 | `POST /api/v1/auth/logout` | |
| ✅ | AccessToken 갱신 | `POST /api/v1/auth/refresh` | |
| ✅ | 사용자명 중복 확인 | `GET /api/v1/auth/check/username` | |
| ✅ | 이메일 중복 확인 | `GET /api/v1/auth/check/email` | |
| ✅ | 닉네임 중복 확인 | `GET /api/v1/auth/check/nickname` | |
| ✅ | 탈퇴 시 JWT 무효화 | — | Redis 블랙리스트 등록 (be-25) |

---

### User
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 유저 프로필 조회 | `GET /api/v1/users/{userId}` | |
| ✅ | 내 프로필 조회 | `GET /api/v1/users/me` | |
| ✅ | 회원 탈퇴 | `DELETE /api/v1/users/me` | JWT 블랙리스트 무효화 완료 (be-25) |
| ✅ | 알림 설정 조회 | `GET /api/v1/users/me/settings` | |
| ✅ | 알림 수신 설정 변경 | `PATCH /api/v1/users/me/settings` | |
| ✅ | GP/AP 잔액 조회 | `GET /api/v1/users/me/wallet` | GP=금고 잔액, AP=지갑. 식량은 위치 저장소로 분리 |
| ✅ | 나의 영토 목록 조회 | `GET /api/v1/users/me/territories` | occupiedAt·militaryCount·isInvincible 완료 (be-25) |
| ✅ | 닉네임 변경 | `PATCH /api/v1/users/me/nickname` | |
| ✅ | 비밀번호 변경 | `PATCH /api/v1/users/me/password` | |
| 🔄 | AP 충전 | `POST /api/v1/users/me/ap/charge` | PG 연동 미구현 (더미 처리 중) |

---

### Map
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 그리드 맵 조회 | `GET /api/v1/map` | |
| ✅ | 영토 상세 조회 | `GET /api/v1/map/territories/{territoryId}` | |
| ✅ | 영토 색상 변경 | `PATCH /api/v1/map/territories/{territoryId}/color` | |
| ✅ | 대륙 목록 조회 | `GET /api/v1/map/continents` | |
| ✅ | 대륙 상세 조회 | `GET /api/v1/map/continents/{continentId}` | |

---

### Auction
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 경매 목록 조회 | `GET /api/v1/auctions` | |
| ✅ | 경매 상세 조회 | `GET /api/v1/auctions/{auctionId}` | ⬜ Redis 캐시 미구현 |
| ✅ | 입찰하기 | `POST /api/v1/auctions/{auctionId}/bids` | Redis 분산락 완료 (be-18) |
| ✅ | 가격 변동 그래프 데이터 | `GET /api/v1/auctions/{auctionId}/bids` | |
| ✅ | 내 입찰 내역 조회 | `GET /api/v1/auctions/my-bids` | |
| ✅ | 영토 경매 이력 조회 | `GET /api/v1/auctions/territories/{territoryId}` | |
| ✅ | 입찰 시 WebSocket 브로드캐스트 | — | `/sub/auction/{auctionId}` (be-23) |

---

### Building
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 영토 건물 목록 조회 | `GET /api/v1/map/territories/{territoryId}/buildings` | |
| ✅ | 영토 건물 배치 | `POST /api/v1/map/territories/{territoryId}/buildings` | |
| ✅ | 건물 업그레이드 | `POST /api/v1/buildings/{buildingId}/upgrade` | |
| ✅ | 건물 수리 | `POST /api/v1/buildings/{buildingId}/repair` | |
| ✅ | 건물 이동 | `PATCH /api/v1/buildings/{buildingId}/move` | |
| ✅ | 건물 보관 | `POST /api/v1/buildings/{buildingId}/store` | |
| ✅ | 섬 정보 조회 | `GET /api/v1/island` | |
| ✅ | 섬 건물 목록 조회 | `GET /api/v1/island/buildings` | |
| ✅ | 섬 건물 배치 | `POST /api/v1/island/buildings` | 일꾼 슬롯 검증 완료 (기본 1, 시즌 패스 +extraBuilders) |
| ✅ | 보관함 목록 조회 | `GET /api/v1/inventory` | |
| ✅ | 보관함 건물 배치 | `POST /api/v1/inventory/{inventoryId}/place` | |

---

### Global Vault
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 글로벌 금고 조회 | `GET /api/v1/global-vault` | |
| ✅ | 자원 이전 | `POST /api/v1/global-vault/transfer` | |

---

### Item / Payment
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 아이템 목록 조회 | `GET /api/v1/items` | |
| ✅ | 아이템 구매 | `POST /api/v1/items/purchase` | |
| ✅ | 아이템 사용 | `POST /api/v1/items/use` | 무적 방어막·공격권(일반/정밀) 연동 완료 |
| ✅ | 보유 아이템 목록 조회 | `GET /api/v1/items/inventory` | |

---

### Land Tax
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 토지세 현황 조회 | `GET /api/v1/land-tax/status` | |
| ✅ | 납세 내역 조회 | `GET /api/v1/land-tax/logs` | |
| ✅ | 세금 배치 스케줄러 | — | 유예기간(24h) + D→S 순차 강제 경매 전환 구현 (be-27) |
| ✅ | Redis 캐시 연동 | — | `land_tax:expected:{userId}` TTL 자정까지 (be-26) |

---

### Season Pass
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 내 시즌 패스 상태 조회 | `GET /api/v1/season-pass/me` | Redis 캐시 완료 |
| ✅ | 시즌 패스 현황 조회 | `GET /api/v1/season-pass` | XP 연동 + 시드 데이터 완료 (be-32) |
| ✅ | 시즌 패스 구매 (프리미엄) | `POST /api/v1/season-pass/purchase` | Redis 캐시 완료 |
| ✅ | 레벨 즉시 구매 | `POST /api/v1/season-pass/level-up` | AP 차감 후 레벨 +1, 최고 레벨 시 409 (all-78). 가격은 `GET /season-pass`의 `levelUpCostAp`로 노출 |
| ✅ | 만료 알림 스케줄러 | — | 만료 3일 전·당일 알림 발송 (SeasonPassScheduler, be-25) |
| ✅ | XP 적립 — 경매 낙찰 | — | `AuctionSettledEvent` 구독, +100 XP (be-32) |
| ✅ | XP 적립 — 공성전 승리 | — | `SiegeVictoryEvent` 신규 이벤트, +50 XP (be-32) |
| ✅ | 미션 목록 조회 | `GET /api/v1/season-pass/missions` | 진행도 포함 (`MissionService`, all-78) |
| ✅ | 미션 보상 수령 | `POST /api/v1/season-pass/missions/{missionId}/claim` | 완료 미션만 수령, 중복 수령 409 (all-78) |
| ✅ | 레벨 보상 수령 | `POST /api/v1/season-pass/rewards/{rewardId}/claim` | 무료/프리미엄 2트랙, 미보유 프리미엄 차단. **수령 시 실제 지급** — ITEM(인벤토리 적립)·GP(지갑 적립) (all-78) |
| ✅ | 미션 진행 이벤트 구독 | — | `MissionEventListener`가 도메인 이벤트로 진행도 갱신 (all-78) |
| ✅ | DB 시드 데이터 | — | `season_pass_level_rewards` 12개(레벨×2트랙, rewardKind/itemType/quantity 구조) + `season_missions` 시드 (be-32, all-78) |

---

### Guild
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 길드 생성 | `POST /api/v1/guilds` | |
| ✅ | 길드 목록 조회 | `GET /api/v1/guilds` | |
| ✅ | 길드 정보 조회 | `GET /api/v1/guilds/{guildId}` | |
| ✅ | 나의 길드 정보 조회 | `GET /api/v1/guilds/me` | |
| ✅ | 길드 정보 수정 | `PATCH /api/v1/guilds/{guildId}` | |
| ✅ | 길드 가입 신청 | `POST /api/v1/guilds/{guildId}/join` | |
| ✅ | 가입 신청 취소 | `DELETE /api/v1/guilds/{guildId}/join` | |
| ✅ | 길드장 이전 | `PATCH /api/v1/guilds/{guildId}/master` | |
| ✅ | 가입 승인 | `PATCH /api/v1/guilds/{guildId}/members/{userId}/approve` | |
| ✅ | 가입 신청 거절 | `PATCH /api/v1/guilds/{guildId}/members/{userId}/reject` | |
| ✅ | 길드 탈퇴 | `DELETE /api/v1/guilds/{guildId}/members/me` | |
| ✅ | 멤버 강제 추방 | `DELETE /api/v1/guilds/{guildId}/members/{userId}` | |
| ✅ | 가입 신청 목록 조회 | `GET /api/v1/guilds/{guildId}/applications` | |

---

### Notification
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 알림 목록 조회 | `GET /api/v1/notifications` | Redis unread 카운터 연동 |
| ✅ | 알림 읽음 처리 | `PATCH /api/v1/notifications/{id}/read` | Redis DECR |
| ✅ | 전체 읽음 처리 | `PATCH /api/v1/notifications/read-all` | Redis SET 0 |

---

### Military (공성전) — 전면 구현·실행 검증 완료 (Stage 1~9 + UI)
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 공성전 선언 | `POST /api/v1/military/siege` | **혼합 병력 `forces` + 공성 건물 `structures`(주둔지 필수)**. 보호/점유·Zone 클리어·쿨다운·공격권 검증 |
| ✅ | 공성 결과 조회 | `GET /api/v1/military/siege/{siegeId}/result` | |
| ✅ | 공성 이벤트 목록·내 이력 | `GET /api/v1/siege/events`, `/siege/my-history` | 상태 필터·페이지네이션 |
| ✅ | 유닛 생산 | `POST /api/v1/military/units` | **위치(`locationId`/`locationType`) 지정** — 병영 레벨·수용 상한·위치 저장소 GP/식량 차감 |
| ✅ | 유닛 목록 조회 | `GET /api/v1/military/units` | **위치별 그룹(`locations[].units/storedFood`)** — 자원 스코프 개편 반영 |
| ✅ | 유닛 주둔/회수 | `POST /military/units/deploy`·`/recall` | 건물 대상 주둔(`buildingId`)·건물별 수용량, Zone별 방어 집계 |
| ✅ | 영토 주둔 유닛 조회 | `GET /military/territory/{id}/garrison` | 회수 목록용(호출자 소유분만→정보 비대칭 유지) |
| ✅ | 정찰 | `POST /military/scout/{territoryId}` | SCOUT 1기 소모, 방어 총 병력 수만 공개 |
| ✅ | 유닛 이동 | `POST /military/units/move` | 위치 간 이동(비용·시간) |
| ✅ | 공격권 조회 | `GET /military/attack-tokens` | |
| ✅ | 공성 건물(`SiegeStructure`) | — | 주둔지(공격 병력 상한)·공성타워(공격력 버프)·보급소(쿨다운 완화), 인접 타일·금고 결제·판정 후 삭제 |
| ✅ | 성 HP 누적·다회 공성 / 성벽 돌파(buildingDamage) | — | 교전(ATK/DEF)과 건물 피해 분리, 성 함락 시 영토 인계 |
| ✅ | 건물 GP 즉시 수리 | `PATCH .../repair` | HP 기반, 위치 저장소 GP |
| ✅ | 판정 스케줄러 | — | 1분 주기 `SiegeScheduler` → `resolveOneSiege`(실행 검증 완료) |
| ✅ | 공성 알림 WebSocket | — | `/sub/user/{userId}/siege-alert` (선언·결과 양측) |
| ✅ | 공성 선언 UI(프론트) | `SiegePage` | `forces`+`structures` 계약 정합, 보유 대기 유닛 선택·주둔지 배치 |
| ✅ | 정밀 공격(건물 지정) UI | `SiegePage` | 일반/정밀 모드 전환, 정찰 건물 목록·그리드 클릭으로 `targetBuildingId` 지정, 공격권 검증까지 연동 |

---

### Building — 식량·유닛 수용 (be-29)
| 상태 | 기능 | 비고 |
|---|---|---|
| ✅ | 식량 저장을 위치 저장소로 | 자원 스코프 개편: `wallets.available_food` DROP → `building_instances.stored_food`(성·저장소) |
| ✅ | UnitType.foodCost (1회 소모) | 기존 foodCostPerHour 대체 |
| ✅ | UnitType.level (병영 레벨 요구치) | DEFAULT 1 |
| ✅ | BuildingType.foodProductionRate | FARMLAND 전용 |
| ✅ | BuildingType.unitCapacityPerLevel | RESIDENCE 전용 |
| ✅ | FarmlandScheduler | 1시간 주기 식량 생산 적립 |
| ✅ | CASTLE 레벨별 기본 유닛 슬롯 | MilitaryPolicy (1→5, 2→10, 3→15) |
| ✅ | FARMLAND·RESIDENCE·WORKSHOP 시드 데이터 | building_types 8종 전체 seed.sql 삽입 완료 (be-33) |

### Building — WORKSHOP GP 생산 (be-30)
| 상태 | 기능 | 비고 |
|---|---|---|
| ✅ | BuildingType.gpProductionRate | WORKSHOP 전용 |
| ✅ | BuildingInstance.workshopDebuffUntil | WORKSHOP 파괴 디버프 타임스탬프 |
| ✅ | WorkshopScheduler | 1시간 주기 GP 생산 적립 (디버프 중 제외) |
| ✅ | MilitaryPolicy.WORKSHOP_DEBUFF_HOURS | 기본 12시간 |
| ✅ | SiegeService — WORKSHOP 파괴 시 디버프 적용 | Zone 2 클리어, HP 0 → workshopDebuffUntil 설정 |

### Building — 섬 등급 시스템 (all-20)
| 상태 | 기능 | 비고 |
|---|---|---|
| ✅ | 섬 성 자동 배치 (신규 유저 + 기존 유저 마이그레이션) | 회원가입 시 HomeIsland·CASTLE 동시 생성 |
| ✅ | 성 레벨별 섬 등급 시스템 (D→10×10, B→15×15, S→20×20) | IslandGrade 엔티티, castle 레벨 업그레이드 시 등급 갱신 |
| ✅ | 섬 GP 수확 엔드포인트 | `POST /api/v1/island/harvest` |
| ✅ | 보관함 → 섬 배치 | `POST /api/v1/inventory/{inventoryId}/place-on-island` |
| ✅ | island_grades DB 테이블 (zone1Radius, zone2Radius) | `IslandGrade` 엔티티 + `IslandGradeSeeder` |
| 🔄 | IslandGrade FK로 HomeIsland 리팩터링 | `island_grade_id` FK 적용됨. `grid_size` 컬럼 제거는 미완(현재 병존) |
| ✅ | 계정 단위 유닛 연구 API | `GET/POST /api/v1/military/research` | 연구소 레벨 검증·금고 GP 차감·완료 시점 반영 구현 |

---

### Ranking
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 시즌 영토 등급 보유 랭킹 | `GET /api/v1/rankings/territory-hold` | Redis Sorted Set, 주기적 배치 집계 |
| ✅ | 시즌 경매 AP 소비 랭킹 | `GET /api/v1/rankings/auction-spend` | Redis Sorted Set, 낙찰마다 즉시 갱신 |
| ✅ | 트로피 랭킹 | `GET /api/v1/rankings/trophy` | user_trophies 점수 내림차순 DB 조회, 내 순위 포함 |
| ✅ | 내 랭킹 조회 | `GET /api/v1/rankings/me` | 두 카테고리 모두 포함 |

---

### Territory Income
| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 영토 수령 | `POST /api/v1/territories/{id}/collect` | Lazy Evaluation 방식, settle() 로직 포함 (be-28) |

---

### Admin (관리자 페이지) — ✅ 구현됨 (컨트롤러 16종)

> 설계: [admin-dashboard](./design/admin-dashboard.md) · API: [admin.md](./api/admin.md)

| 상태 | 기능 | 엔드포인트 | 비고 |
|---|---|---|---|
| ✅ | 인프라: role·JWT·SecurityConfig·AdminAuditLogger·TOTP | — | AdminAuthService |
| ✅ | 대륙 영토 구성·등급 분포 일괄 조정 | `/admin/continents` | AdminContinentController |
| ✅ | 영토 개별 등급·경매 활성/비활성 | `/admin/territories/...` | AdminTerritoryController |
| ✅ | 유저 목록·상세·정지/해제 | `/admin/users/...` | AdminUserController |
| ✅ | 재화·트로피 조정 | `/admin/users/.../wallet` 등 | AdminUserController |
| ✅ | 아이템 지급·가격/한도 | `/admin/items/...` | AdminItemController |
| ✅ | 경매 목록·강제 종료/시작 | `/admin/auctions/...` | AdminTerritoryController |
| ✅ | 시즌·시즌패스 관리 | `/admin/seasons`, `/admin/season-passes` | AdminSeasonController / AdminSeasonPassController |
| ✅ | 지표 대시보드 | `/admin/dashboard/...` | AdminDashboardController |
| ✅ | 감사 로그 열람 | `/admin/audit-logs` | AdminAuditLogController |
| ✅ | 채팅 로그 열람/삭제 | `/admin/chat/...` | AdminChatController |
| ✅ | 유닛·건물 스탯 편집 | `/admin/units`, `/admin/buildings` | AdminUnitController / AdminBuildingController |
| ✅ | 밸런스 설정(공성 등) | `GET/PATCH /admin/settings/balance` | AdminSettingController + BalanceConfig |
| ✅ | 공지·설정 | `/admin/announcements`, `/admin/settings` | AdminAnnouncementController / AdminSettingController |
| ✅ | 관리자 공개 밸런스 값 배선 | — | 수리 GP와 성·숙소·타워·방벽 주둔 수용량을 `BalanceConfig`로 연동. 그 외 Policy 상수는 관리자 조정 범위에서 제외 |

---

## WebSocket / STOMP (실시간)

### 기반 설정
| 상태 | 항목 | 비고 |
|---|---|---|
| ✅ | `WebSocketConfig` | `/pub`·`/sub` prefix, SockJS, StompChannelInterceptor 등록 |
| ✅ | `StompChannelInterceptor` | CONNECT 단계 JWT 검증, 미인증 연결 허용 (공개 채널용) |

### 채팅
| 상태 | 채널 | 설명 |
|---|---|---|
| ✅ | `/pub/chat/{roomId}` | 클라이언트 메시지 발행 (미인증 시 CHAT_ACCESS_DENIED) |
| ✅ | `/sub/chat/{roomId}` | 채팅 메시지 수신 |
| ✅ | `GET /api/v1/chat/rooms/{roomId}/messages` | 히스토리 조회 (커서 페이징, 길드 접근 검증 포함) |
| ✅ | `ChatRoom` 타입 | `WORLD` / `CONTINENT` / `GUILD`. Enum 값 및 관련 로직 일괄 수정 완료 |
| ✅ | WebSocket 에러 응답 | `CustomException` → `/user/queue/errors` 전송 |

### 경매 실시간
| 상태 | 채널 | 설명 |
|---|---|---|
| ✅ | `/sub/auction/{auctionId}` | 입찰 현황 실시간 수신 (be-23) |
| ✅ | `/sub/user/{userId}/auction-result` | 경매 낙찰(WIN)/패찰(LOSE) 개인 알림 |

### 맵 업데이트
| 상태 | 채널 | 설명 |
|---|---|---|
| ✅ | `/sub/map/update` | 영토 점유자 변경 브로드캐스트 (낙찰·점유 만료 시) |

### 알림
| 상태 | 채널 | 설명 |
|---|---|---|
| ✅ | `/sub/user/{userId}/notification` | 개인 알림 수신 (be-17) |
| ✅ | `/sub/user/{userId}/siege-alert` | 공성전 선언·결과 알림 |

---

## 스케줄러 / 배치

| 상태 | 항목 | 비고 |
|---|---|---|
| ✅ | 경매 생명주기 스케줄러 | `AuctionLifecycleService` |
| ✅ | 시즌 영토 등급 보유 집계 배치 | `season_territory_holds` → Redis Sorted Set 갱신 (1시간 주기) |
| ✅ | 토지세 배치 스케줄러 | 매일 자정 차감 + GP 부족 처리 (be-20) |
| ✅ | 전투 결과 처리 스케줄러 | 1분 주기, `SiegeScheduler` |
| ✅ | 시즌 패스 만료 알림 스케줄러 | 만료 3일 전·당일 (SeasonPassScheduler, be-25) |
| ⬜ | 일 정산 배치 (선택) | 미수령 생산량 settle + `territory_production_logs` 기록. 구현 여부 미확정 |
| ✅ | 시즌 종료 배치 | 리그별 보상 지급(GP·토큰) + 서브티어 단위 트로피 소프트 리셋. `SeasonEndBatchService` + `SeasonEndScheduler` (be-34) |

---

## Redis

| 상태 | 키 | 용도 |
|---|---|---|
| ✅ | `refresh_token:{userId}` | RefreshToken 저장 |
| ✅ | `season_pass:my:{userId}` | 시즌 패스 상태 캐시 (TTL 30분) |
| ✅ | `season_pass:progress:{userId}` | 시즌 패스 진행도 캐시 (TTL 30분) |
| ✅ | `ranking:season:{seasonId}:territory_hold` | 시즌 영토 등급 보유 Sorted Set |
| ✅ | `ranking:season:{seasonId}:auction_spend` | 시즌 경매 AP 소비 Sorted Set |
| ✅ | `auction:lock:{auctionId}` | 입찰 분산락 (Redisson, be-18) |
| ⬜ | `auction:bid:{auctionId}` | 경매 상세 캐시 |
| ✅ | `land_tax:expected:{userId}` | 예상 세금 캐시 (TTL: 자정까지) |
| ✅ | `land_tax:grace:{userId}` | 토지세 유예기간 키 (TTL: 24h, be-27) |
| ⬜ | `ws:chat:{roomId}` | 채팅 Pub-Sub 채널 (스케일아웃 시) |
| ⬜ | `ws:user:{userId}` | 개인 알림 Pub-Sub 채널 (스케일아웃 시) |

---

## TODO — 남은 미구현 (2026-07-24 기준, 코드 대조 확정)

> 다음 단계: **성능·부하 테스트 → 최적화 → MSA 분리** (가이드: `docs/design/performance-testing.md`)

핵심 게임 루프(경매·영토·건물·자원·**공성전**·**연구**·길드·랭킹·알림·관리자)는 구현 완료. 남은 것:

| 항목 | 상태 | 비고 |
|---|---|---|
| **AP 충전 실 결제(PG) 연동** | — 범위 제외 | **외부 결제대행사(토스·포트원 등) API·가맹점 계약·키·웹훅 필요**. 개인 프로젝트에서는 mock 결제를 유지 |
| 섬 확장 FE 탭 | ✅ 완료 | 확장 탭에서 현재 등급·그리드·성 레벨·Zone·건축 장인 + 등급 사다리(D~S, A·S 미출시)와 자동 승격 안내 표시 |
| 생산 랭킹 | — 범위 제외 | 모놀리식 릴리스에서 제외 (트로피·영토보유·경매지출·대륙·내 순위 랭킹은 구현 완료) |
| Redis 캐시·Pub-Sub (성능) | ⬜ 선택 | 단일 인스턴스라 현재 불필요, 스케일아웃 시 |
| 일 정산 배치 | ⬜ 선택 | Lazy 정산으로 대체 가능, 구현 여부 미확정 |

공성 후속 개선(선택): 공성 현황 패널 실데이터 · 저장소 꽉참 UI 경고.

---

---

## 프론트엔드 (FE)

### 공통 인프라
| 상태 | 항목 | 비고 |
|---|---|---|
| ✅ | API 클라이언트 (`client.ts`) | 401 → AccessToken 갱신 → 재시도, pending queue 구현 (fe-01) |
| ✅ | STOMP 싱글턴 훅 (`useStompClient`) | `useStompSubscribe` / `useStompPublish`, 에러 시 connectPromise 초기화 (fe-01) |
| ✅ | GNB WebSocket 알림 배지 | `/sub/user/{userId}/notification` 구독 → `incrementNotification` (fe-01) |
| ✅ | AppContext 알림 카운트 | `incrementNotification` / `decrementNotification` / `resetNotifications` (fe-01) |

### 길드
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 길드 목록 조회·검색·페이지네이션 | `GuildListPage` | (fe-01) |
| ✅ | 길드 생성 모달 | `GuildListPage` | 409 중복 에러 처리 포함 (fe-01) |
| ✅ | 가입 신청 / 신청 취소 | `GuildListPage` | (fe-01) |
| ✅ | 길드 상세 (멤버·신청·설정 탭) | `GuildDetailPage` | (fe-01) |
| ✅ | 가입 승인·거절, 멤버 추방, 길드장 이전 | `GuildDetailPage` | (fe-01) |
| ✅ | 길드 정보 수정 (소개글·모집 상태) | `GuildDetailPage` | (fe-01) |

### 알림
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 알림 목록 조회·무한스크롤 | `NotificationPage` | (fe-01) |
| ✅ | 단건 읽음 처리 | `NotificationPage` | `decrementNotification` 연동 (fe-01) |
| ✅ | 전체 읽음 처리 | `NotificationPage` | `resetNotifications` 연동 (fe-01) |

### 인증
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 로그인 | `LoginPage` | |
| ✅ | 회원가입 (중복 확인 포함) | `RegisterPage` | username·email·nickname 중복 검사 |

### 맵 / 영토 / 경매
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 월드맵 그리드 (대륙 진입) | `WorldMapPage` | `MapCanvas` 캔버스 렌더링 |
| ✅ | 대륙 상세 + 영토 선택 패널 | `ContinentPage` / `ContinentSelectedPanel` | 경매 입찰·거래 내역·가격 그래프·점유자 정보 (fe-75) |
| ✅ | 영토 상세 + 대륙 채팅 | `TerritoryDetailPage` / `TerritoryChat` | |
| ✅ | 경매 입찰 (실시간) | `BidPanel` / `BidConfirmModal` | `/sub/auction/{id}` 구독 |
| ✅ | 영토 경매 이력·가격 그래프 | `TerritoryHistoryPanel` | `useTerritoryAuctionHistory` (fe-75) |

### 영토 그리드 / 건물
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 영토 그리드 건물 배치·보관·액션 | `TerritoryGridPage` 외 3종 모달/패널 | 배치·이동·업그레이드·수리·보관 |

### 개인섬
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 개인섬 건물 배치·유닛 훈련·보관함 | `PersonalIslandPage` 외 모달 | GP 생산·식량·주둔 유닛 표시 |
| ✅ | 연구 현황 | `IslandResearchPanel` | 연구소 레벨·유닛별 현재/최대 레벨·진행 중 잔여시간·비용·게이팅 표시, 연구 시작 연동 |
| ✅ | 섬 확장 탭 | `PersonalIslandPage` | 현재 등급·그리드·성 레벨·Zone·건축 장인 + 등급 사다리(D~S, A·S 미출시)·성 레벨업 자동 승격 안내 |

### 공성전 / 아이템 / 시즌패스 / 금고
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 공성전 선언 | `SiegePage` | 백엔드 계약 정합(#132): 보유 대기 유닛에서 `forces` 선택 + 주둔지(공성 건물) 배치, 수용량·금고 비용 |
| ✅ | 유닛 주둔/회수 UI | `TerritoryDeployModal` | 건물 클릭 → 출발 위치·유닛 선택 주둔, 영토 배치 유닛 회수 |
| ✅ | 아이템샵 (구매·사용·보유) | `ItemShopPage` | |
| ✅ | 시즌패스 현황·구매 | `SeasonPassPage` | |
| ✅ | 글로벌 금고 자원 이전 | `VaultPage` | |

### 마이페이지 / 설정 / 충전
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 마이페이지 — 프로필·자산 도넛·바로가기 | `MyPage` / `MyPageQuickLinks` | 활동 탭은 영토 관리로 이전, 바로가기에 영토 관리 카드 추가 |
| ✅ | 설정 — 닉네임·비밀번호 변경, 알림 설정, 회원 탈퇴 | `SettingsPage` | |
| 🔄 | AP 충전 | `ChargePage` | mock 결제키 (`mock-*`) — PG 연동 BE 선행 필요 |

### 영토 관리 (Territory Management) — 신규 통합 페이지
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 단일 탭 페이지 (`?tab=` URL 보존) | `TerritoryManagementPage` | GNB "영토 관리" 클릭 시 경매 진행으로 이동 + hover 드롭다운에서 각 탭 deep-link |
| ✅ | 경매 진행 / 입찰 현황 탭 | `MyBidActivityList` | `useMyBids` — 시간/AP/상회입찰 정렬, 활성 입찰 WS 구독 |
| ✅ | 내 영토 탭 | `MyTerritoryList` | `useVault` 영토 목록 |
| ✅ | 거래 내역 탭 | `MyTradeHistoryList` | `my-bids`의 종료(IDLE) 경매 = 낙찰/패찰 결과. BE 추가 없이 파생 |
| ✅ | 토지세 탭 | `LandTaxView` | 단독 LandTaxPage 흡수, `/app/land-tax`는 리다이렉트 |

### 랭킹
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 영토 보유 / 경매 지출 / 트로피 랭킹 | `RankingPage` | territory·assets·trophy 연동 (`useRanking`). 동작 안 하던 기간 탭 제거, '자산가'→'경매 지출왕' 라벨 수정 |
| — | 대륙 / 생산 랭킹 | — | 탭 제거. 생산 랭킹은 모놀리식 릴리스 범위 제외 |

### 길드
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 길드 목록 조회·검색·페이지네이션 | `GuildListPage` | (fe-01) |
| ✅ | 길드 생성 모달 | `GuildListPage` | 409 중복 에러 처리 포함 (fe-01) |
| ✅ | 가입 신청 / 신청 취소 | `GuildListPage` | (fe-01) |
| ✅ | 길드 상세 (멤버·신청·설정 탭) | `GuildDetailPage` | (fe-01) |
| ✅ | 가입 승인·거절, 멤버 추방, 길드장 이전 | `GuildDetailPage` | (fe-01) |
| ✅ | 길드 정보 수정 (소개글·모집 상태) | `GuildDetailPage` | (fe-01) |

### 알림
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 알림 목록 조회·무한스크롤 | `NotificationPage` | (fe-01) |
| ✅ | 단건 읽음 처리 | `NotificationPage` | `decrementNotification` 연동 (fe-01) |
| ✅ | 전체 읽음 처리 | `NotificationPage` | `resetNotifications` 연동 (fe-01) |

### 섬 / 보관함 (all-20)
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 보관함 → 섬 배치 | 섬 관리 페이지 | API·타입·페이지 연동 완료 (all-20) |

### 토지세 (Land Tax) — 신규
| 상태 | 항목 | 페이지 | 비고 |
|---|---|---|---|
| ✅ | 토지세 현황 (요약 카드·누진세 구간·다음 납부 카운트다운) | `LandTaxStatusSection` | `/land-tax/status` 연동. 누진 하이라이트는 시즌패스 적용 후(실제 청구) 기준 |
| ✅ | 납세 내역 (상태 필터·번호 페이지네이션) | `LandTaxLogList` | `/land-tax/logs` 연동. 필터 5종(전체/납부/미납/면제/강제처분) |
| ✅ | 미납 유예·강제처분 경고 배너 | `LandTaxGraceBanner` | 최신 로그가 `FAILED`면 `chargedAt+24h` 카운트다운, `EVICTED`면 알림 안내 (BE 변경 없음) |
| ✅ | 진입/통합 | `LandTaxView` (영토 관리 토지세 탭) | 영토 관리 페이지 탭으로 통합. GNB 드롭다운 `토지세` → `?tab=tax` |
| — | 설계 보고서 | — | `report/design/2026-06-24-fe-land-tax.md` / API 문서 `docs/api/tax.md` 실제 구현 반영 갱신 |

---

## 다음 단계

모놀리식 기능 구현과 우선순위 성능 검증은 완료 기준에 도달했다. 외부 Render·Supabase·Upstash 호환성 스모크도 완료했으며, Render Free의 512MB 한도로 상시 운영은 지원하지 않는다. 실제 실행 기준은 로컬 Docker Compose다. 개인섬 연구 현황 UI·확장 탭은 구현 완료했다. 단일 인스턴스 이후의 Redis Pub/Sub·경매 상세 캐시는 스케일아웃(MSA) 단계로 이관한다. 생산 랭킹, 실제 PG 결제 연동, MSA 전환은 이 모놀리식 릴리스 범위에서 제외한다.
