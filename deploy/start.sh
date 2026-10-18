#!/usr/bin/env bash
# Start the SULD server.
#   start.sh            start via systemd and wait until the server reports "Done"
#   start.sh --direct   launch Paper inside a tmux session (what suld.service executes)
#   start.sh --proxy-direct   launch the Velocity proxy inside its tmux session (suld-proxy.service, AUTH_MODE=hybrid)
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env

if [[ "${1:-}" == "--proxy-direct" ]]; then
  hybrid || die "the proxy runs only with AUTH_MODE=hybrid"
  [[ -f "$PROXY_JAR" ]] || die "Velocity jar missing: $PROXY_JAR (run deploy.sh)"
  mkdir -p "$RUN_DIR"
  if proxy_running; then
    echo "Proxy already running (tmux session '$TMUX_PROXY_SESSION')"
    exit 0
  fi
  service_user_exec new-session -d -s "$TMUX_PROXY_SESSION" -c "$PROXY_DIR" \
    "exec java -Xms${PROXY_JVM_MAX} -Xmx${PROXY_JVM_MAX} -XX:+UseG1GC -XX:G1HeapRegionSize=4M -XX:+UnlockExperimentalVMOptions -XX:+ParallelRefProcEnabled -XX:+AlwaysPreTouch -XX:MaxInlineLevel=15 -jar ${PROXY_JAR}"
  chmod 660 "$TMUX_SOCK" 2>/dev/null || true
  echo "Velocity launching in tmux session '$TMUX_PROXY_SESSION'"
  exit 0
fi

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
hybrid && systemctl start suld-proxy
log "Waiting for the server to finish booting (up to 180s)..."
if wait_ready 180 "$started"; then
  ok "SULD is up. Console: $BIN_DIR/console.sh"
else
  die "Server did not report 'Done' in time. Check: journalctl -u suld -n 50; tail $SERVER_DIR/logs/latest.log"
fi
