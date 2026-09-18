#!/usr/bin/env bash
# 부하 서브셋의 "서버별 리소스 독립성"을 검증한다.
#
# 무엇을 증명하는가 (cgroup가 실제로 격리하는 것):
#   [메모리] 컨테이너마다 하드 상한 → A가 폭주해도 B의 RAM을 못 먹는다. 초과 시 A만 OOM-kill, B 무영향. (강한 격리)
#   [CPU]    컨테이너마다 쿼터(cpus) 상한 → A가 무한루프여도 자기 상한 이상 코어를 못 가져간다. (상한 격리)
#   [DB]     서비스마다 전용 Postgres 컨테이너(자기 상한) → 커넥션·버퍼 독립.
#
# 무엇은 격리 못 하는가 (정직하게):
#   [디스크 I/O] blkio 미설정 + Docker Desktop은 VM 단일 가상디스크 → PG들이 I/O 대역을 공유. write 폭주는 서로 영향 가능.
#   [CPU 예약]   cpus는 상한(cap)이지 예약(reservation)이 아니다. 총합이 Docker 코어를 초과하면(오버서브스크립션) 부하 시 스케줄러에서 경합. 이 서브셋은 약간 오버서브(≈6.75/6코어)라 완전 예약은 아님.
#   → 즉 "메모리·DB는 독립, CPU는 상한으로 경계, 디스크는 잔여 결합" 이 16GB 단일 머신의 정직한 천장이다.
#
# 사용:  ./scripts/loadtest-verify-isolation.sh [auction-path|map-path|combat-path]   (기본 auction-path)
set -euo pipefail
cd "$(dirname "$0")/.."

PROFILE="${1:-auction-path}"
COMPOSE=(docker compose -f docker-compose.msa.yml -f docker-compose.loadtest.yml --profile "$PROFILE")

# 프로파일별 (컨테이너명, host포트) — 포트 없으면 '-' (스크레이프 스킵)
declare -A SCRAPE=(
  [msa-gateway]=8090 [msa-auction-service]=8082 [msa-user-service]=8083
  [msa-map-service]=8091 [msa-combat-service]=8084
)

echo "==================================================================="
echo " 리소스 독립성 검증 — profile: $PROFILE"
echo "==================================================================="

docker info >/dev/null 2>&1 || { echo "❌ Docker 데몬이 꺼져 있다. Docker Desktop을 먼저 켜라."; exit 1; }

echo; echo "▶ 1) 서브셋 기동 (빌드 포함, 최초는 수 분 소요)"
"${COMPOSE[@]}" up -d --build

echo; echo "▶ 2) 서비스 헬스 대기 (최대 180s) — Spring 서비스는 compose healthcheck가 없어 HTTP /actuator/health로 직접 폴링"
running=$("${COMPOSE[@]}" ps --format '{{.Names}}')
deadline=$((SECONDS+180))
while :; do
  pending=""
  for c in "${!SCRAPE[@]}"; do
    echo "$running" | grep -qx "$c" || continue
    curl -sf "http://localhost:${SCRAPE[$c]}/actuator/health" >/dev/null 2>&1 || pending+=" $c"
  done
  [ -z "$pending" ] && { echo "  ✅ 대상 서비스 전부 health OK"; break; }
  [ $SECONDS -ge $deadline ] && { echo "  ⚠️ 미준비(계속 진행):$pending"; break; }
  sleep 3
done

echo; echo "▶ 3) cgroup 상한이 설정값대로 강제됐나 (docker inspect)"
printf "  %-24s %12s %10s\n" "컨테이너" "MEM_LIMIT" "CPUS"
for c in $("${COMPOSE[@]}" ps --format '{{.Names}}'); do
  mem=$(docker inspect -f '{{.HostConfig.Memory}}' "$c")
  nano=$(docker inspect -f '{{.HostConfig.NanoCpus}}' "$c")
  memh=$(awk "BEGIN{printf \"%.0fM\", $mem/1024/1024}")
  cpus=$(awk "BEGIN{printf \"%.2f\", $nano/1000000000}")
  mark="✅"; [ "$mem" = "0" ] && mark="⚠️무제한"
  printf "  %-24s %12s %10s  %s\n" "$c" "$memh" "$cpus" "$mark"
done

echo; echo "▶ 4) /actuator/prometheus 노출 확인 (계측 살아있나)"
for c in "${!SCRAPE[@]}"; do
  running=$("${COMPOSE[@]}" ps --format '{{.Names}}' | grep -x "$c" || true)
  [ -z "$running" ] && continue
  port=${SCRAPE[$c]}
  if curl -sf "http://localhost:${port}/actuator/prometheus" | grep -q 'jvm_memory_used_bytes'; then
    echo "  ✅ $c (:$port) — prometheus OK, jvm_memory_used 노출"
  else
    echo "  ❌ $c (:$port) — 스크레이프 실패(프로파일/포트/기동 확인)"
  fi
done

echo; echo "▶ 5) 현재 실사용 스냅샷 (MEM USAGE/LIMIT = 컨테이너별 독립 상한)"
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}'

cat <<'EOF'

▶ 다음 — 부하 중 독립성 관측:
  1) 별 터미널에서 실시간 관측:   docker stats
  2) 부하 생성기는 Docker 밖에서:  ./gradlew :loadtest:gatlingRun ...   (미구성 — #4)
  3) 관측 포인트:
     · 부하 서비스 CPU%가 자기 cpus 상한(예: 100%=1.0코어)에서 '평평'해지면 → 상한 격리 작동(다른 서비스 코어 못 뺏음)
     · 부하 서비스 MEM이 자기 LIMIT 근처에서 멈추면 → 메모리 격리 작동
     · 비부하 서비스 지표가 거의 안 흔들리면 → 독립. 같이 튀면 디스크 I/O 잔여결합 의심(위 주석 참고)
EOF
echo "완료."
