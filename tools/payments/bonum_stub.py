#!/usr/bin/env python3
"""A local stand-in for Bonum's merchant API, for the dev server only (docs/PAYMENT_ARCHITECTURE.md §Testing).

It is NOT Bonum and proves nothing about Bonum: it speaks the same paths and fields SÜLD's client uses
(suld-api mn.suld.api.payment.bonum.BonumApi, taken from the open-source Bonum SDK) so the whole path in the game can
be tried end to end: /buy -> intent -> QR on a map -> a signed webhook -> credits once.

  BONUM_TERMINAL_ID=dev-terminal BONUM_APP_SECRET=dev-secret BONUM_CHECKSUM_KEY=dev-checksum \\
      python3 tools/payments/bonum_stub.py --port 18086 --webhook http://127.0.0.1:8085/api/bonum/callback

The plugin then runs with payment.bonum.base-url: http://127.0.0.1:18086 and the same three variables. The values are
dev placeholders, never a merchant's. Test-only endpoints (never part of Bonum):
  GET  /stub/last                     the newest invoice {invoiceId, transactionId, amount}
  POST /stub/pay  {invoiceId, status?, amount?, tamper?, unsigned?, repeat?}
       builds the webhook a payment would send, signs it with the checksum key (x-checksum-v2, HMAC-SHA256 hex) unless
       told not to, posts it to the plugin, and answers {codes:[...], bodies:[...]}.
"""
import argparse
import hashlib
import hmac
import json
import os
import secrets
import sys
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TERMINAL = os.environ.get("BONUM_TERMINAL_ID", "")
APP_SECRET = os.environ.get("BONUM_APP_SECRET", "")
CHECKSUM = os.environ.get("BONUM_CHECKSUM_KEY", "").encode()

lock = threading.Lock()
tokens = {}       # access token -> expiry (epoch s)
refresh = {}      # refresh token -> expiry
invoices = {}     # invoiceId -> {transactionId, amount, created}
order = []
calls = {"token": 0, "refresh": 0, "qr": 0, "invoice": 0}


def new_token():
    a, r = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
    now = time.time()
    tokens[a] = now + 600
    refresh[r] = now + 3600
    return {"tokenType": "Bearer", "accessToken": a, "expiresIn": 600, "refreshToken": r, "refreshExpiresIn": 3600, "unit": "SECONDS"}


class Handler(BaseHTTPRequestHandler):
    server_version = "bonum-stub"

    def log_message(self, fmt, *args):  # quiet: the bot prints what matters
        pass

    def send(self, code, obj):
        body = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def bearer(self):
        h = self.headers.get("Authorization", "")
        if not h.startswith("Bearer "):
            return False
        with lock:
            exp = tokens.get(h[7:])
        return exp is not None and exp > time.time()

    def body(self):
        n = int(self.headers.get("Content-Length", "0") or 0)
        return json.loads(self.rfile.read(n) or b"{}")

    def do_GET(self):
        if self.path == "/bonum-gateway/ecommerce/auth/create":
            if self.headers.get("Authorization") != "AppSecret " + APP_SECRET or self.headers.get("X-TERMINAL-ID") != TERMINAL:
                return self.send(401, {"error": "unauthorized"})
            with lock:
                calls["token"] += 1
                return self.send(200, new_token())
        if self.path == "/bonum-gateway/ecommerce/auth/refresh":
            h = self.headers.get("Authorization", "")
            with lock:
                ok = h.startswith("Bearer ") and refresh.pop(h[7:], 0) > time.time()
                if not ok:
                    return self.send(401, {"error": "bad refresh token"})
                calls["refresh"] += 1
                return self.send(200, new_token())
        if self.path == "/stub/last":
            with lock:
                if not order:
                    return self.send(404, {})
                inv = order[-1]
                return self.send(200, dict(invoiceId=inv, **invoices[inv]))
        if self.path == "/stub/calls":
            with lock:
                return self.send(200, dict(calls))
        self.send(404, {})

    def do_POST(self):
        if self.path in ("/mpay-service/merchant/transaction/qr/create", "/bonum-gateway/ecommerce/invoices"):
            if not self.bearer():
                return self.send(401, {"error": "token"})
            req = self.body()
            if not isinstance(req.get("amount"), int) or req["amount"] <= 0 or not req.get("transactionId"):
                return self.send(400, {"error": "amount and transactionId"})
            inv = "STUB-" + secrets.token_hex(6).upper()
            with lock:
                invoices[inv] = {"transactionId": req["transactionId"], "amount": req["amount"], "created": time.time()}
                order.append(inv)
                if self.path.endswith("create"):
                    calls["qr"] += 1
                    return self.send(200, {"invoiceId": inv, "qrCode": "SULD-STUB|" + inv + "|" + str(req["amount"]) + "|" + req["transactionId"],
                                           "qrImage": "", "links": [{"name": "Stub bank", "description": "dev stand-in",
                                                                     "link": "https://127.0.0.1/stub/" + inv}, {"name": "bad", "link": "javascript:alert(1)"}]})
                calls["invoice"] += 1
                return self.send(200, {"invoiceId": inv, "followUpLink": "https://127.0.0.1/stub/pay/" + inv})
        if self.path == "/stub/pay":
            req = self.body()
            with lock:
                inv = invoices.get(req.get("invoiceId"))
            if inv is None:
                return self.send(404, {"error": "no such invoice"})
            status = req.get("status", "PAID")
            event = {"type": "PAYMENT", "status": "SUCCESS" if status == "PAID" else "FAILED", "message": "stub",
                     "body": {"transactionId": inv["transactionId"], "invoiceId": req["invoiceId"],
                              "amount": req.get("amount", inv["amount"]), "currency": req.get("currency", "MNT"),
                              "completedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "status": status,
                              "invoiceStatus": status}}
            raw = json.dumps(event, indent=2).encode()  # sent with whitespace: the plugin must sign-check the raw bytes
            sig = hmac.new(CHECKSUM, raw, hashlib.sha256).hexdigest()
            if req.get("tamper"):
                raw = raw.replace(str(inv["amount"]).encode(), str(inv["amount"] * 10).encode(), 1)
            codes, bodies = [], []
            for _ in range(max(1, int(req.get("repeat", 1)))):
                r = urllib.request.Request(self.server.webhook, data=raw, method="POST", headers={"Content-Type": "application/json"})
                if not req.get("unsigned"):
                    r.add_header("x-checksum-v2", sig)
                try:
                    with urllib.request.urlopen(r, timeout=15) as res:
                        codes.append(res.status)
                        bodies.append(res.read().decode())
                except urllib.error.HTTPError as e:
                    codes.append(e.code)
                    bodies.append(e.read().decode())
            return self.send(200, {"codes": codes, "bodies": bodies})
        self.send(404, {})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=18086)
    ap.add_argument("--webhook", default="http://127.0.0.1:8085/api/bonum/callback")
    a = ap.parse_args()
    if not (TERMINAL and APP_SECRET and CHECKSUM):
        sys.exit("set BONUM_TERMINAL_ID, BONUM_APP_SECRET and BONUM_CHECKSUM_KEY (dev values)")
    srv = ThreadingHTTPServer(("127.0.0.1", a.port), Handler)
    srv.webhook = a.webhook
    print(f"bonum stub on 127.0.0.1:{a.port}, webhook {a.webhook}", flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
