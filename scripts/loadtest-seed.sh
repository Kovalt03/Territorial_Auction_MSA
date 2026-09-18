#!/usr/bin/env bash
# 부하 테스트 시드 — 백엔드 시더 코드 없이 API+SQL로만 구성(재빌드 불필요).
#   1) signup API로 N명 부하 유저 생성(지갑 자동 생성)
#   2) SQL로 지갑 AP 대량 충전(입찰 에스크로용)
#   3) SQL로 기존 경매 end_at을 미래로 → 입찰 가능화
#
# 왜 API+SQL인가: Wallet 엔티티에 AP 세터가 없고, 경매는 이미 2500개 존재(map 시더 파생).
#   유저 생성만 signup(비번 해시+지갑)으로 하고, AP·경매시각은 loadtest DB에 직접 SQL — prod 코드 0.
#
# 주의(타임존): 경매 end_at은 TIMESTAMPTZ인데 서비스가 LocalDateTime.now()(KST=UTC+9)로 비교한다.
#   now()+interval는 UTC 기준이라 +9h 스큐를 넘기려면 넉넉히(2일) 준다. 안 그러면 "이미 종료된 경매".
#
# 사용:  ./scripts/loadtest-seed.sh [USER_COUNT]   (기본 200)
set -euo pipefail
cd "$(dirname "$0")/.."

USER_COUNT="${1:-200}"
PASSWORD='Load1234!'                 # 회원가입 정책 통과: 영문+숫자+특수, 8~20자
USER_HOST="http://localhost:8083"    # user-service 직접(시딩은 게이트웨이 우회)
EMAIL_DOMAIN="load.test"

docker info >/dev/null 2>&1 || { echo "❌ Docker 꺼짐"; exit 1; }
curl -sf "$USER_HOST/actuator/health" >/dev/null || { echo "❌ user-service(:8083) 미기동 — auction-path 먼저 up"; exit 1; }

echo "▶ 1) 부하 유저 $USER_COUNT 명 signup (기존 이메일은 409 → 무시)"
created=0; existed=0
for i in $(seq 1 "$USER_COUNT"); do
  id=$(printf "load%04d" "$i")
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$USER_HOST/api/v1/auth/signup" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$id\",\"email\":\"$id@$EMAIL_DOMAIN\",\"password\":\"$PASSWORD\",\"nickname\":\"$id\"}")
  case "$code" in
    201) created=$((created+1)) ;;
    409|400) existed=$((existed+1)) ;;
    *) echo "  ⚠️ $id → HTTP $code" ;;
  esac
done
echo "  생성 $created · 기존/중복 $existed"

echo "▶ 2) 부하 유저 지갑 AP 충전 (available_ap=1e9)"
docker exec msa-user-postgres psql -U user -d user -tA -c \
  "UPDATE wallets SET available_ap=1000000000, locked_ap=0
     WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'load%@${EMAIL_DOMAIN}');" \
  | xargs echo "  updated wallets:"

echo "▶ 3) 경매 end_at 미래로(2일) → 입찰 가능화 (TZ 스큐 버퍼 포함)"
docker exec msa-auction-postgres psql -U auction -d auction -tA -c \
  "UPDATE auctions SET end_at=now()+interval '2 days', max_extend_until=now()+interval '3 days';" \
  | xargs echo "  updated auctions:"

echo "✅ 시드 완료 — 다음: ./scripts/dump-tokens.sh $USER_COUNT"
