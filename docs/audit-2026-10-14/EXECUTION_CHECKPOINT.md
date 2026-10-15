# Execution checkpoint — audit-2026-10-14 (local CLI session)

Base: `main` @ `411842d` (= `origin/main`, verified at session start). One agent, no subagents.
**State at the end of the session: Phases 1–6 written; Phase 7 BLOCKED. Nothing is committed or pushed.**
**Superseded:** see "Phase 7 run 2026-10-14 (validation passed; remote-base gate failed)" at the end of this file.

## Blocker (Phase 7)

- In this non-interactive session every invocation of `./gradlew`, `.\gradlew.bat`, `java` and `python`/`py` asked for
  an interactive approval that could not be given (tried at the start and again before Phase 7). The Gradle cache was
  also outside the readable directories, so the Paper API jar could not be inspected.
- Consequently **no code was compiled and no test, simTest or validator was run.** Every "DONE" below means written and
  reviewed by reading, not verified by a build. Per the execution rules, nothing was committed or pushed.
- The earlier web-session sub-audits (`items.md`, `quests.md`, `weapons.md`) and the prompt's "Part 12 / 20 Final
  Questions" are **not in the repository**; findings follow the IDs and file/line references in
  `CLAUDE_MASTER_EXECUTION_PROMPT.md`, each re-verified against source before it was fixed.

## Phase status

| Phase | Status | Notes |
|---|---|---|
| 1 — SULD-F01/F05/F08/F09/F11/F12 | DONE (found in working tree, uncompiled) | BowQuiver restore guard + pickup hook; DeathService trial-wolf shield (EXP save already at `DeathService.java:249-254`); DeathTrialService floor search; CraftOrderService shared mailbox + mailbox fallback. Tests: `BowQuiverTest`, `CraftOrderServiceTest`, `death/DeathTrialServiceTest`. |
| 2 — WP-01..WP-10 | DONE (uncompiled) | SkillService hold-to-draw (`startUsingItem` + `PlayerStopUsingItemEvent` + tick watchdog + repeat-gap fallback + 5 s cap), stray vanilla shot cancel, dry-string cue, L cancels a draw, held-R filter, off-hand R cue; CombatListener draw-force PDC + `empowered()`; soul-fire trail; CombatFeel upright ring −0.18 Y; validator model/texture/parent + 3-stage check. Tests: `skill/BowDrawTest`, `combat/ArrowScaleTest`. |
| 3 — IT-01..IT-20 | DONE for every IT item the prompt specifies (uncompiled) | IT-03 binding on delivery + equip fallback (`PickupBindingTest`); IT-02 tiers.json = sim v2 bands (simTest golden → `liveBossBandIsTheV2Band`); IT-01/15 named drops (`ItemObtainabilityTest`); IT-04/05 class weapon perLevel, in-tier refresh, seeded rolls (`ClassWeaponScalingTest`, `ClassWeaponRefreshTest`); IT-16 generator + T4 data (`ClassArmorScalingTest`); IT-17 salvage, IT-19 sale (`SalvageAndSaleAuditTest`); IT-08 reroll save order; IT-09 accessory save order; IT-12/20 consumables (`ConsumablesTest`). IT-07: no reforge/repair action exists. IT-06/10/11/13/14/18: unspecified — not invented. |
| 4 — QS-01..QS-08 | DONE for every QS item the prompt specifies (uncompiled) | QS-01 party quest + daily credit; QS-02 gate refusal (tracker + book); QS-03 aliases + level placement (`QuestChainOrphanTest`); QS-04/05 giver; QS-07 verified already correct; new QuestType INSPECT_CLUE / CRAFT_ITEM / OVOO_RITUAL wired end to end (`QuestObjectiveVocabularyTest`). QS-06/08: unspecified. |
| 5 — HUD / spell feedback | DONE (uncompiled) | HUD requirements verified present; Онгоны Дуудлага `NO_TARGET` before cost (echo refund removed); Тэнгэрийн Шийтгэл refused unpaid without enemies; soul refusal sounds. |
| 6 — 8 reports | DONE | `GAME_LOGIC_STATUS.md`, `QUEST_AND_TASK_AUDIT.md`, `CLASS_ABILITY_AUDIT.md`, `ITEM_AND_RARITY_AUDIT.md`, `WEAPON_ANIMATION_AUDIT.md`, `COMBAT_AND_FEEDBACK_AUDIT.md` (Part 12 absent — stated, 20 scope questions answered instead), `LAPTOP_PLAYTEST_PLAN.md`, `GAMEPLAY_IMPROVEMENT_ROADMAP.md`. |
| 7 — validate, commit, push | **BLOCKED** | see Blocker. Not committed, not pushed. |

## To finish Phase 7 (in an interactive shell)

```powershell
.\gradlew.bat test simTest --no-daemon          # fix compile errors first if any (see "API assumptions")
py tools\validation\run_all.py
py -I tools\plugins\test_fetch_plugins.py
py tools\content\gen_class_armor.py; git diff --stat suld-api/src/main/resources/items/class_armor.json   # rounding only
git status; git diff --stat                      # review; CLAUDE_MASTER_EXECUTION_PROMPT.md is a scratch file — do not commit it
git fetch origin; git rev-parse origin/main      # must still be 411842d4b7a9092021ef6e770d6f8e74abaa75b9
git add <the files listed below>; git commit; git push origin main
```

Watch in particular: `SimulationTest.liveAndProposedReachSixtyAtTheSamePace` (130–170 h) after the tiers / loot /
class-gear data changes. If it moves out of the band, tune the data with the owner — do not widen the test.

## API assumptions to confirm at compile time (Paper 1.21.11)

`LivingEntity#startUsingItem(EquipmentSlot)`, `LivingEntity#clearActiveItem()`, `HumanEntity#isHandRaised()`,
`io.papermc.paper.event.player.PlayerStopUsingItemEvent#getTicksHeldFor()` (was `getTicksHeld()` — a compile error,
see the follow-up below), `LivingEntity#swingOffHand()`,
`EntityShootBowEvent#setConsumeItem`, `PersistentDataType.FLOAT`, `Attribute.MAX_HEALTH` (already used in the repo),
`Particle.CAMPFIRE_COSY_SMOKE`, `Sound.ITEM_CROSSBOW_LOADING_START`, `Sound.BLOCK_GRINDSTONE_USE`.

## Files changed (for the commit)

Modified (main): suld-api `balance/Economy`, `item/ItemEconomy`, `item/ItemTooltip`, `quest/QuestChain`,
`quest/QuestEngine`, `quest/QuestType`; resources `items/{class_armor,loot,materials,tiers,weapons}.json`;
suld-plugin `SuldPlugin`, `combat/{CombatFeel,CombatListener}`, `content/ContentLoader`,
`death/{DeathService,DeathTrialService}`, `dungeon/DungeonService`, `gui/{Menus,QuestMenu}`,
`item/{BowQuiver,ClassWeapons,CraftOrderService,CraftService,EquipmentService,ItemService}`, `npc/NpcService`,
`prop/PropService`, `quest/{QuestService,QuestTracker}`, `region/OvooService`, `reward/TaskService`,
`skill/{SkillService,SkillTreeService,Ultimates}`, `worldboss/WorldBossService`, `worldevent/WorldEventService`;
resources `content/quests.json`; sim `Engine`, `ProposedRules`; simTest `SimulationTest`; tools
`content/gen_class_armor.py`, `validation/validate_resourcepack.py`.
New (main): `suld-api/.../item/Consumables.java`, `suld-plugin/.../item/ConsumableService.java`.
New (tests): suld-api `classgear/{ClassArmorScalingTest,ClassWeaponScalingTest}`,
`item/{ConsumablesTest,ItemObtainabilityTest,SalvageAndSaleAuditTest}`,
`quest/{QuestChainOrphanTest,QuestObjectiveVocabularyTest}`; suld-plugin `combat/ArrowScaleTest`,
`death/DeathTrialServiceTest`, `item/{BowQuiverTest,ClassWeaponRefreshTest,CraftOrderServiceTest,PickupBindingTest}`,
`skill/BowDrawTest`.
New (docs): `docs/audit-2026-10-14/*` (this file + 8 reports).

## Follow-up 2026-10-14 — four failing suld-api tests (test-only fixes)

A local run (`suld-api/build/test-results`, 04:55) failed four tests. Each was traced to an intentional Phase 3 data
change that the test still pinned at its pre-audit value; **no production code or item data was changed**.

| Test | Failing assertion | Root cause | Fix |
|---|---|---|---|
| `ItemGeneratorTest.fixedRarityItemsKeepTheirExactNumbers` | `expected 21.0 but was 65.0` (baatar.4, legendary, lvl 45) | IT-04 gave class weapons `perLevel.damage` 1.0; growth is `perLevel × (level − 1)` (`ItemGenerator.generate`, `ItemDefinition.min/maxStat`, record javadoc "added per item level above 1"), so 21 + 44 = 65 | Asserts base + growth exactly (21 + 1.0 × 44) across 20 seeds and at the tier level 36, crit stays exactly 11, the data's perLevel is checked |
| `RarityTooltipEconomyTest.tooltipShowsEverythingTheItemIs` | line 78 `+14 Хохирол` (tooltip shows `+38`) | same: baatar.3 at lvl 25 = 14 + 24 | Damage computed from the definition (= 38); now also asserts `+9% Чухал цохилтын магадлал` and the class-weapon lore; every other line unchanged |
| `LootEngineTest.noBundledTableDropsAWeapon` | `loot.goviin_chono dropped weapon.talyn_ild` | IT-01/IT-15 named regional weapons as rare drops | `APPROVED_WEAPON_DROPS` allowlist (6 table → weapon pairs); any other weapon fails, a class weapon fails, and every approved pair must actually drop (no stale entry). New `weaponsReachLootOnlyAsNamedRareDrops`: no WEAPON pool, no weapon in entries/guaranteed, weapons only as approved `rare` entries with chance ≤ 10 % |
| `LootEngineTest.tierBandsDecideRarityWithinTheItemsRange` | `elite band is uncommon..rare; {UNCOMMON=1114, RARE=1542, EPIC=344}` | IT-02 v2 bands (ELITE U55/R35/E10, BOSS R50/E38/L10/A2 — same as `ProposedRules.catalog` and `docs/GEAR_PROGRESSION_SPEC.md`) | Elite ⊆ uncommon..epic, epic share 5–20 %; boss ⊆ rare..ancient (never mythic), some legendary+, legendary+ < 20 % (est. ~6 %). The `normal` check (never reached in the failing run) compared all common vs uncommon drops, an exact 50/50 tie at level 40 (4 uncommon-minimum Ган pieces vs 8 common-capable): it now measures the 75/25 band on common-capable pieces (65–85 %) and that normal stays ≤ uncommon |

Files changed: `suld-api/src/test/java/mn/suld/api/item/ItemGeneratorTest.java`,
`suld-api/src/test/java/mn/suld/api/item/RarityTooltipEconomyTest.java`,
`suld-api/src/test/java/mn/suld/api/loot/LootEngineTest.java`, this file.

**Not run:** `gradlew.bat` was again refused (approval) in this session, so the edits are unverified. Run first:

```powershell
.\gradlew.bat :suld-api:test --tests mn.suld.api.item.ItemGeneratorTest --tests mn.suld.api.item.RarityTooltipEconomyTest --tests mn.suld.api.loot.LootEngineTest --tests mn.suld.api.classgear.ClassWeaponScalingTest --tests mn.suld.api.item.ItemObtainabilityTest --no-daemon
```

Note: the "Live band" column of `docs/GEAR_PROGRESSION_SPEC.md` (generated by `sim/SpecTables`) still shows the
pre-v2 bands; regenerate it with the sim when Phase 7 runs.

## Follow-up 2026-10-14 — two suld-plugin compilation errors (UNVERIFIED)

The latest Gradle test report showed two Java compile errors in suld-plugin. Only these two were fixed. **Both fixes are
UNVERIFIED until a local compile succeeds.** No build or test was run in this session.

| File | Error | Fix |
|---|---|---|
| `suld-plugin/.../skill/SkillService.java` (`onBowRelease`) | `PlayerStopUsingItemEvent.getTicksHeld()` does not exist | Now calls the Paper 1.21.11 method `getTicksHeldFor()`. The draw duration is still `Math.max(ticksHeld, currentTick − drawStart)`, so the tick-watchdog fallback works the same way |
| `suld-plugin/.../gui/ClassSelectionGui.java` | `onDrag(InventoryDragEvent)` declared twice (F02 handler + SULD-F07 handler, both in HEAD `411842d`) | Merged into one `@EventHandler(priority = HIGHEST)` handler that uses `cancelsDrag(...)`. That covers SULD-F07's goal: a drag touching any class-window slot is cancelled, so nothing can be placed there and wiped by `inv.clear()`. The SULD-F07 version cancelled every drag while the window was open. That stricter rule was not kept, because `ClassMenuDragTest` requires a drag only inside the player's own bag to pass. The javadoc now cites both F02 and SULD-F07 |

Not touched: tests, loot balance, item data, deprecation warnings, all other uncommitted work. Run first:

```powershell
.\gradlew.bat :suld-plugin:compileJava :suld-plugin:test --tests mn.suld.plugin.gui.ClassMenuDragTest --tests mn.suld.plugin.skill.BowDrawTest --no-daemon
```

## Log

- Start: git status/diff inspected; Phase 1 found complete in the working tree; checkpoint created.
- Phases 2–5 implemented with tests; each phase recorded here as it finished.
- Self-review pass over the Java changes (API usages cross-checked against existing calls in the repo;
  `hasActiveItem()` → `isHandRaised()`; pattern-variable names de-duplicated in `CombatListener.onMitigate`).
- Phase 6 reports written; counts re-checked against the data (42 chapters: 15 K / 1 C / 5 L / 9 D / 12 X; 234 items).
- Phase 7: second attempt to run Gradle refused (approval) → stopped before commit/push.
- Follow-up: four failing suld-api tests root-caused and fixed test-side (see section above); Gradle refused again,
  so they are unverified. Nothing committed or pushed.
- Follow-up: two suld-plugin compile errors fixed (`getTicksHeldFor()`, one merged `onDrag`). Not compiled, so
  UNVERIFIED. Nothing committed or pushed.

## Phase 7 run 2026-10-14 (validation passed; remote-base gate failed)

Local Windows CLI session, one agent. Environment fixes, for this session only: `py` is not on PATH, so the launcher
`%LOCALAPPDATA%\Programs\Python\Launcher\py.exe` (Python 3.13.15) was used. `JAVA_HOME` is set machine-wide but was
missing from the session, so it was set to `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` per command.

| Step | Command | Result |
|---|---|---|
| 1 | `$env:PYTHONUTF8='1'; py tools\content\gen_class_armor.py` | exit 0, "120 class armour definitions, 30 sets"; byte-identical to HEAD (no diff) |
| 2 | `.\gradlew.bat :suld-plugin:specTables --no-daemon -q` | exit 0, "spec tables written". Only content change: `docs/GEAR_PROGRESSION_SPEC.md` "Live band" for ELITE/BOSS/DUNGEON now equals the v2 `tiers.json` (3 lines). The other 7 spec docs differ only by mixed CRLF/LF that SpecTables writes; `git diff --ignore-cr-at-eol` is empty for them |
| 3 | `.\gradlew.bat test simTest --no-daemon` | exit 0, BUILD SUCCESSFUL. `suld-api:test` and `simTest` were UP-TO-DATE, so they were forced: `.\gradlew.bat :suld-plugin:simTest --rerun` and `.\gradlew.bat :suld-api:test --rerun`, both BUILD SUCCESSFUL. JUnit XML: suld-api 630 tests / 0 failures / 0 errors; suld-plugin test 210 / 0 / 0; simTest 13 / 0 / 0 |
| 4 | `$env:PYTHONUTF8='1'; py tools\validation\run_all.py` | exit 0, "ALL VALIDATIONS PASSED" |
| 4 | `py -X utf8 -I tools\plugins\test_fetch_plugins.py` | exit 0, "Ran 6 tests … OK" (one ResourceWarning, unclosed file at line 132) |
| 5 | `git diff --check`; `git status`; `git diff --cached` | clean; nothing staged; changes are the 8 spec docs above + untracked `CLAUDE_MASTER_EXECUTION_PROMPT.md`. All 8 audit reports + this file are already tracked |
| 6 | `git branch --show-current`; `git fetch origin`; `git rev-parse origin/main` | branch `main`; **origin/main = HEAD = `d04967a`**, not the expected `411842d4b7a9…`. `411842d..origin/main` = one commit: `d04967a tmorius-eng 2026-10-14 13:26 +0800 fix(gameplay): complete SULD audit and regression fixes` (72 files, already contains the audit work and the 8 reports) |
| 7–9 | stage / commit / push | **Not done** — gate in step 6 failed |

**Blocker:** the remote moved off the expected base. The Phase 7 commit appears to have been made and pushed already
as `d04967a` (by the owner, outside this session). This session did not stage, commit or push anything.

**Remaining work (owner decision):**
- Confirm `d04967a` is the intended Phase 7 commit. If so, Phase 7 is done; the validation above ran against that tree
  plus the regenerated docs.
- Optionally commit the one real regeneration, `docs/GEAR_PROGRESSION_SPEC.md` (stale "Live band" column), on top of
  `d04967a`. `git add` normalises the line endings of the other 7 docs, so they would drop out. Never commit
  `CLAUDE_MASTER_EXECUTION_PROMPT.md`.
- This checkpoint file is modified by this update (uncommitted).
- Phase 7 run: steps 1–5 passed (see section above); step 6 found origin/main = `d04967a` ≠ `411842d` → stopped. Nothing staged, committed or pushed.

## Run 2026-10-15 — atlas / waypoint / minimap root-cause audit (validation passed in a worktree; stopped at the gates; completed by the follow-up below)

Local Windows CLI session, one agent, no subagents. Same environment fixes as above (py launcher, `JAVA_HOME`).
Report: `MAPS_GAMEPLAY_ROOT_CAUSE_AUDIT.md`. The eight 2026-10-14 reports were reviewed and left unchanged.

**Initial state (before this run):** branch `main` @ `c1fcc62`, nothing staged. Pre-existing user changes, left
untouched: `suld-plugin/src/test/java/mn/suld/plugin/combat/ArrowScaleTest.java` is modified so its whole content is one
line that looks like an OpenRouter API key; untracked `jcode-windows-x86_64.exe`. Note:
`CLAUDE_MASTER_EXECUTION_PROMPT.md` is already **tracked** (committed in `7685130`, also on `origin/main`) despite the rule never to commit it; this run did not touch it. **The key should be treated as exposed and rotated.** `ArrowScaleTest.java` must not be committed as it is.

**Changed by this run:** `AtlasView`, `AtlasViewTest`, `AtlasSky`, `WaypointGuide`, `ChunkColorStore`,
`ChunkColorStoreTest`, new `map/AtlasInputTest`, `DungeonService`, `SuldPlugin`, `QuestTracker` (the one-identifier
compile fix identical to origin's `2018b2a`), `plugin.yml` (alias `maps`), `docs/WORLD_MAP.md`, `atlasbot.js`,
`partymapbot.js`, new `MAPS_GAMEPLAY_ROOT_CAUSE_AUDIT.md`, this file.

| Step | Command | Result |
|---|---|---|
| 1 | `.\gradlew.bat :suld-api:test --rerun --tests "mn.suld.api.map.*"` (main tree) | exit 0; `AtlasViewTest` 6 / 0 / 0 |
| 2 | `.\gradlew.bat test simTest --no-daemon` (**main tree**) | **exit 1**: `:suld-plugin:compileTestJava` fails at `ArrowScaleTest.java:1` ("class, interface, enum, or record expected"), the pre-existing user change above. Before that, local `HEAD` itself failed `:suld-plugin:compileJava` (`QuestTracker.java:171`, `refusal` already defined); fixed as G-1 |
| 3 | `git worktree add --detach ../suld-validate HEAD`, this run's files copied in (`cmp`-checked), the committed `ArrowScaleTest.java` | temporary validation tree; removed after use |
| 4 | `.\gradlew.bat test --rerun simTest --rerun --no-daemon` (worktree) | exit 0, BUILD SUCCESSFUL. JUnit XML: suld-api 632 / 0 failures / 0 errors; suld-plugin 215 / 0 / 0; simTest 13 / 0 / 0 |
| 5 | `.\gradlew.bat :suld-plugin:specTables --no-daemon -q` (worktree) | exit 0, "spec tables written"; 8 spec docs differ only by line endings (`git diff --ignore-cr-at-eol` empty), so nothing was brought back |
| 6 | `py tools\content\gen_class_armor.py` (worktree) | exit 0, "120 class armour definitions, 30 sets"; `class_armor.json` byte-identical |
| 7 | `$env:PYTHONUTF8='1'; py tools\validation\run_all.py` (main tree) | exit 0, "ALL VALIDATIONS PASSED" |
| 8 | `py -X utf8 -I tools\plugins\test_fetch_plugins.py` | exit 0, "Ran 6 tests … OK" |
| 9 | `git diff --check` | clean |
| 10 | `git fetch origin`; `git rev-parse origin/main` | **`f4c071be387004f27f7e6974443e63c03b641aa8`** ≠ expected `411842d4b7a9092021ef6e770d6f8e74abaa75b9` (`411842d` is an ancestor). Local `main` diverged: 1 ahead (`c1fcc62`), 5 behind (`2018b2a`, `aee8470`, `86c2911`, merge `6232a4f`, `f4c071b`) |
| 11 | stage / commit / push | **Not done.** Stop conditions: origin/main ≠ expected base; the required main-tree `gradlew test simTest` fails because of the pre-existing user change, which may not be altered; no real-client verification |

**Not verified anywhere:** behaviour on a real 1.21.11 client (the new zooms, the action-bar read-out, the unpin
ending the waypoint, the dungeon start closing sky windows, the minimap) and the loadtest bots (they need the dev server).

**Remaining steps (owner):**
1. Rotate the exposed key. Restore or rewrite `ArrowScaleTest.java` yourself (`git diff` shows the committed test).
2. Integrate `origin/main` (`f4c071b`) into local `main`. Local `c1fcc62` touches `QuestChain` / `QuestChainTest`;
   origin's `aee8470` / `86c2911` touch the same quest area, so review that merge by hand. The `QuestTracker` hunk here
   equals origin's.
3. Re-run `.\gradlew.bat test simTest --no-daemon`, the two Python validators, and `atlasbot.js` / `mapcbot.js` /
   `partymapbot.js` on the dev server. Check the client steps in the report §4.
4. Then stage only the files listed under "Changed by this run" (never `CLAUDE_MASTER_EXECUTION_PROMPT.md`,
   `ArrowScaleTest.java` or `jcode-windows-x86_64.exe`) and commit as `fix(gameplay): complete SULD audit and regression fixes`.

Steps 1, 2 and 4 and the automated part of step 3 were done in the follow-up below. The bots and the client steps are
still open.

## Follow-up 2026-10-15 — reconciliation with `origin/main`, main-tree validation, commit and push

Local Windows CLI session, one agent, no subagents. The owner authorised restoring `ArrowScaleTest.java` (they had
already revoked the key), integrating the current `origin/main` instead of the old `411842d` base, and pushing.

**State found:** a `git pull --rebase origin main` stopped halfway (HEAD detached at `f4c071b`, local `main` at a merge
commit `29c0baf`). That merge was never pushed, and it could not be used for two reasons. (1) It committed
`QuestChainTest.java` with conflict markers still in it. (2) It added the untracked 130 MB `jcode-windows-x86_64.exe`,
which is over GitHub's 100 MB limit. The rebase had staged the same conflict markers. `ArrowScaleTest.java` was already
back to its committed content in the working tree.

**Resolution:**
1. `QuestChainTest.normaliseStartsAndCatchesUp`: kept both sides. Origin's assertion (an unknown id with no progression
   hint is preserved) and local `c1fcc62`'s assertion (`q -> false` recovers to the first chapter) both hold under
   origin's `QuestChain.normalise`. `git rebase --continue` then wrote `f56dea3`, which is `c1fcc62` replayed on
   `f4c071b`. Its `QuestChain.java` hunk is already in origin, so the commit only adds one test line.
2. This run's files were restored from `29c0baf` (`git checkout 29c0baf -- <paths>`), leaving out the `.exe` and
   `QuestChainTest.java`. None of those paths changed between the merge base `c67836a` and `f4c071b`, so origin's
   versions lose nothing. The `QuestTracker` hunk matched origin's `2018b2a`, so it no longer shows as a change.
3. `jcode-windows-x86_64.exe` was written back to the working tree from `29c0baf` and listed in `.git/info/exclude`
   (local only, not committed).
4. `ArrowScaleTest.java` matches `HEAD`. `git grep` and a working-tree grep for the OpenRouter key prefix find nothing
   apart from the excluded `.exe`.

| Step | Command (main working tree) | Result |
|---|---|---|
| 1 | `.\gradlew.bat test --rerun simTest --rerun --no-daemon` | **exit 1**: `:suld-plugin:test` 221 run, 1 failed: `JdbcMapPinRepositoryIT.theLimitHoldsWhenAddsRace` ("the limit of 5 holds …: 9") |
| 2 | `.\gradlew.bat :suld-plugin:test --rerun --tests 'mn.suld.plugin.persistence.JdbcMapPinRepositoryIT'` ×3 | exit 0, 0, 1 (the failed run stored 10). Flaky |
| 3 | the same ×4 in a temporary `git worktree` at clean `origin/main` `f4c071b` | exit 0, 0, 1, 1: **the flake is already on `origin/main`**, not caused by this work; worktree removed |
| 4 | `.\gradlew.bat test --rerun simTest --rerun --no-daemon` | **exit 0**, BUILD SUCCESSFUL. JUnit XML: suld-api 632 / 0 failures / 0 errors / 0 skipped; suld-plugin 221 / 0 / 0 / 0; simTest 13 / 0 / 0 / 0 |
| 5 | `$env:PYTHONUTF8 = "1"; py tools\validation\run_all.py` | exit 0, "ALL VALIDATIONS PASSED" |
| 6 | `py -X utf8 -I tools\plugins\test_fetch_plugins.py` | exit 0, "Ran 6 tests … OK" |
| 7 | `git diff --check`, `git diff --cached --check` | exit 0, clean |

**Open:** `JdbcMapPinRepositoryIT.theLimitHoldsWhenAddsRace` fails about half the time, on `origin/main` too. 24 adds
racing a limit of 5 can store 9 or 10 rows. So the `INSERT … SELECT … WHERE (SELECT COUNT(*) …) < ?` guard does not
fully serialise concurrent adds, despite what the `JdbcMapPinRepository` Javadoc says. This affects the real pin
limit, not just the test. Not changed here. The bots and the real-client checks in the report §4 also remain open.

## Follow-up 2026-10-15 (2) — atlas framing, boss bars, minimap stability and lag, accidental `/skills`

Local Windows CLI session, one agent, no subagents. Root causes and fixes: `MAPS_GAMEPLAY_ROOT_CAUSE_AUDIT.md` §5
(H-1 … H-5).

**State found:** `main` = `origin/main` = `0825c8f` after `git fetch`, working tree clean. The preserved work (atlas
zoom and opening scale, tile timeout and cache, waypoint cleanup, the `/maps` alias, the dungeon window close) was
already in `0825c8f`. `ArrowScaleTest.java` equals `HEAD`, and `git grep` finds no `sk-or-v1` key. There was nothing to
reconcile in `QuestTracker.java`; its only change in this run is the boss-bar gate call.
`CLAUDE_MASTER_EXECUTION_PROMPT.md` and the excluded `jcode-windows-x86_64.exe` were not touched.

| Step | Command (main working tree) | Result |
|---|---|---|
| 1 | `.\gradlew.bat :suld-plugin:test --tests …BossBarsTest --tests …AtlasFramingTest --tests …MinimapStabilityTest --tests …NoAccidentalWindowTest --tests …AtlasInputTest --no-daemon` | exit 0: 4 + 6 + 7 + 3 + 4 tests, 0 failures |
| 2 | `.\gradlew.bat test simTest --no-daemon` | exit 0, BUILD SUCCESSFUL (Gradle took most test tasks as up to date from step 1, so step 3 forces a full run) |
| 3 | `.\gradlew.bat test --rerun simTest --rerun --no-daemon` | **exit 0**, BUILD SUCCESSFUL. JUnit XML: suld-api 632 / 0 failures / 0 errors / 0 skipped; suld-plugin 241 / 0 / 0 / 0 (221 + 20 new); simTest 13 / 0 / 0 / 0. The flaky `JdbcMapPinRepositoryIT.theLimitHoldsWhenAddsRace` passed in this run |
| 4 | `$env:PYTHONUTF8 = "1"; py tools\validation\run_all.py` | exit 0, "ALL VALIDATIONS PASSED" |
| 5 | `py -X utf8 -I tools\plugins\test_fetch_plugins.py` | exit 0, "Ran 6 tests … OK" |
| 6 | `git diff --check` (new files included with `git add -N`) | exit 0, clean |

**Not verified:** a real 1.21.11 client (the closer atlas, boss bars leaving and coming back, the four-frame minimap in
fights, Shift + right click doing nothing) and the loadtest bots `mapcbot.js` / `hudbot.js` (updated for the new
behaviour; they need the dev server, and `node` is not installed here, so not even syntax-checked).

## Follow-up 2026-10-15 (3) — true screen waypoints, HUD projection, minimap marks

Local Windows CLI session, one agent, no subagents. Root causes and fixes: `MAPS_GAMEPLAY_ROOT_CAUSE_AUDIT.md` §6
(W-1 … W-6).

**State found:** `main` = `origin/main` = `6f2162b`, working tree clean. `CLAUDE_MASTER_EXECUTION_PROMPT.md`,
`ArrowScaleTest.java` and the excluded `jcode-windows-x86_64.exe` were not touched.

**Changed by this run:**
* new: `suld-api` `map/ScreenProjection`, `map/WaypointHud`; `suld-plugin` `map/ScreenMarkers`;
* changed: `api` `map/Minimap` (pin colours, `Kind.MOB`), `style/PlayerSetting` (`SCREEN_MARKERS`); `plugin`
  `map/WaypointGuide` (the label moved to `ScreenMarkers`, `onChange`, 3D distance), `map/PinService` (`version`),
  `map/MinimapService` (pin version, mobs), `map/AtlasSky` (the «Чиглэл» flag, `/map markers`), `SuldPlugin`
  (wiring);
* tests: new `ScreenProjectionTest` (9), `WaypointHudTest` (19), `ScreenMarkersTest` (8); +2 `MinimapTest`, +1
  `MinimapStabilityTest`;
* docs: `docs/WORLD_MAP.md` (screen markers, minimap marks, sync), `docs/UI_HUB.md`, `docs/STATUS.md`, this audit;
  bot `tools/loadtest/bots/mapcbot.js` (the card in view and at the edge).

| Step | Command (main working tree) | Result |
|---|---|---|
| 1 | `.\gradlew.bat :suld-api:test --tests "mn.suld.api.map.*" --tests "mn.suld.api.style.*" :suld-plugin:test --tests "mn.suld.plugin.map.*" --no-daemon` | first run: 2 failed. (a) The hysteresis test had the wrong sign: facing south, −x is on the right. (b) A real defect: `ScreenProjection.edge` scaled a card onto the top bound at 0.7000000001, just outside the edge area, so the next step skipped it. The result is now clamped onto the bound. Re-run: exit 0 |
| 2 | `.\gradlew.bat test --rerun simTest --rerun --no-daemon` | **exit 0**, BUILD SUCCESSFUL. JUnit XML: suld-api 662 / 0 failures / 0 errors / 0 skipped (632 + 30); suld-plugin 250 / 0 / 0 / 0 (241 + 9); simTest 13 / 0 / 0 / 0. The flaky `JdbcMapPinRepositoryIT.theLimitHoldsWhenAddsRace` passed in this run |
| 3 | `.\gradlew.bat test simTest --no-daemon` | exit 0, BUILD SUCCESSFUL (all up to date after step 2) |
| 4 | `$env:PYTHONUTF8 = "1"; py tools\validation\run_all.py` | exit 0, "ALL VALIDATIONS PASSED" |
| 5 | `py -X utf8 -I tools\plugins\test_fetch_plugins.py` | exit 0, "Ran 6 tests … OK" |
| 6 | `git diff --check` (new files included with `git add -N`) | exit 0, clean |

**Not verified:** a real 1.21.11 client (the card on the place's pixel, edge cards and their arrows while turning,
the atlas flag, red mob dots and coloured pins on the minimap, `/map markers`) and the bot `mapcbot.js` (needs the
dev server; `node` is not installed here, so not even syntax-checked). Client steps: audit §6, "Still open".
