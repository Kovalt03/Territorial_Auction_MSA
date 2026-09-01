# Combat Service 이관 추적

## 브랜치 흐름

`feature/*` → `msa/combat-service` → `dev`

PR 생성·push·merge는 사용자 승인 후 진행한다.

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
