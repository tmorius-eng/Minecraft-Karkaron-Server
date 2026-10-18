#!/usr/bin/env python3
"""Run every SÜLD asset/pipeline validation. Exit non-zero if any fails.
Intended for local use and CI (see .github/workflows)."""
from __future__ import annotations

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CHECKS = ["validate_registry.py", "validate_resourcepack.py", "validate_pack_budget.py", "validate_world_plan.py",
          "validate_glyph_codepoints.py", "check_minimap_shader.py"]


# A check that could not run here (check_minimap_shader.py without moderngl or OpenGL) exits with this: it is reported
# as skipped, never as passed, and fails the run when SULD_REQUIRE_GPU=1 (CI installs Mesa and runs it under Xvfb).
SKIPPED = 75


def main() -> int:
    failed, skipped = [], []
    require = os.environ.get("SULD_REQUIRE_GPU") == "1"
    for check in CHECKS:
        print(f"\n=== {check} ===")
        rc = subprocess.call([sys.executable, os.path.join(HERE, check)])
        if rc == SKIPPED and not require:
            skipped.append(check)
        elif rc != 0:
            failed.append(check + (" (skipped, but SULD_REQUIRE_GPU=1)" if rc == SKIPPED else ""))
    print()
    if skipped:
        print(f"SKIPPED (not run here, not passed): {', '.join(skipped)}")
    if failed:
        print(f"{len(failed)} VALIDATION(S) FAILED: {', '.join(failed)}")
        return 1
    ran = len(CHECKS) - len(skipped)
    print(f"ALL {ran} VALIDATIONS THAT RAN PASSED" + (f" ({len(skipped)} skipped)" if skipped else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
