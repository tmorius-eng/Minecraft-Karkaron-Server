#!/usr/bin/env python3
"""Download the trusted third-party server plugins listed in deploy/plugins/plugins.json.

    python3 tools/plugins/fetch_plugins.py --dest run/plugins --profiles core
    python3 tools/plugins/fetch_plugins.py --dest /opt/suld/server/plugins --profiles core,hardening
    python3 tools/plugins/fetch_plugins.py --dest /tmp/plugins --profiles core,hardening --update-lock

Installs are **pinned** (OPS-002): deploy/plugins/plugins.lock.json names, per plugin id, the exact build (source,
version id, file name and its SHA-512 / SHA-256). With the lock present, every selected plugin is installed from
its lock entry and nothing else: the hash must match, a plugin without an entry fails, and a newer upstream
release is ignored until someone runs `--update-lock`. Two deploys a week apart install byte-identical jars, and
a rollback of the repository rolls the plugin versions back with it.

`--update-lock` resolves the newest *release* built for the target Minecraft version (default from the manifest,
1.21.11) and a Paper-compatible loader, from Modrinth first and Hangar second, installs it into --dest, and writes
the lock (merging with the entries of other profiles). Only builds with a published hash can be locked. Plugins with
no build for the target version are reported and skipped, never silently replaced by an incompatible build.
dest/.suld-plugins.lock.json is the install record of that directory (what is on disk right now).
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
    """(plugin name, class-file major version of the main class) of a Paper/Bukkit or Velocity plugin jar."""
    import re
    import zipfile
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        yml = "plugin.yml" if "plugin.yml" in names else "paper-plugin.yml" if "paper-plugin.yml" in names else None
        if yml:
            text = z.read(yml).decode("utf-8", "replace")
            name = re.search(r"(?m)^name:\s*[\"']?([^\"'\r\n]+)", text)
            main = re.search(r"(?m)^main:\s*[\"']?([^\"'\r\n]+)", text)
            name, main = (name.group(1).strip() if name else None), (main.group(1).strip() if main else None)
        elif "velocity-plugin.json" in names:
            try:
                desc = json.loads(z.read("velocity-plugin.json").decode("utf-8", "replace"))
            except ValueError:
                return None, 0
            if not isinstance(desc, dict):
                return None, 0
            name = desc.get("name") or desc.get("id")
            name, main = (name if isinstance(name, str) else None), (desc["main"] if isinstance(desc.get("main"), str) else None)
        else:
            return None, 0
        major = 0
        if main:
            cls = main.replace(".", "/") + ".class"
            if cls in names:
                head = z.read(cls)[:8]
                major = head[6] * 256 + head[7]
        return name, major


def _version_ok(v):
    """Shape of a project-version object (GetProjectVersions) as far as this installer relies on it."""
    return (isinstance(v, dict) and all(isinstance(v.get(k), str) for k in ("id", "version_number", "version_type", "status", "date_published"))
            and all(isinstance(v.get(k), list) for k in ("game_versions", "loaders", "files")))


def _file_ok(f):
    h = f.get("hashes") if isinstance(f, dict) else None
    return (isinstance(f, dict) and isinstance(f.get("url"), str) and f["url"].startswith("https://")
            and isinstance(f.get("filename"), str) and f["filename"].endswith(".jar") and "/" not in f["filename"] and "\\" not in f["filename"]
            and isinstance(h, dict) and isinstance(h.get("sha512"), str) and re.fullmatch(r"[0-9a-fA-F]{128}", h["sha512"]) is not None)


def modrinth_releases(slug, mc, loaders, types=("release",)):
    """Listed versions of the given types for this game version and loaders, newest first (GET /project/{slug}/version)."""
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(loaders), "include_changelog": "false"})
    endpoint = f"{MODRINTH}/project/{urllib.parse.quote(slug)}/version?{q}"
    data = get_json(endpoint)
    out = []
    for v in data if isinstance(data, list) else []:
        if not _version_ok(v) or v["version_type"] not in types or v["status"] != "listed":
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


# the Modrinth loaders whose builds run on each target, in order of preference
TARGET_LOADERS = {"paper": (["paper"], ["spigot", "bukkit"]), "velocity": (["velocity"],)}


def from_modrinth(slug, mc, allow_beta=False, target="paper"):
    """For Paper: Paper builds first; Bukkit/Spigot builds (they run on Paper) only when a plugin publishes no Paper
    build. For Velocity: Velocity builds only (a project that ships one jar per loader, like AuthMe, lists each)."""
    types = ("release", "beta") if allow_beta else ("release",)
    for loaders in TARGET_LOADERS[target]:
        found = modrinth_releases(slug, mc, loaders, types)
        if found:
            return found
    return []


GEYSERMC = "https://download.geysermc.org/v2/projects"


def from_geysermc(project):
    """Latest build from GeyserMC's official download API (Geyser/Floodgate for Paper: the 'spigot' platform)."""
    meta = get_json(f"{GEYSERMC}/{urllib.parse.quote(project)}/versions/latest/builds/latest")
    if not isinstance(meta, dict) or not isinstance(meta.get("version"), str) or not isinstance(meta.get("build"), int):
        raise RuntimeError("unexpected GeyserMC API response")
    dl = (meta.get("downloads") or {}).get("spigot")
    if not isinstance(dl, dict) or not isinstance(dl.get("name"), str) or not re.fullmatch(r"[0-9a-fA-F]{64}", str(dl.get("sha256", ""))):
        raise RuntimeError("GeyserMC API: no verified spigot download")
    name = dl["name"] if dl["name"].endswith(".jar") and "/" not in dl["name"] else f"{project}-spigot.jar"
    url = f"{GEYSERMC}/{project}/versions/{meta['version']}/builds/{meta['build']}/downloads/spigot"
    return [{"source": "geysermc", "version": f"{meta['version']}-b{meta['build']}", "version_id": str(meta["build"]),
             "file": name, "url": url, "size": -1, "hash": ("sha256", dl["sha256"].lower()),
             "endpoint": f"{GEYSERMC}/{project}/versions/latest/builds/latest"}]


def from_lock(p, entry, mc, target="paper"):
    """The one candidate the lock pins for this plugin, or a RuntimeError when upstream no longer serves that exact file
    (or serves it for another loader: a Paper jar is never installed on the proxy, nor the reverse)."""
    algo, want = entry.get("algo", "sha512"), (entry.get("hash") or "").lower()
    if algo not in ("sha512", "sha256") or not re.fullmatch(r"[0-9a-fA-F]{64}|[0-9a-fA-F]{128}", want):
        raise RuntimeError(f"lock entry has no usable {algo} hash; run --update-lock")
    src = entry.get("source")
    if src == "modrinth":
        endpoint = f"{MODRINTH}/version/{urllib.parse.quote(entry['version_id'])}"
        v = get_json(endpoint)
        if not _version_ok(v):
            raise RuntimeError(f"Modrinth no longer serves version {entry['version_id']} ({entry.get('version')})")
        if not set(v["loaders"]) & {l for group in TARGET_LOADERS[target] for l in group}:
            raise RuntimeError(f"Modrinth version {entry['version_id']} is a {'/'.join(v['loaders'])} build, not one for {target}; run --update-lock")
        if mc not in v["game_versions"]:
            print(f"  .. {p['name']} {entry.get('version')} is not listed for Minecraft {mc} any more (locked build kept)")
        files = [f for f in v["files"] if _file_ok(f) and f["hashes"]["sha512"].lower() == want]
        if not files:
            raise RuntimeError(f"Modrinth version {entry['version_id']} no longer has a file with the locked sha512; run --update-lock")
        f = files[0]
        return {"source": "modrinth", "version": v["version_number"], "version_id": v["id"], "published": v["date_published"],
                "file": entry.get("file") or f["filename"], "url": f["url"], "size": f.get("size") if isinstance(f.get("size"), int) else -1,
                "hash": ("sha512", want), "endpoint": endpoint}
    if src in ("geysermc", "hangar"):
        url = entry.get("url")
        if not isinstance(url, str) or not url.startswith("https://"):
            raise RuntimeError("lock entry has no https download url; run --update-lock")
        return {"source": src, "version": entry.get("version", ""), "version_id": entry.get("version_id", ""), "file": entry["file"],
                "url": url, "size": -1, "hash": (algo, want), "endpoint": url}
    raise RuntimeError(f"unknown lock source {src!r}; run --update-lock")


def lock_entry(build):
    """What the repository lock records about an installed build; a build without a published hash is never locked."""
    if not build.get("hash"):
        raise RuntimeError(f"{build.get('source')} publishes no hash for {build.get('version')}; refusing to lock an unverified build")
    algo, h = build["hash"]
    e = {"source": build["source"], "version": build["version"], "version_id": build.get("version_id", ""), "file": build["file"],
         "algo": algo, "hash": h.lower()}
    if build["source"] != "modrinth":
        e["url"] = build["url"]
    return e


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
    ap.add_argument("--lock", help="pin file (default: plugins.lock.json beside the manifest); '' = unpinned, newest releases")
    ap.add_argument("--update-lock", action="store_true", help="resolve the newest releases and (re)write their lock entries")
    ap.add_argument("--target", choices=sorted(TARGET_LOADERS), default="paper",
                    help="which server the plugins are for: the Paper backend (default) or the Velocity proxy")
    args = ap.parse_args()
    manifest = json.load(open(args.manifest, encoding="utf-8"))
    mc = args.mc or manifest["minecraft"]
    lock_file = os.path.join(os.path.dirname(os.path.abspath(args.manifest)), "plugins.lock.json") if args.lock is None else args.lock
    repo_lock = {"minecraft": mc, "plugins": {}}
    if lock_file and os.path.exists(lock_file):
        repo_lock = json.load(open(lock_file, encoding="utf-8"))
        if not isinstance(repo_lock.get("plugins"), dict):
            sys.exit(f"{lock_file}: not a plugin lock (no 'plugins' object)")
    pinned = bool(lock_file) and not args.update_lock and os.path.exists(lock_file)
    if args.update_lock and not lock_file:
        sys.exit("--update-lock needs a lock path (--lock FILE)")
    print(f"plugins: {'pinned by ' + lock_file if pinned else 'resolving the newest releases' + (' and updating ' + lock_file if args.update_lock else ' (UNPINNED: no lock file)')}")
    profiles = {p.strip() for p in args.profiles.split(",") if p.strip()}
    only = {p.strip() for p in args.only.split(",")} if args.only else None
    os.makedirs(args.dest, exist_ok=True)
    lock_path = os.path.join(args.dest, ".suld-plugins.lock.json")
    lock = json.load(open(lock_path)) if os.path.exists(lock_path) else {}
    chosen = [p for p in manifest["plugins"] if p["profile"] in profiles and (only is None or p["id"] in only)
              and p.get("target", "paper") == args.target]
    for p in chosen:
        if p.get("target", "paper") != "paper" and (p.get("geysermc") or p.get("hangar")):
            sys.exit(f"manifest: {p['id']} targets {p['target']}; only Modrinth serves builds for it")
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
    ok, missing, failed, optional_skipped = [], [], [], []
    installed_ids = set()
    managed = set()
    for p in chosen:
        managed.add(p["name"])
        try:
            candidates = []
            entry = repo_lock["plugins"].get(p["id"]) if pinned else None
            if pinned and entry is None:
                raise RuntimeError(f"not in {os.path.basename(lock_file)}; run fetch_plugins.py --update-lock --only {p['id']}")
            if entry is not None:
                candidates = [from_lock(p, entry, mc, args.target)]
            if p.get("modrinth") and not candidates:
                try:
                    candidates += from_modrinth(p["modrinth"], mc, bool(p.get("allow_beta")), args.target)
                except urllib.error.HTTPError as e:
                    if e.code != 404:
                        raise
            if p.get("geysermc") and not candidates:
                candidates += from_geysermc(p["geysermc"])
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
            if chosen_build is None and entry is not None:
                raise RuntimeError(f"the locked build {entry.get('version')} could not be installed (see above); run --update-lock")
            if chosen_build is None:
                if p.get("optional"):
                    optional_skipped.append(p["name"])
                    print(f"  WARNING: optional {p['name']}: no build for Minecraft {mc} from an allowed source; skipped")
                else:
                    missing.append(p["name"])
                    print(f"  -- {p['name']}: no Java 21 build for Minecraft {mc} could be fetched and verified; skipped")
                continue
            if args.update_lock:
                if not chosen_build.get("hash"):
                    raise RuntimeError(f"{chosen_build['source']} publishes no hash for {chosen_build['version']}; refusing to lock an unverified build")
                repo_lock["plugins"][p["id"]] = lock_entry(chosen_build)
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
            installed_ids.add(p["id"])
            if chosen_build.get("endpoint"):
                print(f"     {chosen_build['endpoint']}")
            ok.append(p["name"])
        except Exception as e:  # report and continue with the others
            if p.get("optional"):
                optional_skipped.append(p["name"])
                print(f"  WARNING: optional {p['name']} skipped: {e}")
            else:
                failed.append(f"{p['name']}: {e}")
                print(f"  !! {p['name']}: {e}")
    # a plugin whose required companions are missing is removed again (e.g. Geyser without ViaVersion)
    for p in chosen:
        need = [r for r in p.get("requires", []) if r not in lock or r not in installed_ids]
        if p["id"] in installed_ids and need:
            f = lock.pop(p["id"], {}).get("file")
            if f and os.path.exists(os.path.join(args.dest, f)):
                os.remove(os.path.join(args.dest, f))
            installed_ids.discard(p["id"])
            (optional_skipped if p.get("optional") else missing).append(p["name"])
            if p["name"] in ok:
                ok.remove(p["name"])
            print(f"  WARNING: {p['name']} needs {', '.join(need)}; removed")
    bedrock = [p for p in chosen if p["id"] in ("geyser", "floodgate")]
    if bedrock:
        print("  Bedrock support: " + ", ".join(f"{p['name']} {'installed' if p['id'] in installed_ids else 'SKIPPED'}" for p in bedrock))
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
    if args.update_lock and not failed:
        repo_lock["minecraft"] = mc
        repo_lock["note"] = ("Pinned third-party plugin builds (OPS-002). fetch_plugins.py installs exactly these; "
                             "refresh with: python3 tools/plugins/fetch_plugins.py --dest <dir> --profiles <profiles> --update-lock")
        repo_lock["plugins"] = dict(sorted(repo_lock["plugins"].items()))
        with open(lock_file, "w", encoding="utf-8") as fh:
            json.dump(repo_lock, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        print(f"lock written: {lock_file} ({len(repo_lock['plugins'])} plugins)")
    print(f"plugins ({args.target}): {len(ok)} installed, {len(missing)} without a {mc} build, {len(failed)} failed"
          + (f", optional skipped: {', '.join(optional_skipped)}" if optional_skipped else ""))
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
