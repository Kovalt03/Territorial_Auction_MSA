# 테스트 및 검증

> 최종 검증 기준: 2026-08-21. 실행 명령과 상세 시나리오는 각 링크된 문서를 따른다.

## API 계약

REST API의 인증, 공통 응답·오류 형식, 도메인별 엔드포인트는 [API 명세 인덱스](../api/README.md)에 정리되어 있다. 실시간 채널·메시지 계약은 [WebSocket/STOMP 문서](../api/websocket/README.md)를 참고한다.

## 단위·통합 테스트

| 영역 | 현재 구성 | 검증 초점 | 실행 |
|---|---|---|---|
| Backend | JUnit 5·Mockito, 테스트 클래스 48개 | Service 성공·실패·경계값, 실제 인프라 연동 | `cd backend && ./gradlew spotlessCheck test gatlingClasses` |
| Frontend | Vitest·React Testing Library, 테스트 파일 3개 | 훅 상태 전이, 공유 컴포넌트 렌더링·상호작용 | `cd frontend && npm run test:run && npm run build` |

테스트 작성 원칙과 범위는 [테스트 전략](../design/testing.md)을 참고한다.

## 부하 테스트 요약

| 시나리오 | 부하 | 핵심 결과 | 상세 보고서 |
|---|---|---|---|
| 우선순위 혼합 Soak | 맵 30 + 경매 20 VU, 1시간 | 71,665 요청, 실패 0, 전체 p95 21ms / p99 247ms | [재검증 보고서](../../report/load/2026-08-21-all-priority-soak-etag.md) |
| STOMP fan-out | 구독자 100명, 메시지 1건 | 100/100 수신, p95 70ms, 실패 0 | [fan-out 보고서](../../report/load/2026-08-21-all-stomp-fanout.md) |
| 단일 경매 집중 Stress | 50→400 VU | 438.09 RPS, 지속 경합에서 p95 1,582ms | [비교·분석 보고서](../../report/perf/2026-08-20-all-priority1-3-comparison.md) |

혼합 Soak에서 확인된 맵 응답 병목은 ETag 조건부 조회로 해소했다. 단일 인기 경매의 지속 경합 한계는 향후 MSA 전환 검토에서 Auction을 첫 분리 후보로 판단한 근거로 남긴다.

## 참고 자료

- [성능·부하 테스트 가이드](../design/performance-testing.md)
- [v1.0.0 모놀리식 릴리스 기준](../releases/v1.0.0-monolith.md)
- [구현 체크리스트](../checklist.md)
