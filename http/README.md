# API 테스트 (HTTP Client · Postman)

게이트웨이(**:8090**) 경유 **전체 public API**를 테스트하는 요청 모음. 모든 요청은 게이트웨이로 보내고, 게이트웨이가 각 서비스로 라우팅하며 JWT subject를 `X-User-Id`로 주입한다.

> **커버리지**: 컨트롤러의 public 엔드포인트 **163개 전부** 수록(자동 생성·검증). 서비스 간 `/internal/**` 계약(93개)은 게이트웨이로 노출되지 않으므로 이 컬렉션 범위 밖 — [internal.md](../docs/api/internal.md) 참고.

## 구성

| 파일 | 도메인 |
|---|---|
| `auth.http` `user.http` | 인증·회원·프로필·위시리스트 (user-service) |
| `auction.http` | 경매·입찰 (auction-service) |
| `map.http` `tax.http` | 맵·영토·대륙·색상·수입·토지세 (map-service) |
| `building.http` `military.http` `global-vault.http` | 건물·섬·인벤토리·병력·공성·금고 (combat-service) |
| `item.http` | 아이템 상점·인벤토리 (item-service) |
| `season.http` | 시즌패스·미션 (season-service) |
| `ranking.http` | 랭킹 (ranking-service) |
| `chat.http` `guild.http` | 채팅·소셜·길드 (social-service) |
| `notification.http` | 알림 (notification-service) |
| `admin.http` | 관리 콘솔 (admin-service) |

`Territorial-Auction.postman_collection.json` — 위 전체를 도메인 폴더로 묶은 Postman 컬렉션(163 요청).

## HTTP Client (IntelliJ Ultimate)

`.http` 파일을 열고 각 요청 옆 ▶ 로 실행. 환경은 `http-client.env.json`(`local` → `baseUrl=http://localhost:8090`)에서 관리.

1. `POST /api/v1/auth/signup` → 회원가입
2. `POST /api/v1/auth/login` → 응답의 accessToken을 `http-client.env.json`의 `accessToken`에 넣기
3. 이후 보호 엔드포인트는 `Authorization: Bearer {{accessToken}}` 자동 사용

## Postman

1. `Territorial-Auction.postman_collection.json` import
2. 컬렉션 변수 `baseUrl`(기본 `http://localhost:8090`)·`accessToken` 확인
3. Auth 폴더에서 로그인 → 받은 토큰을 `accessToken` 변수에 저장 → 다른 폴더 실행

## 관리자(admin) 인증

`admin.http` / Postman `Admin` 폴더의 엔드포인트는 **admin-service 자체 로그인**이 필요하다:
`POST /api/v1/admin/auth/login`(+ TOTP)으로 받은 관리자 토큰을 `accessToken`에 넣어 사용한다(일반 유저 토큰과 별개).

## 커버리지 · 코드↔문서↔컬렉션 대조 (2026-09-09)

컨트롤러 전수 대조 결과. **`.http`/Postman은 코드와 100% 일치**, 문서(`docs/api`)는 63개 뒤처져 있다.

| 서비스 | 코드 | .http/Postman | 문서 | 미문서화 |
|---|---:|---:|---:|---:|
| admin | 58 | 58 | 15 | **43** |
| auction | 6 | 6 | 6 | 0 |
| combat | 37 | 37 | 30 | 7 |
| item | 4 | 4 | 0 | 4 |
| map | 7 | 7 | 7 | 0 |
| notification | 3 | 3 | 3 | 0 |
| ranking | 5 | 5 | 3 | 2 |
| season | 7 | 7 | 3 | 4 |
| social | 16 | 16 | 13 | 3 |
| user | 20 | 20 | 20 | 0 |
| **합계** | **163** | **163** | **100** | **63** |

`/internal/**`(서비스 간 계약) 93개는 게이트웨이로 노출되지 않아 이 컬렉션 범위 밖 — [internal.md](../docs/api/internal.md).

**문서 갱신 필요(코드엔 있으나 미기재)**: admin 43(대부분 admin-service 추출 #40 이전 설계라 `admin.md` 재작성 필요), combat 7(building-shop·repair-all·island/harvest 등), item 4(`item.md` 표 자체 없음), season 4(미션·레벨업·보상 수령), social 3(채팅 메시지·친구), ranking 2(대륙·트로피). 상세는 각 서비스 컨트롤러 대비 `docs/api/*.md` 참고.

## 유지보수

컨트롤러에서 생성했다. 엔드포인트 추가/변경 시 해당 `.http`에 요청을 넣고 Postman 컬렉션도 함께 갱신한다.
