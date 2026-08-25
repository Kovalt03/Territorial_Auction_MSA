# 에러 코드 레퍼런스

전체 API에서 사용하는 에러 코드 목록입니다.

---

## 공통 에러 응답 형식

```json
{
  "status": 400,
  "message": "에러 설명 메시지",
  "data": null
}
```

---

## 에러 코드 목록

### 인증 (Auth)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `UNAUTHORIZED` | 401 | 인증 토큰 없음 또는 만료 |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰 형식 |
| `REFRESH_TOKEN_EXPIRED` | 401 | RefreshToken 만료 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `DUPLICATE_USERNAME` | 409 | 이미 존재하는 유저네임 |
| `DUPLICATE_EMAIL` | 409 | 이미 존재하는 이메일 |
| `DUPLICATE_NICKNAME` | 409 | 이미 존재하는 닉네임 |
| `USER_NOT_FOUND` | 404 | 존재하지 않는 유저 |
| `INVALID_CREDENTIALS` | 401 | 이메일 또는 비밀번호 불일치 |

### 유저 (User)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `USER_NOT_FOUND` | 404 | 존재하지 않는 유저 |
| `NOTIFICATION_SETTING_NOT_FOUND` | 404 | 알림 설정 레코드 없음 |
| `INSUFFICIENT_AP` | 400 | AP 잔액 부족 |
| `INSUFFICIENT_GP` | 400 | GP 잔액 부족 |

### 맵·영토 (Map)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `TERRITORY_NOT_FOUND` | 404 | 존재하지 않는 영토 |
| `NOT_TERRITORY_OWNER` | 403 | 영토 점유자 아님 |
| `TERRITORY_INVINCIBLE` | 400 | 무적 상태인 영토 |
| `TERRITORY_HAS_NO_OWNER` | 400 | 점유자가 없는 영토 |
| `CONTINENT_NOT_FOUND` | 404 | 존재하지 않는 대륙 |
| `INVALID_COLOR_HEX` | 400 | 유효하지 않은 색상 코드 |

### 경매 (Auction)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `AUCTION_NOT_FOUND` | 404 | 존재하지 않는 경매 |
| `AUCTION_ENDED` | 400 | 종료된 경매 |
| `AUCTION_NOT_STARTED` | 400 | 아직 시작되지 않은 경매 |
| `BID_TOO_LOW` | 400 | 최소 입찰 단위 미달 |
| `BID_ON_OWN_TERRITORY` | 400 | 자기 소유 영토 경매에 입찰 불가 |
| `INSUFFICIENT_AP` | 400 | AP 잔액 부족 |

### 건물 (Building)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `BUILDING_NOT_FOUND` | 404 | 존재하지 않는 건물 |
| `INVALID_POSITION` | 400 | 배치 불가 위치 (겹침, 범위 초과) |
| `ZONE_RESTRICTION_VIOLATED` | 400 | Zone 제약 위반 (Castle은 Zone 1만 가능) |
| `INSUFFICIENT_GP` | 400 | GP 부족 |
| `BUILDING_NOT_DESTROYED` | 400 | 수리 대상 건물이 파괴 상태 아님 |
| `MAX_LEVEL_REACHED` | 400 | 이미 최대 레벨 |
| `INVENTORY_ITEM_NOT_FOUND` | 404 | 보관함에 없는 건물 아이템 |

### 군사·공성 (Military)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `NO_ATTACK_TOKEN` | 400 | 공격권 없음 |
| `ZONE_NOT_CLEARED` | 400 | 이전 Zone 미클리어 |
| `ATTACK_COOLDOWN` | 400 | 공격 쿨다운 중 |
| `NO_BARRACKS` | 400 | 병영(Barracks) 없음 |
| `INSUFFICIENT_UNITS` | 400 | 보유 유닛 수 부족 |
| `SIEGE_NOT_FOUND` | 404 | 존재하지 않는 공성전 |

### 알림 (Notification)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `NOTIFICATION_NOT_FOUND` | 404 | 존재하지 않는 알림 |
| `NOT_NOTIFICATION_OWNER` | 403 | 본인 알림 아님 |

### 랭킹 (Ranking)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `RANKING_TYPE_NOT_FOUND` | 400 | 유효하지 않은 랭킹 타입 |

### 글로벌 금고 (Global Vault)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `INSUFFICIENT_GP` | 400 | 보유 GP 부족 |
| `DONATION_AMOUNT_TOO_LOW` | 400 | 최소 기부 금액 미달 (100 GP) |

### 결제·아이템 (Payment)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `PAYMENT_FAILED` | 400 | 결제 실패 |
| `INVALID_AMOUNT` | 400 | 유효하지 않은 충전 금액 |
| `ITEM_NOT_FOUND` | 404 | 존재하지 않는 아이템 |
| `DAILY_LIMIT_EXCEEDED` | 429 | 일일 구매 한도 초과 |

### 길드 (Guild)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `GUILD_NOT_FOUND` | 404 | 존재하지 않는 길드 |
| `INVALID_GUILD_NAME` | 400 | 길드명 형식 오류 |
| `GUILD_NAME_DUPLICATED` | 409 | 이미 존재하는 길드명 |
| `ALREADY_IN_GUILD` | 409 | 이미 길드에 소속된 유저 |
| `ALREADY_APPLIED` | 409 | 이미 가입 신청한 길드 |
| `NOT_IN_GUILD` | 404 | 길드에 소속되지 않은 유저 |
| `GUILD_FULL` | 400 | 길드 정원 초과 |
| `NOT_GUILD_MASTER` | 403 | 길드장 권한 없음 |
| `APPLICATION_NOT_FOUND` | 404 | 해당 유저의 가입 신청 없음 |

### 채팅 (Chat)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `CHAT_ROOM_NOT_FOUND` | 404 | 존재하지 않는 채팅방 또는 잘못된 roomId 형식 |
| `CHAT_ACCESS_DENIED` | 403 | 해당 채팅방에 접근 권한 없음 (길드 비멤버 등) |

### 서버 공통

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |
| `VALIDATION_FAILED` | 400 | 요청 필드 유효성 검증 실패 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
