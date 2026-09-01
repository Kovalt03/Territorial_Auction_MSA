# user-service 추출

user-service는 `user`, `auth` 도메인의 최종 소유 서비스다. 현재 브랜치는 독립 실행과
Auction 의존성 전환을 먼저 확보하는 기반 단계이며, 모놀리스의 사용자 도메인을 아직
삭제하지 않는다.

## 현재 소유 경계

| 기능·데이터 | 현재 쓰기 소유자 | 상태 |
|---|---|---|
| 일반 회원가입·로그인·토큰 갱신 | user-service | 전환 |
| User·Wallet·GlobalVault 원본 | user-service | 신규 사용자 기준 전환 |
| Auction AP escrow·정산·환불 | user-service | 전환 |
| AP 결제 충전 | user-service | 전환 |
| 모놀리스 게임 초기화 | 모놀리스 read-model | `user.created` 소비로 생성 |
| 건물·아이템·시즌 AP/GP 변경 | 모놀리스 | 미전환 |
| 프로필·알림 설정·회원 수정·탈퇴 | 모놀리스 | 미전환 |
| OAuth2·관리자 사용자 관리 | 모놀리스 | 미전환 |

## 가입 흐름

1. user-service가 User, Wallet, GlobalVault와 outbox 레코드를 한 DB 트랜잭션에 저장한다.
2. outbox publisher가 Kafka `user-events`에 `user.created`를 발행하고 성공 시 발행 완료를 기록한다.
3. 모놀리스 `backend-user-projection` consumer group이 User 최소 프로젝션,
   NotificationSetting, UserProfile, HomeIsland와 기본 성을 멱등 생성한다.
4. 실패 레코드는 commit하지 않아 재전달하고, 성공한 offset만 commit한다.

신규 ID는 `1,000,000,000`부터 발급한다. 모놀리스 기존 identity 대역과 충돌을 막기 위한
Strangler 임시 정책이며 서비스 간 관계는 DB FK가 아니라 ID로만 연결한다.
재전달 충돌 검사는 변경 가능한 nickname이 아니라 ID·username·email로 source identity를 확인한다.

## 지갑 정합성

Auction은 `X-Internal-Service-Token`을 포함한 내부 API로만 user-service 지갑을 변경한다.
각 명령은 command key와 request fingerprint를 저장한다.

- 같은 key·같은 payload: 이미 성공한 재시도로 보고 200
- 같은 key·다른 payload: `WALLET_COMMAND_CONFLICT`(409)
- 잔액 부족 또는 locked AP 부족: `INSUFFICIENT_AP`(409)
- 0 이하 금액: `INVALID_WALLET_AMOUNT`(400)

## 완료 전 필수 작업

- building, item, season, admin의 `WalletRepository` 직접 쓰기를 user-service 명령으로 전환
- `GlobalVaultRepository` 소유 위치를 확정하고 모든 AP/GP 조회·쓰기를 단일 원본으로 통합
- 프로필·알림 설정·닉네임·비밀번호·탈퇴 API와 이벤트 전환
- OAuth2와 관리자 사용자 관리 전환
- 모놀리스 User/Wallet/GlobalVault 원본 테이블과 기존 auth/user 코드 제거
- 서비스 간 계약·통합·장애 복구 테스트 및 전체 compose 검증

위 항목 전에는 공개 지갑 API가 user-service DB를 표시하더라도 전체 user-service 추출을
완료로 표시하지 않는다.

## 현재 통합 제한

이 기반 브랜치만으로는 `dev` cutover를 완료할 수 없다.

- 기존 모놀리스 사용자와 OAuth2 신규 사용자는 user-service DB에 없어 지갑 조회·충전·입찰이 실패한다.
- 정지·탈퇴 상태 쓰기는 모놀리스에 남아 user-service 로그인 정책에 반영되지 않는다.
- 신규 일반 가입자는 모놀리스 최소 User 프로젝션만 가지므로 기존 `/users/me`, 비밀번호,
  탈퇴, 닉네임 API를 정상 사용할 수 없다.
- 정산 중 영토 점유 이후 후속 단계가 실패할 때의 전체 saga 보상은 아직 완료되지 않았다.

따라서 이 브랜치는 `msa/user-service` 내부 기반 단계로만 합치고, 위 경로와 데이터 이관을
완료하기 전에는 통합 브랜치를 `dev`로 PR하지 않는다.
