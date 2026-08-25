# API 명세서

> Notion 원본: https://www.notion.so/API-3332efa4278d81ca8919eef1a837288c

---

## 공통 규칙

### Base URL

```
http://localhost:8080   # 로컬 개발
```

환경변수: `{{baseUrl}}`

### 인증

JWT Bearer 토큰 방식

```
Authorization: Bearer {accessToken}
```

- AccessToken 만료 시 `/api/v1/auth/refresh` 로 갱신
- RefreshToken은 HttpOnly Cookie로 관리 (`refreshToken`)

### 공통 응답 형식

```json
{
  "status": 200,
  "message": "OK",
  "data": { ... }
}
```

### 공통 에러 응답

```json
{
  "status": 400,
  "message": "에러 메시지",
  "data": null
}
```

### HTTP 상태 코드

| 코드 | 의미 |
|---|---|
| 200 | 성공 |
| 400 | 잘못된 요청 (유효성 검증 실패 등) |
| 401 | 인증 실패 (토큰 없음 또는 만료) |
| 403 | 권한 없음 |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복 등) |
| 500 | 서버 내부 오류 |

---

## API 목록

| 파일 | 도메인 | 엔드포인트 수 |
|---|---|---|
| [auth.md](./auth.md) | 인증 (회원가입·로그인·토큰·중복확인) | 7 |
| [user.md](./user.md) | 유저 프로필·자산·영토·AP 충전 | 10 |
| [map.md](./map.md) | 맵·영토·대륙 | 5 |
| [auction.md](./auction.md) | 경매·입찰·경매 이력 | 6 |
| [building.md](./building.md) | 건물·섬·보관함 | 11 |
| [military.md](./military.md) | 군사·공성전 | 9 |
| [notification.md](./notification.md) | 알림 | 3 |
| [ranking.md](./ranking.md) | 랭킹 | 6 |
| [global-vault.md](./global-vault.md) | 글로벌 금고 (개인 이전) | 2 |
| [payment.md](./payment.md) | 아이템 샵 | 4 |
| [guild.md](./guild.md) | 길드 | 7 |
| [season.md](./season.md) | 시즌 패스 | 3 |
| [tax.md](./tax.md) | 토지세 | 2 |
| [admin.md](./admin.md) | 관리자 페이지 (대륙 영토 구성·유저·경매·시즌·아이템, ROLE_ADMIN) ⬜ | 25 |
| [websocket/](./websocket/README.md) | WebSocket (STOMP) — chat ✅ / auction·map·notification ⬜ | — |
| [errors.md](./errors.md) | 에러 코드 레퍼런스 | — |
