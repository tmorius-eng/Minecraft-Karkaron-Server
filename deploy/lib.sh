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
  : "${DB_HOST:=127.0.0.1}"
  : "${DB_PORT:=5432}"
  : "${DB_NAME:=suld}"
  : "${DB_USER:=suld}"
  : "${DB_PASS:=}"
  : "${MAX_PLAYERS:=50}"
  : "${ONLINE_MODE:=true}"
  : "${TMUX_SESSION:=suld}"
  : "${BACKUP_KEEP:=14}"
  : "${ACCEPT_EULA:=false}"
  : "${ADMIN_PLAYERS:=}"
  : "${HEALTH_MIN_DISK_MB:=2048}"
  TMUX_SOCK="$RUN_DIR/tmux.sock"
  PAPER_JAR="$SERVER_DIR/paper.jar"
  PLUGIN_CONFIG="$SERVER_DIR/plugins/SULD/config.yml"
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
             SERVER_PORT PACK_PORT MAX_PLAYERS ONLINE_MODE MOTD TMUX_SESSION; do
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
  [[ "$profiles" == "none" ]] && { ok "Third-party plugins: skipped (PLUGIN_PROFILES=none)"; return 0; }
  log "Third-party plugins ($profiles)"
  if [[ "${DRY_RUN:-0}" == "1" ]]; then
    echo "  [dry-run] python3 tools/plugins/fetch_plugins.py --dest $SERVER_DIR/plugins --profiles $profiles"
    return 0
  fi
  as_suld python3 "$REPO_DIR/tools/plugins/fetch_plugins.py" --dest "$SERVER_DIR/plugins" --profiles "$profiles" \
    || die "Plugin install failed (see above). Fix the network or set PLUGIN_PROFILES."
}
