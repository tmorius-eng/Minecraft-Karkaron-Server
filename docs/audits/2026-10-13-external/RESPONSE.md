# Response to the external forensic audit (pasted 2026-10-13, written against `b066572`)

Every finding was checked against the code on `main` (`4b50e9e` and later). **Holds** = the code shows it; **stale** = true once, not
now; **disputed** = the code does not show it. Fixes: one commit each, a test each.

| ID | Claim | Verdict | Evidence / what changed |
|---|---|---|---|
| F01 / PERF-001 | `PlayerDataSaves.now()` → synchronous `p.saveData()` | **Holds, by design** | The item-before-coin order that stops crash dupes (PERS, FIND-01). Measured p50 5 ms, max 8.6 ms, ~1 call / 7 s at 25 bots (`docs/perf/M3_2026-10-12.md`). Removing it reopens the dupe; the plan stays: re-measure on the VPS NVMe, a ledger design only if p99 > 2 ms. The audit's "10 players in one tick" scenario was not measured by anyone. One idle path removed (BowQuiver, `46d4411`). |
| PERF-002 | 50 players → TPS 11.8 | **Stale** | 2026-10-08 bench on a shared 4-vCPU host. Later: 50 bots TPS 17.08 / p95 96 ms; 25 bots TPS 17.7. Still below the 19.5 gate; no new number is claimed here. |
| COMPAT-001 | online-mode excludes "70–85 %" of Mongolian players | **Fact holds, number unsourced** | Java-only + online-mode is an owner decision (2026-10-12). Public listings show local servers are often cracked/Bedrock; no source gives a share. The alpha survey asks client + account type first. |
| F02 / LOGIC-001 | drag into the class window deletes items | **Holds → fixed** | `ClassSelectionGui` had no drag handler and is not a `Menu`. `onDrag` cancels any drag touching the window. `ClassMenuDragTest`. |
| F03 / SEC-001 | `transfer()` failure + payee already spent → payer not refunded | **Holds, narrow; coins conserved** | Needs a failed DB transaction *and* the payee spending inside that write (ms). No coin is created or destroyed (the payee keeps what it spent); the payer's message can overstate the refund. Escrow is the clean design — not built yet (P3). |
| F04 / PERS-001 | death EXP loss not saved at once | **Holds → fixed** | `DeathService.onDeath` now saves the profile with the lock. Live: EXP in SQL 4 s after death (13000 → 12766). |
| F05 | dungeon gates read the player file, never the DB marks | **Holds → fixed** | `PlayerMarks.restore` on login adds clears/sites the DB holds and the file lost (only adds). Live: a DB-only clear came back into `suld:dungeon_clears`. `PlayerMarksMergeTest`. |
| F06 | "nothing is sold for real money" comment; «Наадамч» title | **Disputed / opinion** | The comment says "credits for the store ones (cosmetics only)". «Наадамч» is a festival title, not tied to the removed minigames — kept (native review may decide). A stale "(₮)" in `config.yml` fixed to «зоос». |
| F07 | `CRIT_AT` pruned only above 256 | **Mostly stale** | Also removed on the entity's death (`CombatListener:290`). |
| UX-001 | passive 5–30 min death lock | **Partly stale** | `/trial` (active, ≈ 3 min) since 2026-10-12; L1–5 lock is 1 min. A zero lock below L10 is a design call for the owner. |
| — | dungeons are tuned for parties; solo hits a wall | **Holds (missing) → added** | Boss HP was fixed to the dungeon's *recommended* party. Now `MobScaling.bossPartyScale(recommended, entered)`: the party that entered, capped at the recommendation (solo ×0.44). `BalanceRulesTest`. The simulator keeps the recommended-party boss (an upper bound). featbot clears Саарлын Агуй 16/16. |
| — | "Act I–IV not needed", "don't build Bedrock/payments yet" | Agreed | Already the plan's "do not build yet" list. |

Not verified by this response: a real client, load on the real host, the transfer escrow.
