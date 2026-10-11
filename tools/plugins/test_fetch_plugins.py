#!/usr/bin/env python3
"""Unit test of the plugin lock (OPS-002) in fetch_plugins.py, with the network stubbed out.

    python3 -I tools/plugins/test_fetch_plugins.py

Rules under test: with a lock present, the locked build is installed even when upstream has a newer release; a lock
entry whose hash upstream no longer serves fails instead of falling back; a plugin without a lock entry fails (an
optional one is skipped with a warning); --update-lock refuses a build without a published hash and writes entries.
"""
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import fetch_plugins as fp  # noqa: E402

MC = "1.21.11"
SHA_OLD = "a" * 128
SHA_NEW = "b" * 128


def fake_jar(path, name="Demo"):
    """A minimal plugin jar whose main class targets Java 21 (class version 65)."""
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("plugin.yml", f"name: {name}\nmain: demo.Main\nversion: 1\n")
        z.writestr("demo/Main.class", bytes([0xCA, 0xFE, 0xBA, 0xBE, 0, 0, 0, 65]) + b"\0" * 8)


def version(vid, number, sha, filename, published):
    return {"id": vid, "version_number": number, "version_type": "release", "status": "listed", "date_published": published,
            "game_versions": [MC], "loaders": ["paper"],
            "files": [{"url": f"https://cdn.example/{filename}", "filename": filename, "primary": True, "hashes": {"sha512": sha}}]}


class FakeNet:
    """Modrinth as the installer sees it: a project with an old (locked) and a new release; downloads produce jars."""

    def __init__(self):
        self.old = version("OLDID", "1.0.0", SHA_OLD, "demo-1.0.0.jar", "2026-01-01T00:00:00Z")
        self.new = version("NEWID", "2.0.0", SHA_NEW, "demo-2.0.0.jar", "2026-09-01T00:00:00Z")
        self.served_sha = {SHA_OLD: SHA_OLD, SHA_NEW: SHA_NEW}  # what the download really hashes to
        self.downloaded = []

    def get_json(self, url):
        if "/project/demo/version" in url:
            return [self.new, self.old]
        if url.endswith("/version/OLDID"):
            return self.old
        if url.endswith("/version/NEWID"):
            return self.new
        raise AssertionError("unexpected URL " + url)

    def download(self, url, dest):
        self.downloaded.append(url)
        tmp = dest + ".part"
        fake_jar(tmp)
        return tmp

    def file_hash(self, path, algo):
        # the jar content is synthetic; report the hash the test wants this download to have
        name = os.path.basename(path).replace(".part", "")
        return self.served_sha.get(SHA_OLD if "1.0.0" in name else SHA_NEW, "0" * 128)


class LockTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="suld-lock-test-")
        self.dest = os.path.join(self.tmp, "plugins")
        self.manifest = os.path.join(self.tmp, "plugins.json")
        self.lock = os.path.join(self.tmp, "plugins.lock.json")
        with open(self.manifest, "w", encoding="utf-8") as fh:
            json.dump({"minecraft": MC, "plugins": [{"id": "demo", "name": "Demo", "profile": "core", "modrinth": "demo"},
                                                     {"id": "extra", "name": "Extra", "profile": "core", "modrinth": "extra", "optional": True}]}, fh)
        self.net = FakeNet()

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def run_main(self, *argv):
        out = io.StringIO()
        with mock.patch.object(fp, "get_json", self.net.get_json), mock.patch.object(fp, "download", self.net.download), \
                mock.patch.object(fp, "file_hash", self.net.file_hash), mock.patch.object(sys, "argv", ["fetch_plugins.py", *argv]), \
                mock.patch.object(sys, "stdout", out):
            try:
                fp.main()
                code = 0
            except SystemExit as e:
                code = e.code if isinstance(e.code, int) else 1
        return code, out.getvalue()

    def write_lock(self, sha=SHA_OLD, vid="OLDID"):
        with open(self.lock, "w", encoding="utf-8") as fh:
            json.dump({"minecraft": MC, "plugins": {"demo": {"source": "modrinth", "version": "1.0.0", "version_id": vid,
                                                            "file": "demo-1.0.0.jar", "algo": "sha512", "hash": sha}}}, fh)

    def test_lock_wins_over_a_newer_release(self):
        self.write_lock()
        code, out = self.run_main("--dest", self.dest, "--manifest", self.manifest, "--only", "demo", "--lock", self.lock)
        self.assertEqual(0, code, out)
        self.assertIn("pinned by", out)
        self.assertIn("ok Demo 1.0.0 [OLDID]", out)
        self.assertNotIn("2.0.0", out)
        self.assertTrue(os.path.exists(os.path.join(self.dest, "demo-1.0.0.jar")))
        self.assertEqual(["https://cdn.example/demo-1.0.0.jar"], self.net.downloaded)

    def test_hash_drift_fails_instead_of_falling_back(self):
        self.write_lock(sha="c" * 128)
        code, out = self.run_main("--dest", self.dest, "--manifest", self.manifest, "--only", "demo", "--lock", self.lock)
        self.assertEqual(1, code)
        self.assertIn("no longer has a file with the locked sha512", out)
        self.assertEqual([], self.net.downloaded, "nothing is downloaded for a lock that does not match")

    def test_missing_entry_fails_required_and_skips_optional(self):
        with open(self.lock, "w", encoding="utf-8") as fh:
            json.dump({"minecraft": MC, "plugins": {}}, fh)
        code, out = self.run_main("--dest", self.dest, "--manifest", self.manifest, "--lock", self.lock)
        self.assertEqual(1, code)
        self.assertIn("!! Demo: not in plugins.lock.json", out)
        self.assertIn("WARNING: optional Extra skipped: not in plugins.lock.json", out)

    def test_update_lock_writes_the_newest_verified_build(self):
        self.write_lock()
        code, out = self.run_main("--dest", self.dest, "--manifest", self.manifest, "--only", "demo", "--lock", self.lock, "--update-lock")
        self.assertEqual(0, code, out)
        self.assertIn("ok Demo 2.0.0 [NEWID]", out)
        lock = json.load(open(self.lock, encoding="utf-8"))
        self.assertEqual({"source": "modrinth", "version": "2.0.0", "version_id": "NEWID", "file": "demo-2.0.0.jar", "algo": "sha512", "hash": SHA_NEW},
                         lock["plugins"]["demo"])
        self.assertEqual(MC, lock["minecraft"])
        # and the written lock installs again, byte-identically pinned
        self.net.downloaded.clear()
        code, out = self.run_main("--dest", os.path.join(self.tmp, "second"), "--manifest", self.manifest, "--only", "demo", "--lock", self.lock)
        self.assertEqual(0, code, out)
        self.assertIn("ok Demo 2.0.0 [NEWID]", out)

    def test_update_lock_refuses_a_build_without_a_hash(self):
        build = {"source": "hangar", "version": "1.0", "file": "x.jar", "url": "https://h/x.jar", "hash": None}
        with self.assertRaises(RuntimeError):
            fp.lock_entry(build)  # a hashless build never becomes a lock entry
        with self.assertRaises(RuntimeError):
            fp.from_lock({"name": "X"}, {"source": "hangar", "file": "x.jar", "url": "https://h/x.jar", "algo": "sha256", "hash": ""}, MC)

    def test_repository_lock_is_consistent_with_the_manifest(self):
        root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        manifest = json.load(open(os.path.join(root, "deploy", "plugins", "plugins.json"), encoding="utf-8"))
        lock = json.load(open(os.path.join(root, "deploy", "plugins", "plugins.lock.json"), encoding="utf-8"))
        self.assertEqual(manifest["minecraft"], lock["minecraft"])
        ids = {p["id"] for p in manifest["plugins"]}
        for pid, e in lock["plugins"].items():
            self.assertIn(pid, ids, f"lock entry {pid} is not in the manifest")
            self.assertIn(e["source"], ("modrinth", "geysermc", "hangar"))
            self.assertRegex(e["hash"], r"^[0-9a-f]{64}$|^[0-9a-f]{128}$")
            self.assertTrue(e["file"].endswith(".jar") and "/" not in e["file"], e["file"])
        unlocked = [p["id"] for p in manifest["plugins"] if p["id"] not in lock["plugins"] and not p.get("optional")]
        self.assertEqual([], unlocked, "every required plugin must be pinned")


if __name__ == "__main__":
    unittest.main(verbosity=1)
