#!/bin/bash
# 관리자 계정 일회성 이관: 모놀리식 users(role=ADMIN) → admin-service admin_accounts.
#
# admin-service 추출로 관리자 신원이 게임 users 테이블에서 분리됐다. 기존 운영 관리자
# (이메일·BCrypt 비밀번호 해시·TOTP 시크릿)를 admin 전용 DB로 옮긴다. BCrypt 인코더가
# 동일해 해시를 그대로 이관하면 비밀번호·2FA가 그대로 동작한다.
#
# 크로스-DB(모놀 postgres ↔ admin-postgres)라 소스에서 SQL-안전한 INSERT 문을 생성해
# 타겟 psql로 파이프한다. ON CONFLICT (email) DO NOTHING 이라 여러 번 실행해도 안전하다.
#
# 사용:
#   ./migrate-admin-accounts.sh              # docker 모드(compose.msa 컨테이너)
#   ./migrate-admin-accounts.sh --dry-run    # 생성될 INSERT만 출력(적용 안 함)
#   SRC_DSN=... DST_DSN=... ./migrate-admin-accounts.sh   # 운영(원격 DSN)
#
# 운영 DSN 예: SRC_DSN="postgresql://postgres:pw@monolith-host:5432/territorial_auction"
#              DST_DSN="postgresql://admin:pw@admin-host:5432/admin"

set -euo pipefail

# ── 연결 설정 ─────────────────────────────────────────────
# DSN이 있으면 원격(운영), 없으면 docker exec(로컬 compose).
SRC_DSN="${SRC_DSN:-}"
DST_DSN="${DST_DSN:-}"
SRC_CONTAINER="${SRC_CONTAINER:-msa-postgres}"
SRC_USER="${SRC_USER:-postgres}"
SRC_DB="${SRC_DB:-territorial_auction}"
DST_CONTAINER="${DST_CONTAINER:-msa-admin-postgres}"
DST_USER="${DST_USER:-admin}"
DST_DB="${DST_DB:-admin}"

DRY_RUN=0
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=1

src_psql() {
  if [[ -n "$SRC_DSN" ]]; then
    psql "$SRC_DSN" -v ON_ERROR_STOP=1 "$@"
  else
    docker exec -i "$SRC_CONTAINER" psql -U "$SRC_USER" -d "$SRC_DB" -v ON_ERROR_STOP=1 "$@"
  fi
}

dst_psql() {
  if [[ -n "$DST_DSN" ]]; then
    psql "$DST_DSN" -v ON_ERROR_STOP=1 "$@"
  else
    docker exec -i "$DST_CONTAINER" psql -U "$DST_USER" -d "$DST_DB" -v ON_ERROR_STOP=1 "$@"
  fi
}

# ── 소스에서 SQL-안전한 INSERT 문 생성 ─────────────────────
# format(%L)이 따옴표·NULL·특수문자를 안전하게 처리한다.
# 모놀 role은 ADMIN 단일값 → admin_accounts.role='ADMIN'(SUPER_ADMIN 승격은 이관 후 운영자가).
# 모놀 status(ACTIVE/SUSPENDED/WITHDRAWN) → admin status는 ACTIVE 외 전부 SUSPENDED.
GEN_SQL="COPY (
  SELECT format(
    E'INSERT INTO admin_accounts (email, password_hash, totp_secret, role, status) VALUES (%L, %L, %L, %L, %L) ON CONFLICT (email) DO NOTHING;',
    email,
    password_hash,
    totp_secret,
    'ADMIN',
    CASE WHEN status = 'ACTIVE' THEN 'ACTIVE' ELSE 'SUSPENDED' END
  )
  FROM users
  WHERE role = 'ADMIN'
) TO STDOUT;"

echo ">>> 소스(모놀 users role=ADMIN)에서 이관 대상 조회..."
INSERTS="$(src_psql -tA -c "$GEN_SQL")"

if [[ -z "$INSERTS" ]]; then
  echo ">>> 이관할 관리자 계정이 없습니다(role=ADMIN 없음). 종료."
  exit 0
fi

echo ">>> 이관 대상 $(echo "$INSERTS" | grep -c 'INSERT INTO') 건(이메일만 표시 — 해시·TOTP 시크릿은 미노출):"
echo "$INSERTS" | grep -oE "VALUES \('[^']+'" | sed -E "s/VALUES \('(.*)'/  - \1/"

if [[ "$DRY_RUN" == "1" ]]; then
  echo ">>> --dry-run: 적용하지 않고 종료."
  exit 0
fi

# ── 타겟에 적용(멱등) ──────────────────────────────────────
echo ">>> admin_accounts에 적용 중(ON CONFLICT DO NOTHING)..."
echo "$INSERTS" | dst_psql -f -

echo ">>> 완료. 현재 admin_accounts:"
dst_psql -c "SELECT id, email, role, status, (totp_secret IS NOT NULL) AS totp_enrolled, created_at FROM admin_accounts ORDER BY id;"
