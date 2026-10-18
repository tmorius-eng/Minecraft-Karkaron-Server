#!/usr/bin/env bash
# SULD health check. Exit 0 = healthy (warnings allowed), 1 = at least one failure.
#   health-check.sh [--quiet] [--no-pack]
# Safe to run any time and from cron/monitoring; changes nothing.
set -uo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env

QUIET=0; CHECK_PACK=1
for a in "$@"; do
  case "$a" in --quiet) QUIET=1 ;; --no-pack) CHECK_PACK=0 ;; esac
done
failures=0; warnings=0
pass()  { ((QUIET)) || ok "$*"; }
bad()   { fail "$*"; failures=$((failures + 1)); }
caution() { warn "$*"; warnings=$((warnings + 1)); }

# 1. Process supervision
if systemctl is-active --quiet suld 2>/dev/null; then pass "systemd: suld.service active"
else bad "systemd: suld.service is not active"; fi
if server_running; then pass "tmux session '$TMUX_SESSION' present"
else bad "tmux session '$TMUX_SESSION' missing"; fi
if pgrep -f "java .* -jar $PAPER_JAR" >/dev/null; then pass "java process running"
else bad "no Paper java process"; fi

# 2. Minecraft protocol answers (a real server-list ping, not just an open port)
if reply="$(python3 -I "$LIB_DIR/tools/mc_ping.py" 127.0.0.1 "$SERVER_PORT" 5 2>&1)"; then pass "Minecraft ping OK: $reply"
else bad "Minecraft ping failed on :$SERVER_PORT ($reply)"; fi
if hybrid; then
  if reply="$(python3 -I "$LIB_DIR/tools/mc_ping.py" 127.0.0.1 "$PAPER_PORT" 5 2>&1)"; then pass "Paper behind the proxy answers: $reply"
  else bad "Paper ping failed on 127.0.0.1:$PAPER_PORT ($reply)"; fi
fi

# 3. SULD plugin state from the live log
log="$SERVER_DIR/logs/latest.log"
if [[ -f "$log" ]]; then
  if grep -q 'Failed to initialise SULD' "$log"; then bad "SULD plugin failed to initialise (see $log)"
  elif grep -q '\[SULD\] SULD enabled' "$log"; then pass "SULD plugin enabled"
  else bad "SULD plugin has not reported 'enabled'"; fi
  if grep -q 'SULD storage is MEMORY' "$log"; then caution "SULD is on MEMORY storage — player data is NOT persisted"; fi
else
  bad "server log missing: $log"
fi

# 3b. Authentication hardening (docs/AUTHENTICATION.md): Paper verifies accounts itself (AUTH_MODE=online), or it sits
# on loopback behind the signed Velocity proxy and AuthMe (AUTH_MODE=hybrid). Never a reachable offline-mode Paper.
props="$SERVER_DIR/server.properties"
if ! hybrid; then
  if grep -q '^online-mode=true' "$props" 2>/dev/null; then pass "online-mode=true (accounts verified by Minecraft/Microsoft)"
  else bad "online-mode is not true — identities are NOT verified (docs/AUTHENTICATION.md)"; fi
else
  grep -q '^online-mode=false' "$props" 2>/dev/null && pass "online-mode=false behind the proxy (AUTH_MODE=hybrid)" \
    || bad "AUTH_MODE=hybrid but server.properties is not online-mode=false"
  if grep -q '^server-ip=127.0.0.1$' "$props" 2>/dev/null && grep -q "^server-port=${PAPER_PORT}$" "$props"; then
    pass "Paper bound to 127.0.0.1:$PAPER_PORT only"
  else bad "Paper is not bound to 127.0.0.1:$PAPER_PORT — the offline backend could be reached without the proxy"; fi
  if ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "^(0\.0\.0\.0|\*|\[::\]):${PAPER_PORT}$"; then
    bad "something listens on *:$PAPER_PORT — the backend port must be loopback only"
  fi
  global="$SERVER_DIR/config/paper-global.yml"
  if [[ -f "$global" ]] && sed -n '/^  velocity:/,/^ \{0,2\}[a-z_]/p' "$global" | grep -q '^    enabled: true' \
      && sed -n '/^  velocity:/,/^ \{0,2\}[a-z_]/p' "$global" | grep -Eq "^    secret: '?[0-9a-f]{64}'?$"; then
    pass "paper-global.yml trusts only signed Velocity forwarding"
  else bad "paper-global.yml: proxies.velocity is not enabled with a 64-hex secret"; fi
  for f in "$PROXY_DIR/forwarding.secret" "$global" "$AUTHME_CONFIG" "$PROXY_DIR/plugins/authmevelocity/config.yml"; do
    [[ -f "$f" ]] || { bad "missing: $f"; continue; }
    [[ "$(stat -c %a "$f")" == "600" ]] && pass "mode 600: $f" || bad "$f holds a secret and is not mode 600"
  done
  if systemctl is-active --quiet suld-proxy 2>/dev/null && proxy_running; then pass "systemd: suld-proxy.service active"
  else bad "suld-proxy.service is not active (players cannot connect)"; fi
  if [[ -f "$PROXY_DIR/logs/latest.log" ]] && grep -q 'Done (' "$PROXY_DIR/logs/latest.log"; then pass "Velocity ready"
  else bad "Velocity has not reported 'Done'"; fi
  if grep -q "online-mode = false" "$PROXY_DIR/velocity.toml" && grep -q 'player-info-forwarding-mode = "modern"' "$PROXY_DIR/velocity.toml"; then
    pass "velocity.toml: offline proxy, modern forwarding"
  else bad "velocity.toml is not offline + modern forwarding"; fi
  if [[ -f "$log" ]] && grep -q 'Authentication: HYBRID_AUTHME' "$log" && ! grep -q 'AuthMe cannot be reached' "$log"; then
    pass "SÜLD: HYBRID_AUTHME, AuthMe connected"
  else bad "SÜLD did not report HYBRID_AUTHME with AuthMe connected (see $log)"; fi
  if [[ -n "$DB_PASS" ]] && [[ "$(PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc \
      "select count(*) from information_schema.tables where table_name='authme'" 2>/dev/null)" == "1" ]]; then
    pass "AuthMe accounts on PostgreSQL (table authme)"
  else bad "AuthMe's table is not in PostgreSQL $DB_NAME (AuthMe still on SQLite?)"; fi
fi
if [[ -f "$PLUGIN_CONFIG" ]] && grep -Eq '^\s+allow-insecure-offline-dev-mode:\s*true' "$PLUGIN_CONFIG"; then
  bad "auth.allow-insecure-offline-dev-mode is TRUE — anyone could join as anyone"
fi
if [[ -f "$log" ]] && grep -q 'REFUSING ALL LOGINS' "$log"; then bad "SULD is refusing all logins (server not verifying accounts)"; fi

# 4. Database
if [[ -n "$DB_PASS" ]]; then
  if out="$(PGPASSWORD="$DB_PASS" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc 'select max(version) from suld_schema_version' 2>&1)"; then
    pass "PostgreSQL OK (schema version ${out:-?})"
  else bad "PostgreSQL query failed: ${out%%$'\n'*}"; fi
fi
if ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq '^(0\.0\.0\.0|\*|\[::\]):5432$'; then
  caution "PostgreSQL is listening on all interfaces — it must be localhost-only"
fi

# 5. Resource pack hosting
if ((CHECK_PACK)) && [[ -f "$PLUGIN_CONFIG" ]] && grep -Eq '^\s+enabled: true' <(sed -n '/^resource-pack:/,$p' "$PLUGIN_CONFIG"); then
  url="$(sed -n '/^resource-pack:/,$p' "$PLUGIN_CONFIG" | sed -n "s/^  url: '\(.*\)'.*/\1/p")"
  want="$(sed -n '/^resource-pack:/,$p' "$PLUGIN_CONFIG" | sed -n "s/^  sha1: '\(.*\)'.*/\1/p")"
  if [[ -n "$url" ]] && got="$(curl -fsS --max-time 15 "$url" 2>/dev/null | sha1sum | awk '{print $1}')" && [[ "$got" == "$want" ]]; then
    pass "resource pack reachable and SHA-1 matches"
  else bad "resource pack unreachable or SHA-1 mismatch ($url)"; fi
fi

# 6. Resources
free_mb="$(df -Pm "$SERVER_DIR" 2>/dev/null | awk 'NR==2{print $4}')"
if [[ -n "$free_mb" ]]; then
  if ((free_mb < HEALTH_MIN_DISK_MB)); then caution "low disk: ${free_mb} MB free (< ${HEALTH_MIN_DISK_MB})"
  else pass "disk free: ${free_mb} MB"; fi
fi
avail_mb="$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo)"
if [[ -n "$avail_mb" ]] && ((avail_mb < 256)); then caution "low available memory: ${avail_mb} MB"; fi

# 7. Backups are fresh (daily timer; stale > 2 days is worth knowing)
newest="$(find "$BACKUP_DIR" -maxdepth 1 -name 'suld-*' -type d -printf '%T@\n' 2>/dev/null | sort -n | tail -1)"
if [[ -z "$newest" ]]; then caution "no backups found in $BACKUP_DIR"
elif (( $(date +%s) - ${newest%.*} > 172800 )); then caution "newest backup is older than 2 days"; fi

if ((failures)); then fail "$failures check(s) FAILED, $warnings warning(s)"; exit 1; fi
((QUIET)) || ok "healthy ($warnings warning(s))"
exit 0
