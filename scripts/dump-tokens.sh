#!/usr/bin/env bash
# 부하 유저 N명을 로그인해 accessToken을 Gatling feeder용 CSV로 덤프한다.
#   시나리오마다 로그인부터 하면 user-service가 병목처럼 보이는 착시가 생기므로 사전에 토큰을 뽑아둔다.
#
# 출력:  loadtest/src/gatling/resources/tokens.csv  (헤더 'token' + 1줄당 토큰 1개)
# 사용:  ./scripts/dump-tokens.sh [USER_COUNT]   (기본 200 — loadtest-seed.sh와 동일 수)
set -euo pipefail
cd "$(dirname "$0")/.."

USER_COUNT="${1:-200}"
PASSWORD='Load1234!'
USER_HOST="http://localhost:8083"
EMAIL_DOMAIN="load.test"
OUT="loadtest/src/gatling/resources/tokens.csv"

curl -sf "$USER_HOST/actuator/health" >/dev/null || { echo "❌ user-service(:8083) 미기동"; exit 1; }
mkdir -p "$(dirname "$OUT")"

echo "▶ $USER_COUNT 명 로그인 → 토큰 덤프"
echo "token" > "$OUT"
ok=0; fail=0
for i in $(seq 1 "$USER_COUNT"); do
  id=$(printf "load%04d" "$i")
  tok=$(curl -s -X POST "$USER_HOST/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$id@$EMAIL_DOMAIN\",\"password\":\"$PASSWORD\"}" \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  if [ -n "$tok" ]; then echo "$tok" >> "$OUT"; ok=$((ok+1)); else fail=$((fail+1)); fi
done
echo "  성공 $ok · 실패 $fail → $OUT ($(( $(wc -l < "$OUT") - 1 )) 토큰)"
