# 시스템 아키텍처

> 모놀리식 Spring Boot로 시작해 도메인 경계를 확립하고, 부하 측정 결과를 근거로 MSA로 전환했다. ✅ **전환 완료** — 모놀리식은 제거됐고 모든 도메인이 독립 서비스다(gateway·auction·user·combat·social·notification·item·season·ranking·map·admin·realtime). 서비스 토폴로지·이력은 [전환 허브](./msa/README.md), 서비스 간 계약은 [internal.md](../api/internal.md)가 기준이다.

아래 그림과 "패키지 경계"는 **MSA 전환 전 계층형 모놀리식 기준(역사적 참고)**이다 — 도메인 경계 자체는 그대로 서비스 경계가 됐다.

![Territorial Auction 계층형 시스템 아키텍처](../assets/architecture.svg)

## 런타임 구성

| 계층 | 책임 | 주요 기술 |
|---|---|---|
| Client | 플레이어·관리자 화면 접근 | Web Browser |
| Presentation | SPA 화면, 사용자 상호작용, API·STOMP 연결 | React, TypeScript, Vite, Tailwind CSS |
| Application | 인증, 도메인 규칙, REST API, 실시간 이벤트 | Spring Boot, Spring Security, JPA, WebSocket |
| Infrastructure | 영속화, durable 이벤트, 캐시·락·실시간 relay, 주기 작업 | PostgreSQL, Kafka, Redis, Flyway, Scheduler |

## 패키지 경계

```text
com.territorial.auction
├── domain/
│   ├── auction/        # 경매·입찰
│   ├── auth/           # 인증·JWT
│   ├── building/       # 건물·섬·보관함
│   ├── guild/          # 길드·멤버
│   ├── item/           # 아이템·결제
│   ├── map/            # 맵·영토·대륙
│   ├── military/       # 공성전·유닛
│   ├── notification/   # 알림
│   ├── season/         # 시즌 패스·트로피
│   ├── social/         # 채팅·친구
│   └── user/           # 프로필·설정·자산
└── global/             # 공통 응답·설정·예외·보안·검증
```

## 모놀리식 내부 도메인 협력 원칙

- 다른 도메인의 Service를 직접 주입하지 않는다.
- 단순 조회·참조는 필요한 Repository를 사용한다.
- 여러 도메인의 조정이나 후속 처리는 Spring Application Event로 연결한다.

예: 입찰 성공 시 알림 이벤트를 발행하고, 알림 도메인이 수신 설정을 확인한 뒤 알림을 생성·전송한다.

## 실시간 통신

| 항목 | 값 |
|---|---|
| 연결 | `/ws` (SockJS) |
| 클라이언트 → 서버 | `/pub` |
| 서버 → 클라이언트 | `/sub` |
| 인증 | STOMP CONNECT 단계에서 JWT 검증 |
| 현재 브로커 | Simple In-Memory Broker |
| 확장 후보 | Redis Pub/Sub 또는 RabbitMQ STOMP Relay |

상세 메시지 규약은 [WebSocket 문서](../api/websocket/README.md)를 참고한다.

## MSA 런타임 (현재 — 전환 완료)

부하 테스트에서 단일 인기 경매의 지속 경합이 병목으로 확인돼 auction을 첫 서비스로 추출한 뒤, Strangler로 전 도메인을 추출하고 모놀리식을 제거했다. 현재 `docker-compose.msa.yml`은 gateway + 도메인 서비스 11개(auction·user·combat·social·notification·item·season·ranking·map·admin·realtime) + 서비스별 DB + Kafka/Redis로 구성된다([구동](./msa/local-run.md)).

```text
            ┌───────────────┐
Client ───▶ │  API Gateway  │  JWT 검증 → X-User-Id 주입, 경로 라우팅 (/ws → realtime)
            └──────┬────────┘
   ┌───────────────┼───────────────┬───────────────┬───────────────┐
   ▼               ▼               ▼               ▼               ▼
auction         user            combat          map             realtime
social · notification · item · season · ranking · admin   (각 서비스 전용 DB, gateway·realtime은 무상태)
   └────── /internal HTTP 계약 · Kafka(durable) · Redis(저지연) 이벤트로 협력 ──────┘
```

- **게이트웨이**(Spring Cloud Gateway): 모든 공개 경로를 각 소유 서비스로 라우팅하고 `/ws`는 realtime-service로 보낸다(미매핑 fallback 없음). 유입 인증 헤더를 제거하고 유효 JWT의 subject를 다시 주입하며 combat 등 요청에는 gateway 전용 토큰도 넣는다.
- **DB 분리**: 각 서비스가 전용 PostgreSQL을 소유하며 다른 서비스 DB를 직접 조회하지 않는다.
- **동기 통신**(`/internal`): 예) user-service가 프로필/지갑 합성 시 combat·season·map을 조회, combat-service가 season benefit·map context를 조회, admin-service가 각 서비스에 관리 작업을 위임. [계약](../api/internal.md)
- **비동기 통신**: Kafka가 경매·user·combat 이벤트의 durable 경로를, Redis pub/sub이 WebSocket 저지연 경로(realtime-service relay)를 담당한다. [내부 계약](../api/internal.md)
- **읽기 프로젝션**: 맵 그리드가 auction 테이블을 매번 조회하던 것을 map-service 로컬 read-model(`territory_auction_status`, 이벤트로 유지)로 대체 → 핫패스를 auction 경합에서 격리(부하 실측 p99 ~10배 개선).

## 최종 서비스 토폴로지

게임플레이 결합도 기준으로 도메인을 서비스로 묶었다. 상세·근거·전환 이력은 [MSA 전환 허브](./msa/README.md)가 기준.

| 서비스 | 포함 도메인 |
|---|---|
| auction-service | auction |
| map-service | map (공유 커널, 최후 추출) |
| combat-service | military, building |
| user-service | user, auth (OAuth·지갑·프로필) |
| social-service | social, guild |
| notification-service | notification |
| item-service | item |
| season-service | season |
| ranking-service | ranking |
| admin-service | admin |
| realtime-service | WebSocket 허브 (무상태) |

초기 검토안에서 조정: **auth→user 병합**, **notification 독립**, **economy를 item·season으로 분리**, **ranking·admin·realtime 추가**. **map은 공유 커널**이라 territory를 여러 도메인이 참조하므로 특정 서비스에 병합하지 않고 전환 최후에 분리했다. 공성전은 military 소속이라 combat-service에 포함.

관련 자료: [MSA 전환 허브](./msa/README.md), [도메인 설계](./domain-design.md), [성능 테스트 가이드](./performance-testing.md)
