#!/usr/bin/env bash
# Stop the SULD server gracefully (saves the world and all player profiles).
#   stop.sh [--warn SECONDS]   stop via systemd, optionally warning players first
#   stop.sh --direct           graceful stop inside tmux (what suld.service executes)
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env

graceful_stop() {
  server_running || { echo "SULD not running"; return 0; }
  console_send "stop"
  local waited=0
  while server_running && ((waited < 90)); do
    sleep 2
    waited=$((waited + 2))
  done
  if server_running; then
    warn "Server still running after 90s; forcing the session closed."
    service_user_exec kill-session -t "$TMUX_SESSION" || true
  fi
}

if [[ "${1:-}" == "--direct" ]]; then
  graceful_stop
  exit 0
fi

need_root "$@"
warn_secs=0
if [[ "${1:-}" == "--warn" ]]; then
  warn_secs="${2:?--warn needs seconds}"
fi
if ((warn_secs > 0)) && server_running; then
  console_send "say Сервер ${warn_secs} секундын дараа дахин ачаалагдана / Server stopping in ${warn_secs}s"
  sleep "$warn_secs"
fi
systemctl stop suld
ok "SULD stopped."
