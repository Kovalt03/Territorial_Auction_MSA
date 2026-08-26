# Territorial Auction — 문서 인덱스

> 전체 구현 현황 → **[checklist.md](./checklist.md)**
>
> 프로젝트 소개·로컬 실행 → **[루트 README](../README.md)**
> 화면별 플레이 방법 → **[인터랙티브 사용자 가이드](https://claude.ai/code/artifact/366effa3-8970-4353-a97c-aa4a4fabe49f?via=auto_preview)**

픽셀 경매 · 사이버 영토 전쟁 프로젝트 전체 문서 목록입니다.

---

## 기획 (planning/)

| 파일 | 내용 |
|---|---|
| [overview.md](./planning/overview.md) | 서비스 개요, 핵심 시스템, 화면 구성, 경제 구조, Config 값 |
| [requirements.md](./planning/requirements.md) | 기능 요구사항 정의서 (카테고리별 FR 목록) |
| [wireframes.md](./planning/wireframes.md) | 화면별 와이어프레임 및 UI 구성 설명 |

---

## 설계 (design/)

| 파일 | 내용 |
|---|---|
| [domain-design.md](./design/domain-design.md) | 10개 Bounded Context, 핵심 엔티티·VO, 도메인 간 협력 로직 |
| [architecture.md](./design/architecture.md) | 계층형 런타임 구조, 패키지 경계, 실시간 통신, MSA 전환 기준 |
| [db-schema.md](./design/db-schema.md) | PostgreSQL 전체 테이블 정의 + Redis 키 구조 |
| [access-control-matrix.md](./design/access-control-matrix.md) | 기능별 접근 제어 매트릭스 (F-코드 × 유저 역할) |
| [building-specs.md](./design/building-specs.md) | 건물 타입별 세부 스펙, Zone 배치 규칙, 파괴 효과 |
| [military-specs.md](./design/military-specs.md) | 유닛 타입별 세부 스펙, 전투 공식, 트로피 증감표 |
| [chat-architecture.md](./design/chat-architecture.md) | 채팅 STOMP 구조, 채팅방 타입, 접근 제어, 구현 순서 |
| [performance-testing.md](./design/performance-testing.md) | 성능·부하 테스트 진행 가이드 (계측 → 베이스라인 → 부하 → 최적화) |
| [testing.md](./design/testing.md) | Backend·Frontend·부하 테스트의 공개 전략과 실행 방법 |
| [chat-broker-strategy.md](./design/chat-broker-strategy.md) | SimpleBroker · Redis Pub-Sub · Kafka · RabbitMQ 비교 및 단계별 전략 |
| [siege-system.md](./design/siege-system.md) | 공성전 상세 — Zone 공략, 공성 건물, 보호/점유 분리, 정산 규칙 |
| [admin-dashboard.md](./design/admin-dashboard.md) | 관리자 페이지 기능 명세, 감사 로그, 권한 |
| [code-conventions.md](./design/code-conventions.md) | 코드 컨벤션 |

---

## API 명세 (api/)

> Base URL: `http://localhost:8080`  
> 인증: `Authorization: Bearer {accessToken}`  
> 공통 응답/에러 형식 → [api/README.md](./api/README.md)

### REST API

> 엔드포인트 수 = **문서에 기재된 수**. 구현 수와 다르면 비고에 표시했다.

| 파일 | 도메인 | 문서 | 구현 상태 |
|---|---|---|---|
| [auth.md](./api/auth.md) | 인증·회원 | 7 | ✅ 완료 |
| [user.md](./api/user.md) | 유저 프로필·자산·영토·위시리스트 | 13 | ✅ 완료 |
| [map.md](./api/map.md) | 맵·영토·대륙 | 6 | ✅ 완료 |
| [auction.md](./api/auction.md) | 경매·입찰·경매 이력 | 6 | ✅ 완료 |
| [building.md](./api/building.md) | 건물·섬·보관함·상점 | 11 | ✅ 완료 |
| [military.md](./api/military.md) | 군사·공성전·연구 | 15 | ✅ 완료 |
| [notification.md](./api/notification.md) | 알림 | 3 | ✅ 완료 |
| [ranking.md](./api/ranking.md) | 랭킹 | 3 | ✅ 구현 5 — 대륙·내 순위 문서 누락 |
| [global-vault.md](./api/global-vault.md) | 글로벌 금고 | 2 | ✅ 완료 |
| [payment.md](./api/payment.md) | 아이템 샵 | 4 | 🔄 AP 충전 PG 연동은 더미 (외부 결제대행사 필요) |
| [season.md](./api/season.md) | 시즌 패스 | 3 | ✅ 구현 7 — 문서 보강 필요 |
| [tax.md](./api/tax.md) | 토지세 | 2 | ✅ 완료 |
| [guild.md](./api/guild.md) | 길드 | 13 | ✅ 완료 |
| [territory-income.md](./api/territory-income.md) | 영토 수익 | — | ✅ 완료 (설계 설명 문서) |
| [admin.md](./api/admin.md) | 관리자 페이지 | 25 | ✅ 구현 58 — 문서 보강 필요 |

### 실시간 (WebSocket/STOMP)

| 파일 | 내용 |
|---|---|
| [websocket/README.md](./api/websocket/README.md) | STOMP 연결, 인증, Pub/Sub prefix 규칙 |
| [websocket/auction.md](./api/websocket/auction.md) | 입찰 브로드캐스트 |
| [websocket/map.md](./api/websocket/map.md) | 영토 점유·만료·인계 브로드캐스트 |
| [websocket/chat.md](./api/websocket/chat.md) | 채팅 송수신 |
| [websocket/notification.md](./api/websocket/notification.md) | 알림·공성 경보 |

### 참고

| 파일 | 내용 |
|---|---|
| [errors.md](./api/errors.md) | 전 도메인 에러 코드 통합 레퍼런스 |
| [api/README.md](./api/README.md) | 공통 규칙 (Base URL, 인증, 응답 형식, HTTP 상태 코드) |
| [guild-new-apis-plan.md](./api/guild-new-apis-plan.md) | 길드 추가 API 계획 (설계 검토용) |

---

## MSA 전환 (design/msa/)

| 파일 | 내용 |
|---|---|
| [msa/README.md](./design/msa/README.md) | MSA 전환 허브 — 목표 토폴로지, Strangler 로드맵, PR 전략 |
| [msa/local-run.md](./design/msa/local-run.md) | 로컬 MSA 구동 — Strangler 토폴로지, 서비스당 DB, 자원 절감 |

---

## 운영·사용 가이드

| 파일 | 내용 |
|---|---|
| [operations/local-production.md](./operations/local-production.md) | Docker 기반 로컬 운영 실행, 관리자 초기화, 백업·복구 |
| [operations/external-render-supabase.md](./operations/external-render-supabase.md) | Render·Supabase 외부 호환성 검증과 재현용 설정 |
| [operations/ci-cd-policy.md](./operations/ci-cd-policy.md) | CI/CD·테스트 환경 정책 (MSA 모노레포 전환 대비, 게이트·커버리지·CD·전환 단계) |
| [guides/user-guide.md](./guides/user-guide.md) | 일반 사용자 기능 안내 |
| [guides/admin-guide.md](./guides/admin-guide.md) | 관리자 보안·운영 절차 |
| [guides/development-workflow.md](./guides/development-workflow.md) | 브랜치, 검증, Pull Request 흐름 |
| [testing/README.md](./testing/README.md) | API 계약, 단위·통합 테스트, 부하 테스트 결과 인덱스 |
| [releases/v1.0.0-monolith.md](./releases/v1.0.0-monolith.md) | 모놀리식 릴리스 기준점 |

---

## 빠른 참조

```
새 API 개발 시:
  1. api/README.md — 공통 응답 형식 확인
  2. design/domain-design.md — 도메인 경계 확인
  3. design/db-schema.md — 테이블 구조 확인
  4. errors.md — 에러 코드 확인

전투 관련:
  → military.md + design/military-specs.md

건물 관련:
  → building.md + design/building-specs.md

경제 시스템:
  → payment.md + season.md + tax.md + global-vault.md
```
