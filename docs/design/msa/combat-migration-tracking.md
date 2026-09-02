# Combat Service 이관 추적

## 브랜치 흐름

`feature/*` → `msa/combat-service` → `dev`

PR 생성·push·merge는 사용자 승인 후 진행한다.

## 리뷰 방법

combat 단계 브랜치와 통합 브랜치는 같은 대상을 중복 구현하는 브랜치가 아니다.

- `feature/building-1-combat-scaffold`: 현재 scaffold 단계의 실제 변경분. 작은 PR로 리뷰한다.
- `msa/combat-service`: 모든 단계 PR을 누적하는 통합 브랜치. 단계가 합쳐진 뒤 전체 서비스를 리뷰한다.

원격 push 후 설계 단계만 확인할 때:

```bash
git fetch origin
git log --oneline origin/msa/combat-service..origin/feature/building-1-combat-scaffold
git diff --check origin/msa/combat-service...origin/feature/building-1-combat-scaffold
git diff --stat origin/msa/combat-service...origin/feature/building-1-combat-scaffold
git diff origin/msa/combat-service...origin/feature/building-1-combat-scaffold
```

GitHub에서는 `feature/building-1-combat-scaffold` → `msa/combat-service` PR의 Files changed에서 같은 범위를 확인한다. 이 PR에서는 독립 빌드, combat DB 경계, 내부 헤더 보안, compose와 CI를 검토한다.

통합 브랜치에 단계가 누적된 뒤 combat 전체를 확인할 때:

```bash
git fetch origin
git log --oneline origin/dev..origin/msa/combat-service
git diff --check origin/dev...origin/msa/combat-service
git diff --stat origin/dev...origin/msa/combat-service
git diff origin/dev...origin/msa/combat-service
```

이 비교에서는 서비스 전체 소유권, monolith 삭제 범위, gateway/compose, 테스트 이관과 회귀를 검토한다. `msa/combat-service` → `dev` PR은 모든 단계와 full-stack 검증이 끝난 뒤에만 만든다.

Kafka 전환은 dev 반영 완료(`#9`) 상태다. combat은 기존 Kafka 설정과 `event-topic` header 규칙을 기준으로 새 topic·consumer group만 추가한다.

## 단계

| 단계 | 브랜치 | 범위 | 상태 |
|---|---|---|---|
| 0 | `feature/infra-0-combat-design` | 경계·계약·순서 | 완료 (`#8`) |
| 1 | `feature/building-1-combat-scaffold` | 서비스·DB·보안 scaffold | 완료 (`#11`) |
| 2 | `feature/building-2-combat-core` | island/building/vault/storage | 완료 (`#13`) |
| 3 | `feature/military-3-combat-core` | unit/research/attack-token | 완료 (`#14`) |
| 4 | `feature/military-4-combat-siege` | siege/scheduler/outbox | 구현 완료·PR 준비 |
| 5 | `feature/infra-5-combat-contracts` | 타 서비스 client/event bridge | 예정 |
| 6 | `feature/infra-6-combat-cutover` | gateway/compose/monolith 제거 | 예정 |

## 충돌 회피

- 현재 단계 기준 브랜치: `feature/military-4-combat-siege` (base `origin/msa/combat-service` `097c531`)
- Kafka 이벤트 백본: dev 반영 완료(`#9`)
- MSA 통합 브랜치 PR CI: dev 반영 완료(`#10`)
- combat-service는 기존 broker 구성을 재정의하지 않고 `combat-events` producer/consumer 설정만 추가한다.

## 검증 체크

- [x] combat-service Flyway V1 단독 기동·health `UP` (PostgreSQL 16, 2026-09-01)
- [x] building core 테스트 이전: 104건 통과 (2026-09-01)
- [x] military core 테스트 이전: 15건, combat 전체 119건 통과 (2026-09-02)
- [x] siege 선언·정산·퇴각·스케줄러 테스트 이전: 14건, combat 전체 138건 통과 (2026-09-02)
- [ ] internal API 계약 테스트
- [x] outbox 저장·발행 성공/실패 재시도 테스트 (2026-09-02)
- [ ] 이벤트 소비 중복 처리 테스트 (contracts 단계)
- [ ] gateway route 테스트
- [ ] MSA compose smoke
- [ ] monolith 직접 의존 0건
- [ ] 문서/checklist 최종 갱신
