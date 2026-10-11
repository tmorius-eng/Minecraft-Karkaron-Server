# P1 alpha stabilization: report (2026-10-11)

## Scope and history

- **Scope:** the P1 list of `docs/audits/2026-10-11/10-fix-roadmap.md`, as authorised by the owner. The audit reports are unchanged.
- **Branch:** `claude/p1-alpha-stabilization`, created from `claude/gracious-lamport-5aaowc` (audit commit `a6c7350`).
- **History:** no force-push, and no history was rewritten.

| Commit | What |
|---|---|
| `01ff6a1` | ONB-001: first-join screens wait for the resource-pack answer |
| `4f0e4ce` | GAME-005 / PROG-001 / ONB-002: full HP on class pick, ×0.5→1.0 damage taken at L1–5, 1-minute lock to L5 |
| `2201c05` | ONB-003/004/005/007/011: one onboarding voice; spawn cap near the city; the tracker never says "wait" in town; first chapter announced |
| `64e3bba` | CULT-001, HIST-002, HIST-007, LANG-001/002/003: sacred and historical names off monsters, loot, skills and the store |
| `ba04e43` | Crash loss: level-ups commit at once, autosave 60 s, the item side is written before the DB side |
| `d346789` | SEC-003: `/credits give` is idempotent per store transaction (V16 ledger) and audited |
| `3fab5e5` | OPS-001: backups flush SÜLD first and dump after the world flush, with an off-host copy; restore drilled at schema 16 |
| (this file) | Final report |

## 1. Findings addressed and files changed

| Finding | Change | Main files |
|---|---|---|
| ONB-001 | `PackGate`: the class menu and the returning player's welcome screen open after the pack status is loaded / declined / failed / discarded. ACCEPTED or DOWNLOADED gives the download 90 s; silence opens the screen after 30 s. Quit clears the pending action. The failed-download hint names `/suldpack` | `resourcepack/PackGate.java`, `ResourcePackService.java`, `listener/PlayerLifecycleListener.java`, `gui/MenuListener.java` |
| GAME-005 | Health filled to max after the class pick (and again 5 ticks later, after the class gear) | `skill/SkillTreeService.java` |
| PROG-001 | `CombatRules.newbieTaken`: ×0.5 at L1, +0.1/level, ×1.0 from L6, applied to SÜLD mob hits (bosses unchanged). The QA harness expectation follows | `api/balance/CombatRules.java`, `combat/CombatListener.java`, `skill/qa/QaKit.java` |
| ONB-002 | `death.lock.newbie-levels: 5`, `newbie-minutes: 1` (defaults apply to existing configs). The guide board states the real death costs | `api/config/DeathSettings.java`, `api/death/DeathLock.java`, `SuldConfigFactory.java`, `config.yml`, `branding/GuideBoards.java` |
| ONB-003 | Outskirt (< 450 blocks) level-fitted spawns are capped at player level + 3 | `mob/RegionSpawner.java` |
| ONB-004 | Inside the built city (+32 margin) the tracker points out of town | `quest/QuestTracker.java` |
| ONB-005 | The tutorial's last banner sends the player into the first dungeon only when it is open (`DungeonService.soloEntryRefusal`) | `branding/TutorialService.java`, `dungeon/DungeonService.java` |
| ONB-007 / ONB-011 | Nothing competes with the class menu on join. After the pick: chapter announce + hint, one title, a short card. Returning players get no card. The tutorial's second welcome banner was removed | `branding/OnboardingService.java`, `branding/TutorialService.java`, `gui/ClassSelectionGui.java`, `quest/QuestService.java` |
| CULT-001 | Display renames: Хасар → «Хөгшин Саарал» (and its dungeon and loot), «Бурхан Халдуны Агуй» → «Хэнтийн Гүн Агуй» with boss «Хадны Мангас», «Чингисийн …» → «Хаадын …», «Чингисийн Уур» → «Баатрын Их Уур». Six cosmetics **withheld** from sale («Их Хааны Ач», «Хар/Цагаан Сүлд», «Алтан Ураг», «Цагаан Сар», «Наадамч») | content JSON, item JSON, `Cosmetic*.java`, `Menus.java`, `StyleService.java`, `DungeonLadder.java`, `Ultimate.java`, `KhasarBrain.java`, `NpcDialogue.java` |
| HIST-002 / HIST-007 | The false *Secret History* attribution was removed (the site is now INSPIRED, with no new claim). Deer stones are dated ~2000 years before Chinggis | `content/sites.json`, `content/quests.json` |
| LANG-001/002/003/009 | «Уур хилэн»; «Ангийн Бөөтэй ярилц»; «Дайснаа онил (Q)»; no raw «(VERIFIED)» or «Act II» in Mongolian text | `PlayerClass.java`, `TutorialProgress.java`, `HistoricSiteService.java` |
| Crash loss (E-09) | Level-ups and `/suld exp` save at once (async); autosave every 60 s (was 300 s) | `PlayerLifecycleListener.java`, `SuldCommand.java`, `SuldPlugin.java` |
| PERS-001 / PERS-002 | Player file written before the DB side for armour upgrades, Orb respecs and ovoo/site first-visit guards | `ClassArmor.java`, `SkillMapMenu.java`, `OvooService.java`, `HistoricSiteService.java` |
| SEC-003 | V16 `suld_credit_grants`. `/credits give <player\|uuid> <amount> <txid>`: a replay is a no-op. Audit rows for give and take | `db/migration/*/V16__credit_grants.sql`, `SchemaMigrator.java`, `StyleRepository.java`, `JdbcStyleRepository.java`, `StyleService.java`, `MenuCommands.java`, `docs/OPERATIONS.md` |
| OPS-001 | `/suld flush` writes `flush.done`. `backup.sh` order: flush → save-all → save-off → pg_dump → tar → save-on → checksums → off-host copy (`BACKUP_REMOTE`, rsync/rclone, verified) | `SuldCommand.java`, `deploy/backup.sh`, `deploy/suld.env.example`, `deploy/deploy.sh`, `DEPLOYMENT.md` |
| Docs | Crash-loss table, restore drill, cultural review list | `docs/operations/CRASH_LOSS.md`, `docs/operations/RESTORE_DRILL.md`, `docs/review/CULTURAL_REVIEW_P1.md` |

## 2. Tests (actual results, final head)

| Run | Result |
|---|---|
| `./gradlew build --rerun-tasks` | BUILD SUCCESSFUL, 55.6 s; **508 tests, 0 failed, 0 skipped** (api 383, plugin 112, simTest 13) |
| `python3 tools/validation/run_all.py` | ALL VALIDATIONS PASSED |
| JDBC ITs against PostgreSQL 16.15 (`SULD_TEST_PG_URL`) | 12 classes, **21 tests, 0 failed**; schema at 16 |

New regression tests:

| Test | What it checks |
|---|---|
| `PackGateTest` (6) | Pack-answer gating |
| `DeathLockTest.newPlayersAreLockedOneMinuteAtMost` | 1-minute lock for L1–5 |
| `BalanceRulesTest` (newbieTaken) | New-player damage factor |
| `SuldConfigFactoryTest.newbieDeathLockIsConfigurable` | New config keys |
| `RegionSpawnerPickTest` (2) | Outskirts spawn cap |
| `CulturalNamingTest` (3) | Name blocklist; withheld cosmetics |
| `JdbcCreditGrantIT` (2, H2 + PostgreSQL) | Credit grants idempotent per txid |

## 3. Live checks (bots on Paper 1.21.11; not a human client)

| Check | Result |
|---|---|
| Pack accept / decline / silence | Menu after "loaded" / right after "declined" / only after 30 s |
| HP after class pick | Бөө 20 → 41.6/41.6, Мэргэн 46/46 |
| First hits | −8.15 (L4 bandit bolt, ≈ 16.3 unscaled), −2.86 (Govi wolf) |
| L1 death lock | «1 мин 00 сек» |
| Onboarding | 5 chat lines and the menu before the pick; after it the announce with «➜ Хотын хаалгаар гараад зүүн зүгийн тал руу яв (Хэрлэн).», one title, a 5-line card; rejoin 6 lines, no card |
| Tracker in town | At x=101 inside the walls: «➡ Хэрлэнгийн Тал · зүүн · 104м» |
| South outskirts, L1 player, 90 s | 5 spawns, all level-2 Govi wolves |
| Crash drill | Level-up at 12:49:32, `kill -9` at 12:49:41: after restart still level 2 (the audit's identical drill lost it) |
| Credits | No txid refused; one txid ×3 applied once; balance 850; ledger 2 rows; 4 audit rows |
| Backup + restore | Real `backup.sh` 19.0 s, flush ok, off-host copy verified. Restore to a fresh DB and directory: data identical (one post-backup audit row aside). Booted at schema 16; the player read level 2 / 850 credits |

## 4. Performance: before and after

**Environment (both columns).** Shared 4-vCPU cloud container with 15 GB RAM. The Mineflayer bots ran on the same cores. Java 21.0.12 `-Xmx4G` G1. Paper 1.21.11-132. View distance 6 / simulation distance 4. Pre-generation paused.

**Workload.** The same `mmo.js` harness and role mix, 240 s per run. This is **not** the deployment server and is not a capacity figure.

| Bots | Run | TPS avg (min) | Tick mean / p95 / p99 / max (ms) | Ticks > 50 ms | Loaded chunks avg | SÜLD ms/tick |
|---|---|---|---|---|---|---|
| 10 | audit | 19.68 (18.7) | 20.6 / 28.9 / 46.4 / **2,852** | 41 / 4,860 | 1,787 | 1.49 |
| 10 | P1 | 20.00 (20.0) | 20.4 / 30.5 / 49.8 / **179** | 48 / 4,929 | 2,035 | 1.25 |
| 25 | audit | 19.20 (16.8) | 23.3 / 30.8 / 45.7 / **7,910** | 35 / 4,750 | 1,805 | 1.66 |
| 25 | P1 | 19.88 (19.6) | 39.2 / 64.5 / 100.1 / **305** | 695 / 4,920 | 3,372 | 2.05 |
| 50 | audit | 18.55 (14.0) | 37.8 / 58.7 / 88.9 / **10,500** | 662 / 4,584 | 2,266 | 2.60 |
| 50 | P1 | 17.08 (13.8) | 50.7 / 96.1 / 143.2 / **596** | 2,357 / 4,011 | 3,333 | 2.96 |

**Reading the table honestly.**
- **The multi-second stalls are gone in every P1 run.** The worst tick went from 2.9–10.5 s down to 0.18–0.6 s.
- **The average tick is worse at 25/50.** But the workloads were not identical:
  - in the audit runs, 5 and 8 explore bots were kicked ("floating too long");
  - in the P1 runs none were;
  - so P1 kept ~1.9× more chunks loaded and more players active (25-bot average 23.2 vs 19.7).
- **SÜLD's own per-tick cost rose only 0.4 ms**, in line with the load. Nothing in P1 runs per tick except a cheap city-footprint check in the quest tracker.
- **The stall root cause** is the Paper watchdog dump from the audit run (`docs/audits/2026-10-11` logs). The main thread was blocked in `ServerChunkCache.syncLoad` on chunk (-123, 3) at BLOCKING priority. That was the player's own tick (`Entity.updateFluidOnEyes → Level.getChunkAt`) right after a console `/tp` into ungenerated terrain. **There is no SÜLD frame in the stack.**
  - Vanilla world generation dominates the JFR hot methods.
  - The fix is operational: complete `/suldworld pregen` before opening, and keep teleports async (`SafeTeleport` already uses `teleportAsync`).
- **Not done in this phase:** the re-measure after full pre-generation, and any measurement on the deployment server. Pre-generation was still at 0 of 14,940 chunks when the runs finished.

## 5. Closed-alpha exit criteria: status

| Criterion | Status | Evidence / what is missing |
|---|---|---|
| Onboarding and class selection work in a **real vanilla Java client** | **NOT VERIFIED** | Only bots. One vanilla 1.21.11 client join with "Prompt", "Enabled" and "Disabled" pack settings is required |
| Online-mode authentication with a legitimate Minecraft account | **NOT VERIFIED** | This environment cannot reach `api.minecraftservices.com`; needs the deployment host |
| New-player health, damage, ghost duration, quest routing validated | **Verified with bots** | §3; still needs a human first-session playtest |
| PostgreSQL persistence and the backup/restore procedure | **Verified in the dev container** at schema 16 (V1–V15 + V16) | `RESTORE_DRILL.md`; repeat on the VPS with a real remote target |
| Existing automated tests pass | **Yes** | 508/0; PostgreSQL ITs 21/0 |
| Crash-loss behaviour measured, documented, judged acceptable | **Yes, for a private alpha** | `CRASH_LOSS.md` (≤ 30–60 s EXP; ≤ 5 min ordinary inventory; no creation of coins, credits, relics or upgrades) |
| Culture, legal and client-compatibility checks visible | **Yes** | `docs/review/CULTURAL_REVIEW_P1.md` |

**The project is not declared closed-alpha ready.** The two NOT VERIFIED rows need a human with a real client and the real host. Public beta is a separate decision.

## 6. Remaining blockers, risks and human checks

1. **Real-client pass:**
   - the pack prompt (ONB-001);
   - `docs/HUD_MANUAL_QA.md` at GUI scales 2–4;
   - menus without the pack;
   - a Bedrock client, if Geyser stays.
2. **Online-mode join** on the deployment host; an offline client refused.
3. **On the VPS:**
   - full pre-generation, then the 10/25/50 re-measure with the bots on another machine, against the criteria in `docs/audits/2026-10-11/05-performance-and-capacity.md`;
   - the backup drill with a real `BACKUP_REMOTE`.
4. **Native reviewer and historian sign-off** on every new name and text in `docs/review/CULTURAL_REVIEW_P1.md`. The painted menu art (pagoda towers, blue three-prong banners, the emblem) is **unchanged**.
5. **Before any store goes live:**
   - legal and platform-policy review (Chinggis name use, Minecraft Usage Guidelines, minors and payments);
   - keep the six withheld cosmetics unsold.
6. **Open P2/P3 items** from the roadmap. Examples:
   - SEC-001/002 (default owner `qeevr_`, OP revocation);
   - OPS-002 (plugin lock file);
   - the PERS-002 style-row claims and PERS-004/005;
   - the bot harness still lives outside the repo.
7. **Minor:** the onboarding commit message says "4-line card". The card is 5 lines (header, 3 steps, help links).
