#!/usr/bin/env bash
# Shared helpers for the SULD deployment scripts. Source this file; do not run it.
#
# Conventions
#   * Configuration lives in /etc/suld/suld.env (override with SULD_ENV=...).
#   * DRY_RUN=1 prints every state-changing command instead of running it.
#   * Secrets are never echoed. DB_PASS is only passed via PGPASSWORD to child processes.

# shellcheck disable=SC2034
shopt -u patsub_replacement 2>/dev/null || true  # bash 5.2: keep '&' literal in ${var//a/b}
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SULD_ENV="${SULD_ENV:-/etc/suld/suld.env}"
DRY_RUN="${DRY_RUN:-0}"

log()  { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m OK \033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mWARN\033[0m %s\n' "$*" >&2; }
fail() { printf '\033[1;31mFAIL\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# Re-exec through sudo when not root.
need_root() {
  if [[ $EUID -ne 0 ]]; then
    command -v sudo >/dev/null || die "must run as root (sudo not found)"
    exec sudo -E SULD_ENV="$SULD_ENV" DRY_RUN="$DRY_RUN" bash "$0" "$@"
  fi
}

# Load the environment file, then apply defaults for anything left unset.
load_env() {
  if [[ -e "$SULD_ENV" && ! -r "$SULD_ENV" ]]; then
    die "Config $SULD_ENV exists but is not readable by $(id -un) (expected root:${SULD_USER:-suld} mode 640)."
  fi
  if [[ -f "$SULD_ENV" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$SULD_ENV"
    set +a
  elif [[ "${ALLOW_EXAMPLE_ENV:-0}" == "1" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$LIB_DIR/suld.env.example"
    set +a
  else
    die "Config $SULD_ENV not found. Run deploy.sh first (it creates it from deploy/suld.env.example)."
  fi
  : "${SULD_USER:=suld}"
  : "${SULD_HOME:=/opt/suld}"
  : "${SERVER_DIR:=$SULD_HOME/server}"
  : "${REPO_DIR:=$SULD_HOME/repo}"
  : "${PACK_DIR:=$SULD_HOME/pack}"
  : "${BACKUP_DIR:=$SULD_HOME/backups}"
  : "${RELEASES_DIR:=$SULD_HOME/releases}"
  : "${BIN_DIR:=$SULD_HOME/bin}"
  : "${RUN_DIR:=$SULD_HOME/run}"
  : "${REPO_URL:=https://github.com/tmorius-eng/Minecraft-Karkaron-Server.git}"
  : "${REPO_BRANCH:=main}"
  : "${MC_VERSION:=1.21.11}"
  : "${JAVA_PACKAGE:=openjdk-21-jdk-headless}"
  : "${JVM_MIN:=2G}"
  : "${JVM_MAX:=4G}"
  : "${SERVER_PORT:=25565}"
  : "${PACK_PORT:=8080}"
  : "${BEDROCK_PORT:=}"   # blank = Java Edition only (Geyser is an optional profile)
  : "${DB_HOST:=127.0.0.1}"
  : "${DB_PORT:=5432}"
  : "${DB_NAME:=suld}"
  : "${DB_USER:=suld}"
  : "${DB_PASS:=}"
  : "${MAX_PLAYERS:=25}"   # the last bench (docs/perf/M3_2026-10-12.md) holds 25; raise it after a run on the real host
  : "${ALERT_WEBHOOK_URL:=}"   # operators' webhook (Discord-compatible); blank = no alerts
  : "${REPORT_WEBHOOK_URL:=}"  # the weekly funnel report's webhook; blank = ALERT_WEBHOOK_URL
  : "${DISCORD_WEBHOOK_URL:=}" # the public Discord channel the game posts to (docs/DISCORD.md); blank = off
  : "${ONLINE_MODE:=true}"
  : "${TMUX_SESSION:=suld}"
  : "${BACKUP_KEEP:=14}"
  : "${ACCEPT_EULA:=false}"
  : "${ADMIN_PLAYERS:=}"
  : "${HEALTH_MIN_DISK_MB:=2048}"
  # Accounts (docs/AUTHENTICATION.md): online = Paper verifies every login with Microsoft (default); hybrid = an
  # offline Velocity proxy on SERVER_PORT in front of Paper on 127.0.0.1:PAPER_PORT, AuthMe on both, signed forwarding.
  : "${AUTH_MODE:=online}"
  : "${PAPER_PORT:=25566}"
  : "${VELOCITY_VERSION:=3.5.1}"
  : "${PROXY_DIR:=$SULD_HOME/proxy}"
  : "${PROXY_JVM_MAX:=512M}"
  : "${TMUX_PROXY_SESSION:=suld-proxy}"
  : "${VELOCITY_FORWARDING_SECRET:=}"
  : "${AUTHME_PROXY_SECRET:=}"
  case "$AUTH_MODE" in
    online) BACKEND_IP=""; BACKEND_PORT="$SERVER_PORT"; ENFORCE_SECURE_PROFILE=true ;;
    # chat signing needs the client's Microsoft key, which an offline proxy never asks for
    hybrid) ONLINE_MODE=false; BACKEND_IP=127.0.0.1; BACKEND_PORT="$PAPER_PORT"; ENFORCE_SECURE_PROFILE=false ;;
    *) die "AUTH_MODE must be online or hybrid (is '$AUTH_MODE')" ;;
  esac
  TMUX_SOCK="$RUN_DIR/tmux.sock"
  PAPER_JAR="$SERVER_DIR/paper.jar"
  PROXY_JAR="$PROXY_DIR/velocity.jar"
  PLUGIN_CONFIG="$SERVER_DIR/plugins/SULD/config.yml"
  AUTHME_CONFIG="$SERVER_DIR/plugins/AuthMe/config.yml"
}

hybrid() { [[ "$AUTH_MODE" == "hybrid" ]]; }

# Fill a blank secret in the env file with 32 random bytes (hex). The value is never printed.
ensure_secret() {  # $1 = variable name
  local name="$1"
  [[ -n "${!name:-}" ]] && return 0
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] generate $name into $SULD_ENV (openssl rand -hex 32)"
    printf -v "$name" '%s' "<generated-$name>"
    return 0
  fi
  local value
  value="$(openssl rand -hex 32)"
  if grep -q "^${name}=" "$SULD_ENV"; then sed -i "s|^${name}=.*|${name}=${value}|" "$SULD_ENV"
  else printf '%s=%s\n' "$name" "$value" >>"$SULD_ENV"; fi
  printf -v "$name" '%s' "$value"
  export "${name?}"
  ok "$name generated in $SULD_ENV"
}

# Set key=value lines in server.properties (the keys the deployment owns; the rest is the operator's).
props_set() {  # $1 = file, then key=value ...
  local file="$1"; shift
  local kv key
  for kv in "$@"; do
    key="${kv%%=*}"
    if [[ "$DRY_RUN" == "1" ]]; then echo "  [dry-run] $file: $kv"; continue; fi
    if grep -q "^${key}=" "$file"; then sed -i "s|^${key}=.*|${kv}|" "$file"; else printf '%s\n' "$kv" >>"$file"; fi
  done
}

# Set nested keys in a YAML file a plugin or Paper generated (tools/paper_tune.py; env:NAME values never in argv).
yaml_tune() {  # $1 = file, then a.b.c=value ...
  local file="$1"; shift
  if [[ "$DRY_RUN" == "1" ]]; then printf '  [dry-run] %s:' "$file"; printf ' %s' "$@"; printf '\n'; return 0; fi
  as_suld python3 "$LIB_DIR/tools/paper_tune.py" "$file" "$@" || die "Could not set $* in $file"
}

# Run a state-changing command (or just print it under DRY_RUN=1).
run() {
  if [[ "$DRY_RUN" == "1" ]]; then
    printf '  [dry-run]'
    printf ' %q' "$@"
    printf '\n'
  else
    "$@"
  fi
}

# Run a command as the service user with a sane HOME (gradle/git need it).
as_suld() {
  if [[ $EUID -eq 0 ]]; then
    run runuser -u "$SULD_USER" -- env HOME="$SULD_HOME" "$@"
  else
    run env HOME="$SULD_HOME" "$@"
  fi
}

# Same, but inside a directory.
as_suld_in() {
  local dir="$1"; shift
  as_suld bash -c 'cd "$1" && shift && exec "$@"' _ "$dir" "$@"
}

# Write stdin to a file atomically with mode and owner (no-op print under DRY_RUN).
install_text() {
  local path="$1" mode="$2" owner="$3"
  if [[ "$DRY_RUN" == "1" ]]; then
    printf '  [dry-run] write %s (mode %s, owner %s)\n' "$path" "$mode" "$owner"
    cat >/dev/null
    return 0
  fi
  local tmp
  tmp="$(mktemp "${path}.XXXXXX")"
  cat >"$tmp"
  chmod "$mode" "$tmp"
  chown "$owner" "$tmp"
  mv -f "$tmp" "$path"
}

# Substitute @VAR@ placeholders in a template with the current environment values.
render_template() {
  local tpl="$1" out
  out="$(cat "$tpl")"
  local var val
  MOTD="$(props_escape "${MOTD:-SULD}")"
  for var in SULD_USER SULD_HOME SERVER_DIR REPO_DIR PACK_DIR BACKUP_DIR BIN_DIR RUN_DIR \
             SERVER_PORT PACK_PORT MAX_PLAYERS ONLINE_MODE MOTD TMUX_SESSION BACKEND_IP BACKEND_PORT \
             ENFORCE_SECURE_PROFILE PROXY_DIR TMUX_PROXY_SESSION DB_HOST DB_PORT DB_NAME DB_USER; do
    val="${!var-}"
    out="${out//@${var}@/$val}"
  done
  printf '%s\n' "$out"
}

# Escape non-ASCII as \uXXXX so server.properties is correct whatever encoding the JVM assumes.
props_escape() {
  python3 -I -c 'import sys
print("".join(c if ord(c) < 128 else "\\u%04x" % ord(c) for c in sys.argv[1]))' "$1"
}

# Edit scalar keys in the plugin config. Usage: set_config key=value ... (see tools/yaml_set.py)
set_config() {
  run python3 "$LIB_DIR/tools/yaml_set.py" "$PLUGIN_CONFIG" "$@"
}

service_user_exec() {  # run a tmux command against the SULD socket as the service user
  if [[ $EUID -eq 0 ]]; then
    runuser -u "$SULD_USER" -- tmux -S "$TMUX_SOCK" "$@"
  else
    tmux -S "$TMUX_SOCK" "$@"
  fi
}

server_running() {
  service_user_exec has-session -t "$TMUX_SESSION" 2>/dev/null
}

console_send() {  # send a console command to the running server
  service_user_exec send-keys -t "$TMUX_SESSION:0" "$*" Enter
}

# Wait until the server logs "Done (...)" for the current boot. $1 = timeout seconds.
wait_ready() {
  local timeout="${1:-180}" since="${2:-0}" log="$SERVER_DIR/logs/latest.log" waited=0
  while ((waited < timeout)); do
    if [[ -f "$log" ]] && (( $(stat -c %Y "$log") >= since )) && grep -q 'Done (' "$log"; then
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
  done
  return 1
}

detect_public_host() {
  # The Vultr public IPv4 is bound directly to the NIC; ask the kernel, no third-party service.
  ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src"){print $(i+1); exit}}'
}

sha1_of() { sha1sum "$1" | awk '{print $1}'; }

# --- Higher-level steps shared by deploy.sh and update.sh -------------------------------

find_plugin_jar() {  # $1 = repo dir
  find "$1/suld-plugin/build/libs" -maxdepth 1 -name 'suld-plugin-*.jar' \
    ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-plain.jar' 2>/dev/null | sort | tail -1 || true
}

# Build the plugin from the repo checkout (tests included unless SKIP_TESTS=true).
build_plugin() {
  local task=build
  [[ "${SKIP_TESTS:-false}" == "true" ]] && task=":suld-plugin:shadowJar"
  log "Building SULD ($task)"
  as_suld_in "$REPO_DIR" env GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1g" ./gradlew --no-daemon --console=plain "$task"
}

# Download Paper for MC_VERSION and verify the published SHA-256. $1 = "force" to re-download.
install_paper() {
  local meta url name sum
  meta="$(curl -fsSL "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds/latest")" \
    || die "Could not query the Paper API for ${MC_VERSION}"
  read -r name url sum < <(printf '%s' "$meta" | python3 -I -c '
import sys, json
d = json.load(sys.stdin)["downloads"]["server:default"]
print(d["name"], d["url"], d["checksums"]["sha256"])')
  if [[ "${1:-}" != "force" && -f "$SERVER_DIR/.paper-build" && "$(cat "$SERVER_DIR/.paper-build")" == "$name" && -f "$PAPER_JAR" ]]; then
    ok "Paper already current ($name)"
    return 0
  fi
  log "Downloading $name"
  [[ "$DRY_RUN" == "1" ]] && { echo "  [dry-run] download $url, verify sha256 $sum"; return 0; }
  local tmp
  tmp="$(mktemp "$SERVER_DIR/.paper.XXXXXX")"
  curl -fsSL -o "$tmp" "$url" || { rm -f "$tmp"; die "Paper download failed"; }
  if [[ "$(sha256sum "$tmp" | awk '{print $1}')" != "$sum" ]]; then
    rm -f "$tmp"
    die "Paper SHA-256 mismatch — refusing to install a corrupted/tampered jar"
  fi
  chmod 644 "$tmp"
  chown "$SULD_USER:$SULD_USER" "$tmp"
  [[ -f "$PAPER_JAR" ]] && cp -f "$PAPER_JAR" "$RELEASES_DIR/paper.jar.previous"
  mv -f "$tmp" "$PAPER_JAR"
  printf '%s\n' "$name" >"$SERVER_DIR/.paper-build"
  chown "$SULD_USER:$SULD_USER" "$SERVER_DIR/.paper-build"
  ok "Paper installed ($name, sha256 verified)"
}

# Copy the deployment toolkit into BIN_DIR, root-owned: the Minecraft process (user suld) must
# never be able to modify scripts that root later executes. $1 = source deploy dir.
install_toolkit() {
  local src="$1"
  run install -d -o root -g root -m 755 "$BIN_DIR" "$BIN_DIR/tools" "$BIN_DIR/templates"
  run bash -c 'install -o root -g root -m 755 "$1"/*.sh "$2"/ && install -o root -g root -m 644 "$1"/suld.env.example "$2"/ \
    && install -o root -g root -m 755 "$1"/tools/* "$2"/tools/ && install -o root -g root -m 644 "$1"/templates/* "$2"/templates/' _ "$src" "$BIN_DIR"
}

# Point the plugin at a freshly built pack. $1 = pack file name, $2 = sha1.
apply_pack_config() {
  [[ -n "${PUBLIC_HOST:-}" ]] || die "PUBLIC_HOST is empty; set it in $SULD_ENV (the server's public IP or DNS name)"
  set_config "resource-pack.enabled=r:true" \
             "resource-pack.url=http://${PUBLIC_HOST}:${PACK_PORT}/$1" \
             "resource-pack.sha1=$2"
}

# The Discord webhooks (docs/DISCORD.md) reach the plugin's config.yml from the environment only (yaml_set env:);
# a config.yml from before the keys gets the section appended (the plugin's own v10 migration would add it at the
# next start, too late for this write). Blank = that channel is off.
sync_discord_config() {
  [[ -f "$PLUGIN_CONFIG" ]] || return 0
  if ! grep -q '^discord:' "$PLUGIN_CONFIG"; then
    printf '\n# SULD -> Discord (docs/DISCORD.md): written by deploy from DISCORD_WEBHOOK_URL / ALERT_WEBHOOK_URL; blank = off\ndiscord:\n  webhook: %s\n  staff-webhook: %s\n' "''" "''" >> "$PLUGIN_CONFIG"
  fi
  export DISCORD_WEBHOOK_URL ALERT_WEBHOOK_URL
  set_config "discord.webhook=env:DISCORD_WEBHOOK_URL" "discord.staff-webhook=env:ALERT_WEBHOOK_URL"
}

sync_db_config() {
  set_config "database.type=postgresql" "database.host=${DB_HOST}" "database.port=r:${DB_PORT}" \
             "database.database=${DB_NAME}" "database.username=${DB_USER}" "database.password=env:DB_PASS"
}

# Stage the installed jar under RELEASES_DIR and record it as the current release, so the
# first update after a fresh deploy already has something to roll back to.
record_release() {  # $1 = plugin jar, $2 = pack file, $3 = pack sha1
  local commit staged paper=""
  commit="$(as_suld_in "$REPO_DIR" git rev-parse HEAD 2>/dev/null || echo unknown)"
  staged="SULD-${commit:0:12}.jar"
  run install -o "$SULD_USER" -g "$SULD_USER" -m 644 "$1" "$RELEASES_DIR/$staged"
  [[ -f "$SERVER_DIR/.paper-build" ]] && paper="$(cat "$SERVER_DIR/.paper-build")"
  printf 'COMMIT=%s\nJAR=%s\nPACK_FILE=%s\nPACK_SHA1=%s\nPAPER_BUILD=%s\n' "$commit" "$staged" "$2" "$3" "$paper" \
    | install_text "$RELEASES_DIR/current.state" 644 "root:root"
}

# Trusted third-party plugins (deploy/plugins/plugins.json) for the profiles in PLUGIN_PROFILES
# (default core,hardening; "none" skips). Only builds for the server's Minecraft version are installed.
install_plugins() {
  local profiles="${PLUGIN_PROFILES:-core,hardening}"
  # hybrid: AuthMe joins the backend's plugins whatever the profiles say (no hybrid login without it)
  if hybrid && [[ "$profiles" != "none" && ",$profiles," != *",auth,"* ]]; then profiles="$profiles,auth"; fi
  [[ "$profiles" == "none" ]] && { ok "Third-party plugins: skipped (PLUGIN_PROFILES=none)"; return 0; }
  log "Third-party plugins ($profiles)"
  if [[ "${DRY_RUN:-0}" == "1" ]]; then
    echo "  [dry-run] python3 tools/plugins/fetch_plugins.py --dest $SERVER_DIR/plugins --profiles $profiles"
    return 0
  fi
  as_suld python3 "$REPO_DIR/tools/plugins/fetch_plugins.py" --dest "$SERVER_DIR/plugins" --profiles "$profiles" \
    || die "Plugin install failed (see above). Fix the network or set PLUGIN_PROFILES."
}

# SÜLD's GrimAC punishments (deploy/grim/punishments.yml, docs/ANTICHEAT.md) whenever GrimAC is installed. Grim keeps
# an existing punishments.yml on first boot and reads it again on every start, so the next (re)start applies it.
install_grim_config() {
  local src="$REPO_DIR/deploy/grim/punishments.yml"
  compgen -G "$SERVER_DIR/plugins/grimac*.jar" >/dev/null || compgen -G "$SERVER_DIR/plugins/GrimAC*.jar" >/dev/null \
    || { ok "GrimAC not installed — no anticheat punishments"; return 0; }
  [[ -f "$src" ]] || return 0
  run install -d -o "$SULD_USER" -g "$SULD_USER" -m 755 "$SERVER_DIR/plugins/GrimAC"
  install_text "$SERVER_DIR/plugins/GrimAC/punishments.yml" 644 "$SULD_USER:$SULD_USER" < "$src"
  ok "GrimAC punishments: deploy/grim/punishments.yml"
}

# --- Accounts: AUTH_MODE (docs/AUTHENTICATION.md) -------------------------------------------

# Render a template whose @NAME@ placeholders include secrets: the values come from the environment inside python,
# never through argv or the terminal. $1 = template, then the secret variable names.
render_secret_template() {
  local tpl="$1"; shift
  render_template "$tpl" | SECRET_NAMES="$*" python3 -I -c 'import os, sys
s = sys.stdin.read()
for n in os.environ["SECRET_NAMES"].split():
    v = os.environ.get(n, "")
    if not v:
        sys.exit("render: " + n + " is empty")
    s = s.replace("@" + n + "@", v)
sys.stdout.write(s)'
}

# Download Velocity VELOCITY_VERSION (newest build of it) and verify the published SHA-256, like install_paper.
install_velocity() {
  local meta url name sum
  meta="$(curl -fsSL "https://fill.papermc.io/v3/projects/velocity/versions/${VELOCITY_VERSION}/builds/latest")" \
    || die "Could not query the Velocity API for ${VELOCITY_VERSION}"
  read -r name url sum < <(printf '%s' "$meta" | python3 -I -c '
import sys, json
d = json.load(sys.stdin)["downloads"]["server:default"]
print(d["name"], d["url"], d["checksums"]["sha256"])')
  if [[ -f "$PROXY_DIR/.velocity-build" && "$(cat "$PROXY_DIR/.velocity-build")" == "$name" && -f "$PROXY_JAR" ]]; then
    ok "Velocity already current ($name)"
    return 0
  fi
  log "Downloading $name"
  [[ "$DRY_RUN" == "1" ]] && { echo "  [dry-run] download $url, verify sha256 $sum"; return 0; }
  local tmp
  tmp="$(mktemp "$PROXY_DIR/.velocity.XXXXXX")"
  curl -fsSL -o "$tmp" "$url" || { rm -f "$tmp"; die "Velocity download failed"; }
  if [[ "$(sha256sum "$tmp" | awk '{print $1}')" != "$sum" ]]; then
    rm -f "$tmp"
    die "Velocity SHA-256 mismatch — refusing to install a corrupted/tampered jar"
  fi
  chmod 644 "$tmp"
  chown "$SULD_USER:$SULD_USER" "$tmp"
  mv -f "$tmp" "$PROXY_JAR"
  printf '%s\n' "$name" | install_text "$PROXY_DIR/.velocity-build" 644 "$SULD_USER:$SULD_USER"
  ok "Velocity installed ($name, sha256 verified)"
}

# Everything the account mode owns, on every deploy and update (the server must be stopped):
#   server.properties: online-mode, server-ip, server-port, enforce-secure-profile;
#   config/paper-global.yml: proxies.velocity (seeded before Paper's first boot, tuned after);
#   hybrid: plugins/AuthMe/config.yml once AuthMe wrote it, and the whole proxy (jar, velocity.toml, forwarding
#   secret, AuthMe Velocity, its config).
configure_auth() {
  local props="$SERVER_DIR/server.properties" global="$SERVER_DIR/config/paper-global.yml"
  log "Accounts: AUTH_MODE=$AUTH_MODE"
  [[ -f "$props" || "$DRY_RUN" == "1" ]] && props_set "$props" "online-mode=$ONLINE_MODE" "server-ip=$BACKEND_IP" \
    "server-port=$BACKEND_PORT" "enforce-secure-profile=$ENFORCE_SECURE_PROFILE"
  if ! hybrid; then
    # back from hybrid: Paper must stop trusting the proxy's forwarding
    [[ -f "$global" ]] && grep -q '^proxies:' "$global" && yaml_tune "$global" proxies.velocity.enabled=false
    return 0
  fi
  if [[ -f "$global" ]]; then
    yaml_tune "$global" proxies.velocity.enabled=true proxies.velocity.online-mode=false \
      proxies.velocity.secret=env:VELOCITY_FORWARDING_SECRET
  else
    run install -d -o "$SULD_USER" -g "$SULD_USER" -m 755 "$SERVER_DIR/config"
    render_secret_template "$LIB_DIR/templates/paper-global-proxy.yml.tpl" VELOCITY_FORWARDING_SECRET \
      | install_text "$global" 600 "$SULD_USER:$SULD_USER"
  fi
  [[ "$DRY_RUN" == "1" ]] || chmod 600 "$global"
  if [[ -f "$AUTHME_CONFIG" || "$DRY_RUN" == "1" ]]; then
    # AuthMe writes its config on its first start (deploy.sh finish_hybrid); its accounts live beside SÜLD's tables
    yaml_tune "$AUTHME_CONFIG" DataSource.backend=POSTGRESQL DataSource.caching=false \
      DataSource.mySQLHost="$DB_HOST" "DataSource.mySQLPort='$DB_PORT'" DataSource.mySQLUseSSL=false \
      DataSource.mySQLUsername="$DB_USER" DataSource.mySQLPassword=env:DB_PASS DataSource.mySQLDatabase="$DB_NAME" \
      Hooks.bungeecord=true Hooks.proxySharedSecret=env:AUTHME_PROXY_SECRET settings.enablePremium=true
    [[ "$DRY_RUN" == "1" ]] || chmod 600 "$AUTHME_CONFIG"
    if [[ -f "$REPO_DIR/deploy/authme/messages_mn.yml" ]]; then
      install_text "$SERVER_DIR/plugins/AuthMe/messages/messages_mn.yml" 644 "$SULD_USER:$SULD_USER" \
        < "$REPO_DIR/deploy/authme/messages_mn.yml"
      yaml_tune "$AUTHME_CONFIG" settings.messagesLanguage=mn settings.perPlayerLocale=false  # every player reads Mongolian
    fi
  fi
  run install -d -o "$SULD_USER" -g "$SULD_USER" -m 755 "$PROXY_DIR" "$PROXY_DIR/plugins" "$PROXY_DIR/plugins/authmevelocity"
  install_velocity
  render_template "$LIB_DIR/templates/velocity.toml.tpl" | install_text "$PROXY_DIR/velocity.toml" 644 "$SULD_USER:$SULD_USER"
  if [[ "$DRY_RUN" == "1" ]]; then echo "  [dry-run] write $PROXY_DIR/forwarding.secret (mode 600)"
  else printf '%s' "$VELOCITY_FORWARDING_SECRET" | install_text "$PROXY_DIR/forwarding.secret" 600 "$SULD_USER:$SULD_USER"; fi
  render_secret_template "$LIB_DIR/templates/authme-velocity.config.yml.tpl" AUTHME_PROXY_SECRET \
    | install_text "$PROXY_DIR/plugins/authmevelocity/config.yml" 600 "$SULD_USER:$SULD_USER"
  install_proxy_plugins
}

# The proxy's plugins (target velocity, profile auth): AuthMe Velocity, pinned like the backend's.
install_proxy_plugins() {
  log "Proxy plugins (auth, velocity)"
  if [[ "$DRY_RUN" == "1" ]]; then
    echo "  [dry-run] python3 tools/plugins/fetch_plugins.py --dest $PROXY_DIR/plugins --profiles auth --target velocity"
    return 0
  fi
  as_suld python3 "$REPO_DIR/tools/plugins/fetch_plugins.py" --dest "$PROXY_DIR/plugins" --profiles auth --target velocity \
    || die "Proxy plugin install failed (see above)."
}

proxy_running() {
  service_user_exec has-session -t "$TMUX_PROXY_SESSION" 2>/dev/null
}

# Wait until Velocity logs "Done (...)" for the current boot. $1 = timeout seconds, $2 = boot start (epoch).
wait_proxy_ready() {
  local timeout="${1:-60}" since="${2:-0}" log="$PROXY_DIR/logs/latest.log" waited=0
  while ((waited < timeout)); do
    if [[ -f "$log" ]] && (( $(stat -c %Y "$log") >= since )) && grep -q 'Done (' "$log"; then return 0; fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}
