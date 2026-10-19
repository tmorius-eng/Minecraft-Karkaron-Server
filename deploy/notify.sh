#!/usr/bin/env bash
# Post one alert to the operators' webhook (Discord-compatible JSON), if one is configured:
#   notify.sh "<title>" "<text>"
# ALERT_WEBHOOK_URL comes from /etc/suld/suld.env (never echoed, never written to a log). With it blank this is a no-op
# that exits 0, so every caller (health timer, failed units, backup) behaves the same with or without alerting.
set -uo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
ALLOW_EXAMPLE_ENV="${ALLOW_EXAMPLE_ENV:-0}" load_env
title="${1:-SULD}"; text="${2:-}"
[[ -n "${ALERT_WEBHOOK_URL:-}" ]] || exit 0
host="$(hostname -s 2>/dev/null || echo suld)"
# the body is built by python so quotes, newlines and non-ASCII in the text never break the JSON; the URL is
# passed through the environment, never through argv (ps would show it)
body="$(TITLE="$title" TEXT="$text" HOST="$host" python3 -I -c 'import json, os
text = os.environ["TEXT"][:1500]
print(json.dumps({"content": "**[" + os.environ["HOST"] + "] " + os.environ["TITLE"] + "**\n" + text}))')"
if ! WEBHOOK="$ALERT_WEBHOOK_URL" curl -fsS --max-time 10 -H 'Content-Type: application/json' -d "$body" "$ALERT_WEBHOOK_URL" >/dev/null 2>&1; then
  echo "notify: the webhook did not accept the alert ($title)" >&2
  exit 1
fi
