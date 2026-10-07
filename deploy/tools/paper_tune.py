#!/usr/bin/env python3
"""Set nested scalar keys in Paper/Spigot/Bukkit YAML files that Paper generated itself.

    paper_tune.py FILE a.b.c=value [a.b.d=value ...]
    paper_tune.py --profile deploy/paper-tuning.conf SERVER_DIR

Only keys that already exist are changed (a typo, or a key the installed Paper version does not have, fails with
exit 1 instead of silently writing a setting Paper ignores). Comments, order and every other line are preserved.
Values are written verbatim (numbers, booleans, `default`). The profile format is one `file: a.b.c=value` per line;
`#` starts a comment. Dependency-free (no PyYAML on the server).
"""
import os
import re
import sys

LINE = re.compile(r"^(?P<indent> *)(?P<key>[^#:\s][^:]*?):(?P<rest>.*)$")


def set_keys(path, edits):
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    missing = []
    for dotted, value in edits:
        parts = dotted.split(".")
        depth, start, end, found = 0, 0, len(lines), None
        indent = -1
        for part in parts:
            hit = None
            for i in range(start, end):
                m = LINE.match(lines[i])
                if not m:
                    continue
                ind = len(m.group("indent"))
                if ind <= indent:
                    break  # left the parent block
                if m.group("key").strip().strip("'\"") == part and (hit is None) and (indent < 0 or ind > indent):
                    # first key at the parent's direct child level
                    hit = (i, ind)
                    break
            if hit is None:
                found = None
                break
            i, ind = hit
            indent = ind
            start = i + 1
            # the block of this key ends at the next line indented <= ind
            end = len(lines)
            for j in range(start, len(lines)):
                m2 = LINE.match(lines[j])
                if m2 and len(m2.group("indent")) <= ind:
                    end = j
                    break
            found = i
            depth += 1
        if found is None:
            missing.append(dotted)
            continue
        m = LINE.match(lines[found])
        rest = m.group("rest").strip()
        if rest == "" and found + 1 < len(lines) and LINE.match(lines[found + 1]) and \
                len(LINE.match(lines[found + 1]).group("indent")) > len(m.group("indent")):
            missing.append(dotted + " (is a section, not a value)")
            continue
        lines[found] = m.group("indent") + m.group("key") + ": " + value
    if missing:
        for k in missing:
            print(f"paper_tune: {path}: key not found: {k}", file=sys.stderr)
        return False
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    os.replace(tmp, path)
    return True


def parse(edit):
    key, sep, value = edit.partition("=")
    if not sep or not key.strip():
        raise SystemExit(f"paper_tune: bad edit {edit!r} (want a.b.c=value)")
    return key.strip(), value.strip()


def main(argv):
    if len(argv) >= 3 and argv[1] == "--profile":
        profile, server = argv[2], argv[3] if len(argv) > 3 else "."
        per_file = {}
        with open(profile, encoding="utf-8") as f:
            for raw in f:
                line = raw.split("#", 1)[0].strip()
                if not line:
                    continue
                fname, _, edit = line.partition(":")
                per_file.setdefault(fname.strip(), []).append(parse(edit.strip()))
        ok = True
        for fname, edits in per_file.items():
            path = os.path.join(server, fname)
            if fname == "server.properties":
                ok &= set_properties(path, edits)
            else:
                ok &= set_keys(path, edits)
        return 0 if ok else 1
    if len(argv) < 3:
        print(__doc__)
        return 2
    return 0 if set_keys(argv[1], [parse(e) for e in argv[2:]]) else 1


def set_properties(path, edits):
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    missing = []
    for key, value in edits:
        for i, l in enumerate(lines):
            if l.split("=", 1)[0].strip() == key and not l.lstrip().startswith("#"):
                lines[i] = f"{key}={value}"
                break
        else:
            missing.append(key)
    if missing:
        for k in missing:
            print(f"paper_tune: {path}: key not found: {k}", file=sys.stderr)
        return False
    with open(path + ".tmp", "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    os.replace(path + ".tmp", path)
    return True


if __name__ == "__main__":
    sys.exit(main(sys.argv))
