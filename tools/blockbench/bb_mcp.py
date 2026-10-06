#!/usr/bin/env python3
"""Minimal, dependency-free MCP stdio driver for the Blockbench headless server.

Sends an initialize handshake then a sequence of tool calls, waiting for each
response before sending the next (the server handles calls concurrently, so
dependent calls — create -> edit -> validate — must be serialized by the client).

Usage:
    python3 tools/blockbench/bb_mcp.py --root <dir> --calls calls.json

`calls.json` is a list of {"name": <tool>, "arguments": {...}} objects. The
server command defaults to the published package via npx; override with
--server-cmd for a local checkout.
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import threading
import time


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", action="append", required=True)
    ap.add_argument("--calls", required=True)
    ap.add_argument("--wait", type=float, default=120.0)
    ap.add_argument("--server-cmd", default=None,
                    help="Override server command (default: npx -y github:jasonjgardner/blockbench-mcp-plugin)")
    args = ap.parse_args()

    if args.server_cmd:
        cmd = args.server_cmd.split()
    else:
        cmd = ["npx", "-y", "github:jasonjgardner/blockbench-mcp-plugin"]
    for root in args.root:
        cmd += ["--root", root]

    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                            stderr=subprocess.DEVNULL, text=True, bufsize=1)
    responses: dict[int, dict] = {}

    def reader() -> None:
        for line in proc.stdout:  # type: ignore[union-attr]
            line = line.strip()
            if not line:
                continue
            try:
                msg = json.loads(line)
            except ValueError:
                continue
            if "id" in msg:
                responses[msg["id"]] = msg

    threading.Thread(target=reader, daemon=True).start()

    def send(obj: dict) -> None:
        proc.stdin.write(json.dumps(obj) + "\n")  # type: ignore[union-attr]
        proc.stdin.flush()  # type: ignore[union-attr]

    def wait_for(rid: int) -> dict:
        deadline = time.time() + args.wait
        while time.time() < deadline:
            if rid in responses:
                return responses[rid]
            time.sleep(0.2)
        return {"error": "timeout"}

    send({"jsonrpc": "2.0", "id": 1, "method": "initialize",
          "params": {"protocolVersion": "2024-11-05", "capabilities": {},
                     "clientInfo": {"name": "bb_mcp.py", "version": "1.0"}}})
    wait_for(1)
    send({"jsonrpc": "2.0", "method": "notifications/initialized"})

    calls = json.load(open(args.calls))
    results = []
    rid = 2
    for call in calls:
        send({"jsonrpc": "2.0", "id": rid, "method": "tools/call", "params": call})
        resp = wait_for(rid)
        result = resp.get("result", {})
        text = " ".join(c.get("text", "") for c in result.get("content", []) if c.get("type") == "text")
        results.append({"call": call["name"], "isError": result.get("isError"), "text": text})
        rid += 1

    try:
        proc.stdin.close()  # type: ignore[union-attr]
        proc.terminate()
    except Exception:
        pass

    print(json.dumps(results, indent=2, ensure_ascii=False))
    return 0 if all(r["isError"] in (None, False) for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
