# Notification API

> 구현 상태: 🔲 미구현

---

## 알림 목록 조회

**GET** `/api/v1/notifications?page={0}&size={20}`

**Authorization**: Bearer `{{accessToken}}` (필수)

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": {
    "unreadCount": 3,
    "notifications": [
      {
        "notificationId": 1,
        "type": "OUTBID",
        "message": "테스트영토(2,3)에 상회 입찰이 발생했습니다. 현재가: 2,500 AP",
        "isRead": false,
        "createdAt": "2026-04-27T12:00:00"
      }
    ]
  }
}
```

### 알림 타입

| type | 설명 |
|---|---|
| `OUTBID` | 상회 입찰 — 내 입찰이 넘겨졌을 때 |
| `AUCTION_WIN` | 경매 낙찰 성공 |
| `AUCTION_LOSE` | 경매 낙찰 실패 |
| `SIEGE_ALERT` | 공성전 공격 선언 수신 |
| `SIEGE_RESULT` | 공성전 결과 |
| `TAX_CHARGED` | 토지세 차감 |
| `INCOME` | 영토 생산 정산 |

출처: `notification_logs`  
`unreadCount`: Redis `notification:unread:{userId}`

### 남은작업
- 서비스 구현
- Redis unread 카운터 연동

---

## 알림 읽음 처리

**PATCH** `/api/v1/notifications/{notificationId}/read`

**Authorization**: Bearer `{{accessToken}}` (필수)

`notification_logs.is_read` → `true` 로 변경  
Redis `notification:unread:{userId}` DECR

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
| 404 | NOTIFICATION_NOT_FOUND | 알림 없음 |
| 403 | FORBIDDEN | 본인 알림 아님 |

### 남은작업
- 서비스 구현
- Redis DECR 연동

---

## 알림 전체 읽음

**PATCH** `/api/v1/notifications/read-all`

**Authorization**: Bearer `{{accessToken}}` (필수)

`notification_logs`에서 `is_read=false` → `true` 벌크 UPDATE  
Redis `notification:unread:{userId}` → 0으로 SET

### Response (200 OK)

```json
{
  "status": 200,
  "message": "OK",
  "data": null
}
```

### 남은작업
- 서비스 구현
- Redis SET 0 연동
