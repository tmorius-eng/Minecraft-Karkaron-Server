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

log "Firewall: SSH=$ssh_port Minecraft=$SERVER_PORT pack=$PACK_PORT bedrock=${BEDROCK_PORT}/udp"
run ufw default deny incoming
run ufw default allow outgoing
# SSH rule goes in BEFORE enabling, so enabling can never lock the operator out.
run ufw limit "${ssh_port}/tcp" comment 'ssh (rate-limited)'
run ufw allow "${SERVER_PORT}/tcp" comment 'minecraft'
run ufw allow "${PACK_PORT}/tcp" comment 'suld resource pack'
run ufw allow "${BEDROCK_PORT}/udp" comment 'bedrock (geyser)'
run ufw --force enable
[[ "$DRY_RUN" == "1" ]] || ufw status verbose
