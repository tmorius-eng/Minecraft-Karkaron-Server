#!/usr/bin/env python3
"""Export every rig of tools/model/fauna.py (and update suld-plugin/src/main/resources/models/index.json).

    python3 tools/model/export_fauna.py [--check]
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import fauna  # noqa: E402


def main():
    check = "--check" in sys.argv
    for rid in fauna.RIGS:
        r = subprocess.run([sys.executable, os.path.join(HERE, "export_rig.py"), rid] + (["--check"] if check else []),
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stdout, r.stderr)
            raise SystemExit(f"{rid}: export failed")
        print(rid, "ok")
    if check:
        return
    idx = os.path.join(ROOT, "suld-plugin", "src", "main", "resources", "models", "index.json")
    models = ["khasar"] + [r for r in fauna.RIGS]
    with open(idx, "w") as fh:
        json.dump({"models": models, "mobs": fauna.MOBS}, fh, indent=1, ensure_ascii=False)
        fh.write("\n")
    print("index:", len(models), "models,", len(fauna.MOBS), "mobs")


if __name__ == "__main__":
    main()
