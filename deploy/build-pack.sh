#!/usr/bin/env bash
# Build the SULD resource pack into a deterministic, content-addressed ZIP.
#   build-pack.sh [REPO_DIR]    prints:  PACK_FILE=<name>  PACK_SHA1=<sha1>
# Validation (registry, pack contents, size budget) must pass first. The ZIP name embeds
# the hash, so clients never see a stale cached pack after an update.
set -euo pipefail
# shellcheck source=lib.sh
source "$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")/lib.sh"
load_env
repo="${1:-$REPO_DIR}"
[[ -f "$repo/resourcepack/pack.mcmeta" ]] || die "No resource pack at $repo/resourcepack"

python3 "$repo/tools/validation/run_all.py" >/dev/null || die "Pack/registry validation failed (python3 $repo/tools/validation/run_all.py)"

mkdir -p "$PACK_DIR"
tmp="$(mktemp "$PACK_DIR/.build.XXXXXX")"
python3 -I - "$repo/resourcepack" "$tmp" <<'PY'
import os, sys, zipfile
src, out = sys.argv[1], sys.argv[2]
files = []
for root, _dirs, names in os.walk(src):
    for n in names:
        if n.endswith((".md", ".gitkeep")):
            continue
        full = os.path.join(root, n)
        files.append((os.path.relpath(full, src).replace(os.sep, "/"), full))
files.sort(key=lambda f: (f[0] != "pack.mcmeta", f[0]))  # pack.mcmeta first, then stable order
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for arc, full in files:
        info = zipfile.ZipInfo(arc, date_time=(2020, 1, 1, 0, 0, 0))  # fixed time => reproducible hash
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        with open(full, "rb") as fh:
            z.writestr(info, fh.read())
PY
sha1="$(sha1_of "$tmp")"
name="suld-pack-${sha1:0:12}.zip"
mv -f "$tmp" "$PACK_DIR/$name"
chmod 644 "$PACK_DIR/$name"
if [[ $EUID -eq 0 ]] && id "$SULD_USER" >/dev/null 2>&1; then chown "$SULD_USER:$SULD_USER" "$PACK_DIR/$name"; fi
# Keep the three most recent packs (rollback + clients mid-download), prune the rest.
# shellcheck disable=SC2012
ls -1t "$PACK_DIR"/suld-pack-*.zip 2>/dev/null | tail -n +4 | xargs -r rm -f --
echo "PACK_FILE=$name"
echo "PACK_SHA1=$sha1"
