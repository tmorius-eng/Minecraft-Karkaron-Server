# Instant Revive — design only (DEFERRED)

**Status: DESIGN ONLY, NOT_IMPLEMENTED, by owner decision ("defer paid revive; design doc only; provider Bonum").**
No code, no button, no endpoint and no configuration for paying exist or will exist until the owner approves this
document *and* the open questions below are answered.

## What it would do (from the directive)

Instant Revive would, after a **verified** payment:

* end the active death lock immediately;
* skip the death wound of that death;
* keep everything else exactly as it is.

It must never grant gear, EXP, levels, loot, stats, skill points or anything else. It changes only "wait for the
lock" into "return now".

## Open questions for the owner (blocking)

1. **The project's own rules.** `GAME_DESIGN.md:69` says "No real-money resurrection is part of the core
   architecture". `GAME_DESIGN.md:158-163` says monetisation is "cosmetics only … must not touch power … or progression
   speed". `config.yml:104-106` says "Credits buy cosmetics ONLY (Minecraft EULA: no gameplay advantage for money)".
   Instant Revive contradicts all three. Skipping the wound is a stat difference, and skipping the lock is progression
   speed. The owner must decide to change these rules, in writing, before any implementation.
2. **Mojang / Microsoft commercial usage rules.** The Minecraft Usage Guidelines restrict selling gameplay advantages
   on servers. I could not fetch the current text from this environment (minecraft.net is not reachable here). The
   owner should read the current guidelines and decide whether a paid revive is allowed for SÜLD. A cosmetic-only
   alternative is below.
3. **Price, currency (MNT), refunds and chargebacks**: business decisions.
4. **Provider contract**: Bonum merchant account, webhook signing secret, sandbox access (`PAYMENT_ARCHITECTURE`).

## Lower-risk alternatives the owner may prefer

These are design options; none is implemented.

* **Revival token earned in game.** A rare quest/boss reward that shortens one lock by 50 %. No money, fully within
  the current rules.
* **Clan vigil.** Clan members can perform a shrine ritual that shortens a member's lock by 25 %, at most once per
  death. Social, not paid.
* **Cosmetic-only monetisation for income.** Supporter rank, cosmetic packs, a cosmetic season pass, all through Bonum
  → credits → cosmetics. This is already allowed by `config.yml` and the EULA position above.

## If approved later, the flow

```
death (LOCKED row, death_id) ──► player opens the death UI ──► [INSTANT REVIVE] (only if enabled + provider healthy)
   ──► PaymentService.createIntent(player_uuid, death_id, price) ──► Bonum invoice (QR / deeplink) shown in a dialog
   ──► Bonum webhook (signed) ──► verify signature + amount + currency + intent id + not expired + not already used
   ──► one transaction: payment CONFIRMED, death row LOCKED → INSTANT_REVIVED (only if still LOCKED and death_id matches)
   ──► player restored (no wound for this death), audit `death.instant_revive`
```

Guarantees, every one tested before launch:

* **Never before confirmation.** The client is never trusted.
* **Never twice per transaction.** There is a unique `provider_txn_id`, and the state machine is idempotent.
* **Never for the wrong player or death.** The intent binds both.
* **A restart during confirmation is safe.** Webhook processing is transactional, and an un-applied CONFIRMED payment
  is re-applied on startup.
* **A refund or chargeback after use** marks the payment and flags it for staff. The revive is not undone
  automatically.

See `docs/PAYMENT_ARCHITECTURE.md` for the service, the data and the security model.
