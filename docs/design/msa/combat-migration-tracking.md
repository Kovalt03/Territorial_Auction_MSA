# Combat Service 이관 추적

## 브랜치 흐름

`feature/*` → `msa/combat-service` → `dev`

PR 생성·push·merge는 사용자 승인 후 진행한다.

## 리뷰 방법

두 combat 브랜치는 같은 대상을 중복 구현하는 브랜치가 아니다.

- `feature/infra-0-combat-design`: 현재 단계의 실제 변경분. 작은 PR로 리뷰한다.
- `msa/combat-service`: 모든 단계 PR을 누적하는 통합 브랜치. 현재는 `origin/dev`와 동일하며 단계가 합쳐진 뒤 전체 서비스를 리뷰한다.

원격 push 후 설계 단계만 확인할 때:

```bash
git fetch origin
git log --oneline origin/msa/combat-service..origin/feature/infra-0-combat-design
git diff --check origin/msa/combat-service...origin/feature/infra-0-combat-design
git diff --stat origin/msa/combat-service...origin/feature/infra-0-combat-design
git diff origin/msa/combat-service...origin/feature/infra-0-combat-design
```

GitHub에서는 `feature/infra-0-combat-design` → `msa/combat-service` PR의 Files changed에서 같은 범위를 확인한다. 이 PR에서는 소유권, 계약 경계, 단계 순서와 문서 링크만 검토한다.

통합 브랜치에 단계가 누적된 뒤 combat 전체를 확인할 때:

```bash
git fetch origin
git log --oneline origin/dev..origin/msa/combat-service
git diff --check origin/dev...origin/msa/combat-service
git diff --stat origin/dev...origin/msa/combat-service
git diff origin/dev...origin/msa/combat-service
```

이 비교에서는 서비스 전체 소유권, monolith 삭제 범위, gateway/compose, 테스트 이관과 회귀를 검토한다. `msa/combat-service` → `dev` PR은 모든 단계와 full-stack 검증이 끝난 뒤에만 만든다.

병행 중인 Kafka 작업은 combat과 섞지 않고 별도로 확인한다.

```bash
git log --oneline origin/dev..feature/infra-kafka-events
git diff --check origin/dev...feature/infra-kafka-events
git diff --stat origin/dev...feature/infra-kafka-events
git diff origin/dev...feature/infra-kafka-events
```

Kafka가 먼저 dev에 병합되면 combat 통합 브랜치에 최신 dev를 반영한 뒤 `git diff origin/dev...msa/combat-service`를 다시 확인한다. 이때 예상 충돌 범위는 event adapter, application 설정, `docker-compose.msa.yml`이다.

## 단계

| 단계 | 브랜치 | 범위 | 상태 |
|---|---|---|---|
| 0 | `feature/infra-0-combat-design` | 경계·계약·순서 | 진행 중 |
| 1 | `feature/building-1-combat-scaffold` | 서비스·DB·보안 scaffold | 예정 |
| 2 | `feature/building-2-combat-core` | island/building/vault/storage | 예정 |
| 3 | `feature/military-3-combat-core` | unit/research/attack-token | 예정 |
| 4 | `feature/military-4-combat-siege` | siege/scheduler/outbox | 예정 |
| 5 | `feature/infra-5-combat-contracts` | 타 서비스 client/event bridge | 예정 |
| 6 | `feature/infra-6-combat-cutover` | gateway/compose/monolith 제거 | 예정 |

## 충돌 회피

- 기준 브랜치: `origin/dev` `5bfd88c`
- 병행 브랜치: `feature/infra-kafka-events`
- Kafka 합류 전 기존 broker adapter, broker application 설정, `docker-compose.msa.yml`의 broker 영역은 수정하지 않는다.
- Kafka가 dev에 병합되면 `msa/combat-service`에 최신 dev를 병합하고 단계 5~6을 진행한다.

## 검증 체크

- [ ] combat-service Flyway schema 단독 기동
- [ ] building 테스트 이전
- [ ] military 테스트 이전
- [ ] internal API 계약 테스트
- [ ] outbox·중복 소비 테스트
- [ ] gateway route 테스트
- [ ] MSA compose smoke
- [ ] monolith 직접 의존 0건
- [ ] 문서/checklist 최종 갱신
