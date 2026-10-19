#!/usr/bin/env bash
# The weekly funnel report (docs/ANALYTICS.md «Weekly report»): tools/analytics/funnel.py on the server's own
# database, the Markdown into $RUN_DIR/reports/funnel-<date>.md, the short summary to REPORT_WEBHOOK_URL (default:
# ALERT_WEBHOOK_URL; both blank = the file only). suld-funnel.timer runs it every Monday 09:10 Улаанбаатар (01:10 UTC):
#   funnel-report.sh [days]
# DB_PASS reaches psql through PGPASSWORD only, never on a command line.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env
days="${1:-7}"
script="$REPO_DIR/tools/analytics/funnel.py"
[[ -f "$script" ]] || die "no $script (REPO_DIR=$REPO_DIR)"
mkdir -p "$RUN_DIR/reports"
out="$RUN_DIR/reports/funnel-$(date -u +%F).md"
PGHOST="$DB_HOST" PGPORT="$DB_PORT" PGDATABASE="$DB_NAME" PGUSER="$DB_USER" PGPASSWORD="$DB_PASS" \
  REPORT_WEBHOOK_URL="${REPORT_WEBHOOK_URL:-${ALERT_WEBHOOK_URL:-}}" \
  python3 -I "$script" --days "$days" --out "$out" --webhook
ok "funnel report: $out"
