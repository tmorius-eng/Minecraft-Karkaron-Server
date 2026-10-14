# Payment architecture: the Bonum credit store

**Status: IMPLEMENTED, OFF BY DEFAULT, NOT VERIFIED AGAINST THE LIVE BONUM API.**

* The provider is **Bonum** (the owner's decision; QPay is not used).
* The gateway sells **Сүлд Кредит** only. Credits buy cosmetics, pets and titles: never power (zero pay-to-win).
* The SÜLD side is built and tested end to end against a **local stand-in** of Bonum (`tools/payments/bonum_stub.py`).
  * That proves SÜLD's half of the path only.
  * Bonum's real paths, field names and webhook header come from the open-source Bonum SDK. The official merchant
    documentation could not be reached from the build environment.
  * Before the gateway is switched on, every name in `mn.suld.api.payment.bonum.BonumApi` must be checked against the
    merchant's sandbox (`https://testapi.bonum.mn`) and the merchant portal ([§Before production](#before-production)).
* The paid revive of `docs/INSTANT_REVIVE.md` is **not** sold. That design stays design-only.

## Absolute rules

* **No bank-account monitoring and no bank-transfer detection.**
  * Nothing reads bank statements or SMS, and nothing guesses payments from transfers.
  * A payment exists only when the provider's **signed** callback says so.
* **No hard-coded account numbers, keys or secrets** in code, config or git.
  * The merchant's terminal id, app secret and checksum key come from the environment (`deploy/suld.env`).
  * The config holds only the names of the three variables.
  * Neither the values nor the tokens are ever logged.
* **No fake confirmations.** No command or menu grants a purchase. Staff can see, and reconcile, but never mark paid.
* **The client is never trusted.** The game never accepts "I paid" from a player.

## The flow

```
/buy ─ a package ─▶ PaymentService.buy        (main thread: rate limit, one purchase at a time)
                     │ io:   suld_payment_intents  PENDING, 15 min
                     │ http: Bonum token (cached) ─▶ POST QR {amount, transactionId, expiresIn}
                     ▼
                    the QR on a map in hand + the bank apps' links in chat
                                      ⋮  the player pays in their bank app
Bonum ─ POST callback (x-checksum-v2) ─▶ BonumWebhookServer   (HTTP threads, behind the host's TLS proxy)
                     │ signature over the raw body, else 401 (nothing read)
                     │ PaymentLedger: intent CAS ─▶ CONFIRMED ─▶ credits granted once (txid) ─▶ fulfilled
                     ▼
                    main thread: chat line + toast sound, the map taken, audit payment.confirmed
every minute ─▶ PaymentLedger.reconcile: owed intents granted, old PENDING ones expired
```

### Components

| Part | Where | Does |
|---|---|---|
| `PaymentIntent` | api `payment` | one purchase; state rules; `judge` of a provider event |
| `PaymentLedger` | api `payment` | event → CAS confirm → grant once → fulfilled; `reconcile` |
| `StorePackage` | api `payment` | `store.packages` → credits and the amount charged |
| `BonumApi`, `BonumToken`, `BonumInvoice`, `BonumQr`, `BonumInvoiceReply`, `BonumWebhook`, `BonumPaymentResult`, `BonumSignature` | api `payment.bonum` | Bonum's paths, messages and signature, pure |
| `QrCode`, `QrRaster` | api `payment.qr` | the QR code drawn by SÜLD itself (ISO/IEC 18004, byte mode, v1–40) at a whole scale on a 128 px map |
| `PaymentRepository` (+ in-memory) | api `persistence` | the ledger's storage contract |
| `JdbcPaymentRepository` | plugin `persistence` | V29 tables, `UPDATE … WHERE version = ?` |
| `BonumClient` | plugin `payment.bonum` | `java.net.http`, async only; token single-flight, renewed 30 s early |
| `BonumWebhookServer` | plugin `payment.bonum` | JDK `HttpServer`, POST only, 64 KB, rate limit, signature, event log |
| `QrMaps` | plugin `payment` | the QR map item: new locked map per intent, never reused; cannot be dropped, framed, crafted or stored |
| `PaymentService` | plugin `payment` | `/buy` → intent → QR; webhook → player; reconcile; staff lines |

* There is one payment service, no second payment manager.
* Fulfilment uses the existing credit ledger: `StyleService.grantPurchase` → `suld_credit_grants`
  (`JdbcStyleRepository.grantCredits`), keyed by the intent's own transaction id `bonum:<intent id>`.
  * The ledger row and the balance change commit together.
  * A second grant of the same intent inserts nothing and changes nothing.

### What the player sees

1. `/buy` shows the packages.
   * With the gateway on, a click starts a purchase.
   * With it off, the store link shows as before («Тун удахгүй» when there is none).
2. The QR map goes into the hand: «Bonum QR · 100 кредит», with the price and «… хүртэл» (the intent's end, server zone).
3. Chat shows «Төлбөр: 100 Сүлд Кредит · ₮5,000», «Банкны апп: [Хаан банк] · …» (each link opens the bank app) and «Кредит зөвхөн гоёлд зарцуулагдана».
4. When the payment is confirmed, chat shows «✔ Төлбөр баталгаажлаа: +100 Сүлд Кредит. Баярлалаа!» with `UI_TOAST_CHALLENGE_COMPLETE`, and the map is taken.
5. A failed or expired payment says so and takes the map. At the intent's end, the map is taken anyway.
6. A player can have at most `open-intents-per-player` (2) open intents, and start one purchase every 20 s.

## Data (V29)

`suld_payment_intents`:

| Column | Notes |
|---|---|
| `intent_id` UUID PK | sent to Bonum as `transactionId`, its 32 hex digits (no dashes) |
| `player_uuid` | never sent to Bonum |
| `product`, `credits` | `CREDITS_<n>` |
| `amount`, `currency` | whole tögrög, `MNT` |
| `provider` | `bonum` |
| `state` | `PENDING` → `CONFIRMED` / `FAILED` / `EXPIRED`; a payment that arrives for a FAILED or EXPIRED intent still confirms it (the money was taken); `REFUNDED` is reserved for staff |
| `created_at`, `expires_at` | `invoice-expiry-minutes` (15) |
| `invoice_id` | Bonum's invoice |
| `provider_txn_id` | **unique** per provider, set on confirmation (Bonum's invoice id) |
| `confirmed_at`, `fulfilled_at` | an intent confirmed and not fulfilled is *owed* |
| `version` | the compare-and-set |

`suld_payment_events` is append-only, one row per call received:

* when, the provider, the **SHA-256 of the raw body** (never the body), and whether the signature held;
* the reference and invoice named, the status words, and the outcome.

The outcome is one of `CONFIRMED`, `DUPLICATE`, `MISMATCH`, `FAILED`, `EXPIRED`, `UNKNOWN`, `IGNORED`, `OWED`,
`BAD_SIGNATURE`, `BAD_BODY` or `ERROR`. The log is for audits and replays.

## Security model

| Threat | Defence |
|---|---|
| Fake confirmation | HMAC-SHA256 over the **raw body** with the merchant checksum key, in `x-checksum-v2` (the SDK's name; the header is configurable); a missing, malformed or wrong signature is 401 and the body is not read |
| Replay | the intent moves PENDING → CONFIRMED once (CAS); a replay finds it CONFIRMED and is a no-op (`DUPLICATE`), and the credit grant is keyed by the intent |
| Duplicate / concurrent webhooks | compare-and-set on `version`: of 16 at once exactly one grants (`JdbcPaymentIT`, PostgreSQL and H2) |
| Wrong player | the intent binds the player; the provider only ever sees the intent id |
| Amount / currency tampering | exact match with the intent, else `MISMATCH`: nothing granted, audit `payment.mismatch`, staff alerted |
| Restart during confirmation | the confirmation is stored before the grant; reconcile (start + every minute) grants owed intents, and the grant is idempotent by txid |
| Flood | 64 KB body limit, a token bucket per sender (30, then 2/s), optional `allowed-ips`, a bounded thread pool |
| Script links | bank links are kept only for safe schemes (no `javascript:`, `data:`, `file:`), and never with control characters |
| Leaked secret | rotate in the environment, no code change; nothing secret is in the config or the logs |
| Credits for power | none exist: credits buy cosmetics only |

## Configuration

```yaml
payment:
  bonum:
    enabled: false
    base-url: "https://testapi.bonum.mn"     # production: https://apis.bonum.mn
    terminal-id-env: BONUM_TERMINAL_ID       # the names of the variables, never their values
    app-secret-env: BONUM_APP_SECRET
    checksum-key-env: BONUM_CHECKSUM_KEY
    callback-url: "https://pay.suld.mn/api/bonum/callback"
    language: mn
    invoice-expiry-minutes: 15
    open-intents-per-player: 2
    webhook: {bind: 127.0.0.1, port: 8085, path: /api/bonum/callback, signature-header: x-checksum-v2,
              trusted-proxies: ["127.0.0.1"], allowed-ips: []}
store:
  packages:
    - {credits: 100, price: '₮5,000'}        # amount: 5000 may be set; without it the price's digits are charged
```

* The gateway stays off unless all three variables are set and `base-url` is https.
  * Plain http is allowed only to this machine, which is for the stub.
* The webhook listens on loopback. The host's TLS proxy forwards `callback-url` to it
  (`docs/DEPLOYMENT.md` §Payments).

### Where the directive's names differ

* **Paths.** The directive says `POST /api/v1/invoice` and `invoice_id` / `qr_data` / `qr_image_base64`. The SDK's paths
  and fields are used instead: `/mpay-service/merchant/transaction/qr/create`, `invoiceId`, `qrCode`, `qrImage`, `links`.
* **Signature header.** The directive says `X-Bonum-Signature`. The SDK uses `x-checksum-v2`. The header name is a
  config key.
* **Secrets.** The directive puts `app-id` / `secret-key` in `config.yml`. The rule «no secrets in config» wins: the
  config holds only the variable names.
* **Invoice metadata.** The directive's `BonumInvoice` carries the player's UUID. Here the player is never sent, only
  the intent id. The ledger maps it back.
* **Idempotency.** The directive asks for it "via `invoice_id` in `suld_credit_grants`". Here it goes by the intent
  (`bonum:<intent id>`): one intent, one grant, even if a second invoice were ever made for it.

## Staff

* `/suld payment <player>`: whether the gateway is on (and why not), and the player's last 8 intents with their state.
  Owed and fulfilled intents are marked.
* `/suld payment reconcile`: runs the reconcile at once.
* Neither can mark anything paid. Both are audited (`admin.payment`).

## Testing

| Test | What it shows |
|---|---|
| api `QrCodeTest` | ISO capacity tables, alignment positions, Reed–Solomon against the worked example, format/version BCH words, the map raster |
| outside decoders (one-off, not in CI) | 83 codes written by `QrCode` read back by OpenCV's `QRCodeDetector` and by zxing-cpp, 83/83 each. They cover the four levels, the eight masks forced, versions 1–20 and UTF-8 Cyrillic, plus three 128 px map rasters at ×4. The first mask choice scored finder-like runs inside the symbol only and picked a mask OpenCV could not find (zxing read it). Scoring the quiet zone as light, as the standard says, fixed it. |
| api `PaymentLedgerTest` | paid → granted once; 24 identical events at once → one grant; mismatch; unknown/malformed reference; failed then paid late; a grant that fails is finished by reconcile; restart after the grant; expiry |
| api `BonumMessagesTest`, `BonumSignatureTest`, `BonumPaymentResultTest`, `PaymentIntentTest`, `StorePackageTest` | messages, tokens (never printed), safe links, signatures (Python vectors), packages |
| plugin `JdbcPaymentIT` (H2, PostgreSQL 16) | CAS, the unique provider id, 16 concurrent identical webhooks grant 250 credits once, the event log |
| bot `tools/loadtest/bots/paybot.js` (dev server + stub, 16/16 on 2026-10-14) | see below |

`paybot.js` checks:

* `/buy` → the 100-credit package puts the QR map in hand, shows the price line and the safe bank link only.
* The invoice carries the 32-hex intent reference and 5 000, nothing else.
* The map's pixels, read from the client's map packets, decode (OpenCV) to the stub's QR text.
* An unsigned and a tampered webhook → 401. A signed one with another amount → `MISMATCH` (audit, staff alert). None of them grants a credit.
* The map cannot be dropped.
* The signed payment → `CONFIRMED`, +100 credits once, «✔ Төлбөр баталгаажлаа» with the toast sound, and the map is taken.
* Three replays → `DUPLICATE`.
* A second purchase within 20 s waits; a third open intent is refused.
* `/suld payment` lists the three intents, the paid one fulfilled.
* After a relog the credits stand and no QR map is left.

On the dev database afterwards there was one `bonum:` grant per paid intent. The event log held `BAD_SIGNATURE` and
`MISMATCH` rows with a 64-hex body hash and no body.

The map item's other guards are in code and not bot-tested: no framing, crafting or putting it in another inventory,
and taken at death and at join.

### Running it on the dev server

```
export BONUM_TERMINAL_ID=dev-terminal BONUM_APP_SECRET=dev-secret BONUM_CHECKSUM_KEY=dev-checksum   # dev values only
python3 tools/payments/bonum_stub.py --port 18086 --webhook http://127.0.0.1:8085/api/bonum/callback &
# config.yml: payment.bonum.enabled true, base-url http://127.0.0.1:18086; start the server with the same three variables
QR_PY=<python with opencv> node tools/loadtest/bots/paybot.js <srvctl.sh> 25572 18086
```

## Before production

Each item below is the owner's or a merchant's step. None of them is verified here.

1. A Bonum merchant account: the terminal id, the app secret and the checksum key go into `deploy/suld.env`, not git.
2. Against the sandbox, confirm each of these:
   * the token call and its reply's field names and units;
   * the QR call's path, body and reply (`qrCode` / `qrImage` / `links`);
   * the webhook's header, signing rule (raw body), body shape and status words (`BonumPaymentResult` maps them
     conservatively);
   * whether the webhook URL is set per call or in the portal.
3. Pay a real ₮100 test invoice end to end: the credits arrive once; a replay of the same webhook changes nothing.
4. Put the reverse proxy in front: TLS on `pay.suld.mn` → `127.0.0.1:8085`. Set `allowed-ips` to Bonum's sender
   addresses if Bonum publishes them.
5. The store's legal side: the Minecraft Usage Guidelines (cosmetics only) and Mongolian consumer and tax rules. These
   are the owner's.

**Production-ready is never claimed without a real provider integration test.**
