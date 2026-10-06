#!/usr/bin/env bash
# Grant (or revoke) server operator to Minecraft players on the running server.
#   sudo /opt/suld/bin/op.sh qeevr_ [OtherName ...]      grant op
#   sudo /opt/suld/bin/op.sh --deop Name                 revoke op
# Names are validated (Minecraft names: 3-16 chars, letters/digits/_) before anything is sent
# to the console. With online-mode=true Paper resolves the account UUID from Mojang.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env

action=op
if [[ "${1:-}" == "--deop" ]]; then action=deop; shift; fi
(($#)) || die "usage: op.sh [--deop] <player> [player ...]"
server_running || die "SULD is not running (start it first: sudo $BIN_DIR/start.sh)"

rc=0
for name in "$@"; do
  if [[ ! "$name" =~ ^[A-Za-z0-9_]{3,16}$ ]]; then
    fail "invalid Minecraft name: '$name'"; rc=1; continue
  fi
  console_send "$action $name"
done
sleep 3
for name in "$@"; do
  [[ "$name" =~ ^[A-Za-z0-9_]{3,16}$ ]] || continue
  if grep -qi "\"name\": *\"$name\"" "$SERVER_DIR/ops.json" 2>/dev/null; then
    [[ $action == op ]] && ok "$name is an operator" || { fail "$name is still an operator"; rc=1; }
  else
    [[ $action == deop ]] && ok "$name is not an operator" || { fail "$name was NOT opped (unknown account? check $SERVER_DIR/logs/latest.log)"; rc=1; }
  fi
done
exit "$rc"
