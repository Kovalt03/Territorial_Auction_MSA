# Guild API

> 구현 상태: ✅ 전체 구현 완료 (2026-05-06 기준)

---

## 목차 (구현 현황)

| 엔드포인트 | 메서드 | 인증 | 상태 |
|---|---|---|---|
| `/api/v1/guilds` | [POST](#길드-생성) | 필수 | ✅ 완료 |
| `/api/v1/guilds` | [GET](#길드-목록-조회) | 불필요 | ✅ 완료 |
| `/api/v1/guilds/{guildId}` | [GET](#길드-정보-조회) | 불필요 | ✅ 완료 |
| `/api/v1/guilds/me` | [GET](#나의-길드-정보-조회) | 필수 | ✅ 완료 |
| `/api/v1/guilds/{guildId}` | [PATCH](#길드-정보-수정) | 필수 (MASTER) | ✅ 완료 |
| `/api/v1/guilds/{guildId}/join` | [POST](#길드-가입-신청) | 필수 | ✅ 완료 |
| `/api/v1/guilds/{guildId}/join` | [DELETE](#가입-신청-취소) | 필수 | ✅ 완료 |
| `/api/v1/guilds/{guildId}/master` | [PATCH](#길드장-이전) | 필수 (MASTER) | ✅ 완료 |
| `/api/v1/guilds/{guildId}/members/{userId}/approve` | [PATCH](#가입-승인) | 필수 (MASTER) | ✅ 완료 |
| `/api/v1/guilds/{guildId}/members/{userId}/reject` | [PATCH](#가입-신청-거절) | 필수 (MASTER) | ✅ 완료 |
| `/api/v1/guilds/{guildId}/members/me` | [DELETE](#길드-탈퇴) | 필수 | ✅ 완료 |
| `/api/v1/guilds/{guildId}/members/{userId}` | [DELETE](#멤버-강제-추방) | 필수 (MASTER) | ✅ 완료 |
| `/api/v1/guilds/{guildId}/applications` | [GET](#가입-신청-목록-조회) | 필수 (MASTER) | ✅ 완료 |

---

## 길드 생성

**POST** `/api/v1/guilds`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request

```json
{
  "name": "정복자들",
  "description": "영토 정복을 목표로 하는 길드입니다.",
  "emblem": "https://cdn.example.com/emblems/001.png"
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `name` | String | Y | 길드명 (2~20자, 유니크) |
| `description` | String | N | 길드 소개 (최대 200자) |
| `emblem` | String | N | 엠블럼 이미지 URL |

### 비즈니스 규칙
- 1인 1길드 제한 (이미 길드 소속이면 생성 불가)
- 생성 시 `guild_members`에 role=`MASTER`, status=`ACTIVE`로 즉시 등록

### Response (201 Created)

```json
{
  "status": 201,
  "message": "Created",
  "data": {
    "guildId": 1,
    "name": "정복자들",
    "masterId": 5,
    "masterNickname": "테스트유저",
    "memberCount": 1,
    "createdAt": "2026-04-27T12:00:00Z"
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 409 | GUILD_NAME_DUPLICATED | 이미 존재하는 길드명 |
| 409 | ALREADY_IN_GUILD | 이미 길드에 소속된 유저 |

---

## 길드 목록 조회

**GET** `/api/v1/guilds?page={0}&size={20}&search={검색어}`

- 인증 불필요

### Query Parameters

| parameter | 타입 | 필수 | 설명 |
|---|---|---|---|
| `page` | Integer | N | 페이지 번호 (기본 0) |
| `size` | Integer | N | 페이지 크기 (기본 20) |
| `search` | String | N | 길드명 검색어 |

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "totalCount": 150,
    "page": 0,
    "size": 20,
    "guilds": [
      {
        "guildId": 1,
        "guildName": "정복자들",
        "masterNickname": "테스트유저",
        "memberCount": 12,
        "maxMembers": 30,
        "totalTrophyPoints": 4500,
        "totalTerritories": 8,
        "recruitingStatus": "OPEN"
      }
    ]
  }
}
```

---

## 길드 정보 조회

**GET** `/api/v1/guilds/{guildId}`

- 인증 불필요

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "guildId": 1,
    "name": "정복자들",
    "description": "영토 정복을 목표로 하는 길드입니다.",
    "emblem": "https://cdn.example.com/emblems/001.png",
    "master": {
      "userId": 5,
      "nickname": "테스트유저"
    },
    "memberCount": 12,
    "totalTerritoryCount": 8,
    "members": [
      {
        "userId": 5,
        "nickname": "테스트유저",
        "role": "MASTER",
        "territoryCount": 3,
        "joinedAt": "2026-04-01T00:00:00Z"
      }
    ],
    "createdAt": "2026-04-01T00:00:00Z"
  }
}
```

> `members`: status=`ACTIVE`인 멤버만 반환

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |

---

## 나의 길드 정보 조회

**GET** `/api/v1/guilds/me`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "guildId": 1,
    "guildName": "정복자들",
    "description": "영토 정복을 목표로 하는 길드입니다.",
    "masterNickname": "테스트유저",
    "memberCount": 12,
    "maxMembers": 30,
    "totalTerritories": 8,
    "totalTrophyPoints": 4500,
    "myRole": "MEMBER",
    "joinedAt": "2026-04-10T00:00:00Z"
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | NOT_IN_GUILD | 길드에 소속되지 않은 유저 |

---

## 길드 정보 수정

**PATCH** `/api/v1/guilds/{guildId}`

**Authorization**: Bearer `{{accessToken}}` (필수, 길드장만)

모든 필드 optional — null이면 기존 값 유지 (부분 업데이트)

### Request Body

```json
{
  "description": "새로운 길드 소개글입니다.",
  "emblem": "https://cdn.example.com/emblems/002.png",
  "recruitingStatus": "CLOSED"
}
```

| field | 타입 | 필수 | 설명 |
|---|---|---|---|
| `description` | String | N | 길드 소개 (최대 200자) |
| `emblem` | String | N | 엠블럼 이미지 URL (최대 255자) |
| `recruitingStatus` | String | N | `OPEN` 또는 `CLOSED` |

### 비즈니스 규칙
- 길드명(`name`)은 변경 불가
- null 필드는 해당 컬럼 미변경

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | (validation) | recruitingStatus 값이 OPEN/CLOSED 외 |
| 403 | NOT_GUILD_MASTER | 길드장 권한 없음 |
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |

---

## 길드 가입 신청

**POST** `/api/v1/guilds/{guildId}/join`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Request Body (optional)

```json
{
  "message": "열심히 하겠습니다!"
}
```

> `message` 필드는 선택값 (최대 200자). Request body 자체도 생략 가능.

### 비즈니스 규칙
- `guild_members`에 role=`MEMBER`, status=`PENDING`으로 등록
- 길드장에게 WebSocket 알림 + `notification_logs` 기록 (TODO)

### Response (202 Accepted)

```json
{
  "status": 202,
  "message": "Accepted",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |
| 409 | ALREADY_IN_GUILD | 이미 다른 길드에 소속 |
| 409 | ALREADY_APPLIED | 이미 가입 신청한 길드 |

---

## 가입 신청 취소

**DELETE** `/api/v1/guilds/{guildId}/join`

**Authorization**: Bearer `{{accessToken}}` (필수)

### 비즈니스 규칙
- 본인의 `PENDING` 상태 신청만 취소 가능
- `PENDING` → `CANCELLED`

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 404 | APPLICATION_NOT_FOUND | 취소할 신청 없음 |

---

## 길드장 이전

**PATCH** `/api/v1/guilds/{guildId}/master`

**Authorization**: Bearer `{{accessToken}}` (필수, 현재 길드장만)

### Request Body

```json
{
  "newMasterId": 42
}
```

### 비즈니스 규칙
- 대상자는 동일 길드의 `ACTIVE` 멤버여야 함
- `guilds.master_id` 변경
- 기존 길드장 `guild_members.role` → `MEMBER`
- 신규 길드장 `guild_members.role` → `MASTER`

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | CANNOT_TRANSFER_TO_SELF | 자기 자신에게 이전 시도 |
| 403 | NOT_GUILD_MASTER | 요청자가 길드장 아님 |
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |
| 404 | NOT_IN_GUILD | 대상이 해당 길드 ACTIVE 멤버 아님 |

---

## 가입 승인

**PATCH** `/api/v1/guilds/{guildId}/members/{userId}/approve`

**Authorization**: Bearer `{{accessToken}}` (필수, 길드장만)

### 비즈니스 규칙
- `PENDING` → `ACTIVE` 상태 전환
- 정원(`maxMembers`) 초과 시 승인 불가
- 승인된 유저에게 알림 발송 (TODO)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 400 | GUILD_FULL | 길드 정원 초과 |
| 403 | NOT_GUILD_MASTER | 길드장 권한 없음 |
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |
| 404 | APPLICATION_NOT_FOUND | 해당 유저의 가입 신청 없음 |

---

## 가입 신청 거절

**PATCH** `/api/v1/guilds/{guildId}/members/{userId}/reject`

**Authorization**: Bearer `{{accessToken}}` (필수, 길드장만)

### 비즈니스 규칙
- `PENDING` → `CANCELLED` 상태 전환

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 403 | NOT_GUILD_MASTER | 길드장 권한 없음 |
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |
| 404 | APPLICATION_NOT_FOUND | 해당 유저의 가입 신청 없음 |

---

## 길드 탈퇴

**DELETE** `/api/v1/guilds/{guildId}/members/me`

**Authorization**: Bearer `{{accessToken}}` (필수)

### 비즈니스 규칙
- 길드장은 탈퇴 불가 (단, 혼자 남은 경우 가능)
- `ACTIVE` → `INACTIVE` (소프트 딜리트)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 403 | GUILD_MASTER_CANNOT_LEAVE | 다인 상태에서 길드장 탈퇴 시도 |
| 404 | NOT_IN_GUILD | 해당 길드 소속 아님 |

---

## 멤버 강제 추방

**DELETE** `/api/v1/guilds/{guildId}/members/{userId}`

**Authorization**: Bearer `{{accessToken}}` (필수, 길드장만)

### 비즈니스 규칙
- 길드장 본인 추방 불가
- `ACTIVE` → `KICKED` (소프트 딜리트, 자발 탈퇴 `INACTIVE`와 구분)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 403 | NOT_GUILD_MASTER | 길드장 권한 없음 |
| 403 | CANNOT_KICK_MASTER | 길드장 추방 시도 |
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |
| 404 | NOT_IN_GUILD | 해당 멤버 없음 |

---

## 가입 신청 목록 조회

**GET** `/api/v1/guilds/{guildId}/applications`

**Authorization**: Bearer `{{accessToken}}` (필수, 길드장만)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "guildId": 42,
    "applications": [
      {
        "applicationId": 201,
        "userId": 1055,
        "nickname": "NewWarrior",
        "trophyPoints": 1200,
        "appliedAt": "2026-04-27T15:30:00Z"
      }
    ]
  }
}
```

### 에러

| HTTP | 에러 코드 | 설명 |
|---|---|---|
| 403 | NOT_GUILD_MASTER | 길드장 권한 없음 |
| 404 | GUILD_NOT_FOUND | 존재하지 않는 길드 |

---

## GuildMember 상태 전이표

| 상태 | 설명 | 전이 가능 |
|---|---|---|
| `PENDING` | 가입 신청 대기 중 | → `ACTIVE` (승인), → `CANCELLED` (취소/거절) |
| `ACTIVE` | 정상 길드 멤버 | → `INACTIVE` (자발 탈퇴), → `KICKED` (강제 추방) |
| `INACTIVE` | 자발 탈퇴 (소프트 딜리트) | — |
| `KICKED` | 강제 추방 (소프트 딜리트) | — |
| `CANCELLED` | 신청 취소 또는 거절 | — |
