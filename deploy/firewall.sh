#!/usr/bin/env bash
# Configure the host firewall (ufw). Idempotent. Run by deploy.sh; safe to re-run.
#   firewall.sh [--ssh-port N]
# Opens ONLY: SSH (rate-limited), the Minecraft port, the resource-pack HTTP port and the Bedrock
# (Geyser) UDP port.
# PostgreSQL is never opened (it listens on localhost only). Also configure the Vultr
# cloud firewall group with the same three ports (see DEPLOYMENT.md).
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
ALLOW_EXAMPLE_ENV="${ALLOW_EXAMPLE_ENV:-0}" load_env
[[ "$DRY_RUN" == "1" ]] || need_root "$@"

ssh_port=""
if [[ "${1:-}" == "--ssh-port" ]]; then ssh_port="${2:?--ssh-port needs a number}"; fi
if [[ -z "$ssh_port" ]]; then
  ssh_port="$(sshd -T 2>/dev/null | awk '$1=="port"{print $2; exit}' || true)"
fi
ssh_port="${ssh_port:-22}"

# AUTH_MODE=hybrid: SERVER_PORT is the Velocity proxy; Paper's PAPER_PORT is loopback only and is never opened.
if hybrid && [[ "$PAPER_PORT" == "$SERVER_PORT" || "$PAPER_PORT" == "$PACK_PORT" || "$PAPER_PORT" == "$ssh_port" ]]; then
  die "PAPER_PORT ($PAPER_PORT) collides with a public port; it must stay closed"
fi
log "Firewall: SSH=$ssh_port Minecraft=$SERVER_PORT$(hybrid && echo ' (Velocity)') pack=$PACK_PORT bedrock=${BEDROCK_PORT}/udp"
run ufw default deny incoming
run ufw default allow outgoing
# SSH rule goes in BEFORE enabling, so enabling can never lock the operator out.
run ufw limit "${ssh_port}/tcp" comment 'ssh (rate-limited)'
run ufw allow "${SERVER_PORT}/tcp" comment 'minecraft'
run ufw allow "${PACK_PORT}/tcp" comment 'suld resource pack'
# Bedrock (Geyser) is optional and off by default (SÜLD is Java Edition only, docs/OPERATIONS.md): the UDP port opens
# only when BEDROCK_PORT is set in suld.env.
if [[ -n "${BEDROCK_PORT:-}" ]]; then run ufw allow "${BEDROCK_PORT}/udp" comment 'bedrock (geyser)'; fi
run ufw --force enable
[[ "$DRY_RUN" == "1" ]] || ufw status verbose
