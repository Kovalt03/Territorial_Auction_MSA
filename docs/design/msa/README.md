# MSA 전환

모놀리식 Spring Boot에서 마이크로서비스로의 전환 문서 허브. 전환은 **Strangler(한 서비스씩 추출)**로 진행하며, 모놀리식은 전 과정 동안 계속 운영된다.

> 이 문서가 **목표 서비스 토폴로지의 기준**이다. [아키텍처 문서](../architecture.md)의 런타임 구조와 정합을 맞춘다. CI/CD·검증 정책은 [operations/ci-cd-policy.md](../../operations/ci-cd-policy.md)에 있다(MSA 전용이 아니라 프로젝트 공통 정책이라 그쪽에 둔다).

## 목표 서비스 토폴로지 (7개)

게임플레이 결합도 기준으로 11개 도메인을 7개 서비스로 묶는다.

| 서비스 | 포함 도메인 | 소유 데이터(개괄) |
|---|---|---|
| **auction-service** | auction | 경매·입찰·이력 |
| **map-service** | map | 지도·영토·대륙 (공유 커널) |
| **combat-service** | military, building | 유닛·공성전 / 건물·섬·보관함 |
| **user-service** | user, auth | 신원·지갑(AP)·알림 설정 / 인증·JWT |
| **social-service** | social, guild | 채팅 / 길드·멤버 |
| **notification-service** | notification | 알림 |
| **economy-service** | item, season | 아이템·결제 / 시즌패스·트로피 |

**그룹핑 근거**
- **map 독립 (공유 커널)** — territory를 auction 외 8개 도메인(admin·military·building·user·item·ranking·social·guild)이 참조한다. 특정 도메인 소유가 아니라 모두가 호출하는 공유 커널이므로 **독립 `map-service`**로 두고, 나머지 서비스는 `territoryId`로 참조하거나 표시용 스냅샷을 둔다.
- **auction 단독** — 경매는 지도 위 영토 점유지만 territory 자체는 map-service 소유. auction은 map-service를 참조.
- **combat = military + building** — 유닛·공성전·건물·섬은 "소유한 영토 위에서 벌어지는 것"으로 묶임. **공성전은 military 소속**이라 여기 포함.
- **user + auth** — 인증은 유저 신원과 밀접.
- **social + guild** — 채팅과 길드는 같은 소셜 상호작용.
- **notification 독립** — 다수 서비스가 발행하는 횡단 채널이라 단독.
- **economy = item + season** — 결제·아이템·시즌패스·트로피는 경제/과금 묶음.
- ranking(미구현)은 추후 별도 서비스.

초기 검토안([architecture.md](../architecture.md))에서 조정: auth→user 병합, notification 독립. **map은 초안대로 독립 유지**(공유 커널 특성이 커서 auction 병합은 부적절로 판단).

## 전환 로드맵 (Strangler)

한 서비스씩 추출하며 모놀리식은 전 과정 계속 운영. **auction을 첫 대상**으로 삼은 근거: 부하 테스트에서 단일 인기 경매의 지속 경합이 병목으로 확인됨([testing 결론](../testing.md)).

| 단계 | 대상 서비스 | 상태 |
|---|---|---|
| 0 | 모놀리식 + CI/CD·검증 정책 확립 | ✅ 완료 |
| 1 | **auction-service** (auction) | ✅ **완료** — 서비스 추출·게이트웨이·이벤트 프로젝션·모놀리식 auction 도메인 삭제 |
| 2 | user-service (user, auth) | ✅ 완료 — 신원·인증·AP 지갑·알림 설정·상태 소유 이전 |
| 3 | combat-service (military, building) | 🚧 core·outbox·서비스 계약 연결 완료, 공개 cutover 예정 — [추출 가이드](./combat-extraction.md) · [이관 추적](./combat-migration-tracking.md) |
| 4 | economy-service (item, season) | 예정 |
| 5 | social-service (social, guild) | 예정 |
| 6 | notification-service (notification) | 예정 |
| 7 | **map-service** (map) — 공유 커널이라 의존 도메인이 모두 빠진 **최후에 추출** | 예정 |
| 8 | 모놀리식 잔여 소멸, 전 서비스 확정 | 예정 |

> **map은 공유 커널이라 전환 내내 모놀리식에 남는다.** territory가 필요한 서비스(auction 등)는 그동안 **모놀리식의 territory API를 호출**(DB 공유 아님)하고, 표시용 값은 스냅샷으로 자기 DB에 복사한다. 모든 territory 의존 도메인이 서비스로 빠진 **마지막 단계에 map-service로 분리**하며, 그때 호출 대상이 모놀리식 → map-service로 바뀐다. 추출 순서(2~6)는 도메인 간 의존과 학습 우선순위에 따라 조정 가능.

### 1단계 완료 (auction-service)

첫 서비스 추출을 완료했다. 하드 컷오버(모놀리식 auction 도메인 삭제)까지 진행.

- **서비스 분리**: `services/auction-service` — 자체 DB(`auction-postgres`) 소유. 엔티티는 크로스도메인 관계 대신 ID+스냅샷.
- **게이트웨이**: Spring Cloud Gateway — `/api/v1/auctions/**` → auction-service, JWT의 subject를 `X-User-Id`로 주입.
- **동기 통신**: auction-service → user-service(지갑 에스크로·정산), 모놀리식(영토 점유·성 생성) `/internal/*`. [계약: internal.md](../../api/internal.md)
- **비동기 통신**: Kafka — 경매 생성 트리거와 프로젝션·랭킹·시즌용 durable 이벤트. Redis pub/sub은 입찰·정산 WebSocket 저지연 경로에만 병행.
- **읽기 프로젝션**: 맵 그리드 '경매중' 표시를 auction 테이블 조회 → 모놀리식 로컬 read-model(`territory_auction_status`)로 대체(이벤트 구독). 부하 실측: 경매 쓰기 경합 하 맵 그리드 조회 **p99 ~10배 개선**.
- **실시간·랭킹·시즌**: 클라이언트 WS는 모놀리식 realtime 허브가 이벤트를 구독해 push. 랭킹·시즌 귀속은 이벤트 브리지로 인프로세스 재발행.
- 상세: [auction-extraction.md](./auction-extraction.md) · [auction-migration-tracking.md](./auction-migration-tracking.md)

## 브랜치·PR 전략 (서비스 통합 브랜치)

서비스 추출은 **단계별로 작게 개발하되, dev로는 서비스 완성 단위로 크게 올린다.**

```
feature/{domain}-{n}-{step}  ─┐
feature/{domain}-{n}-{step}  ─┼─(작은 PR)→  msa/{service}  ─(큰 PR)→  dev
feature/{domain}-{n}-{step}  ─┘             (서비스 통합 브랜치)     서비스 완성 단위
```

| 레벨 | 브랜치 | 병합 | 단위 |
|---|---|---|---|
| 단계 작업 | `feature/{domain}-{n}-{step}` | 작은 PR → `msa/{service}` | 추출 한 단계(스캐폴딩·DB분리·입찰치환 등) |
| 서비스 완성 | `msa/{service}` | **큰 PR → `dev`** (Squash) | 서비스 하나 전체 |

- 각 단계 PR·통합 PR 모두 **CI 그린 필수**. dev 머지는 Squash라 dev엔 서비스당 1커밋으로 남는다(세부는 PR 보존).
- Strangler라 `msa/{service}`가 dev에 병합되기 **전까지 모놀리식이 계속 서빙** → 중간 단계가 운영을 깨지 않는다.
- 통합 브랜치는 주기적으로 `dev`를 머지해 drift를 줄인다.
- 순수 문서·정책 같은 단발 변경은 통합 브랜치 없이 `feature/*` → `dev` 직접 PR로 간다(이 규칙은 서비스 추출 코드에 적용).

## 문서 인덱스

이 폴더(`docs/design/msa/`)에는 **서비스 공통 프레임워크 문서**만 둔다.

| 문서 | 내용 |
|---|---|
| [local-run.md](./local-run.md) | 로컬 MSA 구동 — Strangler 토폴로지, 서비스당 DB, 자원 절감, compose 구성 |

### 서비스별 추출 가이드

각 서비스의 추출 가이드(소유/참조 경계·통신 계약·Saga 등)는 **해당 `msa/{service}` 통합 브랜치에서 관리**하고, 서비스 완성 PR로 dev에 함께 병합한다 — 특정 서비스에 종속된 작업 문서라 공통 프레임워크와 분리한다.

| 서비스 | 가이드 | 위치 |
|---|---|---|
| auction | [auction-extraction.md](./auction-extraction.md) | `dev` 병합 완료 |
| user | [user-extraction.md](./user-extraction.md) | `dev` 병합 완료 |
| combat | [combat-extraction.md](./combat-extraction.md) | 현재 `msa/combat-service` 작업 기준 |

## 관련 (MSA 밖 공통 문서)

- [operations/ci-cd-policy.md](../../operations/ci-cd-policy.md) — CI/CD·테스트 정책, MSA 검증 피라미드(격리·계약·풀스택)
- [design/architecture.md](../architecture.md) — 런타임 구조·Bounded Context 경계
- [design/chat-broker-strategy.md](../chat-broker-strategy.md) — STOMP 브로커 전환(서비스 분리 시 Redis relay 검토)
