#!/bin/bash
# 실행 중인 컨테이너에 개발용 더미데이터를 주입합니다.
# 사용: ./scripts/seed-dev.sh

set -e

CONTAINER="territorial-postgres"

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "ERROR: ${CONTAINER} 컨테이너가 실행 중이지 않습니다."
  echo "       'docker compose up -d' 를 먼저 실행하세요."
  exit 1
fi

echo ">>> 더미데이터 주입 중..."
docker exec -i "$CONTAINER" psql -U postgres -d territorial_auction \
  < "$(dirname "$0")/../docker/postgres/seed.sql"

echo ">>> 완료"
docker exec "$CONTAINER" psql -U postgres -d territorial_auction -c "
SELECT 'seasons'                   AS tbl, COUNT(*) FROM seasons
UNION ALL SELECT 'season_passes',          COUNT(*) FROM season_passes
UNION ALL SELECT 'season_pass_level_rewards', COUNT(*) FROM season_pass_level_rewards
UNION ALL SELECT 'users (testuser)',        COUNT(*) FROM users WHERE username = 'testuser'
UNION ALL SELECT 'wallets',                COUNT(*) FROM wallets;"
