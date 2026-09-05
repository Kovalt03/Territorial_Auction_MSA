# WebSocket API

실시간 이벤트 전달을 위해 STOMP over WebSocket을 사용한다.

---

## 연결

| 항목 | 값 |
|---|---|
| 엔드포인트 | `ws://localhost:8090/ws` (게이트웨이 → realtime-service) |
| 프로토콜 | STOMP over WebSocket (SockJS fallback 포함) |
| 환경변수 | `{{wsUrl}}` |

```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);
stompClient.connect({ Authorization: `Bearer ${accessToken}` }, callback);
```

---

## 인증

STOMP CONNECT 헤더에 JWT Bearer 토큰 포함.

```
CONNECT
Authorization: Bearer {accessToken}
```

- 토큰 없이 연결 가능 — 공개 채널(`/sub/map/update` 등)만 수신 가능
- 인증이 필요한 채널은 `Principal` null 체크로 차단

---

## Prefix

| 역할 | prefix |
|---|---|
| 클라이언트 → 서버 발행 | `/pub` |
| 서버 → 클라이언트 구독 | `/sub` |

---

## 채널 목록

### 클라이언트 → 서버 (발행)

| Destination | 설명 | 인증 | 구현 | 문서 |
|---|---|---|---|---|
| `/pub/chat/{roomId}` | 채팅 메시지 전송 | 필수 | ✅ | [chat.md](./chat.md) |
| `/pub/auction/{auctionId}/bid` | 실시간 입찰 | 필수 | ⬜ | [auction.md](./auction.md) |

### 서버 → 클라이언트 (구독)

| Destination | 설명 | 인증 | 구현 | 문서 |
|---|---|---|---|---|
| `/sub/chat/{roomId}` | 채팅 메시지 수신 | 불필요 | ✅ | [chat.md](./chat.md) |
| `/sub/auction/{auctionId}` | 경매 실시간 입찰 현황 | 불필요 | ⬜ | [auction.md](./auction.md) |
| `/sub/map/update` | 맵 영토 상태 변경 | 불필요 | ⬜ | [map.md](./map.md) |
| `/sub/user/{userId}/notification` | 개인 알림 | 필수 | ⬜ | [notification.md](./notification.md) |
| `/sub/user/{userId}/siege-alert` | 공성전 공격 선언 알림 | 필수 | ⬜ | [notification.md](./notification.md) |
| `/sub/user/{userId}/auction-result` | 경매 낙찰/실패 알림 | 필수 | ⬜ | [notification.md](./notification.md) |

---

## 스케일링

멀티 서버 환경에서는 Redis Pub/Sub을 브로커로 사용해 메시지를 라우팅한다.  
현재는 단일 서버(SimpleBroker) 운영. 자세한 전략은 [chat-broker-strategy.md](../../design/chat-broker-strategy.md) 참고.

```
클라이언트 → 서버 A → Redis Pub/Sub → 서버 B → 클라이언트
```
