# CI/CD · 테스트 환경 정책 (MSA)

> ✅ **MSA 전환 완료 기준.** 모놀리식(`backend`)·`backend-ci.yml`은 제거됐다. CI는 **서비스별 워크플로(`services/*-ci.yml`)** + 프론트 + 공통 린트로 운영된다. 이 문서는 현행 CI 정책과, 아직 미구현인 CD·계약 테스트·커버리지 게이트의 목표 설계를 함께 정의한다.
>
> 결정 전제 (유지):
> - **레포 전략**: 모노레포 — 서비스는 `services/*`, 공통 라이브러리는 `libs/common`.
> - **배포 타겟(설계)**: 단일 VPS + `docker-compose.msa.yml` — 이미지 빌드 → 레지스트리 푸시 → 호스트 pull.
>
> 관련: [테스트 전략](../design/testing.md) · [성능·부하](../design/performance-testing.md) · [아키텍처](../design/architecture.md) · [Git 규칙](../../.claude/rules/git.md) · [로컬 MSA 구동](../design/msa/local-run.md) · [운영 배포](./local-production.md)

---

## 1. 목적과 범위

서비스가 12개로 늘어난 상태에서 **CI 게이트·테스트 환경·배포 파이프라인·브랜치 정책**을 서비스가 더 늘어도 깨지지 않게 유지한다. 이 문서가 다루는 것:

- GitHub Actions 워크플로우 구조 (모노레포 경로 필터, 서비스당 1개)
- CI 게이트(무엇이 머지를 막는가)와 required checks
- 테스트 환경(CI 인프라 서비스, 커버리지)
- CD(단일 VPS + `docker-compose.msa.yml`) 파이프라인 — **설계, 미구현**
- 브랜치·머지·시크릿 정책

다루지 않는 것: 테스트 작성 원칙(→ [design/testing.md](../design/testing.md)), 부하 시나리오(→ [performance-testing.md](../design/performance-testing.md)).

---

## 2. 현재 상태 (As-Is)

| 워크플로우 | 트리거 | 하는 일 |
|---|---|---|
| `{service}-ci.yml` (map·combat·gateway·item·ranking·season·admin 등) | PR→`dev`/`main`/`msa/**`, `services/{svc}/**` + `libs/common/**` | `libs/common` → mavenLocal 발행 → `spotlessCheck` → `gradlew build`(test 포함) |
| `frontend-ci.yml` | PR→dev/main/msa/**, `frontend/**` | `tsc --noEmit` → `npm run test:run` → `npm run build` |
| `check-commit-message.yml` | PR→dev/main | 커밋 메시지 `[TYPE]` 규칙 검사 |
| `check-pr-title.yml` | PR | PR 제목 `[PREFIX]` 규칙 검사 |
| `dependabot.yml` | 상시 | gradle·npm·github-actions 의존성 업데이트 |

전 워크플로우에 `concurrency`(이전 실행 취소)와 `timeout-minutes`가 적용돼 있다. 프론트 테스트(`test:run`)는 머지 게이트로 실행된다(모놀 시절 누락 해소).

**남은 갭**
1. **CI 워크플로 커버리지 불완전** — 아직 `*-ci.yml`이 없는 서비스(auction·user·social·notification·realtime)가 있다. 서비스마다 1개씩 채워야 required checks가 완결된다.
2. **커버리지 측정·게이트 부재** — JaCoCo(백)·vitest coverage(프론트)가 CI에 연결돼 있지 않다.
3. **CD 부재** — 배포는 전 과정 수동. GHCR 빌드·푸시·배포 워크플로와 production 오버레이 compose 미구현.
4. **풀스택 스모크·계약 테스트 미자동화** — 서비스 간 통신 검증이 CI 게이트에 없다.

---

## 3. 모노레포 구조 (현행)

패키지 경계 = 디렉토리 경계 = CI 경로 필터 경계를 일치시킨다.

```
Territorial_Auction/
├── services/
│   ├── gateway/               # build.gradle, Dockerfile, src
│   ├── auction-service/
│   ├── user-service/
│   ├── combat-service/
│   ├── map-service/
│   ├── social-service/
│   ├── notification-service/
│   ├── item-service/
│   ├── season-service/
│   ├── ranking-service/
│   ├── admin-service/
│   └── realtime-service/
├── libs/common/               # 서비스 공통 (ApiResponse, ErrorCode, 분산락 등)
├── frontend/
├── docker/redis/              # 공유 Redis 이미지
├── docker-compose.msa.yml     # 로컬 풀스택 (정본)
└── .github/workflows/
```

> 서비스 경계의 원본은 [architecture.md](../design/architecture.md)의 Bounded Context 표다. 이 문서는 그 경계를 CI/CD에 투영할 뿐이다.

---

## 4. CI 정책

### 4.1 워크플로우 분리 원칙

- **서비스 1개 = 워크플로우 1개**, `paths` 필터로 변경된 서비스 + `libs/common`만 실행한다.
- 공통 검사(커밋/PR 린트)는 경로 필터 없이 전 PR에 실행.
- 서비스 CI는 동일 골격을 공유한다(아래 4.2). 중복이 부담이 되면 `workflow_call` reusable 워크플로우로 추출한다 — 현재는 서비스별 파일로 두되 골격을 통일한다.

### 4.2 백엔드 서비스 CI 게이트 (현행 골격)

각 서비스 PR은 아래를 통과해야 머지 가능하다.

| 순서 | 단계 | 명령 |
|---|---|---|
| 1 | 공통 라이브러리 발행 | `libs/common` → `./gradlew publishToMavenLocal -x test` |
| 2 | 포맷 | `services/{svc}` → `./gradlew spotlessCheck` |
| 3 | 컴파일·단위·통합 테스트 | `./gradlew build` |

- JDK 17 temurin, `cache: gradle`.
- **분리 서비스는 self-contained 빌드**: `libs/common`을 mavenLocal에 먼저 발행해 GitHub Packages PAT 없이 빌드된다.
- 커버리지 측정(JaCoCo)·Gatling 컴파일 검증은 **도입 예정**([4.5](#45-커버리지-정책)).

### 4.3 프론트엔드 CI 게이트 (현행)

| 순서 | 단계 | 명령 |
|---|---|---|
| 1 | 타입 체크 | `npx tsc --noEmit` |
| 2 | 단위 테스트 | `npm run test:run` |
| 3 | 빌드 | `npm run build` |

커버리지(`npm run test:coverage`)는 측정만, 게이트 미도입([4.5](#45-커버리지-정책)).

### 4.4 공통 정책 (적용됨)

- **`concurrency`**: 같은 ref의 이전 실행 취소.
  ```yaml
  concurrency:
    group: ${{ github.workflow }}-${{ github.ref }}
    cancel-in-progress: true
  ```
- **캐싱**: 백엔드 `cache: gradle`, 프론트 `cache: npm`(`cache-dependency-path`를 서비스/프론트 경로로).
- **타임아웃**: job마다 `timeout-minutes`(백 20 / 프론트 15 / 체크 5).
- **트리거**: PR base `dev`·`main`·`msa/**`.

### 4.5 커버리지 정책

측정은 강제하되 임계는 **점진 상향**한다. 처음부터 높은 문턱은 우회(테스트 삭제)를 유발한다.

**현재 단계: 미연결 → 리포트-온리 우선 도입.** 서비스별 `build.gradle`에 JaCoCo 리포트, 프론트 vitest coverage를 CI 아티팩트/코멘트로 노출부터 한다. 아래 임계는 베이스라인이 쌓인 뒤 강제한다.

| 대상 | 도구 | 측정 범위 | 도입 임계 | 목표 임계 |
|---|---|---|---|---|
| 백엔드(서비스별) | JaCoCo | Service 레이어 line coverage | 60% | 80% |
| 프론트 | vitest v8 coverage | hooks + components | 50% | 70% |

- 제외: 백엔드 DTO/Entity/config, 프론트 `api/`·`routes.tsx`·`main.tsx`.
- 강제 도입 전까지는 리포트만 노출한다.

---

## 5. 테스트 환경 정책

### 5.1 CI 인프라 서비스

DB 연동 테스트는 실제 DB 사용(Mock DB 금지, [design/testing.md](../design/testing.md)).

- GitHub Actions `services:`로 `postgres:16` + 필요 시 `redis:7`를 서비스별 job에 띄운다.
- 각 서비스 job은 **자기 소유 DB만** 붙는다 — 서비스 간 DB 공유 테스트 금지(경계 검증).
- 프로파일 `SPRING_PROFILES_ACTIVE=test`, 자격증명은 워크플로우 `env`(테스트 전용 값, 시크릿 아님).

### 5.2 Testcontainers 검토 (선택)

`services:` 블록 중복이 부담이 되면 Testcontainers 전환을 검토한다 — 단, 지금 도입하지 않는다. 현행이 단순하고 잘 동작한다.

### 5.3 테스트 계층별 CI 실행 여부

| 계층 | CI(PR) | 비고 |
|---|---|---|
| 단위(Service, hooks) | ✅ 매 PR | 기본 게이트 |
| 통합(실제 PG/Redis) | ✅ 매 PR | `services:` 컨테이너 |
| 계약(Contract) | 🔶 도입 예정 | [5.4](#54-msa-검증-전략-테스트-피라미드) — 상대 서비스 미기동 |
| 풀스택 스모크 | 🔶 merge/야간(미자동화) | [5.4](#54-msa-검증-전략-테스트-피라미드) — 러너에서 `compose up` |
| 부하(Gatling) | ❌ PR 제외 | 실행은 [6.4](#64-부하-테스트-분리) |
| E2E | 미도입 | 수동 QA 대체 |

### 5.4 MSA 검증 전략 (테스트 피라미드)

**"MSA가 잘 도나"는 하나의 질문이 아니라 3개이며, 각각 비용·빈도·실행 위치가 다르다.** push마다 전체 스택을 띄우지 않는다.

| 계층 | 답하는 질문 | 도구 | 어디서 | 언제 | 무게 |
|---|---|---|---|---|---|
| **격리 테스트** | 이 서비스 자체가 맞나 | JUnit + 자기 DB(`services:`/Testcontainers) | CI 러너 | 매 PR | 가벼움 |
| **계약 테스트** | 서비스 **간 API 계약**이 맞나 | Spring Cloud Contract 또는 Pact | CI 러너 | 매 PR | 가벼움 — **상대 미기동** |
| **풀스택 스모크** | 실제로 다 뜨고 통신되나 | `docker compose -f docker-compose.msa.yml up` + 핵심 경로 | CI 러너/스테이징 | merge/야간 | 무거움 |

**핵심 원칙**
- 서비스 간 통신의 1차 방어선은 **계약 테스트**다. 예: auction-service가 user-service의 `/internal/users/{id}/ap/reserve` 형태를 계약으로 고정하면, **상대를 띄우지 않고도** 위반을 CI가 잡는다. 계약의 원본은 [internal.md](../api/internal.md).
- 무거운 풀스택 스모크는 **로컬이 아니라 CI 러너/스테이징**이 짊어진다.
- 계약 도구는 도입 시 하나 확정한다 — Spring 스택 일관성은 Spring Cloud Contract, 폴리글랏 여지는 Pact.

**러너 자원 한계**
- GitHub 무료 러너 = **7GB RAM / 2 CPU**. 서비스 12개 + DB 10개 풀스택 `compose up`은 러너에서 못 뜬다.
- 대응: 스모크는 **필수 경로에 필요한 서비스 서브셋만** 기동하거나(→ [local-run 2절](../design/msa/local-run.md)), **스테이징 VPS/self-hosted 러너**에서 실행한다.

### 5.5 push 시 파이프라인 (요약)

```
PR (변경된 서비스)  → 격리 테스트 (+계약 테스트: 도입 후)     [가벼움, 매번]
merge → main       → 풀스택 스모크(서브셋 compose up)         [무거움, 가끔]
                   → (승인) → CD 배포 (§6)
```

로컬 전체 스택 기동은 [로컬 MSA 구동 가이드](../design/msa/local-run.md).

---

## 6. CD 정책 (단일 VPS + docker-compose) — 설계, 미구현

### 6.1 파이프라인 개요

```
main 머지
  → (수동 승인 게이트: GitHub Environment "production")
  → 변경 서비스별 이미지 빌드
  → GHCR(ghcr.io) 푸시  [tag: git SHA + 서비스별 시맨틱 버전]
  → VPS에서 docker compose pull & up -d (변경 서비스만)
  → /actuator/health 헬스체크 확인
```

- 레지스트리: **GHCR** — Actions 인증이 자연스럽고 무료.
- 배포용 compose는 로컬 `build:`가 아니라 **`image:` 참조**로 전환한다(호스트는 소스 없이 이미지만 pull). `docker-compose.msa.yml`을 기준으로 production 오버레이(`image:` + 시크릿 주입 + 볼륨 영속)를 별도 작성한다.

### 6.2 이미지 태그 전략

| 태그 | 용도 |
|---|---|
| `ghcr.io/<org>/<service>:<git-sha>` | 불변 식별자 — 롤백 기준 |
| `ghcr.io/<org>/<service>:latest` | 호스트 compose 참조(선택) |
| `ghcr.io/<org>/<service>:<semver>` | 릴리스 태깅 |

롤백은 이전 `<git-sha>` 태그로 `up -d` 재실행.

### 6.3 배포 트리거·승인

- **자동 배포 금지** — `main` 머지 후 **수동 승인**(GitHub Environment protection). 배포는 사람이 결정한다([git.md](../../.claude/rules/git.md)).
- 호스트 접근은 SSH 배포 키 또는 self-hosted runner 중 택1(VPS 확정 시). 시크릿은 [7.3](#73-시크릿-관리) 대로.

### 6.4 부하 테스트 분리

Gatling 실행은 별도 워크플로우로:
- 트리거: `workflow_dispatch`(수동) + 선택적 야간 `schedule`.
- 대상: 스테이징/로컬 게이트웨이(:8090), PR 게이트와 무관.
- 결과는 `report/load/`에 커밋. 재구성 방향은 [performance-testing.md](../design/performance-testing.md).

---

## 7. 브랜치·머지·시크릿 정책

### 7.1 브랜치 전략

`feature/*` → `msa/{service}`(또는 도메인 통합) → `dev` → `main`. 단계 PR은 통합 브랜치를 base로, 완성 후 통합 브랜치에서만 `dev`로 PR한다. 순수 문서·정책 단발 변경은 `feature/*` → `dev` 직접 PR 허용. 상세는 [git.md](../../.claude/rules/git.md).

### 7.2 Required Checks (브랜치 보호)

| 브랜치 | 필수 통과 체크 |
|---|---|
| `msa/*` | 변경된 서비스의 CI + `check-commit-message` + `check-pr-title` |
| `dev` | 변경된 서비스의 CI + `check-commit-message` + `check-pr-title` |
| `main` | dev와 동일 + (CD 승인은 머지 후 별도 게이트) |

- 경로 필터 워크플로우는 "변경 없으면 미실행" → required 지정 시 pending으로 막힐 수 있다. 더미 성공 job 또는 paths-filter 대응 패턴으로 처리한다.
- `main` 직접 push 금지, PR 필수.

### 7.3 시크릿 관리

- 모든 배포 시크릿(DB, Redis, JWT, `INTERNAL_API_SECRET`, OAuth, SSH 키)은 **GitHub Secrets / Environment Secrets**로만. 레포 커밋 금지.
- 루트 `.env`는 gitignore 유지(로컬 공유 시크릿 주입). CI 테스트 자격증명은 워크플로우 `env` 하드코딩(테스트 전용 값).
- GHCR 인증은 `GITHUB_TOKEN` 사용 — 별도 PAT 불필요.

### 7.4 보안 스캔

| 스캔 | 도구 | 상태 |
|---|---|---|
| 의존성 취약점 | Dependabot | ✅ 적용됨(`.github/dependabot.yml`) |
| 시크릿 유출 | GitHub secret scanning | 레포 Settings 토글 |
| 컨테이너 이미지 | Trivy(선택) | CD 빌드 후 도입 검토 |
| SAST | CodeQL | 안정화 후 검토 |

---

## 8. 남은 CI/CD 로드맵

MSA 코드 전환은 완료됐다. CI/CD 측 잔여 작업:

- [ ] CI 워크플로 미보유 서비스(auction·user·social·notification·realtime) `*-ci.yml` 추가 — 서비스마다 1개
- [ ] 서비스 CI 골격을 `workflow_call` reusable로 추출(중복 축소)
- [ ] JaCoCo(백)·vitest coverage(프론트) CI 연결 — 리포트-온리 먼저
- [ ] 계약 테스트 도구 확정 + 핵심 계약(`/internal/**`) 검증 도입
- [ ] 풀스택 스모크 워크플로우(서브셋 `compose up`) — merge/야간
- [ ] GHCR 빌드·푸시 + production 오버레이 compose(`image:` 참조)
- [ ] production Environment 승인 게이트 + VPS 배포 스텝 + 헬스체크
- [ ] 부하 테스트 `workflow_dispatch` 분리
- [ ] `dev`/`main` 브랜치 보호 + required checks(경로 필터 pending 대응)
- [ ] 커버리지 임계 강제(백 60→80% / 프론트 50→70%)

---

## 부록 — 미결 사항

| 항목 | 선택지 | 결정 시점 |
|---|---|---|
| VPS 호스트 접근 | SSH 배포 키 vs self-hosted runner | CD 착수 |
| `latest` 태그 사용 | compose가 `latest` vs SHA 고정 | CD 착수 |
| 경로 필터 required 대응 | 더미 성공 job vs paths-filter 액션 | 브랜치 보호 도입 |
| 계약 테스트 도구 | Spring Cloud Contract vs Pact | 계약 테스트 도입 |
| 풀스택 스모크 위치 | 러너 서브셋 vs 스테이징 VPS | 스모크 자동화 착수 |
