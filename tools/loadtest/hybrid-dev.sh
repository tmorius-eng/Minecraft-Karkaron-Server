#!/usr/bin/env bash
# A dev pair for the hybrid login (AUTH_MODE=hybrid, docs/AUTHENTICATION.md), set up by the deploy scripts' own
# configure_auth, so every dev run rehearses the production path:
#   DB_PASS=... [DB_HOST= DB_PORT= DB_NAME= DB_USER=] tools/loadtest/hybrid-dev.sh <dir> setup <suld.jar> <paper.jar>
#   tools/loadtest/hybrid-dev.sh <dir> start | stop
#   tools/loadtest/hybrid-dev.sh <dir> b <console command>   (Paper)   |   p <console command>   (Velocity)
# Velocity 25580 (offline, modern forwarding) -> Paper 127.0.0.1:25581, AuthMe on both, AuthMe's accounts in DB_NAME on
# the dev PostgreSQL (default suld_hybrid_dev). Then: node tools/loadtest/bots/authbot.js 25580 25581 <dir>/server/logs/latest.log
# Dev host only: bots join with offline names. Secrets are generated into <dir>/dev.env (mode 600) and never printed.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="${1:?usage: hybrid-dev.sh <dir> setup|start|stop|b|p ...}"; ACTION="${2:?setup|start|stop|b|p}"
mkdir -p "$DIR"; DIR="$(cd "$DIR" && pwd)"
export SULD_ENV="$DIR/dev.env"
# shellcheck source=../../deploy/lib.sh
source "$REPO/deploy/lib.sh"

session_b=hybdev-b; session_p=hybdev-p
wait_log() {  # $1 = log, $2 = timeout seconds
  for _ in $(seq 1 $(( $2 / 2 ))); do grep -q 'Done (' "$1" 2>/dev/null && return 0; sleep 2; done; return 1
}
start_backend() {
  rm -f "$SERVER_DIR/logs/latest.log"
  tmux new-session -d -s "$session_b" -c "$SERVER_DIR" "java -Xms2G -Xmx2G -jar paper.jar --nogui"
  wait_log "$SERVER_DIR/logs/latest.log" 300 || die "Paper did not boot (see $SERVER_DIR/logs/latest.log)"
}
stop_backend() {
  tmux send-keys -t "$session_b" "stop" Enter 2>/dev/null || return 0
  for _ in $(seq 1 60); do tmux has-session -t "$session_b" 2>/dev/null || return 0; sleep 1; done
  tmux kill-session -t "$session_b"
}

case "$ACTION" in
  setup)
    SULD_JAR="${3:?suld jar}"; PAPER="${4:?paper jar}"
    [[ -f "$SULD_ENV" ]] || {
      [[ -n "${DB_PASS:-}" ]] || die "DB_PASS (the dev PostgreSQL password for AuthMe's accounts) is not set"
      umask 077
      cat >"$SULD_ENV" <<EOF
SULD_USER=$(id -un)
SULD_HOME=$DIR
SERVER_DIR=$DIR/server
PROXY_DIR=$DIR/proxy
RUN_DIR=$DIR/run
REPO_DIR=$REPO
AUTH_MODE=hybrid
SERVER_PORT=25580
PAPER_PORT=25581
MOTD="SULD hybrid dev"
DB_HOST=${DB_HOST:-127.0.0.1}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-suld_hybrid_dev}
DB_USER=${DB_USER:-postgres}
DB_PASS=$DB_PASS
VELOCITY_FORWARDING_SECRET=
AUTHME_PROXY_SECRET=
EOF
      umask 022
    }
    load_env
    ensure_secret VELOCITY_FORWARDING_SECRET
    ensure_secret AUTHME_PROXY_SECRET
    # AuthMe's accounts need their database to exist (the deploy's setup_database does this in production)
    PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -tAc \
      "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 \
      || PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres -qc "CREATE DATABASE \"$DB_NAME\""
    mkdir -p "$SERVER_DIR/plugins" "$RUN_DIR"
    cp -f "$PAPER" "$SERVER_DIR/paper.jar"
    cp -f "$SULD_JAR" "$SERVER_DIR/plugins/SULD.jar"
    printf 'eula=true\n' >"$SERVER_DIR/eula.txt"
    [[ -f "$SERVER_DIR/server.properties" ]] || render_template "$REPO/deploy/templates/server.properties.tpl" >"$SERVER_DIR/server.properties"
    python3 -I "$REPO/tools/plugins/fetch_plugins.py" --dest "$SERVER_DIR/plugins" --profiles auth
    configure_auth
    ok "dev pair set up in $DIR (start it: $0 $DIR start)"
    ;;
  start)
    load_env
    start_backend
    if [[ -f "$AUTHME_CONFIG" ]] && ! grep -q '^    backend: POSTGRESQL' "$AUTHME_CONFIG"; then
      log "AuthMe wrote its config on the first boot: tuning it (deploy's configure_auth) and restarting once"
      stop_backend
      configure_auth
      start_backend
    fi
    rm -f "$PROXY_DIR/logs/latest.log"
    tmux new-session -d -s "$session_p" -c "$PROXY_DIR" "java -Xms${PROXY_JVM_MAX} -Xmx${PROXY_JVM_MAX} -jar velocity.jar"
    wait_log "$PROXY_DIR/logs/latest.log" 60 || die "Velocity did not start (see $PROXY_DIR/logs/latest.log)"
    ok "Velocity :$SERVER_PORT -> Paper 127.0.0.1:$PAPER_PORT"
    ;;
  stop)
    tmux send-keys -t "$session_p" "end" Enter 2>/dev/null || true
    for _ in $(seq 1 15); do tmux has-session -t "$session_p" 2>/dev/null || break; sleep 1; done
    tmux kill-session -t "$session_p" 2>/dev/null || true
    stop_backend
    ok "stopped"
    ;;
  b) shift 2; tmux send-keys -t "$session_b" "$*" Enter ;;
  p) shift 2; tmux send-keys -t "$session_p" "$*" Enter ;;
  *) die "unknown action $ACTION" ;;
esac
