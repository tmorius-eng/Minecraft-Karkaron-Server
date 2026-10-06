#!/usr/bin/env python3
"""Download the trusted third-party server plugins listed in deploy/plugins/plugins.json.

    python3 tools/plugins/fetch_plugins.py --dest run/plugins --profiles core
    python3 tools/plugins/fetch_plugins.py --dest /opt/suld/server/plugins --profiles core,hardening

For each plugin of the selected profiles it resolves the newest *release* built for the target Minecraft
version (default from the manifest, 1.21.11) and a Paper-compatible loader, from Modrinth first and
Hangar second, downloads it, verifies the published hash (SHA-512 / SHA-256), and replaces any older
file of the same plugin it installed before. Plugins with no build for the target version are reported
and skipped, never silently replaced by an incompatible build. A lock file (dest/.suld-plugins.lock.json)
records exactly what was installed.
"""
import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

UA = "tmorius-eng/Minecraft-Karkaron-Server (SULD plugin installer)"
LOADERS = ["paper", "purpur", "spigot", "bukkit"]


def get_json(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=40) as r:
        return json.load(r)


def download(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    tmp = dest + ".part"
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, "wb") as fh:
        while True:
            chunk = r.read(1 << 16)
            if not chunk:
                break
            fh.write(chunk)
    return tmp


def from_modrinth(slug, mc):
    q = urllib.parse.urlencode({"loaders": json.dumps(LOADERS), "game_versions": json.dumps([mc])})
    versions = get_json(f"https://api.modrinth.com/v2/project/{slug}/version?{q}")
    if not versions:
        return None
    releases = [v for v in versions if v.get("version_type") == "release"] or versions
    v = releases[0]
    f = next((f for f in v["files"] if f.get("primary")), v["files"][0])
    return {"source": "modrinth", "version": v["version_number"], "file": f["filename"], "url": f["url"],
            "hash": ("sha512", f["hashes"]["sha512"])}


def from_hangar(project, mc):
    owner, slug = project.split("/", 1)
    q = urllib.parse.urlencode({"platform": "PAPER", "platformVersion": mc, "limit": 5})
    data = get_json(f"https://hangar.papermc.io/api/v1/projects/{owner}/{slug}/versions?{q}")
    for v in data.get("result", []):
        if v.get("channel", {}).get("name", "").lower() not in ("release", ""):
            continue
        d = v.get("downloads", {}).get("PAPER") or {}
        url = d.get("downloadUrl") or d.get("externalUrl")
        if not url:
            continue
        info = d.get("fileInfo") or {}
        return {"source": "hangar", "version": v["name"], "file": info.get("name") or f"{slug}-{v['name']}.jar", "url": url,
                "hash": ("sha256", info.get("sha256Hash")) if info.get("sha256Hash") else None}
    return None


def main():
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ap = argparse.ArgumentParser()
    ap.add_argument("--dest", required=True)
    ap.add_argument("--profiles", default="core")
    ap.add_argument("--manifest", default=os.path.join(root, "deploy", "plugins", "plugins.json"))
    ap.add_argument("--mc")
    ap.add_argument("--only", help="comma-separated plugin ids")
    args = ap.parse_args()
    manifest = json.load(open(args.manifest, encoding="utf-8"))
    mc = args.mc or manifest["minecraft"]
    profiles = {p.strip() for p in args.profiles.split(",") if p.strip()}
    only = {p.strip() for p in args.only.split(",")} if args.only else None
    os.makedirs(args.dest, exist_ok=True)
    lock_path = os.path.join(args.dest, ".suld-plugins.lock.json")
    lock = json.load(open(lock_path)) if os.path.exists(lock_path) else {}
    chosen = [p for p in manifest["plugins"] if p["profile"] in profiles and (only is None or p["id"] in only)]
    ids = {p["id"] for p in chosen}
    for p in chosen:
        for c in p.get("conflicts", []):
            if c in ids:
                sys.exit(f"manifest: {p['id']} conflicts with {c}; select only one")
    ok, missing, failed = [], [], []
    for p in chosen:
        try:
            res = None
            if p.get("modrinth"):
                try:
                    res = from_modrinth(p["modrinth"], mc)
                except urllib.error.HTTPError as e:
                    if e.code != 404:
                        raise
            if res is None and p.get("hangar"):
                res = from_hangar(p["hangar"], mc)
            if res is None:
                missing.append(p["name"])
                print(f"  -- {p['name']}: no release for Minecraft {mc}; skipped")
                continue
            dest = os.path.join(args.dest, res["file"])
            prev = lock.get(p["id"], {})
            if prev.get("file") == res["file"] and os.path.exists(dest):
                print(f"  ok {p['name']} {res['version']} (already installed)")
                ok.append(p["name"])
                continue
            tmp = download(res["url"], dest)
            if res.get("hash"):
                algo, want = res["hash"]
                h = hashlib.new(algo)
                with open(tmp, "rb") as fh:
                    for chunk in iter(lambda: fh.read(1 << 16), b""):
                        h.update(chunk)
                if h.hexdigest().lower() != want.lower():
                    os.remove(tmp)
                    raise RuntimeError(f"{algo} mismatch")
            if prev.get("file") and prev["file"] != res["file"]:
                old = os.path.join(args.dest, prev["file"])
                if os.path.exists(old):
                    os.remove(old)
            os.replace(tmp, dest)
            lock[p["id"]] = {"name": p["name"], "version": res["version"], "file": res["file"], "source": res["source"]}
            print(f"  ++ {p['name']} {res['version']} ({res['source']}) -> {res['file']}")
            ok.append(p["name"])
        except Exception as e:  # report and continue with the others
            failed.append(f"{p['name']}: {e}")
            print(f"  !! {p['name']}: {e}")
    with open(lock_path, "w", encoding="utf-8") as fh:
        json.dump(lock, fh, indent=2)
    print(f"plugins: {len(ok)} installed, {len(missing)} without a {mc} build, {len(failed)} failed")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
