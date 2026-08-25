# WebSocket — Chat

> 구현 상태: ✅ 구현 완료

---

## 채널

| 방향 | Destination | 인증 |
|---|---|---|
| 클라이언트 → 서버 | `/pub/chat/{roomId}` | 필수 |
| 서버 → 클라이언트 | `/sub/chat/{roomId}` | 불필요 |

---

## 채팅방 타입

| roomId | 설명 | 접근 제어 |
|---|---|---|
| `room_global` | 전체 채팅 | 로그인 유저면 누구나 |
| `room_guild_{guildId}` | 길드 채팅 | 해당 길드 멤버만 |
| `room_territory_{territoryId}` | 영토 채팅 | 로그인 유저면 누구나 |

---

## 메시지 형식

### 발행 (클라이언트 → 서버)

**Destination**: `/pub/chat/{roomId}`

```json
{
  "content": "안녕하세요!"
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `content` | String | 필수, 최대 500자 |

### 수신 (서버 → 클라이언트)

**Destination**: `/sub/chat/{roomId}`

```json
{
  "messageId": 1,
  "roomId": "room_global",
  "senderId": 5,
  "senderNickname": "픽셀전사",
  "content": "안녕하세요!",
  "sentAt": "2026-05-08T12:00:00"
}
```

---

## 히스토리 조회 (REST)

입장 시 과거 메시지를 불러올 때 사용한다.

```
GET /api/v1/chat/rooms/{roomId}/messages?before={messageId}&size=30
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `before` | 선택 | 이 messageId 이전 메시지 조회. 미지정 시 최신 30개 |
| `size` | 선택 | 페이지 크기 (기본값 30) |

응답에 `hasNext` 포함 — `true`면 추가 로드 가능.

---

## WebSocket 에러 응답

STOMP 메시지 처리 중 예외 발생 시 `/user/queue/errors` 로 에러가 전송된다.
클라이언트는 해당 채널을 구독해야 에러를 수신할 수 있다.

**Destination**: `/user/queue/errors`

```json
{
  "success": false,
  "message": "해당 채팅방에 접근 권한이 없습니다.",
  "data": null
}
```

---

## 에러 코드

| 에러 코드 | 설명 |
|---|---|
| `CHAT_ROOM_NOT_FOUND` | 존재하지 않는 채팅방 또는 잘못된 roomId |
| `CHAT_ACCESS_DENIED` | 미인증 유저의 채팅 발행 또는 길드 비멤버의 길드 채팅방 접근 |
