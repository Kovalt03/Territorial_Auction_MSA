# Item API (아이템 상점·인벤토리)

> **⚙️ MSA**: 아이템 상점·인벤토리·사용은 **item-service**가 담당한다. 게이트웨이(`/api/v1/items/**`)가 item-service로 라우팅하고 JWT subject를 `X-User-Id`로 주입한다. 구매 시 AP 차감은 user-service `/internal/wallets`, GP 구매권은 combat-service 금고, 무적권 사용은 map-service 소유 검증에 위임한다. 서비스 간 계약: [internal.md](./internal.md).

## 목차

| Method | Endpoint | 기능 | 인증 |
|---|---|---|---|
| GET | `/api/v1/items` | 상점 아이템 목록 조회(보유 수량 포함) | 필수 |
| GET | `/api/v1/items/inventory` | 내 인벤토리 조회(페이지네이션) | 필수 |
| POST | `/api/v1/items/purchase` | 아이템 구매(AP 차감, GP 구매권은 금고 적립) | 필수 |
| POST | `/api/v1/items/use` | 아이템 사용(무적권/공격권 — GP 구매권 사용 불가) | 필수 |

## 아이템 목록 조회

**GET** `/api/v1/items`

상점의 전 아이템과 유저 보유 수량을 반환한다. `item_type`: `INVINCIBILITY` / `ATTACK_NORMAL` / `ATTACK_PRECISION` / `GP_PURCHASE`.

## 인벤토리 조회

**GET** `/api/v1/items/inventory?page={0}&size={20}`

보유 중인 아이템(수량 > 0)을 페이지네이션으로 반환한다.

## 아이템 구매

**POST** `/api/v1/items/purchase`

```json
{ "itemId": 1, "quantity": 1 }
```

- 일일 한도(`daily_limit`) 초과 시 `DAILY_LIMIT_EXCEEDED`.
- 일반 아이템은 인벤토리 지급, `GP_PURCHASE`는 combat-service 금고에 GP 적립(인벤토리 미지급).
- 로컬 지급 후 마지막에 AP 차감(user-service `/internal/wallets`) — 실패 시 트랜잭션 롤백.

## 아이템 사용

**POST** `/api/v1/items/use`

```json
{ "itemId": 1, "targetTerritoryId": 100 }
```

- `INVINCIBILITY`: 대상 영토 소유 검증(map-service) 후 Redis 무적키 설정.
- `ATTACK_NORMAL` / `ATTACK_PRECISION`: combat-service에 공격권 지급 + 수량 차감.
- `GP_PURCHASE`는 사용 불가(`ITEM_NOT_USABLE`).
