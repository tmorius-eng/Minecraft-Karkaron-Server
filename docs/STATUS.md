# SÜLD — Status: the single source of truth

> One table per system: what is **implemented**, what the **bots** verified, what a **human with a real client**
> verified, and what is only **planned**. Update this file in the same commit as the feature. The counts below are
> read from the content files (`suld-plugin/src/main/resources/content/*.json`), not typed by hand; when a doc or an
> in-game line disagrees with this file, this file wins and the other is a bug (audit 2026-10-11 DOC-001).

**Last updated:** 2026-10-13 · **Commit base:** `main` after `f1b7ce6` · **Schema:** V22 · **Bot sweep:**
`tools/loadtest/regress.sh` — 2026-10-13 on `f1b7ce6`: 25/25 runs, 203 checks green; 16 PostgreSQL ITs green · **Owner's one-sitting client test:** `docs/qa/LAPTOP_TEST_2026-10.md`

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
| Death loss | 5 % of the level's progress, 25 % of droppable stacks; lock 5 min → 30 min (cap since config v7), ≤ 1 min up to level 5; `/trial` «Тэнгэрийн сорил» ends it early | `config.yml death:` |

## Systems

| System | Impl | Bots | Client | Notes / open items |
|---|---|---|---|---|
| Auth (Microsoft UUID only, fail closed) | ✅ | ✅ | 👤 G12 | `docs/AUTHENTICATION.md` |
| Resource pack gate (ONB-001) | ✅ | ✅ | 👤 G7 | `PackGate` |
| Classes, spells, class resources, lock-on, dodge | ✅ | ✅ | 👤 | 25 spells; cooldowns per slot |
| Skill tree, builds, respec | ✅ | ✅ (312/313 QA) | 👤 | SP-3 ✅ the Orb's free respec waits a day since the last respec |
| Story 42 chapters, tracker | ✅ | 🤖 first chapters | ⏳ | PROG-004 ✅ gate by chapter id; PROG-005 ✅ chapter EXP never below the previous chapter |
| Dungeon ladder 10, halls, boss brains | ✅ | 🤖 | ⏳ | DG-7 ✅ enrage + 60 s wipes the run; PROG-004 ✅ the loader refuses a story that softlocks a gate |
| Hardcore death, lock, wound | ✅ | ✅ | ⏳ | |
| Relics (2), seize rule | ✅ | ✅ | ⏳ | seize moves to consented fights only (PvP policy, this slice) |
| Clans, parties, chat channels | ✅ | ✅ | ⏳ | party finder: this slice |
| World events | ✅ (1 event; no raid while a world boss stands) | ✅ | ⏳ | «Чонын Их Аян» success: guards warn 2 min before a raid |
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
| «Тэнгэрийн сорил» (`/trial`), lock cap 30 min (config v7) | ✅ | ✅ `trialbot.js` 6/6 | ⏳ | `docs/DEATH_AND_RECOVERY.md`; time to 60 now ~146 h simulated (was ~200), accepted by the owner 2026-10-12 |
| /menu sky window (`MenuSky`, every hub page; chest window only in combat/runs/mid-air) | ✅ | ✅ `skymenubot.js` 8/8 | ⏳ | `docs/UI_HUB.md` §0; owner to check look and text size on the real client |
| Class change + «Ангийн Академи» (V22 class_mastery: each class keeps its tree and gear) | ✅ | ✅ `classchangebot.js` 10/10, `classreconnectbot.js` 6/6 | ⏳ | `docs/CLASS_CHANGE.md` |
| Class trial «Турших» (5 min arena, dummies, nothing kept) + SÜLD bows with Infinity | ✅ | ✅ `classtrialbot.js` 7/7 | ⏳ | `docs/CLASS_CHANGE.md` §Сорилт, §Нум |
| «Ангийн Мастер» (class armour level = per-class mastery 1–60, milestones 20/40/60, «Таван Ангийн Эзэн») | ✅ | ✅ `masterybot.js` 3/3 | ⏳ | `docs/CLASS_CHANGE.md` §Ангийн Мастер |
| «Тэнгэрийн сорил» fought with the class (soul hits/shoots/casts at its wolves, wolf HP = 5 × attack) | ✅ | ✅ `trialclassbot.js` 4/4 × 5 classes | ⏳ | `docs/DEATH_AND_RECOVERY.md` |

## Not built (and said so)

Achievements, battle pass, emotes/pets/mount skins/UI themes, resurrection quests, soul shards, instant revive
(design only, stays unbuilt), payment provider, web dashboard, Ascension IV–X, mastery tracks other than armour,
heroic dungeon mode, player housing.
