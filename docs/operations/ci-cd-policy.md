# CI/CD · 테스트 환경 정책 (MSA 전환 대비)

> ⚠️ **MSA 전환 완료 반영 필요.** 모놀리식(`backend`)·`backend-ci.yml`·`docker-compose.yml`은 제거됐다. 현재 CI는 서비스별 워크플로(`{service}-ci.yml`)로 운영되며, 아래 문서 중 `backend/**` 경로 필터·모놀 공존·단일 스택 기동 서술은 역사적 참고다. 서비스 공통 정책(spotless·build·계약 테스트·시크릿)은 유효.

> 이 문서는 **정책·설계 확정본**이다. 실제 워크플로우 구현은 MSA 서비스 분리가 착수되는 시점에 이 문서의 [구현 체크리스트](#9-구현-체크리스트)를 따라 단계적으로 반영한다.
>
> 결정 전제 (2026-08-25 확정):
> - **레포 전략**: 모노레포 — 서비스를 이 레포 안에서 `services/*` 로 분리한다.
> - **배포 타겟**: 단일 VPS + `docker-compose` — 이미지 빌드 → 레지스트리 푸시 → 호스트 pull.
> - **이번 작업 범위**: 정책 문서화. 워크플로우 코드는 착수 시 반영.
>
> 관련: [테스트 전략](../design/testing.md) · [아키텍처·MSA 전환 기준](../design/architecture.md) · [Git 규칙](../../.claude/rules/git.md) · [로컬 프로덕션 구동](./local-production.md)

---

## 1. 목적과 범위

모놀리식에서 MSA로 넘어가기 전에, **CI 게이트·테스트 환경·배포 파이프라인·브랜치 정책**을 서비스가 늘어나도 깨지지 않는 형태로 먼저 확정한다. 서비스가 8개로 분리된 뒤 정책을 뒤늦게 맞추면 워크플로우 중복과 게이트 누락이 누적된다.

이 문서가 다루는 것:
- GitHub Actions 워크플로우 구조 (모노레포 경로 필터)
- CI 게이트(무엇이 머지를 막는가)와 required checks
- 테스트 환경(CI 인프라 서비스, 커버리지)
- CD(단일 VPS + docker-compose) 파이프라인
- 브랜치·머지·시크릿 정책
- 모놀리식 → MSA 점진 전환(Strangler) 단계

다루지 않는 것: 테스트 작성 원칙(→ [design/testing.md](../design/testing.md)), 부하 테스트 시나리오(→ [performance-testing.md](../design/performance-testing.md)).

---

## 2. 현재 상태 진단 (As-Is)

| 워크플로우 | 트리거 | 하는 일 | 갭 |
|---|---|---|---|
| `backend-ci.yml` | PR→dev/main, `backend/**` | `spotlessCheck` + `gradlew build`(test 포함), PG16·Redis7 서비스 컨테이너 | 커버리지 미측정, Gatling 미실행, `concurrency` 없음 |
| `frontend-ci.yml` | PR→dev/main, `frontend/**` | `tsc --noEmit` + `npm run build` | **Vitest 미실행 — 테스트가 머지 게이트가 아님**, 커버리지 없음 |
| `check-commit-message.yml` | PR→dev/main | 커밋 메시지 `[TYPE]` 규칙 검사 | — |
| `check-pr-title.yml` | PR | PR 제목 `[PREFIX]` 규칙 검사 | — |

**핵심 갭 5가지**
1. 프론트엔드 테스트가 CI에서 실행되지 않는다 → 회귀가 통과된다.
2. 커버리지 측정·리포팅 부재 (백엔드 JaCoCo 미설정, 프론트 vitest coverage CI 미연결).
3. CD 부재 — `render.yaml`은 `autoDeployTrigger: off`, 배포는 전 과정 수동.
4. 중복 실행 취소(`concurrency`) 없음 → 러너 낭비.
5. 단일 경로 필터(`backend/**`) — 서비스가 늘면 서비스별 필터가 필요하다.

---

## 3. 목표 모노레포 구조 (To-Be)

서비스는 이 레포 안에서 분리하되, **패키지 경계 = 디렉토리 경계 = CI 경로 필터 경계**를 일치시킨다.

```
Territorial_Auction/
├── services/
│   ├── auth-service/          # build.gradle, Dockerfile, src
│   ├── user-service/
│   ├── map-service/
│   ├── auction-service/       # 부하 테스트상 첫 분리 후보
│   ├── combat-service/        # military + building
│   ├── social-service/        # social + notification + guild
│   ├── economy-service/       # item + season
│   └── ranking-service/
├── frontend/
├── libs/                      # (선택) 서비스 공통 코드 — ApiResponse, ErrorCode 등
├── docker-compose.yml         # 로컬 전체 기동
├── docker-compose.production.yml
└── .github/workflows/
```

전환 완료 전까지는 현재 `backend/`(모놀리식)와 `services/*`(추출된 서비스)가 **공존**한다. 워크플로우는 두 경로를 모두 인지해야 한다([8. 전환 단계](#8-모놀리식--msa-점진-전환-strangler) 참고).

> 서비스 경계의 원본은 [architecture.md](../design/architecture.md)의 8개 Bounded Context 표다. 이 문서는 그 경계를 CI/CD에 투영할 뿐, 경계 자체를 재정의하지 않는다.

---

## 4. CI 정책

### 4.1 워크플로우 분리 원칙

- **서비스 1개 = 워크플로우 1개**, `paths` 필터로 변경된 서비스만 실행한다.
- 공통 검사(커밋/PR 린트)는 서비스와 무관하므로 경로 필터 없이 전 PR에 실행.
- 워크플로우 본문 중복을 막기 위해, 백엔드 서비스는 **reusable workflow**(`workflow_call`) 하나를 공유하고 서비스별 래퍼가 경로·서비스명만 넘긴다.

```
.github/workflows/
├── _backend-service.yml     # reusable: JDK·spotless·test·coverage (workflow_call)
├── ci-auth-service.yml      # paths: services/auth-service/** → _backend-service.yml 호출
├── ci-map-service.yml
├── ...
├── ci-frontend.yml
├── check-commit-message.yml # 전 PR
└── check-pr-title.yml       # 전 PR
```

### 4.2 백엔드 서비스 CI 게이트 (순서 고정)

각 백엔드 서비스 PR은 아래를 **모두 통과**해야 머지 가능하다.

| 순서 | 단계 | 명령 | 실패 시 |
|---|---|---|---|
| 1 | 포맷 | `./gradlew spotlessCheck` | 차단 |
| 2 | 컴파일·단위·통합 테스트 | `./gradlew build` | 차단 |
| 3 | 커버리지 측정 | `./gradlew jacocoTestReport jacocoTestCoverageVerification` | 임계 미달 시 차단 ([4.5](#45-커버리지-정책)) |
| 4 | Gatling 컴파일 | `./gradlew gatlingClasses` | 차단(시나리오 컴파일 깨짐 방지) |

- Gatling **실행**(부하)은 CI 게이트에 넣지 않는다 — 러너에서 유의미한 부하가 나오지 않고 시간이 과다하다. 부하는 별도 수동/야간 워크플로우로 분리([6.4](#64-부하-테스트-분리)).
- JDK 17 temurin, `cache: gradle` 유지.

### 4.3 프론트엔드 CI 게이트

| 순서 | 단계 | 명령 | 실패 시 |
|---|---|---|---|
| 1 | 타입 체크 | `npx tsc --noEmit` | 차단 |
| 2 | **단위 테스트** | `npm run test:run` | 차단 ← **현재 누락, 반드시 추가** |
| 3 | 커버리지 | `npm run test:coverage` | 임계 미달 시 차단 ([4.5](#45-커버리지-정책)) |
| 4 | 빌드 | `npm run build` | 차단 |

### 4.4 공통 정책

- **`concurrency`**: 모든 CI 워크플로우에 아래를 추가해 같은 PR의 이전 실행을 취소한다.
  ```yaml
  concurrency:
    group: ${{ github.workflow }}-${{ github.ref }}
    cancel-in-progress: true
  ```
- **캐싱**: 백엔드 `cache: gradle`, 프론트 `cache: npm`(현행 유지). 서비스별로 캐시 키가 분리되도록 `cache-dependency-path`를 서비스 경로로 지정.
- **타임아웃**: 각 job에 `timeout-minutes`(백엔드 20, 프론트 15) 명시 — 행(hang) 방지.
- **트리거**: PR은 `dev`, `main` 두 base 모두. push 트리거는 dev/main에만(중복 실행 방지).

### 4.5 커버리지 정책

측정은 강제하되 임계는 **점진 상향**한다. 처음부터 높은 문턱은 우회(테스트 삭제)를 유발한다.

**현재 단계: 리포트-온리 (강제 게이트 보류).** 2026-08-25 실측 결과 프론트 커버리지는 **0.25%**(테스트 파일 3개, `include`가 페이지 전체)다. 이 상태에서 50% 게이트를 걸면 모든 PR이 즉시 실패하므로, 지금은 커버리지를 **측정·리포트만** 하고 차단하지 않는다. 아래 임계는 **베이스라인 테스트가 쌓인 뒤** 도입할 목표치다.

| 대상 | 도구 | 측정 범위 | 도입 임계(예정) | 목표 임계 |
|---|---|---|---|---|
| 백엔드 | JaCoCo | Service 레이어 line coverage | 60% | 80% |
| 프론트 | vitest v8 coverage | hooks + components | 50% | 70% |

- 제외 대상(측정에서 빼는 것): 백엔드는 DTO/Entity/config, 프론트는 `api/`·`routes.tsx`·`main.tsx`(현행 `vite.config.ts` 제외 규칙과 동일).
- 임계 강제 도입 전까지는 커버리지 리포트를 아티팩트/PR 코멘트로 노출만 한다. 강제 시점에는 하락 방지보다 절대 임계 유지 방식으로 시작.

> `backend/build.gradle`에는 `jacoco` 플러그인과 `jacocoTestReport`(리포트-온리)가 도입됐다. 강제용 `jacocoTestCoverageVerification`은 베이스라인 측정 후 추가한다. 서비스 추출 시 각 서비스 `build.gradle`에도 동일 적용.

---

## 5. 테스트 환경 정책

### 5.1 CI 인프라 서비스

프로젝트 원칙상 **DB 연동 테스트는 실제 DB 사용**(Mock DB 금지, [design/testing.md](../design/testing.md)). CI도 이를 따른다.

- **현행 유지**: GitHub Actions `services:`로 `postgres:16` + `redis:7`를 서비스별 job에 띄운다.
- 각 서비스 job은 **자기 소유 스키마/DB만** 붙는다 — 서비스 간 DB 공유 테스트 금지(경계 검증 목적).
- 프로파일은 `SPRING_PROFILES_ACTIVE=test`, 자격증명은 워크플로우 `env`로 주입(현행과 동일, 시크릿 아님 — 테스트 전용 값).

### 5.2 Testcontainers 검토 (선택)

서비스가 늘어 `services:` 블록 중복이 부담되면 Testcontainers로 전환을 검토한다. 단, **지금 도입하지 않는다** — 현행 `services:` 방식이 단순하고 잘 동작하며, 추가 의존성 비용이 이득보다 크다. 전환은 "서비스 5개 이상 + 인프라 조합이 서비스마다 달라질 때"를 트리거로 재검토.

### 5.3 테스트 계층별 CI 실행 여부

| 계층 | CI(PR) | 비고 |
|---|---|---|
| 단위(Service, hooks) | ✅ 매 PR | 기본 게이트 |
| 통합(실제 PG/Redis) | ✅ 매 PR | `services:` 컨테이너 |
| 계약(Contract) | ✅ 매 PR | [5.4](#54-msa-검증-전략-테스트-피라미드) — 상대 서비스 미기동 |
| 풀스택 스모크 | 🔶 merge/야간만 | [5.4](#54-msa-검증-전략-테스트-피라미드) — 러너에서 `compose up` |
| 부하(Gatling) | ❌ PR 제외 | 컴파일만 검증, 실행은 [6.4](#64-부하-테스트-분리) |
| E2E | 미도입 | 수동 QA 대체(프론트 정책과 동일) |

### 5.4 MSA 검증 전략 (테스트 피라미드)

**"MSA가 잘 도나"는 하나의 질문이 아니라 3개의 다른 질문이며, 각각 비용·빈도·실행 위치가 다르다.** push마다 전체 스택을 띄우지 않는다 — 그건 무겁고 불필요하다.

| 계층 | 답하는 질문 | 도구 | 어디서 | 언제 | 무게 |
|---|---|---|---|---|---|
| **격리 테스트** | 이 서비스 자체가 맞나 | JUnit + 자기 DB(Testcontainers/`services:`) | CI 러너 | 매 PR | 가벼움 |
| **계약 테스트** | 서비스 **간 API 계약**이 맞나 | Spring Cloud Contract 또는 Pact | CI 러너 | 매 PR | 가벼움 — **상대 미기동** |
| **풀스택 스모크** | 실제로 다 뜨고 통신되나 | `docker compose up` + 핵심 경로 몇 개 | CI 러너(또는 스테이징) | merge/야간 | 무거움 |

**핵심 원칙**
- "서비스끼리 통신 되나?"의 1차 방어선은 **계약 테스트**다. 예: auction-service가 모놀리식의 `/internal/users/{id}/ap/reserve`를 기대하는 형태를 계약으로 고정하면, **모놀리식을 띄우지 않고도** 계약 위반을 CI가 잡는다. 대부분의 통합 버그가 여기서 걸린다.
- 무거운 풀스택 스모크는 **로컬이 아니라 CI 러너가 짊어진다**. public 레포면 러너 compute는 무료 — push하면 러너가 `compose up` → 스모크 → 폐기.
- 계약 테스트 도구(**Spring Cloud Contract vs Pact**)는 첫 서비스 추출(단계 1) 시점에 하나 확정한다. Spring 스택 일관성 측면에서 Spring Cloud Contract가 유력하나, 폴리글랏 확장 여지를 두려면 Pact.

**러너 자원 한계 (미리 고지)**
- GitHub 무료 러너 = **7GB RAM / 2 CPU**. 서비스가 6~8개로 늘면 풀스택 `compose up`이 러너에서 안 뜰 수 있다.
- 대응: 풀스택 스모크를 **스테이징 VPS 배포 후 그쪽에서 실행**, 또는 **self-hosted 러너**를 VPS에.
- 당분간(2~3 서비스)은 러너로 충분 — 지금 대비만 하고 도입은 유예.

### 5.5 push 시 파이프라인 (요약)

```
PR (변경된 서비스)  → 격리 테스트 + 계약 테스트          [가벼움, 매번]
merge → main       → 풀스택 스모크(러너 compose up)      [무거움, 가끔]
                   → (승인) → CD 배포 (§6)
```

로컬에서 전체 스택을 직접 띄우는 방법은 [로컬 MSA 구동 가이드](../design/msa/local-run.md) 참고.

---

## 6. CD 정책 (단일 VPS + docker-compose)

### 6.1 파이프라인 개요

```
main 머지
  → (수동 승인 게이트: GitHub Environment "production")
  → 서비스별 이미지 빌드
  → GHCR(ghcr.io) 푸시  [tag: git SHA + 서비스별 시맨틱 버전]
  → VPS에서 docker-compose pull & up -d (변경 서비스만)
  → /actuator/health 헬스체크 확인
```

- 레지스트리: **GHCR**(GitHub Container Registry) — Actions와 인증이 자연스럽고 무료. 별도 계정 불필요.
- `docker-compose.production.yml`을 **로컬 build에서 `image:` 참조로 전환**한다(현재는 `build: context`). 호스트는 소스를 두지 않고 이미지만 pull.

### 6.2 이미지 태그 전략

| 태그 | 용도 |
|---|---|
| `ghcr.io/<org>/<service>:<git-sha>` | 불변 식별자 — 롤백 기준 |
| `ghcr.io/<org>/<service>:latest` | 호스트 compose가 참조(선택) |
| `ghcr.io/<org>/<service>:<semver>` | 릴리스 태깅 시 |

롤백은 호스트에서 이전 `<git-sha>` 태그로 `up -d` 재실행.

### 6.3 배포 트리거·승인

- **자동 배포 금지** — CD는 `main` 머지 후 **수동 승인**(GitHub Environment protection rule)을 거친다. 프로젝트 규칙상 배포는 사람이 결정한다([git.md](../../.claude/rules/git.md)의 머지 승인 원칙 연장선).
- 호스트 접근은 SSH(배포 키) 또는 self-hosted runner 중 택1 — VPS 확정 시 결정. 시크릿은 [7.3](#73-시크릿-관리)에 따라 GitHub Secrets로만.

### 6.4 부하 테스트 분리

Gatling 실행은 별도 워크플로우로 분리한다:
- 트리거: `workflow_dispatch`(수동) + 선택적 야간 `schedule`.
- 대상: 스테이징/로컬 프로덕션 환경, PR 게이트와 무관.
- 결과는 `report/load/`에 커밋(현행 네이밍 컨벤션 유지).

---

## 7. 브랜치·머지·시크릿 정책

### 7.1 브랜치 전략

서비스 추출은 `feature/*` → `msa/{service}` → `dev` → `main` 순서로 진행한다. 단계 PR은 해당 서비스 통합 브랜치를 base로 하고, 서비스 전체가 완성된 뒤 `msa/{service}`에서 `dev`로 PR한다. 순수 문서·정책 단발 변경은 `feature/*` → `dev` 직접 PR을 허용한다. 상세는 [git.md](../../.claude/rules/git.md).

### 7.2 Required Checks (브랜치 보호)

| 브랜치 | 필수 통과 체크 |
|---|---|
| `msa/*` | 변경된 서비스의 CI + `check-commit-message` + `check-pr-title` |
| `dev` | 변경된 서비스의 CI + `check-commit-message` + `check-pr-title` |
| `main` | dev와 동일 + (CD 승인은 머지 후 별도 게이트) |

- 경로 필터 워크플로우는 "변경 없으면 실행 안 됨" → required로 지정 시 **미실행 체크가 pending으로 막는 문제**가 있다. 이를 피하려면 각 서비스 워크플로우에 변경 없을 때 성공 처리하는 skip job을 두거나, GitHub의 "경로 필터 + required" 대응 패턴(더미 성공 job)을 적용한다. → 구현 시 결정.
- `main` 직접 push 금지, PR 필수. 관리자도 브랜치 보호 우회 금지.

### 7.3 시크릿 관리

- 모든 배포 시크릿(DB, Redis, JWT, OAuth, SSH 키)은 **GitHub Secrets** 또는 Environment Secrets로만. 레포에 커밋 금지.
- `backend/.env*`는 gitignore 유지(현행). CI 테스트 자격증명은 시크릿이 아닌 워크플로우 `env` 하드코딩(테스트 전용 값).
- GHCR 인증은 `GITHUB_TOKEN`(기본 제공) 사용 — 별도 PAT 불필요.

### 7.4 보안 스캔 (신규 도입 권장, 낮은 우선순위)

| 스캔 | 도구 | 트리거 |
|---|---|---|
| 의존성 취약점 | Dependabot(무료, 설정만) | 상시 |
| 시크릿 유출 | GitHub secret scanning(무료) | push |
| 컨테이너 이미지 | Trivy(선택) | CD 빌드 후 |

Dependabot·secret scanning은 설정 비용이 낮으므로 CI 정비 시 함께 켠다. SAST(CodeQL)는 서비스 안정화 후 검토.

---

## 8. 모놀리식 → MSA 점진 전환 (Strangler)

빅뱅 분리 금지. 부하 테스트에서 병목으로 확인된 **auction-service를 첫 추출 후보**로([design/testing.md](../design/testing.md) 결론) 한 서비스씩 떼어낸다. CI/CD는 각 단계에서 모놀리식과 추출 서비스를 **동시에** 지원한다.

| 단계 | 상태 | CI/CD 조치 |
|---|---|---|
| 0 | 모놀리식(`backend/`)만 존재 | 현행 + 프론트 테스트 게이트·커버리지·concurrency 추가 |
| 1 | 첫 서비스(`services/auction-service/`) 추출 | reusable 워크플로우 도입, 서비스별 경로 필터 워크플로우 1개 추가. `backend/` CI는 유지 |
| 2~7 | 서비스 순차 추출 | 서비스마다 래퍼 워크플로우 1개씩 추가. compose에 서비스 추가 |
| 8 | 모놀리식 잔여 소멸 | `backend-ci.yml` 제거, CD를 전 서비스 매트릭스로 확정 |

각 단계 완료 기준: 해당 서비스가 자기 DB/스키마로 독립 테스트 통과 + compose에서 기동 + 헬스체크 통과.

---

## 9. 구현 체크리스트

MSA 착수 시 이 순서로 워크플로우에 반영한다. **단계 0은 MSA와 무관하게 지금 바로 적용 가능**(현행 모놀리식 CI 개선).

**단계 0 — 지금 적용 가능 (모놀리식 상태)** — 2026-08-25 반영
- [x] `frontend-ci.yml`에 `npm run test:run` 추가 (누락됐던 테스트 게이트)
- [x] 모든 CI 워크플로우에 `concurrency` + `cancel-in-progress`
- [x] 각 job에 `timeout-minutes` 명시 (백엔드 20 / 프론트 15 / 체크 5)
- [x] `backend/build.gradle`에 `jacoco` 플러그인 + `jacocoTestReport`(리포트-온리)
- [x] Dependabot(`.github/dependabot.yml`) 추가 (gradle·npm·github-actions)
- [ ] `jacocoTestCoverageVerification`(백엔드) — 베이스라인 측정 후 임계 확정하여 도입 (지금은 보류)
- [ ] 프론트 커버리지 게이트 — 테스트 축적 후 도입 (현재 0.25%, 보류)
- [ ] secret scanning 활성화 — **GitHub 레포 Settings → Code security**에서 토글(파일 변경 아님)
- [ ] `dev`/`main` 브랜치 보호 규칙 + required checks 지정 — **GitHub Settings → Branches**(파일 변경 아님)

**단계 1 — 첫 서비스 추출 시**
- [ ] `_backend-service.yml` reusable 워크플로우 작성(spotless·test·jacoco·gatlingClasses)
- [ ] `ci-<service>.yml` 래퍼(경로 필터 + reusable 호출) 작성
- [ ] 경로 필터 + required checks의 pending 문제 대응(더미 성공 job)
- [ ] `docker-compose.production.yml`을 `image:` 참조로 전환
- [ ] GHCR 빌드·푸시 워크플로우 + production Environment 승인 게이트
- [ ] VPS 배포 스텝(SSH 또는 self-hosted runner) + 헬스체크 검증

**단계 2 이후**
- [ ] 서비스 추출마다 래퍼 워크플로우 + compose 항목 추가
- [ ] 부하 테스트 `workflow_dispatch` 워크플로우 분리
- [ ] 커버리지 임계 목표치로 상향(백엔드 80% / 프론트 70%)

---

## 부록 — 미결 사항 (구현 전 확정 필요)

| 항목 | 선택지 | 결정 시점 |
|---|---|---|
| VPS 호스트 접근 방식 | SSH 배포 키 vs self-hosted runner | 단계 1 |
| `latest` 태그 사용 여부 | compose가 `latest` 참조 vs SHA 고정 | 단계 1 |
| 경로 필터 required 대응 | 더미 성공 job vs paths-filter 액션 | 단계 1 |
| 스테이징 환경 유무 | production 직행 vs staging 경유 | CD 착수 시 |
