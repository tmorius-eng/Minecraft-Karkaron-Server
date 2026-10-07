# SÜLD economy balance (proposed)

Part of the proposed balance. **Not implemented yet.** Measured in `docs/PROGRESSION_SIMULATION.md` §Economy.

## The problem

Live, sinks absorb **0.3–2.8 %** of coin income. A hardcore player holds **36 million ₮ after 90 days**.

* **The main faucet is selling gear.** Vendor price = sell × rarity multiplier (up to ×120 for mythic) × (1 + level/10).
  A level-60 mythic sells for thousands.
* **The sinks are one-offs.** Ranks are 59,050 ₮ in total, a clan is 500, plus cosmetics. Recurring sinks are tiny:
  repair `5 + ceil(damage/8)`, relay 10, shrine 30. Horses and revival are free.
* The first proposed pass, with the live vendor prices, still had gear sales at 75 % of all income.

## Faucets (proposed)

| Faucet | Rule |
|---|---|
| Mob coins (new) | round((1 + 0.4·L) × tier reward) per kill, party-shared |
| Dungeons | 60 + 12·L per clear × repeat fatigue |
| Story | live chapters × 2.5; Act II 60 + 15·L |
| Daily tasks | 3 × (40 + 8·L) |
| Login | 40·day (+200 on day 7), unchanged |
| Events | 40 + 8·L |
| Level rewards | `LevelRewards`, unchanged (26,745 ₮ in total) |
| Vendor (gear) | sell × min(5, rarity mult.) × (1 + L/30); **legendary and above cannot be sold** (salvage only) |
| Vendor (materials) | kept for upgrades by the proposed policy; the band materials are the upgrade input |

## Sinks (proposed)

| Sink | Cost | Kind |
|---|---|---|
| Repairs | (15 + 4·L) ₮ per active hour of wear; + 3 hours' worth on death | recurring, scales with level |
| Armour tiers T2–T6 | 2,000 / 12,000 / 45,000 / 120,000 / 300,000 ₮ + 5·tier band materials | progression |
| Armour enhancement +1…+5 | 40 × armour level × step × tier per step | progression, repeats every tier |
| Tempering +1…+10 (60) | 15,000 × (t+1) ₮ + 3·(t+1) Тэнгэрийн чулуу | endgame, 825,000 ₮ in total |
| Mythic entry sigil | 400 + 250 × tier per run | recurring endgame |
| Ascension rite | 25,000 × rank ₮ | endgame, 1,375,000 ₮ in total |
| Reforge (loot slots) | 0.6·L² + 25·L + 25 per item level (was linear) | progression |
| Respec | 25 ₮ per point × (1 + L/20); free to 10 | choice |
| Ranks, clan, cosmetics, relay, shrine | unchanged | vanity / service |

## Result (p50, simulation)

| Player | Day 30 sink share | Day 90 sink share | Day-90 balance |
|---|---|---|---|
| Casual | 69 % | 84 % | 77k ₮ |
| Active | 88 % | 96 % | 66k ₮ |
| Hardcore | 93 % | 96 % | 103k ₮ |

C5 (sinks ≥ 60 % over 90 days) passes. The day-7 shares are lower (38–65 %), which is intended: new players save for
their first tier.

## Rules that keep it this way

* Every new faucet ships with a matching sink, priced in the same level band.
* Credits (real money) never touch coins (`config.yml:104-106`; `ItemEconomy` comment). This stays true.
* Crafting arbitrage EC-2 (legendary salvage → `tengeriin_taiag` → sell) closes, because legendary+ cannot be sold.
  Crafted items sell at the proposed vendor price (epic `tengeriin_taiag` ≈ 4.7× base, not 12×).
* Fix EC-4: the shop's sell labels must show the real formula.

## Implementation notes

* `ItemEconomy.sellPrice`: the new formula and the salvage-only rule.
* `NpcService`: repair cost by level.
* The class-gear upgrade service: tiers, enhancement, tempering.
* Mythic sigil recipe.
* Ascension rite.
* Mob coins in `CombatListener` (party-shared).
