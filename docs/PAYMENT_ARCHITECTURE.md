# Payment architecture — design only (DEFERRED)

**Status: DESIGN ONLY, NOT_IMPLEMENTED.**

* Provider: **Bonum** (owner decision).
* Nothing is built until the owner approves `docs/INSTANT_REVIVE.md` and its open questions. The same architecture
  would also serve the already-allowed **cosmetic credits** (`/buy`, `config.yml:149-155`). Today those are delivered
  by a console command (`credits give`), with no payment code at all.

## Absolute rules

* **No bank-account monitoring and no bank-transfer detection.** Nothing reads bank statements or SMS, and nothing
  guesses payments from transfers. A payment exists only when the provider's **signed** callback says so.
* **No hard-coded account numbers, keys or secrets** in code, config or git. Secrets come from the environment / a
  secret store. The webhook secret is never logged.
* **No fake confirmations**: no "test mode grants" in production builds.
* **The client is never trusted.** The game never accepts "I paid" from a player.

## Components

```
suld-plugin
  PaymentService (interface)          createIntent · onProviderEvent · reconcile · status
    BonumPaymentProvider              invoices / QR, webhook verification (only class that knows Bonum)
  PaymentWebhookEndpoint              tiny HTTPS listener (or a reverse-proxy → localhost), POST /payments/bonum
  PaymentLedger (JDBC)                suld_payment_intent · suld_payment_event (V-next migration)
  Fulfilment                          per product: INSTANT_REVIVE(death_id) · CREDITS(amount)
```

* There is one `PaymentService`, no second payment manager.
* Fulfilment calls the existing services: `DeathService` for the revive, `StyleService.giveCredits` for credits, which
  already does an atomic DB update (`JdbcStyleRepository.java:114-152`).

## Data

`suld_payment_intent`:

| Column | Notes |
|---|---|
| `intent_id` UUID PK | sent to Bonum as the merchant reference |
| `player_uuid` | |
| `product` | `INSTANT_REVIVE` / `CREDITS_<n>` |
| `target_id` | `death_id` for a revive |
| `amount_minor`, `currency` | MNT minor units |
| `provider` | `bonum` |
| `created_at`, `expires_at` | the intent expires after 15 min |
| `status` | `PENDING` → `CONFIRMED` / `FAILED` / `EXPIRED` → `REFUNDED` |
| `provider_txn_id` | **unique**, set on confirmation |
| `confirmed_at`, `fulfilled_at` | |
| `version` | optimistic lock |

`suld_payment_event` is an append-only log of every webhook: raw body hash, signature valid y/n, outcome. It is used
for audits and replays.

## Security model

| Threat | Defence |
|---|---|
| Fake confirmation | HMAC/signature verification with the provider secret over the raw body; reject unsigned or invalid; optional IP allow-list |
| Replay | unique `provider_txn_id` + event id; the timestamp must be within 5 min; a processed event is never re-applied |
| Duplicate webhook | idempotent state transitions: `CONFIRMED` → `CONFIRMED` is a no-op |
| Wrong player / death | the intent binds `player_uuid` + `target_id`; fulfilment re-checks the death row is still `LOCKED` and matches |
| Amount / currency tampering | compare with the intent, exact match required |
| Double revive | one DB transaction moves the intent `CONFIRMED` → fulfilled and the death row `LOCKED` → `INSTANT_REVIVED` with version checks; a second attempt finds the row no longer `LOCKED` |
| Restart during confirmation | the webhook is written transactionally; on startup `reconcile()` fulfils `CONFIRMED` but un-fulfilled intents and expires old `PENDING` ones; optional provider status polling |
| Chargeback / refund | `REFUNDED` status; staff alert; the revive is not reversed automatically |
| Leaked secret | rotation without code changes; the secret lives only in the environment |

## Tests (before any production use)

The directive's tests 11–14 and 18 run against the **provider sandbox**:

* signed / unsigned / tampered webhooks;
* a replayed webhook;
* two concurrent identical webhooks;
* wrong death or player;
* expired intent;
* a restart between confirmation and fulfilment;
* refund after fulfilment.

**Production-ready is never claimed without a real provider integration test.**
