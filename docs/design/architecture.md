# 시스템 아키텍처

> 모놀리식 Spring Boot로 시작해 도메인 경계를 확립하고, 부하 측정 결과를 근거로 **MSA 전환에 착수**했다. 현재 auction-service와 user-service 추출이 완료됐으며 combat-service를 설계·이관 중이다. 아래 "MSA 런타임(현재)"과 [전환 허브](./msa/README.md)를 기준으로 한다.

아래 그림은 MSA 전환 전 계층형 모놀리식 기준 구조다.

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

## MSA 런타임 (현재)

부하 테스트에서 단일 인기 경매의 지속 경합이 병목으로 확인돼 auction을 첫 서비스로 추출했고, 이어 user/auth를 user-service로 추출했다. 현재 로컬 런타임은 잔여 모놀리식 + auction-service + user-service + gateway로 구성된다([구동](./msa/local-run.md)).

```text
            ┌───────────────┐
Client ───▶ │  API Gateway  │  JWT 검증 → X-User-Id 주입, 경로 라우팅
            └──────┬────────┘
 /api/v1/auctions/**   user 소유 경로       그 외 · /ws
          │                  │                  │
          ▼                  ▼                  ▼
 ┌───────────────┐  ┌──────────────┐  ┌──────────────┐
 │auction-service│  │ user-service │  │  모놀리식     │
 │  (auction DB) │  │  (user DB)   │  │(잔여 도메인 DB│
 └───────┬───────┘  └──────┬───────┘  │ + realtime WS)│
         └──── 내부 HTTP·비동기 이벤트 ┴──┤              │
                                         └──────────────┘
```

- **게이트웨이**(Spring Cloud Gateway): auction 경로는 auction-service, 인증과 user-service 소유 프로필 명령은 user-service, 그 외와 `/ws`는 모놀리식으로 보낸다. 유입 `X-User-Id`를 제거하고 유효 JWT의 subject를 다시 주입한다.
- **DB 분리**: auction-service와 user-service는 각각 `auction-postgres`, `user-postgres`를 소유하며 다른 서비스 DB를 직접 조회하지 않는다.
- **동기 통신**(`/internal`): auction-service는 AP escrow·정산을 user-service에 요청하고 영토·초기 성 명령은 모놀리식에 요청한다. 모놀리식의 일반 AP 명령과 OAuth provisioning도 user-service 계약을 사용한다. [계약](../api/internal.md)
- **비동기 통신**: Kafka가 경매 생성·프로젝션·랭킹·시즌과 user projection의 durable 경로를 담당한다. Redis pub/sub은 auction 입찰·정산의 WebSocket 저지연 경로에만 병행한다. [내부 계약](../api/internal.md)
- **읽기 프로젝션**: 맵 그리드가 auction 테이블을 매번 조회하던 것을 로컬 read-model(`territory_auction_status`, 이벤트로 유지)로 대체 → 핫패스를 auction 경합에서 격리(부하 실측 p99 ~10배 개선).

## MSA 목표 토폴로지 (7개)

패키지 경계 = 분리 후보 경계. 게임플레이 결합도 기준으로 도메인을 묶는다. 상세·근거·전환 순서는 [MSA 전환 허브](./msa/README.md)가 기준.

| 서비스 | 포함 도메인 |
|---|---|
| auction-service | auction |
| map-service | map (공유 커널, 최후 추출) |
| combat-service | military, building |
| user-service | user, auth |
| social-service | social, guild |
| notification-service | notification |
| economy-service | item, season |

초기 검토안(auth·map 독립, notification을 social에 포함)에서 조정: **auth→user 병합**(인증은 유저와 밀접), **notification 독립**(다수 서비스가 발행하는 횡단 채널). **map은 독립 유지** — territory를 8개 도메인이 참조하는 공유 커널이라 특정 서비스에 병합하지 않고, 전환 최후에 분리한다. ranking(미구현)은 추후 별도. 공성전은 military 소속이므로 combat-service에 포함.

관련 자료: [MSA 전환 허브](./msa/README.md), [도메인 설계](./domain-design.md), [성능 테스트 가이드](./performance-testing.md)
