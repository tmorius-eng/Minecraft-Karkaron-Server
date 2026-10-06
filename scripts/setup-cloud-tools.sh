#!/usr/bin/env bash
#
# Reproducibly install the SÜLD asset-pipeline toolchain in a Claude Cloud
# session: headless Blender, the Blender MCP server, and the Blockbench headless
# MCP server. Run once per fresh session (or wire it as a SessionStart hook)
# BEFORE relying on the MCP servers declared in .mcp.json.
#
# Network hosts required (ask the environment owner to allow any that are denied):
#   archive.ubuntu.com        apt packages (blender, xvfb, python3-numpy)
#   github.com (git protocol) cloning the two MCP servers
#   registry.npmjs.org        bun/npm deps for the Blockbench server
#   pypi.org                  the Blender MCP server (mcp sdk)
#   api.meshy.ai              Meshy generation (credential injected by the proxy)
#   assets.meshy.ai           Meshy model/thumbnail DOWNLOAD  <-- often still denied
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/.tools"
mkdir -p "$TOOLS"

echo "==> [1/4] Blender + headless render deps (apt)"
if ! command -v blender >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y --no-install-recommends blender xvfb python3-numpy
fi
blender --version | head -1

echo "==> [2/4] Blockbench headless MCP (git + bun)"
if [[ ! -d "$TOOLS/blockbench-mcp/.git" ]]; then
  git clone --depth 1 https://github.com/jasonjgardner/blockbench-mcp-plugin "$TOOLS/blockbench-mcp"
fi
( cd "$TOOLS/blockbench-mcp" && bun install )

echo "==> [3/4] Blender MCP server (git + venv)"
if [[ ! -d "$TOOLS/blender-mcp/.git" ]]; then
  git clone --depth 1 https://github.com/djeada/blender-mcp-server "$TOOLS/blender-mcp"
fi
python3 -m venv "$TOOLS/blender-mcp/.venv"
"$TOOLS/blender-mcp/.venv/bin/pip" install --quiet --disable-pip-version-check "$TOOLS/blender-mcp"

echo "==> [4/4] Done. MCP servers in .mcp.json will load on the NEXT session start."
echo "    (MCP tools are registered at session startup; run this, then start a new session.)"
