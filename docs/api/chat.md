# Chat / Social REST API

> **⚙️ MSA**: 채팅·소셜은 **social-service**가 담당한다. 게이트웨이가 `/api/v1/chat/**`·`/api/v1/social/**`를 social-service로 라우팅한다. 실시간 메시지 push는 realtime-service(STOMP `/sub/chat/{roomId}`)가 담당하며, 아래는 히스토리·발송의 **REST** 경로다. WebSocket 규약은 [websocket/chat.md](./websocket/chat.md).

## 목차

| Method | Endpoint | 기능 | 인증 |
|---|---|---|---|
| GET | `/api/v1/chat/rooms/{roomId}/messages` | 채팅방 메시지 히스토리 조회(페이지네이션) | 필수 |
| POST | `/api/v1/chat/rooms/{roomId}/messages` | 채팅 메시지 전송(저장 + 브로드캐스트) | 필수 |
| GET | `/api/v1/social/friends` | 친구 목록 조회(미구현 스텁) | 필수 |

## 메시지 히스토리 조회

**GET** `/api/v1/chat/rooms/{roomId}/messages?page={0}&size={30}`

방(WORLD / CONTINENT:{id} / GUILD:{id}) 메시지를 최신순으로 페이지네이션 반환한다.

## 메시지 전송

**POST** `/api/v1/chat/rooms/{roomId}/messages`

```json
{ "content": "안녕하세요" }
```

DB에 저장한 뒤 realtime-service를 통해 `/sub/chat/{roomId}` 구독자에게 브로드캐스트한다.

## 친구 목록 조회

**GET** `/api/v1/social/friends`

현재 미구현 스텁(향후 친구 시스템 도입 시 확장).
