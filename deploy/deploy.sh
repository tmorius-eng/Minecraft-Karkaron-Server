#!/usr/bin/env bash
# SULD first-time deployment for a FRESH Ubuntu 24.04 server (Vultr or any VPS). Idempotent.
#
#   sudo ./deploy/deploy.sh [--dry-run] [--skip-firewall]
#
#   1st run: creates /etc/suld/suld.env (generates the DB password), then STOPS so you can
#            review it and accept the Minecraft EULA. Nothing is installed yet.
#   2nd run: installs Java 21, PostgreSQL, nginx, builds SULD, installs Paper, wires everything
#            into systemd, opens the firewall, starts the server, and runs the health check.
#   --dry-run prints every step instead of executing it (safe anywhere, even on a laptop).
#
# See DEPLOYMENT.md for the full walkthrough.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"

SKIP_FIREWALL=0
for a in "$@"; do
  case "$a" in
    --dry-run) DRY_RUN=1; export DRY_RUN ;;
    --skip-firewall) SKIP_FIREWALL=1 ;;
    -h|--help) sed -n '2,13p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "unknown option: $a" ;;
  esac
done
[[ "$DRY_RUN" == "1" ]] && export ALLOW_EXAMPLE_ENV=1
[[ "$DRY_RUN" == "1" ]] || need_root "$@"
REPO_ROOT="$(cd "$LIB_DIR/.." && pwd)"

to_mb() {  # 4G / 3500M -> megabytes
  case "$1" in
    *[Gg]) echo $(( ${1%[Gg]} * 1024 )) ;;
    *[Mm]) echo "${1%[Mm]}" ;;
    *) die "JVM size must end in G or M: $1" ;;
  esac
}

# ---------------------------------------------------------------- 0. env file (first run)
ensure_env_file() {
  [[ -f "$SULD_ENV" ]] && return 0
  log "Creating $SULD_ENV from the example"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] would create $SULD_ENV (mode 600), generate DB_PASS, detect PUBLIC_HOST, then stop for review"
    return 0
  fi
  install -d -m 755 "$(dirname "$SULD_ENV")"
  install -m 600 -o root -g root "$LIB_DIR/suld.env.example" "$SULD_ENV"
  local pass host
  pass="$(openssl rand -hex 24)"
  host="$(detect_public_host || true)"
  sed -i "s|^DB_PASS=.*|DB_PASS=${pass}|; s|^PUBLIC_HOST=.*|PUBLIC_HOST=${host}|" "$SULD_ENV"
  echo
  ok "Created $SULD_ENV (database password generated, public host: ${host:-<not detected>})"
  echo "    Review it, then set ACCEPT_EULA=true (https://aka.ms/MinecraftEULA) and, if the"
  echo "    repository is private or you want another branch, REPO_URL / REPO_BRANCH."
  echo "    Then run this script again:  sudo $0"
  exit 0
}

# ---------------------------------------------------------------- 1. preflight
preflight() {
  log "Preflight checks"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] checks: Ubuntu 24.04, ACCEPT_EULA, DB_PASS, PUBLIC_HOST, RAM >= JVM_MAX+768MB, 8GB disk, network"
    return 0
  fi
  # shellcheck disable=SC1091
  . /etc/os-release
  if [[ "${ID:-}" != "ubuntu" || "${VERSION_ID:-}" != "24.04" ]]; then
    warn "Tested on Ubuntu 24.04; this is ${PRETTY_NAME:-unknown}. Continuing, but expect surprises."
  fi
  [[ "$ACCEPT_EULA" == "true" ]] || die "Set ACCEPT_EULA=true in $SULD_ENV after reading https://aka.ms/MinecraftEULA"
  # Production identity = Minecraft/Microsoft authentication. Never deploy an offline-mode server.
  [[ "$ONLINE_MODE" == "true" ]] || die "ONLINE_MODE must be true in production (see docs/AUTHENTICATION.md)"
  [[ -n "$DB_PASS" ]] || die "DB_PASS is empty in $SULD_ENV"
  [[ -n "${PUBLIC_HOST:-}" ]] || die "PUBLIC_HOST is empty in $SULD_ENV (public IP or DNS name players use)"
  local mem_mb need_mb disk_mb
  mem_mb="$(awk '/MemTotal/{print int($2/1024)}' /proc/meminfo)"
  need_mb=$(( $(to_mb "$JVM_MAX") + 768 ))
  if ((mem_mb < need_mb)); then
    die "RAM ${mem_mb} MB is too small for JVM_MAX=$JVM_MAX (needs ~${need_mb} MB incl. OS + PostgreSQL). Lower JVM_MAX or use a bigger plan."
  fi
  disk_mb="$(df -Pm /opt | awk 'NR==2{print $4}')"
  ((disk_mb >= 8192)) || die "Only ${disk_mb} MB free under /opt; need at least 8 GB."
  local host
  for host in fill.papermc.io github.com repo.maven.apache.org; do
    curl -fsSI --max-time 10 "https://$host" >/dev/null 2>&1 || warn "Cannot reach https://$host (needed for install/build)"
  done
  ok "preflight passed (RAM ${mem_mb} MB, disk ${disk_mb} MB free)"
}

# ---------------------------------------------------------------- 2. packages
install_packages() {
  log "Installing packages"
  run env DEBIAN_FRONTEND=noninteractive apt-get update -y
  run env DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      ca-certificates curl git unzip zip tmux nginx postgresql postgresql-client ufw fail2ban \
      python3 openssl iproute2 "$JAVA_PACKAGE"
  if [[ "$DRY_RUN" != "1" ]]; then
    java -version 2>&1 | grep -q 'version "21' || die "Java 21 not active ($(java -version 2>&1 | head -1)); check JAVA_PACKAGE"
  fi
  run systemctl enable --now postgresql nginx fail2ban
}

# ---------------------------------------------------------------- 3. user + directories
create_user_and_dirs() {
  log "Service user and directories"
  if ! id "$SULD_USER" >/dev/null 2>&1; then
    run useradd --system --create-home --home-dir "$SULD_HOME" --shell /usr/sbin/nologin "$SULD_USER"
  fi
  run install -d -o "$SULD_USER" -g "$SULD_USER" -m 755 "$SULD_HOME" "$SERVER_DIR" "$PACK_DIR" "$RELEASES_DIR" "$RUN_DIR"
  run install -d -o "$SULD_USER" -g "$SULD_USER" -m 750 "$BACKUP_DIR"
  # Env file: root-writable, group-readable by the service user (it contains the DB password).
  run chown "root:$SULD_USER" "$SULD_ENV"
  run chmod 640 "$SULD_ENV"
}

# ---------------------------------------------------------------- 4. source code
fetch_source() {
  log "Fetching source ($REPO_URL @ $REPO_BRANCH)"
  if [[ -d "$REPO_DIR/.git" ]]; then
    as_suld_in "$REPO_DIR" git fetch --depth 1 origin "$REPO_BRANCH"
    as_suld_in "$REPO_DIR" git reset --hard FETCH_HEAD
  else
    as_suld git clone --depth 1 --branch "$REPO_BRANCH" "$REPO_URL" "$REPO_DIR"
  fi
}

# ---------------------------------------------------------------- 5. database
setup_database() {
  log "PostgreSQL: role '$DB_USER' and database '$DB_NAME'"
  # Idempotent; also re-syncs the role password with $SULD_ENV. Password goes via stdin, not argv.
  run runuser -u postgres -- psql -X -q -v ON_ERROR_STOP=1 -v dbuser="$DB_USER" -v dbname="$DB_NAME" -v pw="$DB_PASS" -f - <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'dbuser', :'pw')
  WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'dbuser') \gexec
SELECT format('ALTER ROLE %I PASSWORD %L', :'dbuser', :'pw') \gexec
SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8''', :'dbname', :'dbuser')
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'dbname') \gexec
SQL
  if [[ "$DRY_RUN" != "1" ]]; then
    PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc 'select 1' >/dev/null \
      || die "Cannot log in to PostgreSQL as $DB_USER over TCP (check pg_hba.conf / DB_HOST / DB_PORT)"
    ok "database reachable with the app credentials"
  fi
}

# ---------------------------------------------------------------- 6. build + Paper
build_and_paper() {
  local jar
  if [[ -n "${PLUGIN_JAR:-}" ]]; then
    [[ -f "$PLUGIN_JAR" ]] || die "PLUGIN_JAR=$PLUGIN_JAR does not exist"
    jar="$PLUGIN_JAR"
    log "Using prebuilt plugin jar $jar"
  else
    build_plugin
    jar="$(find_plugin_jar "$REPO_DIR")"
    [[ "$DRY_RUN" == "1" ]] && jar="$REPO_DIR/suld-plugin/build/libs/suld-plugin.jar"
    [[ -n "$jar" ]] || die "Build produced no plugin jar"
  fi
  PLUGIN_JAR_BUILT="$jar"
  install_paper
}

# ---------------------------------------------------------------- 7. server files
configure_server() {
  log "Server files"
  run install -d -o "$SULD_USER" -g "$SULD_USER" -m 755 "$SERVER_DIR/plugins" "$SERVER_DIR/plugins/SULD"
  printf 'eula=true\n' | install_text "$SERVER_DIR/eula.txt" 644 "$SULD_USER:$SULD_USER"
  if [[ ! -f "$SERVER_DIR/server.properties" ]]; then
    render_template "$LIB_DIR/templates/server.properties.tpl" | install_text "$SERVER_DIR/server.properties" 644 "$SULD_USER:$SULD_USER"
  else
    ok "server.properties exists — left untouched"
  fi
  run install -o "$SULD_USER" -g "$SULD_USER" -m 644 "$PLUGIN_JAR_BUILT" "$SERVER_DIR/plugins/SULD.jar"
  install_plugins
  if [[ ! -f "$PLUGIN_CONFIG" ]]; then
    log "Creating plugin config from the jar's bundled default"
    if [[ "$DRY_RUN" == "1" ]]; then
      echo "  [dry-run] unzip config.yml from the jar into $PLUGIN_CONFIG"
    else
      unzip -p "$PLUGIN_JAR_BUILT" config.yml | install_text "$PLUGIN_CONFIG" 600 "$SULD_USER:$SULD_USER"
    fi
  fi
  # Credentials in the env file are authoritative; keep the plugin in sync on every deploy.
  [[ "$DRY_RUN" == "1" ]] || sync_db_config
  # Authentication hardening is enforced on every deploy, whatever was edited by hand.
  [[ "$DRY_RUN" == "1" ]] || set_config "auth.allow-insecure-offline-dev-mode=r:false"
  if [[ "$DRY_RUN" != "1" && -f "$SERVER_DIR/server.properties" ]]; then
    sed -i 's/^online-mode=.*/online-mode=true/' "$SERVER_DIR/server.properties"
  fi
  [[ "$DRY_RUN" == "1" ]] && echo "  [dry-run] set database.* in $PLUGIN_CONFIG from the env file"
  chmod 600 "$PLUGIN_CONFIG" 2>/dev/null || true
}

# ---------------------------------------------------------------- 8. resource pack + nginx
setup_pack_hosting() {
  log "Resource pack build + nginx hosting"
  render_template "$LIB_DIR/templates/nginx-pack.conf.tpl" | install_text /etc/nginx/conf.d/suld-pack.conf 644 root:root
  run rm -f /etc/nginx/sites-enabled/default
  run nginx -t
  run systemctl reload nginx
  local out pack sha
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] build-pack.sh -> $PACK_DIR/suld-pack-<sha1>.zip; set resource-pack.url/sha1 in plugin config"
    return 0
  fi
  out="$(as_suld bash "$LIB_DIR/build-pack.sh" "$REPO_ROOT")"
  pack="$(sed -n 's/^PACK_FILE=//p' <<<"$out")"
  sha="$(sed -n 's/^PACK_SHA1=//p' <<<"$out")"
  [[ -n "$pack" && -n "$sha" ]] || die "build-pack.sh did not report a pack"
  apply_pack_config "$pack" "$sha"
  record_release "$PLUGIN_JAR_BUILT" "$pack" "$sha"
  ok "pack $pack (sha1 $sha) -> http://${PUBLIC_HOST}:${PACK_PORT}/$pack"
}

# ---------------------------------------------------------------- 9. scripts + systemd
install_service() {
  log "Installing control scripts and systemd units"
  install_toolkit "$LIB_DIR"
  render_template "$LIB_DIR/templates/suld.service.tpl"        | install_text /etc/systemd/system/suld.service 644 root:root
  render_template "$LIB_DIR/templates/suld-backup.service.tpl" | install_text /etc/systemd/system/suld-backup.service 644 root:root
  render_template "$LIB_DIR/templates/suld-backup.timer.tpl"   | install_text /etc/systemd/system/suld-backup.timer 644 root:root
  run systemctl daemon-reload
  run systemctl enable suld.service suld-backup.timer
  run systemctl start suld-backup.timer
}

# ---------------------------------------------------------------- 10. go
start_and_verify() {
  log "Starting SULD"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] systemctl restart suld; wait for 'Done'; run health-check.sh; op.sh ${ADMIN_PLAYERS:-<none>}"
    return 0
  fi
  local started
  started="$(date +%s)"
  systemctl restart suld
  if ! wait_ready 300 "$started"; then
    fail "Server did not finish booting within 5 minutes."
    echo "    journalctl -u suld -n 50 ; tail -n 80 $SERVER_DIR/logs/latest.log"
    exit 1
  fi
  sleep 5
  "$BIN_DIR/health-check.sh" || { fail "Health check failed — see output above."; exit 1; }
  if [[ -n "$ADMIN_PLAYERS" ]]; then
    log "Granting operator to: $ADMIN_PLAYERS"
    # shellcheck disable=SC2086  # intentional split: space-separated names
    "$BIN_DIR/op.sh" $ADMIN_PLAYERS || warn "Could not op every admin; retry: sudo $BIN_DIR/op.sh <name>"
  fi
}

summary() {
  cat <<EOM

SULD deployment complete.
  Connect:        ${PUBLIC_HOST}:${SERVER_PORT}   (Minecraft Java ${MC_VERSION})
  Console:        sudo ${BIN_DIR}/console.sh        (detach: Ctrl-b then d)
  Control:        sudo ${BIN_DIR}/{start,stop,restart}.sh   |   systemctl status suld
  Health:         sudo ${BIN_DIR}/health-check.sh
  Backups:        daily 04:30 UTC (systemctl list-timers suld-backup.timer) -> ${BACKUP_DIR}
  Update:         sudo ${BIN_DIR}/update.sh
  Operators:      ${ADMIN_PLAYERS:-<none>}   (more: sudo ${BIN_DIR}/op.sh <name>)
  Also open the same ports in the Vultr cloud firewall group — see DEPLOYMENT.md.
EOM
}

PLUGIN_JAR_BUILT=""
ensure_env_file
load_env
[[ "$DRY_RUN" == "1" ]] && [[ -z "$DB_PASS" ]] && DB_PASS="<generated-on-first-run>"
[[ "$DRY_RUN" == "1" ]] && [[ -z "${PUBLIC_HOST:-}" ]] && PUBLIC_HOST="<detected-public-ip>"
[[ "$DRY_RUN" == "1" ]] && ACCEPT_EULA=true
preflight
install_packages
create_user_and_dirs
fetch_source
setup_database
if [[ "$DRY_RUN" != "1" ]] && systemctl is-active --quiet suld 2>/dev/null; then
  log "Server is running; stopping it for the file swap"
  systemctl stop suld
fi
build_and_paper
configure_server
setup_pack_hosting
install_service
if ((SKIP_FIREWALL)); then warn "Skipping firewall (--skip-firewall). Make sure the host is protected."; else DRY_RUN="$DRY_RUN" bash "$LIB_DIR/firewall.sh"; fi
start_and_verify
summary
