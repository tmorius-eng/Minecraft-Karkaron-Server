# SÜLD — Status: the single source of truth

> One table per system: what is **implemented**, what the **bots** verified, what a **human with a real client**
> verified, and what is only **planned**. Update this file in the same commit as the feature. The counts below are
> read from the content files (`suld-plugin/src/main/resources/content/*.json`), not typed by hand; when a doc or an
> in-game line disagrees with this file, this file wins and the other is a bug (audit 2026-10-11 DOC-001).

**Last updated:** 2026-10-11 · **Commit base:** `main` after `56865ba` · **Schema:** V19

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
| Mobs | 32 | `content/mobs.json` |
| Regions | 9 (Kharkhorum + 4 inner + 4 outer) | `content/world.json` |
| Named areas / ovoo | 40 / 40 | `content/world.json`, `OvooService` |
| Historic sites | 16 | `content/sites.json` |
| World events | 1 (Чонын Довтолгоо); campaigns can unlock more into the rotation | `content/events.json` |
| Хуралдай campaigns | 4 | `content/campaigns.json` |
| Relics | 2 | `content/events.json` |
| Classes / spells / ultimates | 5 / 25 / 15 | `PlayerClass`, `Spell`, `Ultimate` |
| Skill-tree nodes | 111 per class + 5 universal | `skills/*.json` |
| Class weapon tiers | 6 (levels 1/12/24/36/48/60) | `WeaponTiers` |
| Level cap | 60; Ascension I–III | `Balance`, `Ascension` |
| Level curve | 242·L^2.2 (live), legacy 100·L^1.75 rescaled on load | `config.yml` |
| Death loss | 5 % of the level's progress, 25 % of droppable stacks; lock 5 min → 24 h, ≤ 1 min up to level 5 | `config.yml death:` |

## Systems

| System | Impl | Bots | Client | Notes / open items |
|---|---|---|---|---|
| Auth (Microsoft UUID only, fail closed) | ✅ | ✅ | 👤 G12 | `docs/AUTHENTICATION.md` |
| Resource pack gate (ONB-001) | ✅ | ✅ | 👤 G7 | `PackGate` |
| Classes, spells, class resources, lock-on, dodge | ✅ | ✅ | 👤 | 25 spells; cooldowns per slot |
| Skill tree, builds, respec | ✅ | ✅ (312/313 QA) | 👤 | SP-3 orb cooldown ⏳ |
| Story 42 chapters, tracker | ✅ | 🤖 first chapters | ⏳ | PROG-004/005 ⏳ |
| Dungeon ladder 10, halls, boss brains | ✅ | 🤖 | ⏳ | DG-7 enrage wipe ⏳ |
| Hardcore death, lock, wound | ✅ | ✅ | ⏳ | |
| Relics (2), seize rule | ✅ | ✅ | ⏳ | seize moves to consented fights only (PvP policy, this slice) |
| Clans, parties, chat channels | ✅ | ✅ | ⏳ | party finder: this slice |
| World events | ✅ (1 event) | ✅ | ⏳ | campaign system: this slice |
| Daily reward / tasks, leaderboards, trade, horses, smith | ✅ | 🤖 | ⏳ | streak resets to day 1 (B.8 ⏳) |
| HUD, menus (HD painted) | ✅ | — | 👤 G7 | `docs/HUD_MANUAL_QA.md` rows not recorded |
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
| Bot harness in the repo | ✅ | — | — | `tools/loadtest/` (B.4 done) |
| PvE-only + consented PvP (`/duel`, arenas) | ✅ | ✅ a hit without consent does no damage | ⏳ | owner decision 2026-10-11; `GAME_DESIGN.md` «PvP policy» |
| Их Хуралдай (weekly vote, V18) | ✅ | ✅ `khuraldai-smoke.js` 22/22 on PostgreSQL | ⏳ | `docs/KHURALDAI.md` |
| Campaigns: goals, tiers, flush, resolve, world effects (V18) | ✅ | ✅ deliver → success → reward → effect → chronicle; restart restores; flag re-applies | ⏳ | 4 campaigns in `content/campaigns.json`; kill/clear hooks not bot-exercised yet; palace notice board and Hall of Fame displays are v1.1 |
| Баатрын Хроник (V19), `/chronicle`, «Миний түүх» | ✅ | ✅ | ⏳ | |
| Party finder (`/party find|join|recruit|role`) | ✅ | ✅ | ⏳ | chat board; a GUI is v1.1 |

## Not built (and said so)

Achievements, battle pass, emotes/pets/mount skins/UI themes, resurrection quests, soul shards, instant revive
(design only, stays unbuilt), payment provider, web dashboard, Ascension IV–X, mastery tracks other than armour,
heroic dungeon mode, world bosses, player housing.
