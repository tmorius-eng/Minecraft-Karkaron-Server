#!/usr/bin/env python3
"""Minecraft Java 'Server List Ping': proves the server accepts connections and answers.

    mc_ping.py HOST PORT [TIMEOUT]   ->  prints "<version> <online>/<max>", exit 0; else exit 1.
"""
import json
import socket
import struct
import sys


def varint(n: int) -> bytes:
    out = b""
    while True:
        b = n & 0x7F
        n >>= 7
        out += struct.pack("B", b | (0x80 if n else 0))
        if not n:
            return out


def read_varint(sock) -> int:
    num = 0
    for shift in range(0, 35, 7):
        byte = sock.recv(1)
        if not byte:
            raise ConnectionError("closed")
        num |= (byte[0] & 0x7F) << shift
        if not byte[0] & 0x80:
            return num
    raise ValueError("varint too long")


def main(argv):
    host, port = argv[1], int(argv[2])
    timeout = float(argv[3]) if len(argv) > 3 else 5.0
    try:
        with socket.create_connection((host, port), timeout=timeout) as s:
            h = host.encode()
            handshake = varint(0) + varint(767) + varint(len(h)) + h + struct.pack(">H", port) + varint(1)
            s.sendall(varint(len(handshake)) + handshake)
            s.sendall(varint(1) + varint(0))
            read_varint(s)  # packet length
            read_varint(s)  # packet id
            length = read_varint(s)
            data = b""
            while len(data) < length:
                chunk = s.recv(length - len(data))
                if not chunk:
                    raise ConnectionError("short read")
                data += chunk
        status = json.loads(data)
        players = status.get("players", {})
        print(f'{status.get("version", {}).get("name", "?")} {players.get("online", "?")}/{players.get("max", "?")}')
        return 0
    except Exception as exc:  # noqa: BLE001 - any failure means "not healthy"
        print(f"ping failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
