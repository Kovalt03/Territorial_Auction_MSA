# 로컬 MSA 구동 가이드

> MSA 전환은 **Strangler(한 서비스씩 추출)**로 진행한다. 따라서 로컬에서도 "8개를 한 번에" 띄우지 않는다 — 모놀리식 + 추출된 서비스만 나란히 띄운다. 이 문서는 그 구동 방법과 자원을 가볍게 유지하는 법을 정리한다.
>
> 배경 결정: [CI/CD·테스트 정책](../../operations/ci-cd-policy.md) · [아키텍처](../architecture.md). 서비스 추출 설계는 별도 문서(`design/msa-*-extraction.md`)에서 다룬다.

---

## 1. 개념 — 무엇이 같이 뜨는가

전환 단계별로 `docker compose`가 띄우는 것이 달라진다.

**단계 0 (기준점, 모놀리식)** — `docker-compose.yml`
```
redis · postgres · backend(단일) · frontend
```

**단계 3 (combat-service 공개 cutover·모놀리식 코드 제거, 현재)** — `docker-compose.msa.yml`
```
redis                     ← 공유 (Redisson 분산락·캐시·실시간 pub/sub)
kafka                     ← durable 서비스 이벤트 백본 (호스트 9092)
postgres                  ← 모놀리식 전용 (호스트 5432)
auction-postgres          ← auction-service 전용 (호스트 5433)
user-postgres             ← user-service 전용 (컨테이너 내부 전용)
combat-postgres           ← combat-service 전용 (호스트 5434)
backend(모놀리식)          ← auction/user/combat 제외 잔여 도메인 + realtime (호스트 8080)
auction-service           ← 경매·입찰·이력             (호스트 8082)
user-service              ← 신원·인증·AP 지갑·알림 설정 (컨테이너 내부 전용)
combat-service            ← DB·building·unit/research·siege·공개 API·계약·outbox (호스트 8084)
gateway                   ← auction/user/combat 소유 경로→각 서비스, 그 외→모놀리식 (호스트 8090)
frontend                  ← 게이트웨이로 프록시          (호스트 3000)
```
> 게이트웨이는 도입됨(1단계에 포함). 프론트 API/WS 프록시 대상은 **게이트웨이(8090)**다.

핵심:
- **DB는 서비스당 컨테이너**(결정 사항). auction/user/combat 서비스는 각자 소유한 PostgreSQL에만 붙고, 모놀리식 DB를 직접 조회하지 않는다.
- 서비스는 외부 도메인 키를 **FK가 아닌 ID 값**으로 보관한다. 다른 도메인 데이터가 필요하면 내부 REST 또는 비동기 이벤트로 얻는다.
- 서비스 간 통신은 **컨테이너 이름 DNS**로 해결된다 — `http://backend:8080`, `http://auction-service:8080`. 로컬에선 Eureka 등 서비스 디스커버리 불필요.

---

## 2. 자원을 가볍게 유지하는 법

"로컬에서 다 띄우면 무겁다"의 실질적 해소책.

1. **필요한 것만 띄운다.** 경매만 작업하면 모놀리식 전체가 아니라 의존 대상만:
   ```bash
   docker compose -f docker-compose.msa.yml up redis auction-postgres auction-service
   ```
   compose는 명시한 서비스 + 그 `depends_on`만 기동한다.
2. **모놀리식이 "나머지 세계" 역할을 한다.** 현재는 `backend + auction-service + user-service + combat-service`가 내부 HTTP와 Kafka 이벤트로 연결된다. combat 공개 요청은 gateway가 combat-service로 보내고, 모놀리식은 필요한 combat 조회·자원 명령만 내부 계약으로 호출한다.
3. **전체가 다 떠야 하는 검증은 로컬이 아니라 CI 러너에 맡긴다.** push하면 러너가 `compose up` → 스모크 → 폐기한다([정책 5.4](../../operations/ci-cd-policy.md#54-msa-검증-전략-테스트-피라미드)).
4. **인터랙티브하게 무거운 스택이 필요하면 Codespaces**(클라우드 개발환경)로 노트북 부하 0.

> 참고 사용량: 앱 1개(Spring Boot) ≈ 300~500MB, postgres 1개 ≈ 50~150MB, redis ≈ 20MB. 전체가 무거우면 1번 방식으로 필요한 서비스만 선택 기동한다.

---

## 3. compose 파일 구성 (실제)

> `docker-compose.msa.yml`은 리포 루트에 **존재**한다. 아래는 실제 구성 요약 — 전체는 파일 참조.

| 서비스 | 이미지/빌드 | 호스트 포트 | 요점 |
|---|---|---|---|
| `redis` | redis 7 | 6379 | 분산락·캐시·WebSocket 저지연 pub/sub |
| `kafka` | apache/kafka 3.9 | 9092 | 경매·유저 durable 이벤트 백본 |
| `postgres` | 모놀리식 DB | 5432 | `territorial_auction` |
| `auction-postgres` | auction DB(별도) | 5433 | `auction`(Flyway 소유) |
| `user-postgres` | user DB(별도) | 내부 전용 | `user`(Flyway 소유) |
| `combat-postgres` | combat DB(별도) | 5434 | `combat`(Flyway 소유) |
| `backend` | `./backend` | 8080 | auction/user/combat 내부 API client와 잔여 도메인 |
| `auction-service` | `context: .`(루트) | 8082 | map→backend, wallet→user-service, 초기 성→combat-service |
| `user-service` | `context: .`(루트) | 내부 전용 | 신원·인증·AP 지갑·알림 설정 |
| `combat-service` | `context: .`(루트) | 8084 | building·unit/research·siege·외부 adapter·admin 계약·outbox |
| `gateway` | `./services/gateway` | 8090 | `JWT_SECRET` 공유, 경로 라우팅 |
| `frontend` | node:20 | 3000 | `API_TARGET=http://gateway:8080` |

**주의점**
- **DB 분리**: `postgres` / `auction-postgres` / `user-postgres` / `combat-postgres`는 별도 컨테이너·볼륨이며 서로 직접 조회하지 않는다.
- **Kafka durable 경로**: `territory-auction-ready`, `auction-events`, `user-events`, `combat-events`, `territory-events`를 서비스별 producer/consumer가 사용한다. combat 이벤트는 부작용별 backend consumer group과 receipt로 중복 적용을 막는다.
- **공유 Redis**: 분산락·캐시와 auction WebSocket 저지연 pub/sub에 사용한다. durable 상태 반영은 Redis에 의존하지 않는다.
- 각 서비스는 **자기 Flyway 마이그레이션**을 자기 DB에 적용한다.
- **분리 서비스 이미지는 self-contained 빌드**: auction/user/combat Dockerfile은 이미지 안에서 공유 라이브러리 `common`을 `mavenLocal`에 발행한 뒤 빌드한다 → **GitHub Packages PAT 없이** 빌드된다. (`.dockerignore`로 컨텍스트 경량화)
- 컨테이너 시각은 `TZ: Asia/Seoul`(단일 타임존).

---

## 4. 프론트엔드 진입점 — gateway (도입됨)

게이트웨이(Spring Cloud Gateway)를 **도입 완료**했다. 프론트는 게이트웨이(8090) 하나로 프록시하고, 게이트웨이가 경로로 분기한다.

| 경로 | 대상 |
|---|---|
| `/api/v1/auctions/**` | auction-service |
| `/api/v1/auth/**` | user-service |
| user-service가 소유한 프로필 쓰기·알림 설정·탈퇴 경로 | user-service |
| building·island·inventory·global-vault·military·siege 경로 | combat-service |
| `/api/v1/map/territories/{territoryId}/buildings` | combat-service |
| `/ws/**`, 그 외 | 모놀리식 |

게이트웨이는 라우팅 외에 **인증 경계** 역할도 한다: 유입 `X-User-Id`와 `X-Gateway-Service-Token`을 제거하고, 유효 Bearer JWT의 subject와 gateway 전용 토큰을 다시 주입한다. combat-service는 gateway 토큰을 고정 시간 비교한 요청만 수용한 뒤 user ID를 인증 주체로 사용한다. 계약: [internal.md](../../api/internal.md) · [access-control-matrix](../access-control-matrix.md).

---

## 5. 구동·확인 명령

```bash
# 전체 기동 (현재 Strangler 단계)
export INTERNAL_API_SECRET=local-internal-secret
docker compose -f docker-compose.msa.yml up -d --build

# 경매만 작업 — 필요한 것만
docker compose -f docker-compose.msa.yml up redis kafka auction-postgres user-postgres auction-service user-service backend

# combat DB·building·unit/research·siege core 기동 확인
docker compose -f docker-compose.msa.yml up redis kafka combat-postgres combat-service

# 상태·헬스체크
docker compose -f docker-compose.msa.yml ps
docker compose -f docker-compose.msa.yml exec auction-service wget -qO- http://localhost:8080/actuator/health
docker compose -f docker-compose.msa.yml exec user-service wget -qO- http://localhost:8080/actuator/health
docker compose -f docker-compose.msa.yml exec combat-service wget -qO- http://localhost:8080/actuator/health

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
