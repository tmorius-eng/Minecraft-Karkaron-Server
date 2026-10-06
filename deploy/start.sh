#!/usr/bin/env bash
# Start the SULD server.
#   start.sh            start via systemd and wait until the server reports "Done"
#   start.sh --direct   launch Paper inside a tmux session (what suld.service executes)
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env

if [[ "${1:-}" == "--direct" ]]; then
  [[ -f "$PAPER_JAR" ]] || die "Paper jar missing: $PAPER_JAR (run deploy.sh)"
  [[ -f "$SERVER_DIR/eula.txt" ]] || die "eula.txt missing: set ACCEPT_EULA=true and re-run deploy.sh"
  mkdir -p "$RUN_DIR"
  if server_running; then
    echo "SULD already running (tmux session '$TMUX_SESSION')"
    exit 0
  fi
  # shellcheck disable=SC2086  # AIKAR_FLAGS is intentionally word-split into JVM flags
  cmd="exec java -Xms${JVM_MIN} -Xmx${JVM_MAX} ${AIKAR_FLAGS:-} -jar ${PAPER_JAR} --nogui"
  service_user_exec new-session -d -s "$TMUX_SESSION" -c "$SERVER_DIR" "$cmd"
  chmod 660 "$TMUX_SOCK" 2>/dev/null || true
  echo "SULD launching in tmux session '$TMUX_SESSION'"
  exit 0
fi

need_root "$@"
started="$(date +%s)"
systemctl start suld
log "Waiting for the server to finish booting (up to 180s)..."
if wait_ready 180 "$started"; then
  ok "SULD is up. Console: $BIN_DIR/console.sh"
else
  die "Server did not report 'Done' in time. Check: journalctl -u suld -n 50; tail $SERVER_DIR/logs/latest.log"
fi
