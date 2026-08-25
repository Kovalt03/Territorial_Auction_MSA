# 로컬 운영 실행 가이드

이 구성은 단일 Docker Compose로 프론트엔드(Nginx), 백엔드(Spring Boot), PostgreSQL, Redis를 실행한다. 개발용 `docker-compose.yml`과 데이터 볼륨을 공유하지 않는다.

## 사전 조건

- Docker Desktop 또는 Docker Engine + Compose v2
- 최소 4 GB의 Docker 메모리 권장
- 포트 `3000`이 비어 있어야 함(필요하면 `APP_PORT`로 변경)

## 최초 설정과 기동

```bash
cp backend/.env.production.example backend/.env.production
# backend/.env.production에서 JWT_SECRET, DB_PASSWORD, POSTGRES_PASSWORD를 서로 다른 강한 값으로 변경
docker compose -f docker-compose.production.yml up -d --build
```

브라우저는 `http://localhost:3000`에서 연다. 백엔드는 호스트에 직접 공개하지 않으며 프론트엔드의 `/api/`와 `/ws/` 프록시를 통해서만 접근한다.

정상 기동 확인:

```bash
docker compose -f docker-compose.production.yml ps
docker compose -f docker-compose.production.yml exec backend wget -qO- http://localhost:8080/actuator/health
```

백엔드는 최초 빈 DB에서 Flyway `V1` 스키마 마이그레이션과 초기 게임 데이터를 생성한다. 이후 기동에서는 마이그레이션 이력을 검증하고 Hibernate는 스키마를 변경하지 않고 검증만 한다.

## 관리자 계정

운영 DB에는 기본 관리자 계정이 없다. 최초 한 번만 `backend/.env.production`에 아래 두 값을 설정하여 부트스트랩한 뒤, 관리자 로그인과 TOTP 설정을 끝내면 두 값을 제거하고 재기동한다.

```dotenv
ADMIN_SEED_EMAIL=operator@example.com
ADMIN_SEED_PASSWORD=long-unique-password
```

`ADMIN_IP_ALLOWLIST`에는 관리자 로그인 허용 IP를 쉼표로 구분해 설정한다. 빈 값은 모든 IP를 허용하므로 외부에 노출하는 환경에서는 사용하지 않는다.

## 백업과 복구

백업 파일은 Git에 올리지 않는 별도 안전한 위치에 보관한다.

```bash
mkdir -p backups
docker compose -f docker-compose.production.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > backups/territorial-$(date +%F).dump
```

복구는 현재 데이터를 덮어쓸 수 있으므로 서비스 중지와 백업 검증 후에만 수행한다. 대상 DB와 파일을 확인한 뒤 다음 명령을 사용한다.

```bash
docker compose -f docker-compose.production.yml exec -T postgres \
  sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < backups/restore-target.dump
```

복구 뒤에는 `docker compose -f docker-compose.production.yml restart backend`를 실행하고 health endpoint를 다시 확인한다.

## 운영 명령

```bash
# 로그 보기
docker compose -f docker-compose.production.yml logs -f backend

# 중지(데이터 볼륨 유지)
docker compose -f docker-compose.production.yml down

# 포트 변경 예: http://localhost:8088
APP_PORT=8088 docker compose -f docker-compose.production.yml up -d
```

`down -v`는 PostgreSQL과 Redis의 운영 데이터를 삭제한다. 폐기할 검증 환경이 아닌 이상 사용하지 않는다.

## 외부 배포 전 필수 점검

- HTTPS 종단과 실제 도메인을 준비하고 OAuth 공급자 리디렉션 URI를 갱신한다.
- `JWT_SECRET`, DB 비밀번호, OAuth 시크릿을 배포 플랫폼의 비밀 환경 변수에만 저장한다.
- `ADMIN_IP_ALLOWLIST`를 설정하고 시드 관리자 환경 변수는 제거한다.
- DB 백업과 복구 절차를 실제 한 번 검증한다.
- `/actuator/health`만 모니터링 경로로 사용한다. production profile은 다른 Actuator endpoint를 노출하지 않는다.
