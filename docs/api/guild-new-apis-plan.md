# Guild 신규 API 기획 스펙 (구현 전 정의)

> 작성일: 2026-05-06  
> 대상 브랜치: feature/be-14-guild-api

---

## 구현 대상 (4개)

### 1. 가입 신청 거절

**PATCH** `/api/v1/guilds/{guildId}/members/{targetUserId}/reject`

- 권한: 길드장(MASTER)만 가능
- 대상: `PENDING` 상태인 가입 신청
- 처리: `PENDING` → `CANCELLED` (기존 cancel() 도메인 메서드 재사용)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| NOT_GUILD_MASTER | 403 | 요청자가 길드장 아님 |
| GUILD_NOT_FOUND | 404 | 길드 없음 |
| APPLICATION_NOT_FOUND | 404 | PENDING 신청 없음 |

---

### 2. 길드장 이전

**PATCH** `/api/v1/guilds/{guildId}/master`

**Request Body**
```json
{ "newMasterId": 42 }
```

- 권한: 현재 길드장만 가능
- 대상: 동일 길드의 `ACTIVE` 멤버여야 함
- 처리:
  - `guilds.master_id` → 새 길드장으로 변경
  - 현재 길드장 `guild_members.role` → MEMBER
  - 새 길드장 `guild_members.role` → MASTER

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| NOT_GUILD_MASTER | 403 | 요청자가 길드장 아님 |
| GUILD_NOT_FOUND | 404 | 길드 없음 |
| CANNOT_TRANSFER_TO_SELF | 400 | 자기 자신에게 이전 |
| NOT_IN_GUILD | 404 | 대상이 해당 길드 ACTIVE 멤버 아님 |

---

### 3. 멤버 강제 추방

**DELETE** `/api/v1/guilds/{guildId}/members/{targetUserId}`

- 권한: 길드장(MASTER)만 가능
- 제약: 길드장 본인 추방 불가
- 처리: `ACTIVE` → `KICKED` (소프트 딜리트, 별도 Status 값으로 구분)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| NOT_GUILD_MASTER | 403 | 요청자가 길드장 아님 |
| GUILD_NOT_FOUND | 404 | 길드 없음 |
| CANNOT_KICK_MASTER | 403 | 길드장 추방 시도 |
| NOT_IN_GUILD | 404 | 대상이 ACTIVE 멤버 아님 |

---

### 4. 길드 정보 수정

**PATCH** `/api/v1/guilds/{guildId}`

**Request Body** (모든 필드 optional — null이면 해당 필드 미변경)
```json
{
  "description": "새 소개글",
  "emblem": "https://cdn.example.com/emblems/002.png",
  "recruitingStatus": "CLOSED"
}
```

- 권한: 길드장(MASTER)만 가능
- 변경 가능 필드: description, emblem, recruitingStatus
- 변경 불가 필드: name (유니크 제약, 혼동 방지)

| 에러 코드 | HTTP | 설명 |
|---|---|---|
| NOT_GUILD_MASTER | 403 | 요청자가 길드장 아님 |
| GUILD_NOT_FOUND | 404 | 길드 없음 |

---

## 엔티티 변경 사항

**GuildMember.Status**에 `KICKED` 추가 (강제 추방, 자발 탈퇴 INACTIVE와 구분)

**GuildMember 도메인 메서드 추가**
- `promoteToMaster()` — role = MASTER
- `demoteToMember()` — role = MEMBER
- `kick()` — status = KICKED

**Guild 도메인 메서드 추가**
- `transferMaster(User newMaster)` — master 변경
- `updateInfo(String description, String emblem, RecruitingStatus recruitingStatus)` — null이면 미변경
