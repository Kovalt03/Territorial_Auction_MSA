# 로컬 MSA 구동 가이드

> ✅ **MSA 전환 완료** — 모놀리식은 제거됐고 전 도메인이 독립 서비스다. 로컬 풀스택은 `docker-compose.msa.yml` 하나로 띄운다. 이 문서는 구동 방법과 자원을 가볍게 유지하는 법을 정리한다.
>
> 배경 결정: [CI/CD·테스트 정책](../../operations/ci-cd-policy.md) · [아키텍처](../architecture.md) · [전환 이력](./README.md).

---

## 1. 개념 — 무엇이 같이 뜨는가

전 스택은 `docker-compose.msa.yml`로 기동한다:

```
redis                     ← 공유 (Redisson 분산락·캐시·실시간 pub/sub, 빌드: ./docker/redis)
kafka                     ← durable 서비스 이벤트 백본 (호스트 9092)
{svc}-postgres × 10        ← 서비스별 전용 DB (auction·user·combat·social·notification·item·season·ranking·map·admin)
gateway                   ← 공개 진입점·경로 라우팅 (호스트 8090)
auction-service           ← 경매·입찰·이력 (8082)
user-service              ← 신원·인증(OAuth)·지갑·프로필 (내부 전용)
combat-service            ← 병력·건물·공성 (8084)
social-service / notification-service / item-service / season-service / ranking-service
map-service               ← 맵·영토 공유 커널 (8091)
admin-service             ← 관리 콘솔 (8092)
realtime-service          ← WebSocket 실시간 허브 (8093)
frontend                  ← 게이트웨이로 프록시 (호스트 3000)
```
> 프론트 API/WS 프록시 대상은 **게이트웨이(8090)**다. 미매핑 경로 fallback(모놀)은 없다.

핵심:
- **DB는 서비스당 컨테이너.** 각 서비스는 자기 PostgreSQL에만 붙고 다른 서비스 DB를 직접 조회하지 않는다.
- 서비스는 외부 도메인 키를 **FK가 아닌 ID 값**으로 보관한다. 다른 도메인 데이터가 필요하면 `/internal/**` 계약 또는 Kafka/Redis 이벤트로 얻는다.
- 서비스 간 통신은 **컨테이너 이름 DNS**로 해결된다 — `http://auction-service:8080` 등. 로컬에선 서비스 디스커버리 불필요.
- 공유 시크릿은 리포 루트 `.env`(gitignore)에서 주입한다(`JWT_SECRET`·`INTERNAL_API_SECRET`·OAuth client 등).

---

## 2. 자원을 가볍게 유지하는 법

"로컬에서 다 띄우면 무겁다"의 실질적 해소책.

1. **필요한 것만 띄운다.** 경매만 작업하면 전 스택이 아니라 의존 대상만:
   ```bash
   docker compose -f docker-compose.msa.yml up redis auction-postgres auction-service
   ```
   compose는 명시한 서비스 + 그 `depends_on`만 기동한다.
2. **서비스는 `/internal/**` 계약과 Kafka/Redis 이벤트로 느슨히 연결된다.** 한 도메인만 작업하면 그 서비스 + 의존 대상(+DB)만 띄우면 된다. 전 스택이 없어도 대부분의 단위/도메인 작업이 가능하다.
3. **전체가 다 떠야 하는 검증은 로컬이 아니라 CI 러너에 맡긴다.** push하면 러너가 `compose up` → 스모크 → 폐기한다([정책 5.4](../../operations/ci-cd-policy.md#54-msa-검증-전략-테스트-피라미드)).
4. **인터랙티브하게 무거운 스택이 필요하면 Codespaces**(클라우드 개발환경)로 노트북 부하 0.

> 참고 사용량: 앱 1개(Spring Boot) ≈ 300~500MB, postgres 1개 ≈ 50~150MB, redis ≈ 20MB. 전체가 무거우면 1번 방식으로 필요한 서비스만 선택 기동한다.

---

## 3. compose 파일 구성 (실제)

> `docker-compose.msa.yml`은 리포 루트에 **존재**한다. 아래는 실제 구성 요약 — 전체는 파일 참조.

| 서비스 | 이미지/빌드 | 호스트 포트 | 요점 |
|---|---|---|---|
| `redis` | 빌드 `./docker/redis` | 6379 | 분산락·캐시·WebSocket 저지연 pub/sub |
| `kafka` | apache/kafka 3.9 | 9092 | durable 서비스 이벤트 백본 |
| `{svc}-postgres` × 10 | postgres 16 | auction 5433·combat 5434·나머지 내부 | 서비스별 전용 DB(Flyway 각자 소유) |
| `gateway` | `./services/gateway` | 8090 | 공개 진입점·`JWT_SECRET` 공유·경로 라우팅 |
| `auction-service` | `context: .`(루트) | 8082 | map/user/combat 계약 호출 |
| `user-service` | `context: .`(루트) | 내부 전용 | 신원·인증(OAuth)·지갑·프로필 |
| `combat-service` | `context: .`(루트) | 8084 | 병력·건물·공성·outbox |
| `social` / `notification` / `item` / `season` / `ranking` / `admin`-service | `context: .`(루트) | admin 8092 외 내부 | 각 도메인 |
| `map-service` | `context: .`(루트) | 8091 | 맵·영토 공유 커널 |
| `realtime-service` | `context: .`(루트) | 8093 | WebSocket 실시간 허브(무상태) |
| `frontend` | node:20 | 3000 | `API_TARGET=http://gateway:8080` |

**주의점**
- **DB 분리**: `postgres` / `auction-postgres` / `user-postgres` / `combat-postgres`는 별도 컨테이너·볼륨이며 서로 직접 조회하지 않는다.
- **Kafka durable 경로**: `territory-auction-ready`, `auction-events`, `user-events`, `combat-events`, `territory-events`를 서비스별 producer/consumer가 사용한다. combat 이벤트는 소비 서비스(map·season·notification·realtime)별 consumer group과 receipt로 중복 적용을 막는다.
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
| `/ws/**` | realtime-service |

게이트웨이는 라우팅 외에 **인증 경계** 역할도 한다: 유입 `X-User-Id`와 `X-Gateway-Service-Token`을 제거하고, 유효 Bearer JWT의 subject와 gateway 전용 토큰을 다시 주입한다. combat-service는 gateway 토큰을 고정 시간 비교한 요청만 수용한 뒤 user ID를 인증 주체로 사용한다. 계약: [internal.md](../../api/internal.md) · [access-control-matrix](../access-control-matrix.md).

---

## 5. 구동·확인 명령

```bash
# 전체 기동 (현재 Strangler 단계)
export INTERNAL_API_SECRET=local-internal-secret
docker compose -f docker-compose.msa.yml up -d --build

# 경매만 작업 — 필요한 것만
docker compose -f docker-compose.msa.yml up redis kafka auction-postgres auction-service map-service user-service

# combat DB·building·unit/research·siege core 기동 확인
docker compose -f docker-compose.msa.yml up redis kafka combat-postgres combat-service

# 상태·헬스체크
docker compose -f docker-compose.msa.yml ps
docker compose -f docker-compose.msa.yml exec auction-service wget -qO- http://localhost:8080/actuator/health
docker compose -f docker-compose.msa.yml exec user-service wget -qO- http://localhost:8080/actuator/health
docker compose -f docker-compose.msa.yml exec combat-service wget -qO- http://localhost:8080/actuator/health

# 특정 서비스 로그
docker compose -f docker-compose.msa.yml logs -f auction-service

# 서비스 간 통신 확인 (auction → user-service DNS 해석)
docker compose -f docker-compose.msa.yml exec auction-service wget -qO- http://user-service:8080/actuator/health

# 중지(볼륨 유지)
docker compose -f docker-compose.msa.yml down
```

`down -v`는 서비스별 PG 볼륨을 모두 삭제한다 — 폐기용 검증 환경이 아니면 쓰지 않는다.

---

## 6. 자주 겪을 문제

| 증상 | 원인 | 해결 |
|---|---|---|
| 서비스 간 호출 실패 | base-url이 `localhost` | 컨테이너 간엔 서비스명 사용 — `http://user-service:8080` 등 |
| 부팅 시 DB 연결 실패 | PG 준비 전 앱 기동 | `depends_on` + `condition: service_healthy`, 앱에 재시도 |
| 마이그레이션 충돌 | 두 서비스가 같은 DB에 Flyway 적용 | DB를 서비스별로 분리했는지 확인(경계 위반) |
| 노트북이 느려짐 | 전체 스택 상시 기동 | [2절](#2-자원을-가볍게-유지하는-법) — 필요한 것만 띄우기 |

---

## 7. 관련 문서

- [CI/CD·테스트 정책](../../operations/ci-cd-policy.md) — push 시 검증 파이프라인, 계약 테스트
- [운영 배포](../../operations/local-production.md) — ⚠️ 아직 모놀 기반(MSA 재작성 필요)
- [아키텍처·MSA 전환 기준](../architecture.md)
