#!/usr/bin/env python3
"""Minimal, dependency-free editor for two-level scalar keys in the SULD config.yml.

    yaml_set.py FILE section.key=value [section.key=r:rawvalue ...]

Values are written as single-quoted strings unless prefixed with ``r:`` (raw: numbers,
booleans) or ``env:NAME`` (string taken from environment variable NAME, for secrets). Comments and every other line are preserved. Fails (exit 1) if a key is missing,
so a typo can never silently leave production on the wrong setting.
"""
import re
import sys


def fmt(value: str) -> str:
    if value.startswith("env:"):  # read from the environment so secrets never appear in argv/ps
        import os
        value = os.environ[value[4:]]
    elif value.startswith("r:"):
        return value[2:]
    return "'" + value.replace("'", "''") + "'"


def main(argv):
    if len(argv) < 3:
        print(__doc__)
        return 2
    path, edits = argv[1], argv[2:]
    wanted = {}
    for edit in edits:
        key, sep, value = edit.partition("=")
        if not sep or "." not in key:
            print(f"bad edit '{edit}' (expected section.key=value)", file=sys.stderr)
            return 2
        section, name = key.split(".", 1)
        wanted[(section, name)] = fmt(value)

    with open(path, encoding="utf-8") as fh:
        lines = fh.read().split("\n")

    section = None
    done = set()
    for i, line in enumerate(lines):
        top = re.match(r"^([A-Za-z0-9_-]+):\s*(#.*)?$", line)
        if top:
            section = top.group(1)
            continue
        if line and not line.startswith((" ", "\t", "#")):
            section = None  # a different top-level construct
            continue
        m = re.match(r"^(\s+)([A-Za-z0-9_-]+):(\s*)(.*)$", line)
        if m and section is not None and (section, m.group(2)) in wanted:
            lines[i] = f"{m.group(1)}{m.group(2)}: {wanted[(section, m.group(2))]}"
            done.add((section, m.group(2)))

    missing = set(wanted) - done
    if missing:
        for section, name in sorted(missing):
            print(f"key not found in {path}: {section}.{name}", file=sys.stderr)
        return 1

    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
