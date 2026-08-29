# WebSocket — Auction

> 구현 상태: `/sub/auction/{auctionId}` 브로드캐스트 ✅ 구현. `/pub/.../bid`(STOMP 입찰) ⬜ 미구현 — 입찰은 REST(`POST /api/v1/auctions/{id}/bids`)로 한다.
>
> **⚙️ MSA**: 클라이언트 WebSocket(`/ws`)은 **모놀리식**이 소유한다(게이트웨이가 `/ws`→모놀리식 라우팅). 입찰은 auction-service(REST)가 처리하고 `auction.bid` 이벤트를 발행하면, 모놀리식 **realtime 허브**가 이를 구독해 `/sub/auction/{auctionId}`로 push한다. 이전 최고 입찰자에겐 `OUTBID` 알림도 이 경로로 발송(이벤트에 previousBidderId 포함).

---

## 채널

| 방향 | Destination | 인증 | 설명 |
|---|---|---|---|
| 클라이언트 → 서버 | `/pub/auction/{auctionId}/bid` | 필수 | 실시간 입찰 (미구현 — REST 사용) |
| 서버 → 클라이언트 | `/sub/auction/{auctionId}` | 불필요 | 경매 실시간 입찰 현황 (이벤트→realtime 허브) |

---

## 메시지 형식

### 입찰 발생 시 브로드캐스트

**Destination**: `/sub/auction/{auctionId}`

새 입찰 발생 시 해당 경매를 구독 중인 모든 클라이언트에 전송.

```json
{
  "auctionId": 1,
  "currentPrice": 2500,
  "bidderId": 9,
  "bidderNickname": "입찰왕",
  "bidAt": "2026-05-08T12:01:00"
}
```

Anti-sniping으로 `endAt`이 연장된 경우 `endAt` 필드 추가.

---

## 발행 위치

`AuctionService.placeBid()` — 입찰 성공 직후 `SimpMessagingTemplate.convertAndSend()` 호출.
