# WebSocket — Map

> 구현 상태: ✅ 구현됨

---

## 채널

| 방향 | Destination | 인증 | 설명 |
|---|---|---|---|
| 서버 → 클라이언트 | `/sub/map/update` | 불필요 | 영토 상태 변경 브로드캐스트 |

---

## 메시지 형식

**Destination**: `/sub/map/update`

경매 낙찰·취소·점유 만료 또는 공성전 승리로 영토 상태가 변경된 뒤 전송한다.

```json
{
  "territoryId": 10,
  "coordX": 12,
  "coordY": 7,
  "ownerId": 5,
  "ownerNickname": "픽셀전사",
  "status": "OCCUPIED"
}
```

| 필드 | 타입 | 설명 |
|---|---|
| `territoryId` | number | 변경된 영토 ID |
| `coordX`, `coordY` | number | 50×50 맵 좌표 |
| `ownerId` | number \| null | 점유자 ID. `IDLE`일 때 `null` |
| `ownerNickname` | string \| null | 점유자 닉네임. `IDLE`일 때 `null` |
| `status` | `OCCUPIED` \| `IDLE` | 변경 후 영토 상태 |

---

## 발행 위치

- `AuctionLifecycleService` — 낙찰, 관리자 강제 취소, 점유 만료 처리 후 `afterCommit`에서 발행
- `SiegeService` — 공성 승리로 영토 인계 후 `afterCommit`에서 발행
