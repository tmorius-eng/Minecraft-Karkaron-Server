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

import re
import time

# Modrinth API v2 (https://docs.modrinth.com/api/): public endpoints, no token. A unique User-Agent is required.
UA = "tmorius-eng/Minecraft-Karkaron-Server/1.0"
MODRINTH = "https://api.modrinth.com/v2"
MAX_ATTEMPTS = 5


def _open(url, accept=None, timeout=40):
    """urlopen with the project User-Agent; on HTTP 429 waits X-Ratelimit-Reset / Retry-After and retries."""
    headers = {"User-Agent": UA}
    if accept:
        headers["Accept"] = accept
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=timeout)
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == MAX_ATTEMPTS:
                raise
            wait = None
            for h in ("X-Ratelimit-Reset", "Retry-After"):
                v = e.headers.get(h)
                if v and v.strip().isdigit():
                    wait = int(v.strip())
                    break
            wait = min(60, max(1, wait if wait is not None else 2 ** attempt))
            print(f"  .. Modrinth rate limit (HTTP 429); waiting {wait} s (attempt {attempt}/{MAX_ATTEMPTS})")
            time.sleep(wait)


def get_json(url):
    with _open(url, accept="application/json") as r:
        return json.load(r)


def download(url, dest):
    tmp = dest + ".part"
    with _open(url, timeout=120) as r, open(tmp, "wb") as fh:
        while True:
            chunk = r.read(1 << 16)
            if not chunk:
                break
            fh.write(chunk)
    return tmp


def file_hash(path, algo):
    h = hashlib.new(algo)
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest().lower()


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


def _version_ok(v):
    """Shape of a project-version object (GetProjectVersions) as far as this installer relies on it."""
    return (isinstance(v, dict) and all(isinstance(v.get(k), str) for k in ("id", "version_number", "version_type", "status", "date_published"))
            and all(isinstance(v.get(k), list) for k in ("game_versions", "loaders", "files")))


def _file_ok(f):
    h = f.get("hashes") if isinstance(f, dict) else None
    return (isinstance(f, dict) and isinstance(f.get("url"), str) and f["url"].startswith("https://")
            and isinstance(f.get("filename"), str) and f["filename"].endswith(".jar") and "/" not in f["filename"] and "\\" not in f["filename"]
            and isinstance(h, dict) and isinstance(h.get("sha512"), str) and re.fullmatch(r"[0-9a-fA-F]{128}", h["sha512"]) is not None)


def modrinth_releases(slug, mc, loaders):
    """Listed releases for this game version and loaders, newest first (GET /project/{slug}/version)."""
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(loaders), "include_changelog": "false"})
    endpoint = f"{MODRINTH}/project/{urllib.parse.quote(slug)}/version?{q}"
    data = get_json(endpoint)
    out = []
    for v in data if isinstance(data, list) else []:
        if not _version_ok(v) or v["version_type"] != "release" or v["status"] != "listed":
            continue
        if mc not in v["game_versions"] or not set(loaders) & set(v["loaders"]):
            continue
        files = [f for f in v["files"] if _file_ok(f)]
        f = next((f for f in files if f.get("primary") is True), files[0] if files else None)
        if not f:
            continue
        out.append({"source": "modrinth", "version": v["version_number"], "version_id": v["id"], "published": v["date_published"],
                    "file": f["filename"], "url": f["url"], "size": f.get("size") if isinstance(f.get("size"), int) else -1,
                    "hash": ("sha512", f["hashes"]["sha512"].lower()), "endpoint": endpoint})
    out.sort(key=lambda r: r["published"], reverse=True)
    return out


def from_modrinth(slug, mc):
    """Paper builds first; Bukkit/Spigot builds (they run on Paper) only when a plugin publishes no Paper build."""
    return modrinth_releases(slug, mc, ["paper"]) or modrinth_releases(slug, mc, ["spigot", "bukkit"])


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
    reject_path = os.path.join(args.dest, ".suld-plugins.java-rejected.json")
    try:
        rejected = {h for h in json.load(open(reject_path)) if isinstance(h, str)}
    except (OSError, ValueError, TypeError):
        rejected = set()
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
            if p.get("hangar") and not candidates:
                try:
                    candidates += from_hangar(p["hangar"], mc)
                except urllib.error.HTTPError as e:
                    if e.code != 404:
                        raise
            prev = lock.get(p["id"], {})
            chosen_build = None
            fresh = False
            for res in candidates[:6]:
                dest = os.path.join(args.dest, res["file"])
                algo, want = res["hash"] if res.get("hash") else (None, None)
                if algo and want.lower() in rejected:
                    print(f"  .. {p['name']} {res['version']} needs a newer Java (known); trying an older release")
                    continue
                if os.path.exists(dest) and algo and file_hash(dest, algo) == want.lower():
                    if jar_info(dest)[1] <= JAVA_CLASS_MAX:
                        chosen_build = res
                        break
                    os.remove(dest)
                    continue
                tmp = download(res["url"], dest)
                if res.get("size", -1) >= 0 and os.path.getsize(tmp) != res["size"]:
                    os.remove(tmp)
                    print(f"  !! {p['name']} {res['version']}: size mismatch")
                    continue
                if algo and file_hash(tmp, algo) != want.lower():
                    os.remove(tmp)
                    print(f"  !! {p['name']} {res['version']}: {algo} mismatch")
                    continue
                major = jar_info(tmp)[1]
                if major > JAVA_CLASS_MAX:
                    os.remove(tmp)
                    if algo:
                        rejected.add(want.lower())
                    print(f"  .. {p['name']} {res['version']} needs a newer Java (class version {major}); trying an older release")
                    continue
                os.replace(tmp, dest)
                chosen_build = res
                fresh = True
                break
            if chosen_build is None:
                missing.append(p["name"])
                print(f"  -- {p['name']}: no Java 21 build for Minecraft {mc}; skipped")
                continue
            if prev.get("file") and prev["file"] != chosen_build["file"]:
                old = os.path.join(args.dest, prev["file"])
                if os.path.exists(old):
                    os.remove(old)
            lock[p["id"]] = {"name": p["name"], "version": chosen_build["version"], "version_id": chosen_build.get("version_id", ""),
                             "file": chosen_build["file"], "source": chosen_build["source"],
                             "sha": chosen_build["hash"][1].lower() if chosen_build.get("hash") else "",
                             "endpoint": chosen_build.get("endpoint", "")}
            state = "downloaded" if fresh else "already installed"
            print(f"  ok {p['name']} {chosen_build['version']} [{chosen_build.get('version_id', '')}] {chosen_build['file']} ({state}, hash verified)")
            if chosen_build.get("endpoint"):
                print(f"     {chosen_build['endpoint']}")
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
    with open(reject_path, "w", encoding="utf-8") as fh:
        json.dump(sorted(rejected), fh, indent=2)
    print(f"plugins: {len(ok)} installed, {len(missing)} without a {mc} build, {len(failed)} failed")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
