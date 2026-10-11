# 10: Fix roadmap

- **Scope:** an ordered remediation plan.
- **Status:** **None of these fixes is implemented by this audit.** Phase A was read-only. The owner reviews this list and then starts a separate, controlled fix phase.
- **Format:** each row gives root cause → exact change, dependencies, risk, effort (S ≤ ½ day, M ≤ 3 days, L > 3 days), verification and acceptance criteria.

## P0: none found

Nothing qualifies as exploitable duplication or data loss, a security failure, a severe crash, or a blocker that makes a safe launch impossible on its own.

**Watch item:** ONB-001 would become P0-like in effect if a client test confirms it. If it does, every new player loses the pack. It is therefore **step 1** below.

## P1: before any outside player (closed alpha entry)

Ordered by dependency and value.

| # | ID | Root cause → exact change | Depends on | Risk | Effort | Verification | Acceptance |
|---|---|---|---|---|---|---|---|
| 1 | ONB-001 | Pack and class-menu screens are sent in one tick → in `PlayerLifecycleListener`, defer `classSelectionGui().open` until `PlayerResourcePackStatusEvent` is terminal (LOADED / DECLINED / FAILED_*) or 15 s pass; show a «Дүрс багц ачаалж байна…» title meanwhile | – | Low | S | First **confirm** with one vanilla client (Prompt setting). Unit-test the gate (terminal status / timeout → open) | Three client joins (Prompt / Enabled / Disabled) each end with a recorded pack status and the class menu |
| 2 | GAME-005 | `applyBaseHealth` only clamps down → when the class is first chosen (`ClassSelectionGui.select`), call `p.setHealth(maxHp.getValue())` after the attributes are applied | – | Low | S | Bot: `/profile` HP == max right after the pick | 0/5 classes start below 100 % HP |
| 3 | PROG-001 | The level-1 "par" assumes 51 HP / 8 armour → add `CombatRules.newbieTaken(level)` (×0.5 at L1, linear to ×1.0 at L6) and apply it in `CombatListener` mob→player damage; or fit `MobScaling.parHp/parArmor` at L1–5 to a fresh character. Re-run `simulate -Pquick` | 2 | Medium (balance) | S | `MobScalingTest`; simulator C1–C8; first-fight bot QA (20 fights × 5 classes) | Normal L1–5 fight ≤ 35 % of the lowest base HP; < 5 % bot deaths |
| 4 | ONB-002 | No newbie floor on the lock curve → `death.lock.newbie-levels: 5`, `newbie-minutes: 1` in `DeathSettings`/`DeathLock`; fix the board text (`GuideBoards.java:82-83`) | – | Low | S | `DeathLockTest` | ≤ 60 s lock at L1–5; unchanged at L6+ |
| 5 | CULT-001 | Real sacred and historical names in fiction and store data → display renames only (keep IDs): boss «Хасар» → invented wolf name; dungeon «Хасарын Агуй» → «Чонын Агуй»; «Бурхан Халдуны Агуй» → invented Khentii cave (savdag freed, not killed); «Чингисийн Өв»/items → «Хаадын Өв»; remove «Их Хааны Ач»; make «Хар/Цагаан Сүлд», «Цагаан Сар» earn-only; remove «Наадамч». Add a content test with a name blocklist | native reviewer sign-off | Low | S | Content test; native read-through | No blocklisted real name in boss, dungeon, item or store names; reviewer sign-off recorded |
| 6 | HIST-002, LANG-001/002/003, HIST-007 | Text errors on the first-session path and one false citation → exact replacements in `06` (sites.json:23, PlayerClass.java:21 «Уур хилэн», TutorialProgress.java:16/19, quests.json:231); hide raw «(VERIFIED)» (`HistoricSiteService.java:289`) | 5 | Low | XS | Native read-through | Zero flagged strings remain |
| 7 | ONB-003/004/005/011 | Direction and tracker contradictions → (a) in `RegionSpawner`, outskirt picks use `min(localLevel, playerLevel+2)`; (b) `QuestTracker.line` treats `city().near(…,32)` as "in the city" → «Хотын хаалгаар гар ➜»; (c) conditional final tutorial banner from `DungeonService.v2Gate`; (d) `announceStart` for chapter 1 | – | Low | S | Pure spawner test; tracker unit test; bot replay of the first 5 min | No spawn > L4 within 450 blocks for an L1 player; no ✔ inside the walls |
| 8 | ONB-007 + ONB-006 | Five services each add a welcome; one-click class → one onboarding owner: no card while a class menu is open, no card on rejoin, one boss bar (merge tutorial into quest bar); add a «Батлах / Буцах» confirm holder | 1 | Low | M | Bot counts lines in the first 30 s; GUI test | ≤ 6 chat lines and 1 bar in the first 30 s; class set only after "Батлах" |
| 9 | OPS-001 | Backup order and location → new `/suld flush` (saves profiles, styles and clans, waits); `backup.sh`: flush → `save-all flush` + `save-off` → `pg_dump` → tar → `save-on`; optional `BACKUP_REMOTE` (restic/rclone); timed restore drill at V15, documented | – | Low | M | Scripted restore drill (PG container: V1–V15, dump, restore, compare counts) | Restore at the current schema documented with a time; backups exist off-host |
| 10 | PERF-001/002 | Unproven capacity; stalls → move the bot harness into `tools/loadtest/`; full pre-generation; re-run 10/25/50 on the target host with the bots elsewhere; capture spark and JFR during stalls; set the launch cap | host available | Low | M | Procedure in `05` | TPS ≥ 19.5, p95 ≤ 40 ms, p99 ≤ 50 ms, max ≤ 1 s for 2 h at the cap |
| 11 | ANA-001 | Funnel blind spots → emit `join_attempt`, `resource_pack_status`, `tutorial_step`, `quest_complete`, `active_5m`; guard `FIRST_BOSS` to the first clear; use `FIRST_DEATH` | – | Low | S | `JdbcAnalyticsIT` + a bot run checks rows | Every funnel step in `03` produces a row |
| 12 | TEST-001 | No CI → `.github/workflows/ci.yml`: `./gradlew build` + `python3 tools/validation/run_all.py` + PG ITs against a `postgres:16` service | – | Low | S | The workflow runs on a PR | Green CI required before merge |

## P1 before monetisation (only when a store is about to go live)

| # | ID | Change | Effort | Acceptance |
|---|---|---|---|---|
| 13 | SEC-003 | `credits give <uuid> <amount> <txid>`; V16 `suld_credit_grants(txid PK, player_uuid, amount, at, actor)` inserted in the same transaction as `addCredits`; `AuditEvent` per grant; UUID-only from the console | M | Replaying a store command is a no-op; every grant has an audit row |
| 14 | Monetisation review | The owner reads the current Minecraft Usage Guidelines and EULA (fetch failed here) for: credit-only cosmetics, the mount-speed rank never sold, instant revive never sold, Steve-like art; Mongolian rules for minors and payments | owner | Written decision recorded in `docs/PAYMENT_ARCHITECTURE.md` |

## P2: during the closed alpha

| ID | Change | Effort |
|---|---|---|
| SEC-001 | `OwnerService.isOwner`: honour a pin only while its name is still in `owners:`; prune stale pins | S |
| SEC-002 | Ship `owners: []`; remove `qeevr_` from the migration (`SuldPlugin.java:530`) and `suld.env.example`; `deploy.sh` writes the owners from `ADMIN_PLAYERS` (UUIDs) | S |
| PERS-001 | `PlayerDataSaves.now(p)` after `take(...)` / `consumeOne` and before `profiles().save` in `ClassArmor.upgrade` and the Orb reset (or route through `itemsForCoins`) | S |
| OPS-002 | `plugins.lock.json` (version id + sha512); install strictly from the lock; refuse missing hashes; `update.sh` skips plugins unless `--plugins` | M |
| ONB-008 | Fix the hint to `/suldpack`; decide `required` (fix `DEPLOYMENT.md:230`); if not required, give the menus a no-pack fallback | S |
| ONB-009 | Floodgate detection: text HUD, skippable lock-on step; or declare "Java only" and move Geyser to optional | M |
| PROG-002 | Pass the configured curve to `ContentLoader`, `DailyReward`, `DailyTasks` (`04` step 1) | S |
| PROG-003/004 | `dungeons.json` owns the ladder with `requiresChapter` ids; loader ordering check (`04` step 2) | M |
| PROG-005 | Chapter level = max(heuristic, previous chapter level) | S |
| GAME-001 | Remove `essentials.sethome.multiple` and `essentials.tpa*` from `permissions/luckperms.txt` default (or level-gate them) | S |
| DOC-001 | Apply the `09` §4 doc fixes (README, GAME_DESIGN, PROJECT_STATE, DEPLOYMENT, ROADMAP, the TipsService and MOTD counts) | S |
| LANG-004/007/008 | One spelling of «түмэн»; new rarity ladder; un-overload «Цол/Зэрэг/Түвшин» (`06` §2.2) | S |
| VIS-001/002/004/005/006 | Repaint the towers and banners in the welcome and main art; commission an original emblem; replace the cherry groves; paint over or replace the AI cards (drop Steve and the pumpkin) | M–L (art) |
| HIST-019/021 | Black General as a tragic spirit; rename the regions to match geography | S |
| UX-1/UX-4 | Hide credits from the sidebar; coin glyph or «зоос» instead of ₮ for in-game coins | S |

## P3: backlog

- **PERS-002 to PERS-006:** order the guard before the reward; reverse the item side on a failed trade; order the mailbox writes; the clan version check.
- **PERS-003:** autosave to 60 s, or throttle sharer saves.
- **OPS-003:** implement `/suld reload` or fix the header.
- **OPS-004:** pack-server timeouts and bind address.
- **OPS-005:** clean up the failed constructor.
- **OPS-006:** escalate repeat kicks to tempbans after calibration.
- **OPS-007:** V16 indexes.
- **THR-001:** a main-thread badge cache for the chat thread.
- **SEC-004:** in-code permission checks.
- **GAME-002/003/004:**
  - classless kill guard;
  - quit cleanup;
  - DU-1 same-inventory sweep;
  - SP-3 orb cooldown;
  - DG-7 enrage wipe;
  - EC-4 labels.
- **PROG-006:** a wolf-raid loot band.
- **DATA-001/002 and `04` steps 3–8:** `classes.json`, `spells.json`, `balance.yml`, `economy.json`, `tutorial.json`, message bundle.
- **PERF-003:** trace the falling-block entities.
- **Remaining Low HIST/LANG/VIS items** (`06`).

## Release gates

Static checks, in-game verification and real-player testing are kept apart.

| Gate | Required behaviour | Test | Current result | Blocker | Acceptance |
|---|---|---|---|---|---|
| G1 Build | Compiles; all tests pass | `./gradlew build` | **PASS** (493/0, 59 s) | – | Green in CI |
| G2 CI | Every change is built and tested automatically | GitHub Actions | **FAIL** (no CI) | TEST-001 | Required check on `main` |
| G3 Assets | Pack and registry valid, within budget | `tools/validation/run_all.py` | **PASS** | – | Green in CI |
| G4 PostgreSQL | Repositories behave on real PostgreSQL | `Jdbc*IT` with `SULD_TEST_PG_URL` | **PASS** (19/0 on PG 16.15) | – | Runs in CI |
| G5 Persistence on crash | No duplication; bounded loss | `kill -9` drills (armour upgrade, ovoo, trade, level-up) | **PARTIAL**: level-up lost (E-09); dup windows by code reading | PERS-001/002/003 | No dup; ≤ 60 s EXP loss |
| G6 First join (bots) | Class menu forced; first reward; relog persistence | `firstjoin.js` | **PASS** (E-06/07) | – | Repeatable in CI-less smoke |
| G7 First join (real client) | Pack prompt honoured; menus render; HUD readable at scales 2–4 | `docs/HUD_MANUAL_QA.md` + ONB-001 test | **NOT RUN** | ONB-001 | All 16 HUD QA rows pass; pack status recorded |
| G8 First fights | New characters survive the first fights | First-fight bot QA | **FAIL** (start ~23 HP; computed one-shot by a bolt) | PROG-001, GAME-005 | < 5 % deaths |
| G9 Culture and language | No real sacred or historical names misused; first-session text correct | Content blocklist test + native review | **FAIL** | CULT-001, HIST-002, LANG-001 | Reviewer sign-off |
| G10 Capacity | Holds the launch cap | `05` procedure on the target host | **FAIL / UNVERIFIED** (50: TPS 18.5, p95 59 ms on a shared box) | PERF-001 | `05` criteria for 2 h |
| G11 Backup / restore | Consistent backup, off-host, restorable | Restore drill at V15 | **FAIL** (never at V15; on-box) | OPS-001 | Timed drill documented |
| G12 Online-mode auth | Only Microsoft-verified accounts join | Real client on the deployed host | **NOT RUN** (environment blocks Mojang APIs) | – | A genuine join, and an offline client refused |
| G13 Human playtest | 5 Mongolian players complete the `03` tasks | Usability test | **NOT RUN** | – | `03` success criteria |
| G14 Monetisation (only if a store goes live) | Idempotent grants; policy reviewed | `SEC-003` tests + owner review | **FAIL** (not built) | SEC-003 | Replay-safe; written policy |

**Closed alpha (8–12 friends):**
- G1, G3, G4 and G6 pass today.
- **G7, G8 and G9 must pass first.**
- G11 must at least have off-host copies.

**Public beta:** every gate, including G10 at the intended cap and G13.
