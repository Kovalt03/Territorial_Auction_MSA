# 채팅 아키텍처 설계

## 개요

실시간 채팅은 WebSocket + STOMP 프로토콜로 구현한다.  
메시지 히스토리 조회 등 보조 기능만 REST로 제공한다.

---

## WebSocket 엔드포인트

| 항목 | 값 |
|---|---|
| 핸드셰이크 URL | `ws://host/ws` |
| 프로토콜 | STOMP over WebSocket (SockJS fallback 포함) |
| 인증 | STOMP CONNECT 헤더 `Authorization: Bearer {JWT}` |

---

## STOMP 구조

### Prefix

| 역할 | prefix |
|---|---|
| 클라이언트 → 서버 발행 | `/pub` |
| 서버 → 클라이언트 구독 | `/sub` |

### 메시지 발행 / 구독

| 방향 | destination |
|---|---|
| 클라이언트 → 서버 | `/pub/chat/{roomId}` |
| 서버 → 클라이언트 | `/sub/chat/{roomId}` |

발행 body:
```json
{ "content": "안녕하세요!" }
```

수신 메시지 구조:

| 필드 | 타입 | 설명 |
|---|---|---|
| `messageId` | Long | 메시지 PK |
| `roomId` | String | 채팅방 식별자 |
| `senderId` | Long | 발신자 유저 ID |
| `senderNickname` | String | 발신자 닉네임 |
| `content` | String | 메시지 본문 |
| `sentAt` | String (ISO-8601) | 발신 시각 |

---

## 채팅방 타입

채팅방은 `chat_rooms` 테이블로 관리하며, roomId는 아래 형식의 문자열로 식별한다.

| roomId 형식 | 타입 | 설명 |
|---|---|---|
| `room_global` | `GLOBAL` | 전체 채팅 (서버 1개 고정) |
| `room_guild_{guildId}` | `GUILD` | 길드 채팅 |
| `room_territory_{territoryId}` | `TERRITORY` | 영토별 경매 채팅 |

---

## 접근 제어

메시지 발행 시 roomId를 파싱해 채팅방 타입에 따라 검증한다.

| 채팅방 타입 | 검증 조건 |
|---|---|
| `GLOBAL` | 로그인 유저면 누구나 |
| `GUILD` | 해당 길드 멤버인지 확인 |
| `TERRITORY` | 로그인 유저면 누구나 (추후 점유자 제한 검토) |

---

## JWT 인증

WebSocket은 Spring Security 필터 체인 밖에서 동작한다.  
STOMP CONNECT 단계에서 `StompChannelInterceptor`가 JWT를 검증하고 Principal을 주입한다.  
이후 핸들러에서는 일반 REST와 동일하게 Principal로 유저를 식별한다.

---

## 메시지 처리 흐름

```
클라이언트 SEND /pub/chat/{roomId}
    → @MessageMapping 핸들러 (ChatController)
        → roomId 파싱 & 접근 제어 검증
        → ChatService.sendMessage()
            → ChatMessage DB 저장
            → SimpMessagingTemplate.convertAndSend("/sub/chat/{roomId}", dto)
    → 구독 중인 모든 클라이언트 수신
```

---

## REST 보조 API

| Method | URL | 설명 |
|---|---|---|
| `GET` | `/api/v1/chat/rooms/{roomId}/messages` | 이전 메시지 이력 조회 (커서 기반 무한 스크롤) |

쿼리 파라미터: `before={messageId}&size=30`  
`before` 미지정 시 최신 30개 반환. 응답에 `hasNext` 포함.

---

## 관련 DB 테이블

| 테이블 | 설명 |
|---|---|
| `chat_rooms` | 채팅방 정보 (타입, 연결 대상 ID) |
| `chat_messages` | 메시지 내용, 발신자, 타임스탬프 |
