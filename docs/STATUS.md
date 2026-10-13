# SÜLD — Status: the single source of truth

> One table per system: what is **implemented**, what the **bots** verified, what a **human with a real client**
> verified, and what is only **planned**. Update this file in the same commit as the feature. The counts below are
> read from the content files (`suld-plugin/src/main/resources/content/*.json`), not typed by hand; when a doc or an
> in-game line disagrees with this file, this file wins and the other is a bug (audit 2026-10-11 DOC-001).

**Last updated:** 2026-10-13 · **Commit base:** `main` after `2de740e` · **Schema:** V26 · **Bot sweep:**
`DEV_CAMPAIGNS=1 tools/loadtest/regress.sh` (37 runs) — 2026-10-13 on the world-map C build: **35/37**; the two red
runs were a real yard bug and a stale test, both fixed and rerun green on the final build (not a second full sweep):
`trainingbot.js` 26/26 (a wool awning over the Баатар station had floated its weapon 6 blocks up — the yard now
stands on its median floor), `dungeon-clear.js` 5/5 (the console `/chronicle` lists the latest 60 entries and repeated
sweeps' campaign results had pushed the first clear out; the bot reads `/chronicle dungeon`, which the console now
honours); on the final build also `mapcbot.js` 13/13, `atlasbot.js` 10/10, `healthbot.js` 3/3. Before: 34/34, 315
checks, on the heroic-dungeons build (with `heroicbot.js` 19/19). On the death-reform build before it: 31/33; hubbot (one chat line of unknown
source in the `/profile` window, 2 standalone reruns 31/31; the bot now prints the line it saw) and campaign-effects
(1 of 3 wolves killed: the kill was counted, the bot sat in a pit under the others; it now brings a wolf beyond melee
reach to itself). A sweep with a missing log directory had printed «ALL GREEN» without running a bot: `regress.sh`
now fails that. Unit api 537 + plugin 163 + sim 13 (world map C build); PostgreSQL 16 ITs 40/40 (19 files), and in CI on every push ·
**Owner's one-sitting client test:** `docs/qa/LAPTOP_TEST_2026-10.md`

## Legend

| Column | Meaning |
|---|---|
| Impl | the code exists and is wired in (`./gradlew build` green) |
| Bots | verified live on Paper 1.21.11 with Mineflayer bots (`tools/loadtest/`, `docs/audits/2026-10-11/11-*`) |
| Client | verified by a human in a real Minecraft Java client |
| Status values | ✅ done · 🤖 bots only · 👤 owner-attested (verbal, not recorded in this repo) · ⏳ open · — not applicable |

## Owner attestations (2026-10-11)

The owner stated in conversation that these were done on the real host with no errors. They are **not** recorded by a
file in this repository, so they are marked 👤 rather than ✅ until a `docs/qa/*.md` row records them:

| Gate (audit `10-fix-roadmap.md`) | Attested |
|---|---|
| G7 Real-client first join (pack prompt, class menu, HUD at GUI scales 2–4) | 👤 |
| G9 Culture and language: native reviewer sign-off on the P1 renames | 👤 |
| G11 Backup / restore on the VPS with an off-host copy | 👤 |
| G12 Online-mode Microsoft login on the deployed host | 👤 |

## Content counts (the numbers every doc must use)

| Thing | Count | Source |
|---|---|---|
| Story chapters | 42 (Act I 1–18, Act II 19–42) | `content/quests.json` |
| Dungeons (ladder rungs) | 10 | `content/dungeons.json`, `DungeonLadder` |
| Bosses | 10 (one per dungeon) | `content/mobs.json` `role: boss` + `mob.khasar` |
| Mobs | 34 (32 + 2 world bosses) | `content/mobs.json` |
| Regions | 9 (Kharkhorum + 4 inner + 4 outer) | `content/world.json` |
| Named areas / ovoo | 40 / 40 | `content/world.json`, `OvooService` |
| Historic sites | 16 | `content/sites.json` |
| World events | 1 (Чонын Довтолгоо); campaigns can unlock more into the rotation | `content/events.json` |
| Хуралдай campaigns | 7 (4 open + the 3 chapters of «Эхний аян») | `content/campaigns.json` |
| World bosses | 2 (Хөвсгөлийн Их Баавгай L20, Алтайн Их Аварга L50) | `content/events.json` `worldBosses` |
| Recipes | 43 (levels 1–60) | `items/recipes.json` |
| Relics | 2 | `content/events.json` |
| Classes / spells / ultimates | 5 / 25 / 15 | `PlayerClass`, `Spell`, `Ultimate` |
| Skill-tree nodes | 111 per class + 5 universal | `skills/*.json` |
| Class weapon tiers | 6 (levels 1/12/24/36/48/60) | `WeaponTiers` |
| Level cap | 60; Ascension I–III | `Balance`, `Ascension` |
| Level curve | 242·L^2.2 (live), legacy 100·L^1.75 rescaled on load | `config.yml` |
| Death loss | levels 1–10 (no Ascension): nothing; from 11: 10 % of the level's progress (the beam gives 70 % back), 25 % of droppable stacks, one wound step; no timer since config v8: a soul comes back by `/trial` or the 90-s ovoo ritual | `config.yml death:` |

## Systems

| System | Impl | Bots | Client | Notes / open items |
|---|---|---|---|---|
| Auth (Microsoft UUID only, fail closed) | ✅ | ✅ | 👤 G12 | `docs/AUTHENTICATION.md` |
| Resource pack gate (ONB-001) | ✅ | ✅ | 👤 G7 | `PackGate` |
| Classes, spells, class resources, lock-on, dodge | ✅ | ✅ | 👤 | 25 spells; cooldowns per slot |
| Skill tree, builds, respec | ✅ | ✅ (312/313 QA) | 👤 | SP-3 ✅ the Orb's free respec waits a day since the last respec |
| Story 42 chapters, tracker | ✅ | 🤖 first chapters | ⏳ | PROG-004 ✅ gate by chapter id; PROG-005 ✅ chapter EXP never below the previous chapter |
| Dungeon ladder 10, halls, boss brains | ✅ | 🤖 | ⏳ | DG-7 ✅ enrage + 60 s wipes the run; PROG-004 ✅ the loader refuses a story that softlocks a gate |
| Hardcore death, soul state, wound | ✅ | ✅ | ⏳ | the reform of 2026-10-13: see the row «Death reform» |
| Relics (2), seize rule | ✅ | ✅ | ⏳ | seize moves to consented fights only (PvP policy, this slice) |
| Clans, parties, chat channels | ✅ | ✅ | ⏳ | party finder: this slice |
| World events | ✅ (1 event; no raid while a world boss stands) | ✅ | ⏳ | «Чонын Их Аян» success: guards warn 2 min before a raid; raid wolves spawn on the ground, not on leaves (2026-10-13; probe: 4 raid wolves at the forest spot, 0 on leaves) |
| Daily reward / tasks, leaderboards, trade, horses, smith | ✅ | 🤖 | ⏳ | B.8 ✅ a missed day costs one streak step, never a reset; 43 recipes + order board (`docs/CRAFTING.md`) |
| HUD, menus (HD painted) | ✅ | — | 👤 G7 | `docs/HUD_MANUAL_QA.md` rows not recorded |
| «Командын Өргөө» HD window system (`/menu` hub, profile, rankup, shop ×2, travel, settings, help pages; `suld:hd` font; default tooltip frame; themed buttons; slot glow) | ✅ | ✅ `hubbot.js` 30/30 | ⏳ owner: GUI scale 2/3/4 | `docs/UI_HUB.md`; the picture's 8 commands all work; `/travel` trips only from a relay (GAME-001) |
| Chat walls → windows (`/help <page>` `/rules` `/commands` links, `/clan info|top`, `/party info`, `/relic list|info`, `/loot`, `/classgear`, `/khuraldai|duel|dungeon|suldevent status`, `/item inspect|compare`, `/profile|exp|class|quest info`) | ✅ | 🤖 no chat after the command | ⏳ | `ui/Pages` dialogs; console keeps the text |
| Per-player settings (V21 `settings_off`: tracker, sidebar, tips, toasts, menu sounds, chat heads, invites, duels) | ✅ | ✅ toggle round-trip | ⏳ | `PlayerSetting`; `/settings` |
| M3 performance: rig view range by mob kind, tracking ranges, passive spawn caps | ✅ | ◐ leg A measured (25 bots: TPS 17.7, p95 85 ms); leg B pending | — | `docs/perf/M3_2026-10-12.md`; production gate unchanged (`docs/perf/FINAL_REPORT_50.md`) |
| Kharkhorum slice 1 (ceremonial axis), hand-built spawn | ✅ | ✅ | 👤 | auto-repair off; palace interior, clan district, relic hall ⏳ |
| Historic sites 16, ovoo 40, discovery EXP | ✅ | ✅ | ⏳ | |
| Progress marks mirror (V17) | ✅ | ⏳ | — | clears / sites / ovoo → `suld_player_marks`; backfill on login |
| Analytics (DB sink, sessions, funnel events ANA-001, `tools/analytics/funnel.py`) | ✅ | ✅ 17 event types seen on PostgreSQL in one bot run | — | web dashboard ⏳ (the Markdown tool is v0) |
| Backups (flush → dump → off-host), restore drill | ✅ | ✅ dev container | 👤 G11 | |
| Credit store (idempotent grants) | ✅ | ✅ | — | payment provider not built; EULA review ⏳ |
| CI (`.github/workflows/ci.yml`: build + tests, PostgreSQL 16 ITs, validators, plugin-lock test) | ✅ | — | — | TEST-001 / G2 **PASS**: first GitHub run green on `d2b6a34`; bots stay a local step |
| Class pick confirm page «Батлах / Буцах» (ONB-006) | ✅ | ✅ `khuraldai-smoke.js` 22/22 picks through it | ⏳ | bots click the class, then slot 11 |
| In-game counts from content (`ContentCounts`, DOC-001/ONB-010) | ✅ | — | ⏳ | `ContentCountsTest` blocks literal counts |
| Security hygiene: no shipped owner (SEC-002), owner pins revocable (SEC-001), default group 1 home + no `/tpa` (GAME-001), pinned plugins (OPS-002) | ✅ | — | — | `PermissionFileTest`, `tools/plugins/test_fetch_plugins.py` |
| Daily streak: a missed day costs one step, never a reset (B.8) | ✅ | — | — | `DailyRewardTest` |
| One level curve for levelling and rewards (PROG-002); chapter EXP never collapses in Act II (PROG-005) | ✅ | — | — | `BalanceCurveTest`, `ContentDataTest`; simulator golden tests pass |
| Wolf raid gear is a 50 % roll, not a guaranteed top-band item (PROG-006) | ✅ | — | — | `ItemCatalogLoaderTest` |
| Same-inventory duplicate caught by the periodic sweep (DU-1) | ✅ | — | — | |
| Sidebar without credits (UX-1); coins read «зоос», ₮ only for real money (UX-4) | ✅ | — | ⏳ | `CoinSignTest` |
| `/report <text>` → audit log + online staff | ✅ | — | ⏳ | one a minute |
| Trade pays before goods move; coin-conserving revert; ordered mailbox (PERS-004/005) | ✅ | — | — | `ProfileTransferTest` |
| Admin checks in code (SEC-004); classless kills pay nothing (GAME-002); failed start frees the pool (OPS-005) | ✅ | — | — | |
| Pack server timeouts + elastic pool (OPS-004); required-pack decline explained in Mongolian (ONB-008) | ✅ | ✅ a bot that declines is told why | ⏳ | |
| Tracker points at the region holding a dungeon's gate (DATA-002) | ✅ | — | ⏳ | `ContentDataTest` |
| Daily tasks marked «Аянд тусална» when they feed the week's KILL_MOBS campaign | ✅ | ✅ marked during a wolf campaign | ⏳ | plan §3.2 day → week loop |
| An orphaned campaign row (content removed) is cancelled on start, so the week is never stuck | ✅ | ✅ | — | |
| Reward guards stored before the reward: daily, tasks, level rewards (PERS-002) | ✅ | — | — | `StyleService.guardThen` |
| Clan saves never let an older snapshot overwrite a newer row (PERS-006) | ✅ | ✅ PostgreSQL IT | — | `JdbcClanRepositoryIT` |
| Leaderboard + analytics purge indexes (OPS-007, V20) | ✅ | ✅ 31 JDBC ITs on PostgreSQL 16 | — | |
| Shop sell labels no longer quote fixed prices (EC-4); PlayerClass javadoc tells the truth (DATA-001) | ✅ | — | ⏳ | |
| Bot harness in the repo | ✅ | — | — | `tools/loadtest/` (B.4 done) |
| PvE-only + consented PvP (`/duel`, arenas) | ✅ | ✅ a hit without consent does no damage | ⏳ | owner decision 2026-10-11; `GAME_DESIGN.md` «PvP policy»; a lethal duel hit, or the burn a duel hit started, is a defeat at ¼ health, not a hardcore death; outside a duel the same burn kills (`duel-defeat.js` 7/7) |
| Их Хуралдай (weekly vote, V18) | ✅ | ✅ `khuraldai-smoke.js` 22/22 on PostgreSQL | ⏳ | `docs/KHURALDAI.md` |
| Campaigns: goals, tiers, flush, resolve, world effects (V18) | ✅ | ✅ deliver → success → reward → effect → chronicle; restart restores; flag re-applies; `campaign-effects.js` 6/6: kills count, PROP and NPC effects placed | ⏳ | 7 campaigns in `content/campaigns.json` (4 open + the «Эхний аян» arc); `dungeon-clear.js` 5/5: a clear counts and is chronicled |
| Integration review 2026-10-12 (12 findings) | ✅ | ✅ duel paths live | — | mailbox partial stacks; trade goods in flight at shutdown; reward guard waits for a loaded style and an in-flight write, undoes one claim; rotation honours the arc; a missed ballot still decides; interlude force survives restarts; resolve waits for the last flush and retries; «Чонын Их Аян» success = guards warn 2 min before a raid; a soul's site visit waits |
| Дархан recipe book (43 recipes, 9-level bands, smith window) | ✅ | ✅ `craft.js` 6/6 | ⏳ | `docs/CRAFTING.md`; crafted level held to the recipe band (was the crafter's level) |
| Дархан order board (`/order`: fee in escrow, fill, withdraw, 7-day expiry) | ✅ | ✅ `craft-orders.js` 9/9 | ⏳ | `docs/CRAFTING.md` |
| World bosses (weekly hours, crowd-sized, everyone at the fight rewarded) | ✅ | ✅ `world-boss.js` 10/10 | ⏳ | `docs/WORLD_BOSSES.md`; Хроник first/fastest kill |
| Plaza boards: Хуралдай notice board + Hall of Fame | ✅ | ✅ both stand | ⏳ | `PlazaBoards`, text at GUI scale to check by the owner |
| Баатрын Хроник (V19), `/chronicle`, «Миний түүх» | ✅ | ✅ | ⏳ | |
| Party finder (`/party find|join|recruit|role`) | ✅ | ✅ | ⏳ | chat board + a window with click-to-join cards |
| External audit 2026-10-12 (FIND-01…10) | ✅ | ✅ | — | `docs/audits/2026-10-12-external/RESPONSE.md`: H2 shaded (boots offline), mailbox stuck-file guard, scoped spell flag, `player.save_data` probe, Java-only deploy |
| «Тэнгэрийн сорил» (`/trial`), no wait after a loss since 2026-10-13, a win leaves a «Сүнсний Хэлтэрхий» | ✅ | ✅ `trialbot.js` 9/9 | ⏳ | `docs/DEATH_AND_RECOVERY.md`; time to 60 ~147 h simulated (owner's band 130–170 h) |
| /menu sky window (`MenuSky`, every hub page; chest window only in combat/runs/mid-air) | ✅ | ✅ `skymenubot.js` 8/8 | ⏳ | `docs/UI_HUB.md` §0; owner to check look and text size on the real client |
| Class change + «Ангийн Академи» (V22 class_mastery: each class keeps its tree and gear) | ✅ | ✅ `classchangebot.js` 10/10, `classreconnectbot.js` 6/6 | ⏳ | `docs/CLASS_CHANGE.md` |
| Class trial «Турших» (5 min arena, dummies, nothing kept) + SÜLD bows with Infinity | ✅ | ✅ `classtrialbot.js` 7/7 | ⏳ | `docs/CLASS_CHANGE.md` §Сорилт, §Нум; since 2026-10-13 the dummies are SÜLD targets: spells, crits, passives and named numbers reach them (they used to be skipped, and a big hit was zeroed) |
| «Дайчдын Дасгалын Талбай» (`/dasgal`, class window «⚔ Туршиж үзэх»): any class at level 60, Зэрэг III, build A/B, T6 kit on the training Wardens (∞, no AI, ×1.6, never despawn), named numbers, spell/passive explanations, 10 s DPS sidebar; a stand-in profile nothing writes | ✅ | ✅ `trainingbot.js` 24/24, `trainingcrash.js` 6/6 (SIGKILL during training: bag, spot, real profile back), `trainingpay.js` 8/8 (a campaign resolved during training waits, then pays the real character on leaving: database coins 20 → 358 = +338 paid, tier claimed; a trainee's swings hurt no mob) | ⏳ | `docs/TRAINING_GROUNDS.md` (§What comes due while training: campaign tier, order refund and item, mailbox, world boss/event, party, `/pay`, staff commands, discoveries); HD Warden texture; staff `/dasgal yard build` (open ground only, undo log) |
| «Ангийн Мастер» (class armour level = per-class mastery 1–60, milestones 20/40/60, «Таван Ангийн Эзэн») | ✅ | ✅ `masterybot.js` 3/3 | ⏳ | `docs/CLASS_CHANGE.md` §Ангийн Мастер |
| External audit 2026-10-13 (`docs/audits/2026-10-13-external/RESPONSE.md`): class window refuses drags; death EXP saved with the lock; dungeon clears/sites restored from the DB into a file that lost them; boss HP by the party that entered (≤ recommended) | ✅ | ✅ dbfix check (EXP in SQL 4 s after death; DB clear restored), featbot 16/16 (dungeon clear) | ⏳ | `ClassMenuDragTest`, `PlayerMarksMergeTest`, `BalanceRulesTest` |
| Self-audit 2026-10-13 fixes: a lock that ends mid-trial stops the trial (BUG-DEATH-01); the class confirm page no longer calls the pick permanent (BUG-ONB-01); tutorial mask before its reward; one writer per state file; no idle quiver saves; ITs refuse a non-test DB | ✅ | ✅ `trial-lockend.js` 5/5, `permbot.js` 20/20 (staff commands refuse a player) | ⏳ | unit: `DeathTrialTest`, `ClassConfirmNoteTest`, `AtomicYamlOrderTest`, `TestDbGuardTest` |
| «Тэнгэрийн сорил» fought with the class (soul hits/shoots/casts at its wolves, wolf HP = 5 × attack) | ✅ | ✅ `trialclassbot.js` 4/4 × 5 classes | ⏳ | `docs/DEATH_AND_RECOVERY.md`; since 2026-10-13 the wolves spawn on the ground, never on a tree's crown (a forest's leaves had put them out of reach of the soul and its arrows); the trial bots count only spirit wolves (a natural pack lives at the test spot) |
| Heroic dungeons «Тэнгэрийн Гэсгээлт Агуй» (directive 2026-10-13 Phase 3, V24 `suld_heroic_keys` / `suld_heroic_runs`): keys +1…+20 at level 60, hall mobs at Lv 60 (+10 % health, +8 % damage a key), a clock with 15 s a death, four steppe affixes rotating weekly, «Эртний Хас Чулуу» for a timed key, spent at the smith to reroll an endgame item's affixes; `/dungeon heroic`, the keys window, Shift + gate | ✅ | ✅ `heroicbot.js` 19/19 | ⏳ | `docs/HEROIC_DUNGEONS.md` (concurrency audit included); api `HeroicLadderTest`, `HeroicKeyTest`, `AffixRerollTest`, `MobDefinitionLevelTest`; `JdbcHeroicRepositoryIT` (H2, PostgreSQL 16); the halls' lanterns do not warm against «Цагаан Зуд» (the bot found every spot warm before) |
| Weapon mastery 1–50 (directive Phase 4, V25 `suld_weapon_mastery`): five tracks levelled by damage dealt to SÜLD mobs with that kind of weapon (points = damage ÷ the player's basic hit; stand-ins and dummies earn nothing), +0.05 % hit a level (2.5 % at 50), titles, `/mastery`; additive writes every 30 s, at quit and at shutdown | ✅ | ⏳ | ⏳ | api `WeaponMasteryTest`; `JdbcWeaponMasteryIT` (H2, PostgreSQL 16); the doc `docs/MASTERY_AND_PROFESSIONS.md` is still to write; professions not built |
| Тэнгэрийн Зэрэг IV–X (owner: small power + prestige): no оноо after III; each rank needs Тэнгэрийн Од, a timed heroic key and a weapon mastery level, then the rite fee (25 000 × the new rank); names get a ✧ rank mark | ⏳ | ⏳ | ⏳ | api `AscensionHonorTest`; the shrine menu shows the gates and the rite; **the IV–VI perks (PvP guard, fortune, lightning), the VII–IX aura and the X monument are not wired yet** |
| World map «Сүлдийн Атлас» (`docs/WORLD_MAP.md`): A — the real generated world read like a vanilla map (natural biome colours, no prediction), stored per region, the scan of the world on disk; B — `/map`, the atlas on a wall of 25 real maps (pan, zoom to the whole world, place filters, tooltips); C — pins (V26 `suld_map_pins`, 30 a player), the waypoint (bar + see-through label), the latest death, the corner minimap (the pack's `rendertype_text` shader) | ✅ | ✅ `atlasbot.js` 10/10, `mapcbot.js` 13/13 (incl. an arrow through a minimap frame flies on) | ⏳ | **seen on a real 1.21.11 client** (vanilla client run headless here: Xvfb + Mesa, scripted input; screenshots sent to the owner, 2026-10-13) — that run fixed the atlas's parchment hiding all 25 maps (an invisible frame draws its map 1/16 deeper), the HUD and boss bars over the title and cartouche (camera 6 blocks back = 1 map pixel per screen pixel at 1080p; HUD and waypoint step aside), rain across the map (clear while open), a label too small to read; the minimap's frames moved off the line of fire (three, above the look; 72/72 grabs drawn through fast turns) and a projectile flies through them; a human check on the owner's PC is still owed; api `MapTerrainTest`, `NaturalPaletteTest`, `AtlasViewTest`, `MapPinTest`, `MinimapTest`; `ChunkColorStoreTest`, `MapColorTableTest`, `JdbcMapPinRepositoryIT` (PostgreSQL 16) |
| Health across a relog and a restart (found on the real client, 2026-10-13): loading the player file clamped the health to the class base (the gear's and the tree's health are transient modifiers the game does not save: 61/61 came back 43), and the skill tree's `stop()` set every online player's maximum to 20 before the server saved them (a restart left everyone at 20 HP); the real health is now written to the player file at quit and at stop, and what loading took away is given back 10 ticks after the join | ✅ | ✅ `healthbot.js` 3/3 (failed 1/3 on the build before: 60.4 → 43 → 20.5) | ⏳ | `SkillTreeService.rememberHealth/restoreHealth` |
| Training yard on its own floor (found by the sweep, 2026-10-13): a station stood on the highest block of its spot, so a wool awning 5 blocks over the Баатар station floated that weapon and its click box 6 blocks up; the yard's level is now the median ground of its centre, stations and group, and a spot more than 3 blocks off it stands on the floor nearest that level | ✅ | ✅ `trainingbot.js` 26/26 (all five weapons at +1.5) | ⏳ | `TrainingYard.level/ground` |
| Death reform (directive 2026-10-13, config v8, V23 `suld_soul_beacons`): levels 1–10 lose nothing; a soul without a timer rises by the nearest ovoo; «Сүнсний Гэрэлт» 10 min, 70 % of the lost EXP back once (compare-and-set); «Овооны цацал» (3 sunwise rounds + 90 s); «Сүнсний Хэлтэрхий» heals 60 active minutes | ✅ | ✅ `soulreform.js` 19/19, `trialbot.js` 9/9, `trial-lockend.js` 5/5 | ⏳ | `docs/DEATH_AND_RECOVERY.md` (concurrency audit included); api `DeathReformTest`, `SoulBeaconTest`, `OvooRitualTest`, `SoulShardTest`; `JdbcDeathRepositoryIT` 24 concurrent claims → 1 (H2, PostgreSQL 16); full simulation: live L1–L10 pass (147 h to 60); the reference model's C9 is 88 % (was 90 %) with the same death design; loot loss and wear unchanged (the owner may turn them off) |

## Not built (and said so)

Achievements, battle pass, emotes/pets/mount skins/UI themes, resurrection quests, instant revive
(design only, stays unbuilt), payment provider, web dashboard, the Ascension IV–X perks, professions, player housing.
