#!/usr/bin/env bash
# The whole bot regression in one command, on a local dev Paper (offline mode):
#   tools/loadtest/regress.sh <srvctl.sh> <server latest.log> [port]
# Runs every pass/fail bot in a fixed order and prints one table (bot · result · seconds); exit 1 on any failure.
# DEV_CAMPAIGNS=1 also runs campaign-effects.js, dungeon-clear.js, campaign-deliver.js and trainingpay.js: dev/make_dev_campaigns.py
# writes the dev campaigns into the server's plugins/SULD/content/campaigns.json (server dir = two levels over the log),
# the server restarts (<srvctl> stop|start), and the file is removed and the server restarted after. DEV_CRASH=1 also
# runs trainingcrash.js and sellcrash.js last: they kill the server with SIGKILL (during training; 60 ms after a sale's
# confirm) and start it again (dev server only).
# HYBRID=1 HYBRID_DIR=<dir> also runs authbot.js on the dev pair of hybrid-dev.sh (set up once: hybrid-dev.sh <dir> setup …):
# the pair gets this build's jar (suld-plugin/build/libs, or SULD_JAR), starts, is tested and stopped.
# REGRESS_ONLY="label label …" runs only those rows (labels as the table prints them, e.g. "trialclassbot:darkhan tradebot
# authbot"): to finish a sweep that was cut short or to re-run one bot under the sweep's own harness; the groups still
# need their DEV_*/HYBRID flags.
# healthbot.js and sitesbot.js (last of the main runs) restart the server once each with <srvctl> stop|start.
# NODE_PATH must reach mineflayer.
set -u
CTL=${1:?srvctl.sh}; LOG=${2:?latest.log}; PORT=${3:-25572}
DIR=$(cd "$(dirname "$0")/bots" && pwd)
OUT=${REGRESS_OUT:-$(mktemp -d)}
mkdir -p "$OUT" || { echo "cannot create $OUT" >&2; exit 2; }
RUNS=(
  "hubbot|$CTL $LOG $PORT"
  "skymenubot|$CTL $PORT"
  "atlasbot|$CTL $PORT"
  "mapcbot|$CTL $PORT"
  "skyclickbot|$CTL $PORT"
  "partymapbot|$CTL $PORT"
  "dodgehudbot|$CTL $PORT"
  "packmodesbot:nil|$CTL nil $PORT"
  "packmodesbot:foreign|$CTL foreign $PORT"
  "packmodesbot:none|$CTL none $PORT"
  "classchangebot|$CTL $PORT"
  "classreconnectbot:nil|$CTL nil $PORT"
  "classreconnectbot:none|$CTL none $PORT"
  "classtrialbot|$CTL $PORT"
  "trainingbot|$CTL $PORT"
  "mergenbowbot|$CTL $PORT"
  "masterybot|$CTL $PORT"
  "trialbot|$CTL $PORT"
  "trial-lockend|$CTL $PORT"
  "soulreform|$CTL $PORT"
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
  "heroicbot|$CTL $LOG $PORT"
  "featbot|$CTL $LOG"
  "permbot|$CTL $LOG $PORT"
  "questregionbot|$CTL $PORT"
  "honorbot|$CTL $PORT"
  "professionbot|$CTL $PORT"
  "achievebot|$CTL $PORT"
  "healthbot|$CTL $PORT"
  "sitesbot|$CTL $LOG $PORT"
  "tradebot|$CTL $PORT"
  "discordbot|$CTL $PORT"
  "motdbot|$CTL $PORT"
  "eventsbot|$CTL $PORT"
  "referralbot|$CTL $PORT"
  "topbot|$CTL $LOG $PORT"
  "shopbot|$CTL $PORT"
)
DEV_RUNS=("campaign-effects|$CTL $LOG $PORT" "dungeon-clear|$CTL $LOG $PORT"
  "campaign-deliver|$CTL $LOG test_deliver item.chonon_arisan 60 $PORT" "trainingpay|$CTL $LOG $PORT")
SRV=$(cd "$(dirname "$LOG")/.." && pwd)
GEN="$(cd "$(dirname "$0")" && pwd)/dev/make_dev_campaigns.py"
bad=0
printf '%-28s %-8s %6s  %s\n' BOT RESULT SEC DETAIL
run_all() {
for r in "$@"; do
  label=${r%%|*}; args=${r#*|}; bot=${label%%:*}
  if [ -n "${REGRESS_ONLY:-}" ]; then case " $REGRESS_ONLY " in *" $label "*) ;; *) continue ;; esac; fi
  t0=$(date +%s)
  # shellcheck disable=SC2086
  PORT=$PORT timeout "${BOT_TIMEOUT:-420}" node "$DIR/$bot.js" $args > "$OUT/$label.log" 2>&1
  rc=$?
  sec=$(( $(date +%s) - t0 ))
  # a bot that never ran (no log: the redirect failed) is a FAIL, never a silent green (2026-10-13: a missing
  # REGRESS_OUT directory printed 28 «PASS 0» rows and «ALL GREEN»)
  pass=$(grep -c '^PASS' "$OUT/$label.log" 2>/dev/null); pass=${pass:-0}
  fail=$(grep -c '^FAIL' "$OUT/$label.log" 2>/dev/null); fail=${fail:-0}
  if [ ! -s "$OUT/$label.log" ] || [ "$fail" -gt 0 ] || [ "$pass" -eq 0 ] || { [ "$rc" -ne 0 ] && [ "$rc" -ne 1 ]; } || { [ "$rc" -eq 1 ] && [ "$fail" -eq 0 ]; }; then
    res=FAIL; bad=$((bad + 1))
    detail=$(grep -m1 '^FAIL' "$OUT/$label.log" | cut -c1-90); [ -z "$detail" ] && detail="exit $rc, $pass pass lines"
  else
    res="PASS $pass"; detail=""
  fi
  printf '%-28s %-8s %6s  %s\n' "$label" "$res" "$sec" "$detail"
  sleep 2
done
}
run_all "${RUNS[@]}"
if [ "${DEV_CAMPAIGNS:-0}" = 1 ]; then
  # the campaign bots need the dev campaigns: the server's own content copy, read at start
  python3 "$GEN" "$SRV" > /dev/null && "$CTL" stop > /dev/null && "$CTL" start > /dev/null
  run_all "${DEV_RUNS[@]}"
  python3 "$GEN" "$SRV" --remove > /dev/null && "$CTL" stop > /dev/null && "$CTL" start > /dev/null
fi
if [ "${DEV_CRASH:-0}" = 1 ]; then
  run_all "trainingcrash|$CTL $PORT" "sellcrash|$CTL $PORT 60"
fi
if [ "${HYBRID:-0}" = 1 ]; then
  HDIR=${HYBRID_DIR:?HYBRID=1 needs HYBRID_DIR, a pair from tools/loadtest/hybrid-dev.sh setup}
  HYB="$(cd "$(dirname "$0")" && pwd)/hybrid-dev.sh"
  JAR=${SULD_JAR:-$(find "$(cd "$(dirname "$0")/../.." && pwd)/suld-plugin/build/libs" -name 'suld-plugin-*.jar' ! -name '*-sources.jar' 2>/dev/null | head -1)}
  if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then echo "no plugin jar for the hybrid pair (./gradlew build, or SULD_JAR=…)" >&2; exit 2; fi
  "$HYB" "$HDIR" stop > /dev/null 2>&1
  cp -f "$JAR" "$HDIR/server/plugins/SULD.jar"
  pp=$(grep '^SERVER_PORT=' "$HDIR/dev.env" | cut -d= -f2); bp=$(grep '^PAPER_PORT=' "$HDIR/dev.env" | cut -d= -f2)
  if "$HYB" "$HDIR" start > "$OUT/hybrid-start.log" 2>&1; then
    run_all "authbot|${pp:-25580} ${bp:-25581} $HDIR/server/logs/latest.log"
  else
    printf '%-28s %-8s %6s  %s\n' authbot FAIL 0 "the dev pair did not start: $OUT/hybrid-start.log"; bad=$((bad + 1))
  fi
  "$HYB" "$HDIR" stop > /dev/null 2>&1
fi
echo "logs: $OUT"
[ "$bad" -eq 0 ] && echo "ALL GREEN" || echo "$bad FAILED"
[ "$bad" -eq 0 ]
