# 운영 배포 가이드 (MSA)

> ✅ **MSA 전환 완료 기준.** 로컬 풀스택 실행의 정본은 `docker-compose.msa.yml`이다([로컬 MSA 구동](../design/msa/local-run.md)). 이 문서는 그 위에 **운영(외부 노출) 배포** 시 필요한 하드닝·초기화·백업을 정의한다.
>
> ⚠️ **운영 전용 오버레이는 아직 작성되지 않았다.** 구 모놀 기반 production compose는 backend와 함께 제거됐다. 운영 배포는 아래 설계대로 `docker-compose.msa.yml`에 **production 오버레이**(예: `docker-compose.prod.yml` — GHCR `image:` 참조 + 시크릿 주입 + 볼륨 영속)를 새로 작성해 얹는다 — 파이프라인은 [ci-cd-policy §6](./ci-cd-policy.md#6-cd-정책-단일-vps--docker-compose-설계-미구현).

---

## 1. 구성 개요

운영 스택은 로컬과 같은 토폴로지다 — 서비스 12개 + 서비스별 PostgreSQL 10개 + 공유 Redis·Kafka. 다른 점은:

| 항목 | 로컬(`docker-compose.msa.yml`) | 운영(오버레이) |
|---|---|---|
| 이미지 | `build:`(로컬 빌드) | `image: ghcr.io/<org>/<service>:<sha>` (호스트는 pull만) |
| 공개 포트 | 게이트웨이 8090, 서비스 디버그 포트 노출 | **게이트웨이(HTTPS)만** 공개, 서비스·DB는 내부 네트워크 전용 |
| 시크릿 | 루트 `.env`(gitignore) | 호스트 시크릿 스토어/`.env.production`(레포 밖) |
| 볼륨 | 개발 볼륨 | 영속 볼륨 + 백업 대상 |
| 프론트 | Vite dev(3000) | 정적 빌드 산출물을 게이트웨이/리버스 프록시 뒤에서 서빙 |

원칙:
- **게이트웨이(:8090)만 외부에 노출**한다. 개별 서비스와 각 PostgreSQL은 Docker 내부 네트워크에서 컨테이너명 DNS로만 통신한다.
- 서비스는 **자기 DB에만** 붙는다. 서비스 경계를 넘는 데이터는 `/internal/**` 계약과 Kafka/Redis 이벤트로 얻는다.
- 각 서비스는 부팅 시 **자기 Flyway 마이그레이션**을 자기 DB에 적용·검증한다(Hibernate는 검증만, 스키마 변경 금지).

## 2. 사전 조건

- Docker Engine + Compose v2가 설치된 단일 VPS(또는 스테이징 호스트)
- 전 스택 상시 기동은 무겁다 — 서비스 12 + DB 10 기준 **최소 8~12GB RAM 권장**
- HTTPS 종단(리버스 프록시/로드밸런서)과 실제 도메인
- GHCR pull 권한(`GITHUB_TOKEN` 또는 배포용 토큰)

## 3. 기동 (오버레이 작성 후)

```bash
# 운영 오버레이(docker-compose.prod.yml, 작성 후)로 pull & 기동 (이미지 참조 + 시크릿 주입)
docker compose -f docker-compose.msa.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.msa.yml -f docker-compose.prod.yml up -d

# 상태·헬스체크 (게이트웨이 + 핵심 서비스)
docker compose -f docker-compose.msa.yml -f docker-compose.prod.yml ps
docker compose ... exec gateway        wget -qO- http://localhost:8080/actuator/health
docker compose ... exec auction-service wget -qO- http://localhost:8080/actuator/health
```

각 서비스는 최초 빈 DB에서 Flyway로 스키마와 초기 게임 데이터(시더)를 생성한다. 이후 기동에서는 마이그레이션 이력을 검증한다.

## 4. 관리자 계정 부트스트랩

관리 콘솔은 **admin-service**가 자체 인증(로그인 + TOTP)으로 서빙한다. 운영 DB에는 기본 관리자 계정이 없다 — admin-service의 부트스트랩 절차(초기 관리자 시드 환경변수)로 최초 계정을 만든 뒤, 로그인·TOTP 설정을 끝내고 시드 값을 제거·재기동한다. IP 허용 목록(`ADMIN_IP_ALLOWLIST` 계열)은 관리자 로그인 허용 IP를 쉼표로 지정하고, 빈 값(전체 허용)은 외부 노출 환경에서 쓰지 않는다.

> 계정 이관·시드 스크립트 세부는 admin-service 배포 문서를 따른다([admin-dashboard](../design/admin-dashboard.md)).

## 5. 백업과 복구 (서비스 DB별)

각 서비스가 **자기 DB를 소유**하므로 백업도 DB 컨테이너별로 수행한다. 특정 서비스만 복구하는 것도 가능하다(단, 서비스 간 상태는 이벤트로 이어지므로 시점 정합에 유의).

```bash
# 예: auction DB 백업
mkdir -p backups
docker compose ... exec -T auction-postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > backups/auction-$(date +%F).dump

# 전체 백업은 서비스별로 반복 (auction·user·combat·map·social·notification·item·season·ranking·admin)
```

복구는 현재 데이터를 덮어쓰므로 대상 DB·파일 확인과 서비스 중지 후에만:

```bash
docker compose ... exec -T auction-postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
  < backups/restore-target.dump
docker compose ... restart auction-service
```

백업 파일은 Git에 올리지 않고 별도 안전한 위치에 보관한다.

## 6. 운영 명령

```bash
# 특정 서비스 로그
docker compose ... logs -f auction-service

# 변경 서비스만 롤아웃 (이전 SHA 태그로 재지정 시 롤백)
docker compose ... pull auction-service && docker compose ... up -d auction-service

# 중지(볼륨 유지)
docker compose ... down
```

`down -v`는 **모든 서비스 DB·Redis 볼륨을 삭제**한다 — 폐기용 검증 환경이 아니면 쓰지 않는다.

## 7. 외부 배포 전 필수 점검

- HTTPS 종단·실제 도메인 준비, **OAuth 공급자 redirect URI를 게이트웨이/user-service 콜백 주소로 갱신**(OAuth는 user-service 소유).
- `JWT_SECRET`, `INTERNAL_API_SECRET`, 각 DB 비밀번호, OAuth 시크릿을 배포 플랫폼의 비밀 환경 변수에만 저장(레포·채팅 금지).
- 게이트웨이만 공개, 서비스·DB 포트는 내부 전용으로 닫는다.
- admin IP 허용 목록 설정, 시드 관리자 환경 변수 제거.
- 서비스별 DB 백업·복구 절차를 실제 한 번 검증.
- 모니터링은 각 서비스 `/actuator/health` — 운영 프로파일은 그 외 Actuator endpoint를 노출하지 않는다.
- 라이브 스모크: 회원가입/로그인 → 맵 조회 → 입찰 → WS 갱신 → 관리자 로그인/TOTP → OAuth 리디렉션.

---

## 관련 문서

- [로컬 MSA 구동](../design/msa/local-run.md) — 개발 풀스택(정본 compose)
- [CI/CD·배포 파이프라인](./ci-cd-policy.md) — GHCR 빌드·푸시·VPS 배포 설계
- [서비스 간 계약](../api/internal.md) · [아키텍처](../design/architecture.md)
