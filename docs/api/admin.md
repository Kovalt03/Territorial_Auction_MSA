# Admin API (관리자 페이지)

> 구현 상태: ✅ 관리자 화면과 API는 구현되어 있다. 이 문서는 초기 설계 계약을 포함하므로, 세부 엔드포인트의 최종 사실은 Controller와 [체크리스트](../checklist.md)를 함께 확인한다.
> 설계 문서: [admin-dashboard](../design/admin-dashboard.md)

---

## 개요

운영자(`ROLE_ADMIN`)가 유저·경매·시즌·아이템을 조회·개입하기 위한 관리자 전용 API. 모든 엔드포인트는 `/api/v1/admin/**` 하위에 격리되며, 일반 유저 API와 재사용하지 않는다.

## 인증 / 권한

- 모든 엔드포인트 `ROLE_ADMIN` 필요. 미보유 시 `403 FORBIDDEN`.
- 인증 방식은 기존 JWT Bearer와 동일하되, 토큰 페이로드에 `role` 클레임이 포함된다.
- 모든 **쓰기 작업(POST/PATCH/DELETE)** 은 `admin_audit_logs`에 자동 기록된다.

## 공통 응답 형식

```json
{ "status": 200, "message": "OK", "data": { ... } }
```

## 공통 에러

| 코드 | 상황 |
|---|---|
| 401 | 토큰 없음/만료 |
| 403 | `ROLE_ADMIN` 아님 |
| 404 | 대상 리소스 없음 |
| 409 | 상태 충돌 (이미 종료된 경매 강제 종료 등) |

---

## 목차

| Method | Endpoint | 기능 | 감사로그 | 구현 |
|---|---|---|---|---|
| GET | `/api/v1/admin/continents` | [대륙 영토 구성 현황 ⭐](#대륙-영토-구성-현황) | — | ⬜ |
| GET | `/api/v1/admin/continents/{continentId}/territories` | [대륙 영토 목록](#대륙-영토-목록) | — | ⬜ |
| PATCH | `/api/v1/admin/continents/{continentId}/grade-distribution` | [등급 분포 일괄 조정 ⭐](#등급-분포-일괄-조정) | ✅ | ⬜ |
| GET | `/api/v1/admin/users` | [유저 목록·검색](#유저-목록검색) | — | ⬜ |
| GET | `/api/v1/admin/users/{userId}` | [유저 상세](#유저-상세) | — | ⬜ |
| PATCH | `/api/v1/admin/users/{userId}/status` | [계정 정지/해제](#계정-정지해제) | ✅ | ⬜ |
| POST | `/api/v1/admin/users/{userId}/wallet/adjust` | [재화 조정](#재화-조정) | ✅ | ⬜ |
| PATCH | `/api/v1/admin/users/{userId}/trophy` | [트로피 조정](#트로피-조정) | ✅ | ⬜ |
| POST | `/api/v1/admin/users/{userId}/items/grant` | [아이템 지급](#아이템-지급) | ✅ | ⬜ |
| GET | `/api/v1/admin/auctions` | [경매 목록](#경매-목록) | — | ⬜ |
| GET | `/api/v1/admin/auctions/{auctionId}` | [경매 상세](#경매-상세) | — | ⬜ |
| POST | `/api/v1/admin/auctions/{auctionId}/force-end` | [경매 강제 종료](#경매-강제-종료) | ✅ | ⬜ |
| POST | `/api/v1/admin/territories/{territoryId}/start-auction` | [경매 강제 시작](#경매-강제-시작) | ✅ | ⬜ |
| PATCH | `/api/v1/admin/territories/{territoryId}/auction-enabled` | [경매 활성/비활성](#경매-활성비활성) | ✅ | ⬜ |
| PATCH | `/api/v1/admin/territories/{territoryId}/grade` | [영토 등급 변경](#영토-등급-변경) | ✅ | ⬜ |
| GET | `/api/v1/admin/seasons` | [시즌 목록](#시즌-목록) | — | ⬜ |
| POST | `/api/v1/admin/seasons` | [시즌 생성](#시즌-생성) | ✅ | ⬜ |
| PATCH | `/api/v1/admin/seasons/{seasonId}` | [시즌 수정(시작/종료)](#시즌-수정시작종료) | ✅ | ⬜ |
| GET | `/api/v1/admin/items` | [아이템 목록](#아이템-목록) | — | ⬜ |
| PATCH | `/api/v1/admin/items/{itemId}` | [아이템 가격·한도 수정](#아이템-가격한도-수정) | ✅ | ⬜ |
| GET | `/api/v1/admin/item-purchases` | [아이템 구매 이력](#아이템-구매-이력) | — | ⬜ |
| GET | `/api/v1/admin/dashboard/summary` | [지표 요약](#지표-요약) | — | ⬜ |
| GET | `/api/v1/admin/audit-logs` | [감사 로그 조회](#감사-로그-조회) | — | ⬜ |
| GET | `/api/v1/admin/chat/rooms/{roomId}/messages` | [채팅 로그 조회](#채팅-로그-조회) | — | ⬜ |
| DELETE | `/api/v1/admin/chat/messages/{messageId}` | [채팅 메시지 삭제](#채팅-메시지-삭제) | ✅ | ⬜ |

---

## 대륙 영토 구성 관리 ⭐ (핵심)

각 대륙(행성)이 몇 개의 영토를 어느 등급 분포로 운영하는지 조회·조정. 경매 공급·밸런스의 핵심 레버이자 성능 실험의 조건 설정 도구. 설계: [admin-dashboard §2.2 / §5.4](../design/admin-dashboard.md).

### 대륙 영토 구성 현황
**GET** `/api/v1/admin/continents`

전체 대륙별 등급 분포·총 영토 수·경매 활성 수 집계.

Response 200:
```json
{
  "status": 200, "message": "OK",
  "data": {
    "continents": [
      {
        "continentId": 1, "name": "글리치", "minTrophyRequired": 0,
        "totalTerritories": 50,
        "gradeBreakdown": { "S": 4, "A": 10, "B": 20, "C": 16 },
        "auctionEnabledCount": 8,
        "biddingCount": 5, "occupiedCount": 30, "idleCount": 15
      }
    ]
  }
}
```

### 대륙 영토 목록
**GET** `/api/v1/admin/continents/{continentId}/territories?grade={}&status={}`

대륙 내 영토를 좌표·등급·상태·경매활성 여부로 반환(그리드 뷰용). `grade`/`status` 필터 선택.

### 등급 분포 일괄 조정
**PATCH** `/api/v1/admin/continents/{continentId}/grade-distribution`

대륙의 목표 등급 분포를 지정하면 영토 등급을 일괄 재배정한다.

Request:
```json
{
  "distribution": { "S": 4, "A": 10, "B": 20, "C": 16 },
  "reason": "글리치 밸런스 조정"
}
```
- `distribution` 합계는 대륙 총 영토 수와 일치해야 한다(개수 고정 재배정). 불일치 시 `400`. 개수 증감 허용 여부는 [OQ-12](../design/admin-dashboard.md#10-미결-사항-open-questions).
- BIDDING 중인 영토의 즉시 반영 범위는 [OQ-4](../design/admin-dashboard.md#10-미결-사항-open-questions).
- 변경 전/후 분포를 감사 로그 + 실험 조건 스냅샷으로 기록.

Response 200 — `{ continentId, before, after, changedCount }`.

---

## 유저 관리

### 유저 목록·검색
**GET** `/api/v1/admin/users?q={검색어}&status={ACTIVE}&page={0}&size={20}`

| parameter | 타입 | 필수 | 설명 |
|---|---|---|---|
| `q` | String | N | 닉네임/username 부분 검색 |
| `status` | String | N | `ACTIVE`/`SUSPENDED`/`WITHDRAWN` 필터 |
| `page`,`size` | Integer | N | 페이지네이션 (size 최대 100) |

Response 200 — `data.users[]`: `{ userId, username, nickname, status, availableAP, availableGP, createdAt }`, `totalCount`.

### 유저 상세
**GET** `/api/v1/admin/users/{userId}`

Response 200:
```json
{
  "status": 200, "message": "OK",
  "data": {
    "userId": 7, "username": "player7", "nickname": "정복자", "status": "ACTIVE",
    "wallet": { "availableAP": 57000, "lockedAP": 0, "availableGP": 0, "availableFood": 100 },
    "territoryCount": 12,
    "trophy": { "score": 1500, "league": "GOLD" },
    "seasonPass": { "seasonId": 1, "purchased": true, "level": 8 },
    "createdAt": "2026-01-02T09:00:00Z"
  }
}
```

> `wallet.availableGP`는 **금고 잔액**(`global_vaults.stored_gp`), `wallet.availableFood`는 **소유 위치(영토+섬) 저장소 식량 합**이다. 지갑에는 GP·식량 컬럼이 없다.

### 계정 정지/해제
**PATCH** `/api/v1/admin/users/{userId}/status`

Request:
```json
{ "status": "SUSPENDED", "reason": "약관 위반 - 부정 입찰" }
```
- `status`: `ACTIVE` | `SUSPENDED` (필수)
- `reason`: 필수. 감사 로그 `detail_json`에 기록.
- 기존 `UserStatus`/`updateStatus()` 재사용. 정지 유저는 로그인 시 `403 SUSPENDED_USER`.

Response 200 — `{ userId, status }`.

### 재화 조정
**POST** `/api/v1/admin/users/{userId}/wallet/adjust`

Request:
```json
{ "currency": "AP", "amount": 5000, "reason": "CS 보상 - 결제 오류 보정" }
```
- `currency`: `AP` | `GP` — `AP`는 지갑, `GP`는 **글로벌 금고**(`global_vaults.stored_gp`)를 조정한다.
- `amount`: 정수(음수 허용 = 차감). 결과가 0 미만이면 `400`.
- `reason`: 필수.

Response 200 — `{ userId, currency, before, after }`.

### 트로피 조정
**PATCH** `/api/v1/admin/users/{userId}/trophy`

Request: `{ "delta": -50, "reason": "집계 오류 보정" }` — `delta`는 증감량. 리그(league)는 조정 후 자동 재계산.

### 아이템 지급
**POST** `/api/v1/admin/users/{userId}/items/grant`

Request: `{ "itemId": 3, "quantity": 1, "reason": "CS 보상" }`

---

## 경매·영토 관리

### 경매 목록
**GET** `/api/v1/admin/auctions?status={BIDDING}&coordX={}&coordY={}&page=0&size=20`

- `status` 필터, 좌표 검색 지원. 관리자용 확장 필드(입찰 수, 현재 최고가, 마감 시각) 포함.

### 경매 상세
**GET** `/api/v1/admin/auctions/{auctionId}` — 경매 정보 + **전체 입찰 내역**(입찰자 ID·금액·시각) 반환.

### 경매 강제 종료
**POST** `/api/v1/admin/auctions/{auctionId}/force-end`

Request:
```json
{ "mode": "SETTLE", "reason": "가격 조작 의심" }
```
- `mode`: `SETTLE`(현재 최고가로 즉시 낙찰) | `CANCEL`(낙찰 없이 종료, 입찰 잠금 AP 전액 환불) — **정책 확정 필요([OQ-2](../design/admin-dashboard.md#10-미결-사항-open-questions))**
- 이미 종료된 경매면 `409`.

Response 200 — `{ auctionId, mode, winnerId, finalPrice, refundedUserIds }`.

### 경매 강제 시작
**POST** `/api/v1/admin/territories/{territoryId}/start-auction`

- IDLE 영토를 즉시 경매(BIDDING)로 전환. 내부적으로 `nextAuctionAt`을 현재로 설정하거나 `startBidding()` 직접 호출.
- 이미 BIDDING/OCCUPIED면 `409`. 비활성(`auctionEnabled=false`) 영토면 `409`.

Request: `{ "reason": "이벤트 조기 오픈" }`

### 경매 활성/비활성
**PATCH** `/api/v1/admin/territories/{territoryId}/auction-enabled`

Request: `{ "enabled": false, "reason": "분쟁 지역 경매 일시 중단" }`
- `enabled=false` → 스케줄러 `createAuctions()`가 해당 영토를 재경매하지 않음.
- **신규 플래그 `territories.auction_enabled` 도입 전제([OQ-6](../design/admin-dashboard.md#10-미결-사항-open-questions))**

### 영토 등급 변경
**PATCH** `/api/v1/admin/territories/{territoryId}/grade`

Request: `{ "grade": "S", "reason": "밸런스 조정" }`
- `Territory.grade`(FK) 변경. 다음 경매 시작가·생산량에 반영. 소급 적용 범위는 [OQ-4](../design/admin-dashboard.md#10-미결-사항-open-questions).

---

## 시즌·랭킹 운영

### 시즌 목록
**GET** `/api/v1/admin/seasons` — 전체 시즌(번호·기간·활성 여부).

### 시즌 생성
**POST** `/api/v1/admin/seasons`

Request:
```json
{ "seasonNumber": 2, "startedAt": "2026-08-01T00:00:00Z", "endedAt": "2026-09-01T00:00:00Z" }
```

### 시즌 수정(시작/종료)
**PATCH** `/api/v1/admin/seasons/{seasonId}`

Request: `{ "endedAt": "2026-07-15T00:00:00Z", "reason": "조기 종료" }`
- 종료 = `endedAt`을 현재/과거로 → `SeasonEndScheduler`가 정산 배치 수행.

---

## 아이템

### 아이템 목록
**GET** `/api/v1/admin/items` — 아이템별 `costAP`/`costGP`/`dailyLimit` 포함.

### 아이템 가격·한도 수정
**PATCH** `/api/v1/admin/items/{itemId}`

Request: `{ "costAP": 250, "dailyLimit": 5, "reason": "밸런스 패치" }` — 적용 시점 정책은 [OQ-10](../design/admin-dashboard.md#10-미결-사항-open-questions).

### 아이템 구매 이력
**GET** `/api/v1/admin/item-purchases?userId={}&itemType={}&from={}&to={}&page=0&size=20`

---

## 운영 보조

### 지표 요약
**GET** `/api/v1/admin/dashboard/summary`

Response 200:
```json
{
  "status": 200, "message": "OK",
  "data": {
    "totalUsers": 1240,
    "dau": 312,
    "activeAuctions": 47,
    "todayApCharged": 1830000
  }
}
```
- `dau` 정의(로그인 vs API 호출)는 [OQ-5](../design/admin-dashboard.md#10-미결-사항-open-questions). 최근활동 컬럼 필요.

### 감사 로그 조회
**GET** `/api/v1/admin/audit-logs?adminUserId={}&action={}&from={}&to={}&page=0&size=20`

Response 200 — `data.logs[]`: `{ id, adminUserId, action, targetType, targetId, detail, createdAt }`.

### 채팅 로그 조회
**GET** `/api/v1/admin/chat/rooms/{roomId}/messages?page=0&size=50` — 관리자는 길드 멤버십 검증 없이 접근.

### 채팅 메시지 삭제
**DELETE** `/api/v1/admin/chat/messages/{messageId}`

Request: `{ "reason": "욕설/비방" }` — Response `200`, `ApiResponse<Void>`.
