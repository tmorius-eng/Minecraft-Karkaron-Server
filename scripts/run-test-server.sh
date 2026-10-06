#!/usr/bin/env bash
#
# Stand up a local Paper test server with the freshly built SULD plugin.
#
# This is a self-contained alternative to `./gradlew :suld-plugin:runServer`.
# It downloads the Paper server jar directly from Paper's v3 "fill" download API
# and runs it headless. Requires outbound access to:
#   - fill.papermc.io          (build metadata)
#   - fill-data.papermc.io     (the Paper server jar)
#   - piston-data.mojang.com / piston-meta.mojang.com / libraries.minecraft.net
#                              (vanilla server + libraries that paperclip patches)
# plus Maven Central (Paper downloads the plugin's `libraries:` on first load).
#
# Usage: scripts/run-test-server.sh [minecraft-version]
set -euo pipefail

MC_VERSION="${1:-1.21.11}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/run"
API="https://fill.papermc.io/v3/projects/paper"

echo "==> Building SULD plugin"
"$ROOT/gradlew" :suld-plugin:shadowJar

echo "==> Resolving latest Paper build for $MC_VERSION"
BUILD_JSON="$(curl -fsSL "$API/versions/$MC_VERSION/builds/latest")"
JAR_URL="$(printf '%s' "$BUILD_JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["downloads"]["server:default"]["url"])')"
JAR_NAME="$(printf '%s' "$BUILD_JSON" | python3 -c 'import sys,json;print(json.load(sys.stdin)["downloads"]["server:default"]["name"])')"

mkdir -p "$RUN_DIR/plugins"
if [[ ! -f "$RUN_DIR/$JAR_NAME" ]]; then
  echo "==> Downloading $JAR_NAME"
  curl -fsSL -o "$RUN_DIR/$JAR_NAME" "$JAR_URL"
fi

echo "==> Installing plugin and accepting EULA"
cp "$ROOT"/suld-plugin/build/libs/suld-plugin-*.jar "$RUN_DIR/plugins/"
# Remove the -sources jar if the glob copied it.
rm -f "$RUN_DIR"/plugins/*-sources.jar
echo "eula=true" > "$RUN_DIR/eula.txt"
cat > "$RUN_DIR/server.properties" <<PROPS
online-mode=false
motd=SULD test server
max-players=10
view-distance=6
spawn-protection=0
PROPS

echo "==> Starting Paper ($JAR_NAME). Type 'stop' to shut down."
cd "$RUN_DIR"
exec java -Xms1G -Xmx2G -jar "$JAR_NAME" --nogui
