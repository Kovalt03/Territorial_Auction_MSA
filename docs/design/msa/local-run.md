# 로컬 MSA 구동 가이드

> MSA 전환은 **Strangler(한 서비스씩 추출)**로 진행한다. 따라서 로컬에서도 "8개를 한 번에" 띄우지 않는다 — 모놀리식 + 추출된 서비스만 나란히 띄운다. 이 문서는 그 구동 방법과 자원을 가볍게 유지하는 법을 정리한다.
>
> 배경 결정: [CI/CD·테스트 정책](../../operations/ci-cd-policy.md) · [아키텍처](../architecture.md). 서비스 추출 설계는 별도 문서(`design/msa-*-extraction.md`)에서 다룬다.

---

## 1. 개념 — 무엇이 같이 뜨는가

전환 단계별로 `docker compose`가 띄우는 것이 달라진다.

**단계 0 (현재, 모놀리식)** — `docker-compose.yml`
```
redis · postgres · backend(단일) · frontend
```

**단계 1 (auction-service 추출 후)** — `docker-compose.msa.yml`
```
redis                     ← 공유 (Redisson 분산락 · 서비스 간 이벤트)
monolith-postgres         ← 기존 backend 전용
auction-postgres          ← auction-service 전용 (신규)
backend(모놀리식)          ← auction 제외 전 도메인
auction-service           ← auctions · bids 만
frontend
(gateway)                 ← 서비스 2개 이상부터 도입 검토
```

핵심:
- **DB는 서비스당 컨테이너**(결정 사항). `auction-service`는 `auction-postgres`에만 붙고, 모놀리식 DB를 절대 직접 조회하지 않는다.
- `auction-service`는 `userId`·`territoryId`를 **FK가 아닌 ID 값**으로만 보관한다. 다른 도메인 데이터가 필요하면 모놀리식에 **REST 호출** 또는 **Redis 이벤트**로 얻는다.
- 서비스 간 통신은 **컨테이너 이름 DNS**로 해결된다 — `http://backend:8080`, `http://auction-service:8080`. 로컬에선 Eureka 등 서비스 디스커버리 불필요.

---

## 2. 자원을 가볍게 유지하는 법

"로컬에서 다 띄우면 무겁다"의 실질적 해소책.

1. **필요한 것만 띄운다.** 경매만 작업하면 모놀리식 전체가 아니라 의존 대상만:
   ```bash
   docker compose -f docker-compose.msa.yml up redis auction-postgres auction-service
   ```
   compose는 명시한 서비스 + 그 `depends_on`만 기동한다.
2. **모놀리식이 "나머지 세계" 역할을 한다.** Strangler 초기엔 통신 상대가 모놀리식 하나뿐이라, `backend + auction-service` 2개 앱이면 통합 흐름을 전부 재현할 수 있다(≈2~3GB).
3. **전체가 다 떠야 하는 검증은 로컬이 아니라 CI 러너에 맡긴다.** push하면 러너가 `compose up` → 스모크 → 폐기한다([정책 5.4](../../operations/ci-cd-policy.md#54-msa-검증-전략-테스트-피라미드)).
4. **인터랙티브하게 무거운 스택이 필요하면 Codespaces**(클라우드 개발환경)로 노트북 부하 0.

> 참고 사용량: 앱 1개(Spring Boot) ≈ 300~500MB, postgres 1개 ≈ 50~150MB, redis ≈ 20MB. 단계 1(앱 2 + PG 2 + redis + frontend)은 4GB Docker 메모리에서 충분히 돈다.

---

## 3. compose 파일 구성 (목표 스켈레톤)

> `docker-compose.msa.yml`은 **단계 1 착수 시 생성**한다. 아래는 그 목표 형태다. `auction-service/` 디렉토리와 Dockerfile이 만들어진 뒤 유효해진다.

```yaml
services:
  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]

  monolith-postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: territorial_auction
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes: [monolith-pg:/var/lib/postgresql/data]

  auction-postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: auction
      POSTGRES_USER: auction
      POSTGRES_PASSWORD: auction
    volumes: [auction-pg:/var/lib/postgresql/data]

  backend:                      # 모놀리식 (auction 제외)
    build: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: local
      DB_HOST: monolith-postgres
      REDIS_HOST: redis
    depends_on:
      monolith-postgres: { condition: service_healthy }
      redis: { condition: service_healthy }

  auction-service:
    build: ./services/auction-service
    environment:
      SPRING_PROFILES_ACTIVE: local
      DB_HOST: auction-postgres
      REDIS_HOST: redis
      MONOLITH_BASE_URL: http://backend:8080   # AP 차감 등 동기 호출 대상
    depends_on:
      auction-postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
      backend: { condition: service_started }

  frontend:
    build: ./frontend
    ports: ["3000:80"]
    depends_on: [backend, auction-service]

volumes:
  monolith-pg:
  auction-pg:
```

**주의점**
- 각 서비스는 **자기 Flyway 마이그레이션**을 자기 DB에 적용한다. 마이그레이션 경로가 서비스별로 분리돼야 한다.
- 서비스별로 컨테이너 포트는 각자 `8080`이어도 무방하다(네트워크가 분리됨). 호스트로 노출할 필요가 있는 것만 `ports:` 매핑한다.
- 개발 편의상 컨테이너 시각은 `TZ: Asia/Seoul`로 맞춘다(단일 타임존 운영 규칙).

---

## 4. 프론트엔드 진입점 — gateway는 언제

지금 프론트는 vite 프록시로 `/api`, `/ws`를 백엔드 하나로 보낸다. 서비스가 둘이 되면 "어떤 요청이 어느 서비스로 가나"를 정할 곳이 필요하다.

| 서비스 수 | 진입점 | 방법 |
|---|---|---|
| 1 (현재) | 모놀리식 직결 | vite 프록시 그대로 |
| 2~ | **경로 기반 라우팅** | 우선 **vite 프록시/nginx**로 `/api/v1/auctions/**` → auction-service, 나머지 → backend |
| 다수 + 인증·레이트리밋 공통화 필요 | **API Gateway** | Spring Cloud Gateway 도입 검토 |

Gateway(Spring Cloud Gateway)는 학습 가치가 있으나 **처음부터 넣지 않는다** — 서비스 2개는 nginx/프록시 경로 분기로 충분하고, 컨테이너·복잡도만 늘어난다. "공통 인증·레이트리밋·집계가 여러 서비스에 반복될 때"를 도입 트리거로 잡는다.

---

## 5. 구동·확인 명령

```bash
# 전체 기동 (단계 1)
docker compose -f docker-compose.msa.yml up -d --build

# 경매만 작업 — 필요한 것만
docker compose -f docker-compose.msa.yml up redis auction-postgres auction-service backend

# 상태·헬스체크
docker compose -f docker-compose.msa.yml ps
docker compose -f docker-compose.msa.yml exec auction-service wget -qO- http://localhost:8080/actuator/health

# 특정 서비스 로그
docker compose -f docker-compose.msa.yml logs -f auction-service

# 서비스 간 통신 확인 (auction → backend DNS 해석)
docker compose -f docker-compose.msa.yml exec auction-service wget -qO- http://backend:8080/actuator/health

# 중지(볼륨 유지)
docker compose -f docker-compose.msa.yml down
```

`down -v`는 서비스별 PG 볼륨을 모두 삭제한다 — 폐기용 검증 환경이 아니면 쓰지 않는다.

---

## 6. 자주 겪을 문제

| 증상 | 원인 | 해결 |
|---|---|---|
| `auction-service`가 backend를 못 찾음 | `MONOLITH_BASE_URL`이 `localhost` | 컨테이너 간엔 서비스명 사용 — `http://backend:8080` |
| 부팅 시 DB 연결 실패 | PG 준비 전 앱 기동 | `depends_on` + `condition: service_healthy`, 앱에 재시도 |
| 마이그레이션 충돌 | 두 서비스가 같은 DB에 Flyway 적용 | DB를 서비스별로 분리했는지 확인(경계 위반) |
| 노트북이 느려짐 | 전체 스택 상시 기동 | [2절](#2-자원을-가볍게-유지하는-법) — 필요한 것만 띄우기 |

---

## 7. 관련 문서

- [CI/CD·테스트 정책](../../operations/ci-cd-policy.md) — push 시 검증 파이프라인, 계약 테스트
- [모놀리식 로컬 운영](../../operations/local-production.md) — 단일 스택 실행(단계 0)
- [아키텍처·MSA 전환 기준](../architecture.md) — 8개 Bounded Context 경계
