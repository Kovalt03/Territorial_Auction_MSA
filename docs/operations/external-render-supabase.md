# 외부 관리형 플랫폼 배포 (MSA)

> ✅ **MSA 전환 완료 기준.** 아래 §1은 모놀리식 시절 Render + Supabase 외부 호환성을 **한 번 검증한 기록**이고, §2 이후는 MSA에서 외부 배포가 어떻게 달라지는지와 현실적 선택지를 정리한다. MSA의 정식 배포 경로는 **단일 VPS + `docker-compose.msa.yml` 오버레이**다([운영 배포](./local-production.md) · [CI/CD §6](./ci-cd-policy.md#6-cd-정책-단일-vps--docker-compose-설계-미구현)).

---

## 1. 모놀리식 Render 검증 (역사적 기록)

> 2026-08-22, **모놀리식(backend 1개 서비스)** 기준 일회성 호환성 검증. 지금은 재현되지 않으며 참고용으로만 남긴다.

당시 구성은 단일 백엔드였다:

| 구성 요소 | 서비스 | 역할 |
|---|---|---|
| Frontend | Render Static Site | Vite 정적 파일 |
| Backend | Render Web Service **1개** | Spring Boot API·STOMP·OAuth |
| PostgreSQL | Supabase | 영속 데이터(단일 DB) |
| Redis | 외부 Redis / Render Key Value | 캐시·토큰·분산 락 |

검증에서 Render API health, Supabase/Flyway 연결, Upstash TLS 캐시, CORS·SockJS, Static Site의 API·WS 주소 반영을 확인했다. 다만 **Render Free 512MB 한도를 초과**해 지속 실행은 불가했다 — 단일 모놀조차 무료 인스턴스에 맞지 않았다. 당시의 재현 설정(`render.yaml`, 서비스 1개 전제)은 모놀 폐기와 함께 제거됐다 — MSA(서비스 12개)에는 그대로 쓸 수 없기 때문이다.

## 2. MSA에서 무엇이 달라지나

모놀은 "웹 서비스 1개 + DB 1개"였지만 MSA는:

- **웹 서비스 12개**(게이트웨이 + 11 도메인/실시간 서비스)
- **관리형 PostgreSQL 10개**(서비스별 전용 DB)
- **공유 Redis 1개** + **Kafka 클러스터 1개**(durable 이벤트 백본)

관리형 PaaS(Render 등)에서 이를 재현하려면 웹 서비스 12개 + 관리형 DB 10개 + Redis + Kafka를 각각 프로비저닝해야 한다 — **무료/취미 티어로는 불가능**하고, 유료로도 개인 프로젝트에는 과도하다. 특히 관리형 Kafka는 대부분 별도 유료 서비스다.

## 3. 현실적 배포 선택지

| 방식 | 적합성 | 비고 |
|---|---|---|
| **단일 VPS + docker-compose** | ✅ **권장(정식 경로)** | 전 스택을 한 호스트에 `docker-compose.msa.yml` 오버레이로. 이미지는 GHCR pull. [운영 배포](./local-production.md) |
| 관리형 PaaS(서비스별 배포) | △ 비용·복잡도 과다 | 웹 12 + 관리형 DB 10 + Redis + Kafka. 학습·데모 목적이 아니면 비권장 |
| 관리형 K8s | △ 개인 프로젝트엔 과함 | 확장이 실제 필요해질 때 재검토 |

## 4. 외부 배포 시 공통 준비 (플랫폼 무관)

어느 방식이든 아래는 동일하게 필요하다:

- **HTTPS 종단 + 실제 도메인** — 외부에는 게이트웨이만 노출한다.
- **OAuth redirect URI 갱신** — OAuth는 user-service 소유. 콜백 주소를 실제 도메인/게이트웨이 경로로 등록한다.
  ```text
  https://<도메인>/login/oauth2/code/google
  https://<도메인>/login/oauth2/code/kakao
  ```
- **시크릿**: `JWT_SECRET`, `INTERNAL_API_SECRET`, 서비스별 DB 자격증명, OAuth 시크릿을 플랫폼 비밀 스토어에만. 레포·채팅 금지.
- **프론트 주소 정합**: `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS`, 프론트 `VITE_API_BASE_URL`·`VITE_WS_URL`을 같은 실제 주소로 함께 바꾼다.
- **데이터 영속**: 서비스별 DB 백업·복구 절차 검증([운영 배포 §5](./local-production.md#5-백업과-복구-서비스-db별)).
- **라이브 스모크**: 회원가입/로그인 → 맵 → 입찰 → 2세션 STOMP 갱신 → 관리자 로그인/TOTP → OAuth 성공·실패 리디렉션.

## 5. 관리형 데이터 스토어를 쓸 경우

VPS에서도 데이터 계층만 관리형(Supabase·Upstash 등)으로 빼는 하이브리드는 가능하다:

- **PostgreSQL(Supabase 등)**: 서비스별로 **독립 데이터베이스/스키마**를 분리해 프로비저닝한다 — 서비스가 남의 DB에 붙지 않는 경계를 그대로 유지. Render/외부에서는 IPv4 Session Pooler 연결을 우선 검토, `DB_SSL_MODE=require`.
- **Redis(Upstash 등)**: 공유 1개. TLS 사용 여부 확인.
- **Kafka**: 관리형이 부담이면 VPS 내 단일 브로커로 유지하는 편이 현실적이다.

## 참고

- [운영 배포 가이드(MSA)](./local-production.md) · [CI/CD·배포](./ci-cd-policy.md) · [아키텍처](../design/architecture.md)
- [Render Blueprint YAML](https://render.com/docs/blueprint-spec) · [Supabase PostgreSQL 연결](https://supabase.com/docs/guides/database/connecting-to-postgres)
