# SÜLD — Gameplay improvement roadmap (audit 2026-10-14)

Effort: S ≤ ½ day · M 1–2 days · L 3–5 days · XL > 1 week. Every item has acceptance criteria that can be checked.

## P0 — before this audit's changes can ship

| # | Item | Effort | Depends on | Acceptance criteria |
|---|---|---|---|---|
| P0-1 | Compile and run the full suite on the uncommitted changes | S | — | `gradlew test simTest` green; every new test (14 classes) runs and passes |
| P0-2 | Re-check the simulation pace band after IT-02 / IT-01 / IT-04 / IT-16 | S–M | P0-1 | `liveAndProposedReachSixtyAtTheSamePace` within 130–170 h; if outside, tune the BOSS band (not the test) and record the owner's decision |
| P0-3 | Run the validators | S | — | `run_all.py` ALL PASSED; `test_fetch_plugins.py` OK |
| P0-4 | Laptop playtest §4 (20 tests) | M | P0-1 | no FAIL in the 20; W-01/W-10 confirm the client mirrors `startUsingItem` |
| P0-5 | Regenerate class armour once | S | P0-3 | `gen_class_armor.py` output differs from the committed T4 data by rounding only |
| P0-6 | Commit and push after review | S | P0-1…P0-5 | one commit on `main`, `origin/main` was `411842d` before the push |

## P1 — next content/balance pass

| # | Item | Effort | Depends on | Acceptance criteria |
|---|---|---|---|---|
| P1-1 | WORLD_EVENT band (ancient 75 / mythic 25) to the v2 band (epic 40 / legendary 45 / ancient 12 / mythic 3) | S | P0-2 | world-boss loot legendary+ ≤ 60 %; simTest band holds |
| P1-2 | Use the new objective kinds in Act II (e.g. ch. 41 → OVOO_RITUAL; a deer-stone INSPECT_CLUE; a first CRAFT_ITEM chapter) | M | cultural review (CULT-001) | ≤ 22 of 42 chapters are kill/discover; ContentDataTest green |
| P1-3 | Unit seams for `partyNearby` and `gateRefusal` (inject the player list / refusal) | S | — | tests cover range, world, soul, training exclusions and the 2 s cache |
| P1-4 | A JUnit test that every INSPECT_CLUE target exists in props.json | S | P1-2 | test fails on a dangling prop id |
| P1-5 | Wire `loot.quest.reward` and `loot.chest.steppe` (today: no caller) or delete them | S | owner decision | no loot table without a caller (a test lists callers) |
| P1-6 | Volley arrows carry a draw force too (they still lose damage at range) | S | — | Олон Сум damage at 30 m ≈ at 5 m |
| P1-7 | Consumable sources beyond rare drops (a vendor at Kharkhorum, a recipe for the whetstone) | M | economy review | each consumable has ≥ 2 sources; ECONOMY_BALANCE_SPEC sink/source updated |

## P2 — quality and feel

| # | Item | Effort | Depends on | Acceptance criteria |
|---|---|---|---|---|
| P2-1 | Faster class-bow draw stages for the class bow only (e.g. 0.35 / 0.55 s) with `FULL_DRAW_TICKS` to match | S | P0-4 feedback | full draw ≤ 0.6 s; DPS unchanged in simTest |
| P2-2 | Sound pass: one cue table for every refusal/feedback (today spread over SkillService/SkillTreeService/ConsumableService) | M | — | a single `FeedbackCues` class; no duplicated literals |
| P2-3 | IT-06/10/11/13/14/18, QS-06/08: recover the original web-session reports (items.md, quests.md, weapons.md) and the Part-12 questions | S | owner | the eight reports updated with the original IDs |
| P2-4 | HUD: show consumable cooldowns next to buffs | S | — | HUD buff chip «Айраг 23с» while on cooldown |

## P3 — later

| # | Item | Effort | Acceptance criteria |
|---|---|---|---|
| P3-1 | Hold-to-charge for the other classes' heavy spells (reuse the draw machinery) | L | design doc approved; no combo regressions in C-01/C-02 |
| P3-2 | A replay/QA harness that drives `PlayerInteractEvent` sequences headlessly (MockBukkit or a test server) for combo/draw regression | XL | W-01…W-12 automated |
