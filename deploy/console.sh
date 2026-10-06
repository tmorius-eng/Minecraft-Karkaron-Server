#!/usr/bin/env bash
# Attach to the live server console (detach with Ctrl-b then d; do NOT Ctrl-c).
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env
server_running || die "SULD is not running."
exec runuser -u "$SULD_USER" -- tmux -S "$TMUX_SOCK" attach -t "$TMUX_SESSION"
