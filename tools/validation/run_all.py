#!/usr/bin/env python3
"""Run every SÜLD asset/pipeline validation. Exit non-zero if any fails.
Intended for local use and CI (see .github/workflows)."""
from __future__ import annotations

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CHECKS = ["validate_registry.py", "validate_resourcepack.py", "validate_pack_budget.py", "validate_world_plan.py"]


def main() -> int:
    failed = 0
    for check in CHECKS:
        print(f"\n=== {check} ===")
        rc = subprocess.call([sys.executable, os.path.join(HERE, check)])
        if rc != 0:
            failed += 1
    print(f"\n{'ALL VALIDATIONS PASSED' if not failed else f'{failed} VALIDATION(S) FAILED'}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
