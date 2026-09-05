# Render + Supabase 외부 호환성 검증 가이드

> ⚠️ 이 절차는 **모놀리식 시절**의 일회성 외부 호환성 검증 기록이다. MSA 전환 후 외부 배포는 다중 서비스 구성으로 재작성이 필요하다.

이 절차는 (모놀리식) 앱이 외부 HTTPS 환경에서도 동작하는지 확인하기 위한 일회성 호환성 검증이다. 현재 상시 실행 경로는 [로컬 MSA 실행 가이드](../design/msa/local-run.md)다.

> 2026-08-22 검증에서 Render API health, Supabase/Flyway 연결, Upstash TLS 캐시 조회, CORS·SockJS, Static Site의 API·WebSocket 주소 반영을 확인했다. 다만 Render Free 인스턴스는 512MB 컨테이너 한도를 초과해 지속 실행할 수 없었다. 따라서 이 구성은 재현용 설정으로만 보관하며 자동 배포하지 않는다.

## 구성

| 구성 요소 | 서비스 | 역할 |
|---|---|---|
| Frontend | Render Static Site | Vite 정적 파일 제공 |
| Backend | Render Web Service | Spring Boot API·STOMP·OAuth 콜백 |
| PostgreSQL | Supabase | 영속 데이터 |
| Redis | 외부 Redis 또는 Render Key Value | 캐시·토큰·분산 락 |

`render.yaml`은 Render Blueprint 재현 설정이다. API와 정적 프론트엔드 서비스를 만들 수 있지만, `autoDeployTrigger: off`를 유지한다. 상시 서비스를 만들거나 실제 사용자 트래픽을 받는 용도로 사용하지 않는다.

## 브랜치·실행 원칙

- `dev`는 로컬 개발 통합 브랜치이며, 로컬 Docker Compose가 실제 실행 기준이다.
- `main`은 `dev`에서 승격한 릴리스·배포 설정 기준 브랜치다. Render를 다시 검증할 때만 `main`의 특정 커밋을 수동 동기화한다.
- `feature/* → dev → main` PR 흐름을 유지한다. Render의 자동 배포는 켜지 않는다.

## 배포 전 준비

1. Supabase 프로젝트를 만들고 Database의 connection string을 확인한다. Render 환경에서는 IPv4를 지원하는 Session Pooler 연결을 우선 검토한다.
2. Redis 서비스를 준비한다. 호스트, 포트, 비밀번호, TLS 사용 여부를 확보한다.
3. Render에서 이 저장소의 `main` 브랜치와 루트 `render.yaml`로 Blueprint를 생성한다.
4. API의 `sync: false` 환경 변수를 Render 대시보드에서 입력한다. 비밀값은 Git이나 채팅에 저장하지 않는다.

## 필수 환경 변수

| 변수 | 값 |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Supabase 연결 정보 |
| `DB_SSL_MODE` | `require` |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` | 선택한 Redis 연결 정보 |
| `JWT_SECRET` | Render가 생성하거나 별도 강한 값 지정 |
| `FRONTEND_BASE_URL` | 실제 Render Static Site HTTPS 주소 |
| `CORS_ALLOWED_ORIGINS` | 위와 동일한 단일 HTTPS origin |
| `GOOGLE_*`, `KAKAO_*` | OAuth 검증 시 실제 공급자 자격 증명 |
| `ADMIN_IP_ALLOWLIST` | 운영자 공인 IP 목록 |

서비스 이름이나 custom domain을 바꾸면 `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS`, 프론트의 `VITE_API_BASE_URL`, `VITE_WS_URL`를 같은 실제 주소로 함께 바꾼다.

## OAuth 설정

OAuth를 검증할 경우 공급자 콘솔에 다음 redirect URI를 등록한다.

```text
https://territorial-auction-api.onrender.com/login/oauth2/code/google
https://territorial-auction-api.onrender.com/login/oauth2/code/kakao
```

custom domain을 사용하면 위 API 도메인을 해당 도메인으로 대체한다. OAuth 토큰은 현재 콜백 URL의 query string으로 전달되는 기존 구현이므로, 외부 검증은 테스트 계정으로만 수행하고 브라우저 기록·공유 URL을 남기지 않는다.

## 검증 순서

1. API `https://territorial-auction-api.onrender.com/actuator/health`가 `UP`인지 확인한다.
2. Static Site에서 회원가입·로그인·맵 조회·경매 목록을 확인한다.
3. 두 브라우저 세션으로 입찰과 STOMP 갱신을 확인한다.
4. 관리자 로그인과 TOTP를 확인한다.
5. OAuth는 테스트 계정으로 성공·실패 리디렉션을 각각 확인한다.
6. 검증 후 시드 관리자 환경 변수와 테스트 계정을 제거한다. 서비스를 유지할 필요가 없으면 사용자 확인 후 Render/Supabase/Redis 리소스를 중지·삭제한다.

## 참고

- [Render Blueprint YAML Reference](https://render.com/docs/blueprint-spec)
- [Render environment variables](https://render.com/docs/configure-environment-variables)
- [Supabase PostgreSQL 연결 방식](https://supabase.com/docs/guides/database/connecting-to-postgres)
