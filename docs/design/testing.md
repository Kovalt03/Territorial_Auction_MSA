# 테스트 전략

## 범위와 도구

| 영역 | 도구 | 중점 |
|---|---|---|
| Backend 단위 테스트 | JUnit 5, Mockito | Service의 성공·실패·경계값 |
| Backend 통합 검증 | Spring Boot Test, 실제 PostgreSQL·Redis | 애플리케이션 컨텍스트와 인프라 연동 |
| Frontend 단위·컴포넌트 테스트 | Vitest, React Testing Library | 훅 상태 전이와 사용자 상호작용 |
| 성능·부하 테스트 | Gatling Java DSL | 경매·맵·공성·채팅·Soak 정합성 |

## 작성 원칙

- Backend는 Service 단위 테스트를 우선하며, Repository 자체나 Lombok 생성 코드는 테스트하지 않는다.
- DB·Redis 동작을 확인해야 하는 테스트는 mock 데이터베이스 대신 실제 인프라를 사용한다.
- Frontend는 비즈니스 로직을 훅 단위로, UI는 역할 기반 질의와 사용자 행동으로 검증한다.
- 페이지 전체 스타일이나 외부 라이브러리 내부 동작은 수동 QA 또는 통합 검증 범위로 둔다.

## 실행

```bash
# backend/
./gradlew spotlessCheck test gatlingClasses

# frontend/
npm run test:run
npm run build
```

Gatling 부하 테스트는 MSA 전환으로 재구성이 필요하다(구 `backend/src/gatling`은 제거). 부하 종류·합격 기준·재구성 방향은 [성능 테스트 가이드](./performance-testing.md)를 참고한다.

## 참고

- [시스템 아키텍처](./architecture.md)
- [성능 테스트 가이드](./performance-testing.md)
- [v1.0.0 릴리스 기준](../releases/v1.0.0-monolith.md)
