#!/usr/bin/env bash
# The whole bot regression in one command, on a local dev Paper (offline mode):
#   tools/loadtest/regress.sh <srvctl.sh> <server latest.log> [port]
# Runs every pass/fail bot in a fixed order and prints one table (bot · result · seconds); exit 1 on any failure.
# DEV_CAMPAIGNS=1 also runs campaign-effects.js and dungeon-clear.js (they need the dev campaigns test_kill/test_prop/
# test_npc/test_clear in plugins/SULD/content/campaigns.json, see their headers). NODE_PATH must reach mineflayer.
set -u
CTL=${1:?srvctl.sh}; LOG=${2:?latest.log}; PORT=${3:-25572}
DIR=$(cd "$(dirname "$0")/bots" && pwd)
OUT=${REGRESS_OUT:-$(mktemp -d)}
RUNS=(
  "hubbot|$CTL $LOG $PORT"
  "skymenubot|$CTL $PORT"
  "dodgehudbot|$CTL $PORT"
  "packmodesbot:nil|$CTL nil $PORT"
  "packmodesbot:foreign|$CTL foreign $PORT"
  "packmodesbot:none|$CTL none $PORT"
  "classchangebot|$CTL $PORT"
  "classreconnectbot:nil|$CTL nil $PORT"
  "classreconnectbot:none|$CTL none $PORT"
  "classtrialbot|$CTL $PORT"
  "mergenbowbot|$CTL $PORT"
  "masterybot|$CTL $PORT"
  "trialbot|$CTL $PORT"
  "trialclassbot:baatar|$CTL $PORT 10"
  "trialclassbot:mergen|$CTL $PORT 11"
  "trialclassbot:boo|$CTL $PORT 12"
  "trialclassbot:darkhan|$CTL $PORT 13"
  "trialclassbot:khulegchin|$CTL $PORT 14"
  "ascendstarsbot|$CTL $PORT"
  "duel-defeat|$CTL $LOG $PORT"
  "craft|$CTL $LOG $PORT"
  "craft-orders|$CTL $LOG $PORT"
  "world-boss|$CTL $LOG $PORT"
  "khuraldai-smoke|$CTL $LOG $PORT"
  "featbot|$CTL $LOG"
)
if [ "${DEV_CAMPAIGNS:-0}" = 1 ]; then
  RUNS+=("campaign-effects|$CTL $LOG $PORT" "dungeon-clear|$CTL $LOG $PORT")
fi
bad=0
printf '%-28s %-8s %6s  %s\n' BOT RESULT SEC DETAIL
for r in "${RUNS[@]}"; do
  label=${r%%|*}; args=${r#*|}; bot=${label%%:*}
  t0=$(date +%s)
  # shellcheck disable=SC2086
  PORT=$PORT timeout "${BOT_TIMEOUT:-420}" node "$DIR/$bot.js" $args > "$OUT/$label.log" 2>&1
  rc=$?
  sec=$(( $(date +%s) - t0 ))
  pass=$(grep -c '^PASS' "$OUT/$label.log"); fail=$(grep -c '^FAIL' "$OUT/$label.log")
  if [ "$fail" -gt 0 ] || [ "$pass" -eq 0 ] || { [ "$rc" -ne 0 ] && [ "$rc" -ne 1 ]; } || { [ "$rc" -eq 1 ] && [ "$fail" -eq 0 ]; }; then
    res=FAIL; bad=$((bad + 1))
    detail=$(grep -m1 '^FAIL' "$OUT/$label.log" | cut -c1-90); [ -z "$detail" ] && detail="exit $rc, $pass pass lines"
  else
    res="PASS $pass"; detail=""
  fi
  printf '%-28s %-8s %6s  %s\n' "$label" "$res" "$sec" "$detail"
  sleep 2
done
echo "logs: $OUT"
[ "$bad" -eq 0 ] && echo "ALL GREEN" || echo "$bad FAILED"
[ "$bad" -eq 0 ]
