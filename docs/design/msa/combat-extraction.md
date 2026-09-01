# Combat Service 추출 가이드

> 기준: `origin/dev` `2fe55c8` (Kafka 이벤트 백본·MSA PR CI 포함) · 대상: `building` + `military`
>
> 단계별 진행 상태와 리뷰 명령은 [combat-migration-tracking.md](./combat-migration-tracking.md)를 따른다.

## 목표

`services/combat-service`가 건물·섬·보관함·GP 금고와 유닛·연구·공성전을 하나의 DB 트랜잭션 경계로 소유한다. 공개 API 형태는 유지하며, 다른 서비스의 User·Territory 엔티티나 DB를 참조하지 않는다.

## 소유권

combat DB 소유:

- building: `building_types`, `building_level_specs`, `building_castle_limits`, `building_instances`
- island/resource: `island_grades`, `home_islands`, `global_vaults`
- military: `unit_types`, `unit_type_level_specs`, `unit_instances`, `unit_research`, `attack_tokens`
- siege: `siege_events`, `siege_forces`, `siege_structures`, `siege_results`
- reliability/projection: `combat_outbox`, `combat_commands`, `combat_user_snapshots`

외부 참조는 FK 없이 ID로 저장한다.

- user-service: `userId`, `attackerId`, `defenderId`, `ownerId`
- monolith map: `territoryId`, `homeTerritoryId`, `deployedTerritoryId`, `targetTerritoryId`
- combat 내부의 Building·Island·UnitType·Siege 관계만 FK를 유지한다.

## 추출 순서

military가 금고·저장소·섬·건물 HP에 의존하므로 building을 먼저 옮긴다.

1. combat-service scaffold, Flyway V1, 내부 인증과 공통 응답
2. island/building/vault/storage와 user event 기반 bootstrap
3. unit/research/attack-token
4. siege와 scheduler, outbox
5. map/user/auction/item/season/admin 계약과 monolith event bridge
6. gateway/compose cutover 후 monolith building/military 삭제

통합 중에는 gateway가 계속 monolith를 향한다. 운영 데이터 이전이 필요하지 않으므로 CDC·dual-write·백필은 만들지 않고 최종 cutover 때 새 combat DB를 사용한다.

## 공개 라우트

최종 cutover 시 다음 기존 경로를 combat-service로 우선 라우팅한다.

- `/api/v1/buildings/**`, `/api/v1/building-types`, `/api/v1/building-shop/**`
- `/api/v1/island/**`, `/api/v1/inventory/**`, `/api/v1/global-vault/**`
- `/api/v1/military/**`, `/api/v1/siege/**`
- `/api/v1/map/territories/{territoryId}/buildings`

마지막 경로는 map의 일반 fallback보다 먼저 선언한다. admin building/unit API는 monolith가 인증·감사를 유지하고 combat 내부 API를 호출한다.

## 서비스 간 계약 원칙

- `/internal/**`는 gateway에 노출하지 않고 `X-Internal-Service-Token`을 검증한다.
- 변경 명령은 `commandKey`로 멱등 처리한다.
- combat은 user-service의 AP spend/credit/get 계약을 재사용한다.
- combat은 monolith map의 territory combat-context를 조회한다.
- map의 영토 상실, combat의 공성 결과·건물 변경은 outbox 이벤트로 전달한다.
- season·notification·realtime은 combat DB를 읽지 않고 combat 이벤트를 소비한다.
- item/season의 GP 보상과 토지세 차감은 combat 내부 자원 명령을 사용한다.

세부 endpoint와 event payload는 구현 단계마다 `docs/api/internal.md`에 추가한다.

## 이벤트 백본

Kafka 이전이 dev에 반영됐으므로 combat-service도 같은 패턴을 사용한다.

- durable 이벤트는 Kafka `combat-events`로 발행하고 논리 이벤트명은 `event-topic` header로 구분한다.
- aggregate ID를 record key로 사용해 같은 aggregate의 순서를 유지한다.
- DB 변경과 이벤트 생성은 combat outbox로 묶고, broker 전송 성공 후 published 처리한다.
- season·map projection처럼 재처리가 필요한 소비자는 Kafka consumer group을 각각 소유한다.
- 공성 선언·결과의 WebSocket 저지연 전달이 필요하면 auction과 동일하게 Redis pub/sub을 병행하되, 상태 반영 소비자는 Kafka만 사용한다.
- 이벤트 DTO는 서비스별로 정의하고 Java 클래스를 공유하지 않는다.

## 완료 조건

- 기존 building/military 핵심 테스트를 combat-service로 이전해 통과
- 외부 서비스 JPA 엔티티·Repository·DB FK 참조 0건
- 내부 명령의 인증·멱등·오류 계약 테스트 통과
- outbox 재시도와 중복 소비 테스트 통과
- 가입 → 섬/성 → 건물/자원 → 유닛/연구 → 공성 full-stack smoke 통과
- gateway 공개 URL과 response shape 회귀 없음
- monolith building/military 코드와 직접 Repository 의존 제거
