# 시스템 아키텍처

> 현재 구조는 Spring Boot 모놀리식이다. 도메인 경계를 먼저 확립하고, 성능 측정 결과를 근거로 MSA 전환을 검토한다.

![Territorial Auction 계층형 시스템 아키텍처](../assets/architecture.svg)

## 런타임 구성

| 계층 | 책임 | 주요 기술 |
|---|---|---|
| Client | 플레이어·관리자 화면 접근 | Web Browser |
| Presentation | SPA 화면, 사용자 상호작용, API·STOMP 연결 | React, TypeScript, Vite, Tailwind CSS |
| Application | 인증, 도메인 규칙, REST API, 실시간 이벤트 | Spring Boot, Spring Security, JPA, WebSocket |
| Infrastructure | 영속화, 캐시·락·토큰, 주기 작업 | PostgreSQL, Redis, Flyway, Scheduler |

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

## 도메인 협력 원칙

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

## MSA 전환 검토 기준

현재 패키지 경계는 향후 분리 후보 경계다. 분리는 선행하지 않고, 부하 테스트에서 병목·독립 확장 필요성·데이터 정합성 비용이 확인될 때 검토한다.

| 분리 후보 | 포함 도메인 |
|---|---|
| auth-service | auth |
| user-service | user |
| map-service | map |
| auction-service | auction |
| combat-service | military, building |
| social-service | social, notification, guild |
| economy-service | item, season |

관련 자료: [도메인 설계](./domain-design.md), [성능 테스트 가이드](./performance-testing.md), [v1.0.0 릴리스 기준](../releases/v1.0.0-monolith.md)
