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

**단계 1 (auction-service 추출 후, 현재)** — `docker-compose.msa.yml`
```
redis                     ← 공유 (Redisson 분산락 · 서비스 간 이벤트 버스)
postgres                  ← 모놀리식 전용 (호스트 5432)
auction-postgres          ← auction-service 전용 (호스트 5433)
backend(모놀리식)          ← auction 제외 전 도메인      (호스트 8080)
auction-service           ← 경매·입찰·이력             (호스트 8082)
gateway                   ← /api/v1/auctions→auction-service, 그 외→모놀리식 (호스트 8090)
frontend                  ← 게이트웨이로 프록시          (호스트 3000)
```
> 게이트웨이는 도입됨(1단계에 포함). 프론트 API/WS 프록시 대상은 **게이트웨이(8090)**다.

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

## 3. compose 파일 구성 (실제)

> `docker-compose.msa.yml`은 리포 루트에 **존재**한다. 아래는 실제 구성 요약 — 전체는 파일 참조.

| 서비스 | 이미지/빌드 | 호스트 포트 | 요점 |
|---|---|---|---|
| `redis` | redis 7 | 6379 | **공유** 이벤트 버스 + 캐시 |
| `postgres` | 모놀리식 DB | 5432 | `territorial_auction` |
| `auction-postgres` | auction DB(별도) | 5433 | `auction`(Flyway 소유) |
| `backend` | `./backend` | 8080 | `AUCTION_SERVICE_BASE_URL`로 admin `/internal` 호출 |
| `auction-service` | `context: .`(루트) | 8082 | `MONOLITH_BASE_URL=http://backend:8080` |
| `gateway` | `./services/gateway` | 8090 | `JWT_SECRET`은 `backend/.env` 공유, 라우팅 |
| `frontend` | node:20 | 3000 | `API_TARGET=http://gateway:8080` |

**주의점**
- **DB 분리**: `postgres`(모놀리식) / `auction-postgres`(auction-service) 별도 컨테이너·볼륨. auction-service는 모놀리식 DB를 직접 조회하지 않는다.
- **공유 redis 단일 인스턴스**: 서비스 간 이벤트(auction.*)가 오가려면 **동일 인스턴스**여야 한다(분리 redis면 이벤트 유실).
- 각 서비스는 **자기 Flyway 마이그레이션**을 자기 DB에 적용한다.
- **auction-service 이미지는 self-contained 빌드**: 빌드 컨텍스트가 리포 루트(`context: .`)이고, Dockerfile이 이미지 안에서 공유 라이브러리 `common`을 `mavenLocal`에 발행한 뒤 빌드한다 → **GitHub Packages PAT 없이** 빌드된다. (`.dockerignore`로 컨텍스트 경량화)
- 컨테이너 시각은 `TZ: Asia/Seoul`(단일 타임존).

---

## 4. 프론트엔드 진입점 — gateway (도입됨)

게이트웨이(Spring Cloud Gateway)를 **도입 완료**했다. 프론트는 게이트웨이(8090) 하나로 프록시하고, 게이트웨이가 경로로 분기한다.

| 경로 | 대상 |
|---|---|
| `/api/v1/auctions/**` | auction-service |
| `/ws/**`, 그 외 | 모놀리식 |

게이트웨이는 라우팅 외에 **인증 경계** 역할도 한다: 유입 `X-User-Id`를 제거(위조 방지)하고 유효 Bearer JWT의 subject를 `X-User-Id`로 주입 → 내부 서비스는 이 헤더를 신뢰한다(auction-service엔 Security 없음). 계약: [internal.md](../../api/internal.md) · [access-control-matrix](../access-control-matrix.md).

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
