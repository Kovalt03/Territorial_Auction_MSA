# 개발 워크플로

## 브랜치 흐름

```text
feature/* → dev → main
```

- `feature/*`: 기능·수정 단위 작업 브랜치
- `dev`: 로컬 개발 통합 브랜치
- `main`: 릴리스와 외부 호환성 설정의 기준 브랜치

`main`의 외부 호환성 설정은 자동 배포를 의미하지 않는다. 현재 로컬 풀스택은 [MSA 로컬 구동](../design/msa/local-run.md)(`docker-compose.msa.yml`)이다. 프로덕션 배포 구성은 재작성 예정.

## 이름과 커밋

- 브랜치: `feature/{be|fe|all}-{number}-{description}`
- 커밋: `[FEAT] 설명`, `[FIX] 설명`, `[TEST] 설명`, `[DOCS] 설명` 형식
- 한 커밋에는 하나의 응집된 변경 목적을 담는다.

## 검증과 Pull Request

1. 변경 범위에 맞는 테스트와 정적 검사를 실행한다.
2. 기능 브랜치에서 `dev`를 대상으로 Pull Request를 생성한다.
3. CI 결과와 변경 내용을 확인한 뒤 병합한다.
4. 릴리스·외부 호환성 기준을 갱신할 때 `dev`의 검증된 변경을 `main`에 반영한다.

## 참고

- [테스트 전략](../design/testing.md)
- [로컬 운영 실행 가이드](../operations/local-production.md)
- [릴리스 기준점](../releases/v1.0.0-monolith.md)
