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

# 3b. Authentication hardening (identity must come from Minecraft/Microsoft)
if grep -q '^online-mode=true' "$SERVER_DIR/server.properties" 2>/dev/null; then pass "online-mode=true (accounts verified by Minecraft/Microsoft)"
else bad "online-mode is not true — identities are NOT verified (docs/AUTHENTICATION.md)"; fi
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
