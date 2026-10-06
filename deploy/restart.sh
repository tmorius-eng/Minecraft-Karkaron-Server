#!/usr/bin/env bash
# Restart the SULD server. Usage: restart.sh [--warn SECONDS]
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env
need_root "$@"
if [[ "${1:-}" == "--warn" ]]; then
  "$BIN_DIR/stop.sh" --warn "${2:?--warn needs seconds}"
else
  "$BIN_DIR/stop.sh"
fi
"$BIN_DIR/start.sh"
