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


JAVA_CLASS_MAX = 65  # Paper 1.21.11 runs on Java 21


def jar_info(path):
    """(plugin name, class-file major version of the main class) of a plugin jar."""
    import re
    import zipfile
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        yml = "plugin.yml" if "plugin.yml" in names else "paper-plugin.yml" if "paper-plugin.yml" in names else None
        if not yml:
            return None, 0
        text = z.read(yml).decode("utf-8", "replace")
        name = re.search(r"(?m)^name:\s*[\"']?([^\"'\r\n]+)", text)
        main = re.search(r"(?m)^main:\s*[\"']?([^\"'\r\n]+)", text)
        major = 0
        if main:
            cls = main.group(1).strip().replace(".", "/") + ".class"
            if cls in names:
                head = z.read(cls)[:8]
                major = head[6] * 256 + head[7]
        return (name.group(1).strip() if name else None), major


def from_modrinth(slug, mc):
    """Candidate builds, best first: releases newest first, then pre-releases."""
    q = urllib.parse.urlencode({"loaders": json.dumps(LOADERS), "game_versions": json.dumps([mc])})
    versions = get_json(f"https://api.modrinth.com/v2/project/{slug}/version?{q}") or []
    ordered = [v for v in versions if v.get("version_type") == "release"] + [v for v in versions if v.get("version_type") != "release"]
    out = []
    for v in ordered[:6]:
        f = next((f for f in v["files"] if f.get("primary")), v["files"][0] if v["files"] else None)
        if f:
            out.append({"source": "modrinth", "version": v["version_number"], "file": f["filename"], "url": f["url"],
                        "hash": ("sha512", f["hashes"]["sha512"])})
    return out


def from_hangar(project, mc):
    owner, slug = project.split("/", 1)
    q = urllib.parse.urlencode({"platform": "PAPER", "platformVersion": mc, "limit": 5})
    data = get_json(f"https://hangar.papermc.io/api/v1/projects/{owner}/{slug}/versions?{q}")
    out = []
    for v in data.get("result", []):
        if v.get("channel", {}).get("name", "").lower() not in ("release", ""):
            continue
        d = v.get("downloads", {}).get("PAPER") or {}
        url = d.get("downloadUrl") or d.get("externalUrl")
        if not url:
            continue
        info = d.get("fileInfo") or {}
        out.append({"source": "hangar", "version": v["name"], "file": info.get("name") or f"{slug}-{v['name']}.jar", "url": url,
                    "hash": ("sha256", info.get("sha256Hash")) if info.get("sha256Hash") else None})
    return out


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
    managed = set()
    for p in chosen:
        managed.add(p["name"])
        try:
            candidates = []
            if p.get("modrinth"):
                try:
                    candidates += from_modrinth(p["modrinth"], mc)
                except urllib.error.HTTPError as e:
                    if e.code != 404:
                        raise
            if p.get("hangar"):
                try:
                    candidates += from_hangar(p["hangar"], mc)
                except urllib.error.HTTPError as e:
                    if e.code != 404:
                        raise
            prev = lock.get(p["id"], {})
            chosen_build = None
            for res in candidates:
                dest = os.path.join(args.dest, res["file"])
                if os.path.exists(dest):
                    if jar_info(dest)[1] <= JAVA_CLASS_MAX:
                        chosen_build = res
                        break
                    os.remove(dest)
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
                        print(f"  !! {p['name']} {res['version']}: {algo} mismatch")
                        continue
                if jar_info(tmp)[1] > JAVA_CLASS_MAX:
                    os.remove(tmp)
                    print(f"  .. {p['name']} {res['version']} needs a newer Java; trying an older build")
                    continue
                os.replace(tmp, dest)
                chosen_build = res
                break
            if chosen_build is None:
                missing.append(p["name"])
                print(f"  -- {p['name']}: no Java 21 build for Minecraft {mc}; skipped")
                continue
            if prev.get("file") and prev["file"] != chosen_build["file"]:
                old = os.path.join(args.dest, prev["file"])
                if os.path.exists(old):
                    os.remove(old)
            lock[p["id"]] = {"name": p["name"], "version": chosen_build["version"], "file": chosen_build["file"], "source": chosen_build["source"]}
            print(f"  ok {p['name']} {chosen_build['version']} ({chosen_build['source']})")
            ok.append(p["name"])
        except Exception as e:  # report and continue with the others
            failed.append(f"{p['name']}: {e}")
            print(f"  !! {p['name']}: {e}")
    keep = {v.get("file") for v in lock.values()}
    for jar in sorted(os.listdir(args.dest)):
        if not jar.endswith(".jar") or jar in keep or jar.startswith("suld-plugin") or jar == "SULD.jar":
            continue
        try:
            name, major = jar_info(os.path.join(args.dest, jar))
        except Exception:
            continue
        if name in managed or (name == "Essentials" and "EssentialsX" in managed) or (name == "Vault" and "VaultUnlocked" in managed) or major > JAVA_CLASS_MAX:
            print(f"  xx removing stray {jar} ({name})")
            os.remove(os.path.join(args.dest, jar))
    with open(lock_path, "w", encoding="utf-8") as fh:
        json.dump(lock, fh, indent=2)
    print(f"plugins: {len(ok)} installed, {len(missing)} without a {mc} build, {len(failed)} failed")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
