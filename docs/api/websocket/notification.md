# WebSocket — Notification

> 구현 상태: ⬜ 미구현

---

## 채널

| Destination | 인증 | 설명 |
|---|---|---|
| `/sub/user/{userId}/notification` | 필수 | 개인 알림 수신 |
| `/sub/user/{userId}/siege-alert` | 필수 | 공성전 공격 선언 알림 |
| `/sub/user/{userId}/auction-result` | 필수 | 경매 낙찰/실패 알림 |

---

## 메시지 형식

### 개인 알림

**Destination**: `/sub/user/{userId}/notification`

```json
{
  "notificationId": 42,
  "type": "OUTBID",
  "message": "테스트영토(2,3)에 상회 입찰이 발생했습니다. 현재가: 2,500 AP",
  "createdAt": "2026-05-08T12:01:00"
}
```

| `type` | 설명 | 발행 위치 |
|---|---|---|
| `OUTBID` | 내 입찰이 상회 입찰로 넘겨짐 | `AuctionService.placeBid()` |
| `AUCTION_WIN` | 경매 낙찰 성공 | `AuctionLifecycleService.settleAuction()` |
| `AUCTION_LOSE` | 경매 낙찰 실패 | `AuctionLifecycleService.settleAuction()` |
| `SIEGE_ALERT` | 공성전 공격 선언 수신 | 공성전 선언 시 |
| `SIEGE_RESULT` | 공성전 결과 | 공성전 종료 시 |
| `TAX_CHARGED` | 토지세 차감 | 토지세 정산 시 |
| `INCOME` | 영토 생산 정산 | 정산 주기마다 |
| `GUILD_JOIN_REQUEST` | 길드 가입 신청 (길드장 수신) | 가입 신청 시 |
| `GUILD_JOIN_APPROVED` | 길드 가입 승인 (신청자 수신) | 승인 시 |

---

### 공성전 공격 선언 알림

**Destination**: `/sub/user/{userId}/siege-alert`

내 영토에 공성전이 선언되면 방어자에게 전송.

```json
{
  "siegeId": 1,
  "attackerNickname": "공격왕",
  "targetTerritoryId": 10,
  "attackZone": 2,
  "resolveAt": "2026-05-08T15:30:00"
}
```

---

### 경매 결과 알림

**Destination**: `/sub/user/{userId}/auction-result`

경매 종료 시 낙찰자 및 낙찰 실패 입찰자 각각에게 전송.

```json
{
  "auctionId": 1,
  "type": "AUCTION_WIN",
  "territoryId": 10,
  "finalPrice": 3000
}
```
