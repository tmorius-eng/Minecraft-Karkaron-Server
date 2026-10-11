# 02: Critical findings, ranked

- **Audited commit:** `main` @ `50d5138`.
- **Evidence:** `E-nn` refers to `11-evidence-and-reproducibility.md`.
- **Topic detail:**
  - `03` onboarding
  - `04` data and hardcoding
  - `05` performance
  - `06` history
  - `07` progression
  - `08` security and persistence

## Severity scale

| Level | Meaning |
|---|---|
| P0 / Critical | Exploitable duplication or data loss, security failure, severe crash, or a blocker that makes a safe launch impossible. |
| P1 / High | Core feature broken, repeatable exploit, severe onboarding failure, persistence inconsistency, or unacceptable performance. |
| P2 / Medium | Substantial problem that has a workaround. |
| P3 / Low | Polish. |

## Confidence scale

| Level | Meaning |
|---|---|
| Confirmed | The code path was read end to end, or the behaviour was measured live. |
| Strongly Supported | Static reasoning that still needs one runtime check. |
| Unverified | Neither of the above. |

## Headline

**No P0 was found.**

The integrity core holds up under reading, ITs and live tests:
- relic CAS
- profile "newer version wins"
- transactional `/pay`
- atomic credits
- single dungeon completion
- menus cancel every click and drag
- soulbound guard
- no SQL injection
- no committed secrets

The launch risk is concentrated elsewhere:
1. **The first five minutes:** pack prompt, health, lethality and death lock.
2. **Culturally sensitive naming** that Mongolian players will see on day one.
3. **Unproven capacity at 50 players.**
4. **Operations that have never run for real:** no deployment, no off-host backup, no restore drill at the current schema.

## P1 / High

### ONB-001: The class menu covers the resource-pack prompt on first join
- **Confidence:** Strongly Supported. Needs one vanilla-client join. Bots auto-accept packs, so they cannot show this.
- **Type:** probable defect.
- **Evidence:**
  - `suld-plugin/.../listener/PlayerLifecycleListener.java:49` sends the pack (`setResourcePack(..., required=true)`).
  - `:58` opens `ClassSelectionGui` in the same tick, so two screen-owning packets go out together.
  - `config.yml:93` `required: true`.
  - There is no resend or timeout path in `ResourcePackService`.
- **Reproduction:** Fresh vanilla 1.21.11 client with the server entry set to "Prompt" (the default). Join as a new player, then note whether the pack confirm screen survives the container screen. Pick a class and see whether the prompt ever returns. Check `/suldpack status`.
- **Impact:** If confirmed, every new Java player plays without the pack:
  - glyph titles show as boxes
  - painted menus are missing
  - "required" is silently not enforced
- **Root cause:** Two client screens are sent in one tick, and the client keeps only the last.
- **Fix:** Open the class menu only after `PlayerResourcePackStatusEvent` reaches a terminal status, or after 15 s.
  - *Alternative:* send the pack in the configuration phase.
- **Test / acceptance:**
  - Unit-test the gate.
  - Manual: Prompt / Enabled / Disabled joins each end with a recorded pack status *and* the class menu.
- **Effort:** S. **Fix risk:** Low.

### PROG-001 + GAME-005: New characters start at about 23 HP with 0 armour, against mobs tuned for 51 HP and 8 armour
- **Confidence:**
  - HP: **Confirmed (measured live)**.
  - Damage: Strongly Supported (formulas).
- **Type:** confirmed defect (health) + probable defect (lethality).
- **Evidence:**
  - Measured after the class pick:
    - Мэргэн 23/43 HP
    - Бөө 23/48 HP
    - Баатар 24/82 HP
    - "Хуяг: 0" for all three (E-08)
  - `SkillTreeService.java:416-423` raises max HP but only clamps current HP *down* (`if (p.getHealth() > max) setHealth(max)`), so a fresh player keeps the vanilla ~20 HP.
  - Mob damage is fitted to par HP 51 and armour 8 at level 1 (`suld-api/.../balance/MobScaling.java:36-60`, `docs/DIFFICULTY_CURVE.md:48`).
  - Computed hits:
    - Govi wolf (L2): 10.8 per bite
    - bandit bolt (L4): ~26.8
    - Gobi scorpion (L7): 21.0
- **Reproduction:**
  1. New account, pick any class, `/profile`: HP ≈ 23.
  2. Walk east and fight a wolf, or take a bandit bolt.
- **Impact:** The first or second fight can kill. Combined with ONB-002, a likely first-session quit.
- **Root cause:**
  - No heal on class pick.
  - The simulator's level-1 "par player" is stronger than a real fresh character.
  - No newbie damage curve.
- **Fix:**
  1. `p.setHealth(max)` when the class is first chosen.
  2. `CombatRules.newbieTaken(level)` (×0.5 at L1 → ×1.0 at L6), or make par HP/armour at L1–5 match a fresh character.
  3. Re-run `simulate -Pquick`.
- **Test / acceptance:**
  - A bot asserts health == max right after selection.
  - A `MobScalingTest` asserts that at L1–5 a normal fight costs ≤ 35 % of the lowest class base HP.
  - Bot run: < 5 % deaths in 20 first fights per class.
- **Effort:** S. **Fix risk:** Medium (balance re-simulation).

### ONB-002: A 5-minute "soul" lock for a level-1 death
- **Confidence:** Confirmed.
- **Type:** design concern.
- **Evidence:**
  - `config.yml:62-64`: `min-minutes: 5`, geometric curve.
  - `suld-api/.../death/DeathLock.java:19-34`.
  - While locked, a soul can only talk, read or revive (`DeathService.java:596-611`).
  - Player-facing text never warns about it (`GuideBoards.java:82-83`); `docs/OPERATIONS.md:117` says 30 s.
- **Impact:** A newcomer's likely first death (see PROG-001) costs 5 minutes of nothing.
- **Fix:**
  - Add `death.lock.newbie-levels: 5` with a 30–60 s lock.
  - Fix the board text.
  - *Alternative:* the first death is free.
- **Test:** `DeathLockTest`, ≤ 60 s for L1–5.
- **Effort:** S. **Fix risk:** Low.

### CULT-001 (HIST-024, HIST-023, HIST-025, HIST-027): Real sacred and historical names used as farmable monsters and paid items
- **Confidence:** Confirmed (data). The sensitivity is a judgement that **must** be confirmed by native reviewers.
- **Type:** design concern, with reputational and possibly legal risk.
- **Evidence:**
  - **Khasar.** «Хасар — Агуйн Эзэн» (`content/mobs.json:25`) is Chinggis's brother's name on the first, farmed beast boss. Players loot «Хасарын Зүрх» and «Хасарын Соёо» (`items/jewelry.json:25`, `items/weapons.json:68,95`). Internal docs call it "not the historical Qasar"; players never see that.
  - **Burkhan Khaldun.** «Бурхан Халдуны Агуй» (`dungeons.json:188`): the sacred mountain (UNESCO 2015 per search snippet) becomes a monster dungeon whose spirit is killed.
  - **Chinggis items.** «Чингисийн Малгай/Дээл/Өмд/Гутал/Тамга», set «Чингисийн Өв» (`items/armor.json:525-603`, `jewelry.json:175,195`, `sets.json:5`).
  - **Paid lineage and sacred tags.** Real-money tags «⚜ Их Хааны Ач ⚜» (`CosmeticCatalog.java:74`) and «Хар Сүлд» / «Цагаан Сүлд» / «Цагаан Сар» (`:62-67`; credit-only because `coin-max-rarity: COMMON`).
  - **The project's own rules.** The style guide already forbids real-person likeness and real emblems without labelling (`docs/ASSET_STYLE_GUIDE.md:136-142`).
- **Impact:** These are the first dungeon and the most visible loot and store items. They are the likeliest source of public criticism from Mongolian players and press. A 2006 Mongolian proposal on commercial use of Chinggis's name exists; whether it was enacted is unverified.
- **Fix (display names only; IDs may stay):**
  - Rename the boss and dungeon: e.g. «Хөгшин Саарал», «Чонын Агуй».
  - Move combat off Burkhan Khaldun: an invented «Хар Хадны Агуй». The savdag is freed, not slain.
  - Rename the set: «Хаадын Өв».
  - Retire «Их Хааны Ач».
  - Make the sacred-banner tags earn-only.
- **Test:** A content test fails if a display name contains a blocklisted real name (Чингис, Хасар, Бурхан Халдун, Алтан ураг…) outside history-labelled text.
- **Effort:** S. **Fix risk:** Low (data only).

### HIST-002: A false *Secret History* citation shown as "VERIFIED"
- **Confidence:** Confirmed. The wording is in the data; the source claim was checked via search snippets only.
- **Evidence:**
  - `content/sites.json:23` says the 1206 enthronement at Khödöö Aral is "told in the Secret History". The site carries `"history": "VERIFIED"`, and the player sees «Түүхэн газар (VERIFIED)» (`HistoricSiteService.java:289`).
  - Per search snippets (`06`), §202 places 1206 at the source of the Onon. The project's own `docs/research/history/README.md` says the same.
- **Fix:**
  - Rewrite the line (exact text in `06`), or relabel the site INSPIRED.
  - Hide the raw "(VERIFIED)".
- **Effort:** XS.

### LANG-001: Баатар's resource is labelled «Хил» (= border)
- **Confidence:** Confirmed.
- **Evidence:** `suld-api/.../clazz/PlayerClass.java:21` `"Хил / Rage"`. It appears on the HUD (`HudPanel.java:554`), the class menu and the profile.
- **Fix:** «Уур хилэн / Rage» (HUD «Уур»).
- **Effort:** XS.

### PERF-001: 50 concurrent players are not demonstrated at 20 TPS
- **Confidence:** Confirmed (measured). The hardware limits are stated in `05`.
- **Evidence (MEASURED, this audit):** 4 vCPU shared with the bots, Paper 1.21.11-132, `-Xmx4G` G1, view 6 / simulation 4.

  | Load | TPS avg (min) | Tick mean / p95 / p99 / max (ms) | Ticks > 50 ms |
  |---|---|---|---|
  | 10 bots | 19.68 (18.7) | 20.6 / 28.9 / 46.4 / 2,852 | 41 / 4,860 |
  | 25 bots | 19.20 (16.8) | 23.3 / 30.8 / 45.7 / 7,910 | 35 / 4,750 |
  | 50 bots | 18.55 (14.0) | 37.8 / 58.7 / 88.9 / 10,500 | **662 / 4,584** |

  (In the 25 run, 5 explore bots were kicked; in the 50 run, 8 were — see the bot-artefact note in `05`.)
- **Prior evidence (REPORTED):** `docs/perf/WORLD_50.md` reports TPS 11.8 at 50 on similar shared hardware. Its "release gate NOT PASSED" still stands.
- **Root cause (profile):** Chunk generation dominates the JFR hot methods: noise, aquifer and palette code. SÜLD's own probes sum to roughly 2 ms per tick at 50 players. Multi-second stalls occurred in every run; their cause was not isolated.
- **Fix:**
  - Finish world pre-generation before opening.
  - Test on the target host with the bots on another machine.
  - Find the stalls with spark or JFR thread dumps.
  - Set the launch cap at the level that passes the gate (likely 25 until re-measured).
- **Acceptance:** On the target host, 2 h at the launch cap: TPS ≥ 19.5, p95 ≤ 45 ms, no tick > 1 s.
- **Effort:** M.

### OPS-001: Backups are skewed and on-box only, and restore was never tested at the current schema
- **Confidence:** Confirmed.
- **Severity:** Raised from P2 (lane) to P1 as a launch gate.
- **Evidence:**
  - `deploy/backup.sh:30-49` runs `pg_dump` *before* `save-all`.
  - No SÜLD flush first.
  - `BACKUP_DIR=/opt/suld/backups` on the same host.
  - `DEPLOYMENT.md:287` restore was verified at "schema v2"; the schema is now V15.
  - `DEPLOYMENT.md:3-4`: "Nothing here has been deployed yet".
- **Impact:** A restore can produce item+coin duplicates or losses. Losing the disk loses every backup.
- **Fix:**
  1. Add a `suld flush` admin command.
  2. Reorder: flush → `save-all flush` + `save-off` → `pg_dump` → `tar` → `save-on`.
  3. Copy off-host (restic/rclone).
  4. Run a timed restore drill at V15.
- **Effort:** M.

### SEC-003: `/credits give` is not idempotent or audited (gate before any store goes live)
- **Severity:** P1 *before monetisation*; P2 today, because no store exists.
- **Confidence:** Confirmed.
- **Evidence:** `command/MenuCommands.java:68-90` → `style/StyleService.java:418-438`:
  - no transaction id
  - only a log line
  - name-based target
- **Impact:** A store retry grants paid credits twice. Buyers who never joined get nothing, and nothing records it.
- **Fix:**
  - `credits give <uuid> <amount> <txid>`.
  - A `suld_credit_grants(txid PK, …)` insert in the same transaction as the balance update.
  - An `AuditEvent` for every grant.
- **Test:** The same txid twice → one grant.
- **Effort:** M.

## P2 / Medium (one line each; details in the topic reports)

| ID | Title | Conf. | Report |
|---|---|---|---|
| ONB-008 | Declining the required pack disconnects with no SÜLD explanation; the hint names admin-only `/suldpack force` | Confirmed | 03 |
| ONB-003 | Main avenue exits south into the Gobi; 40 % of nearby spawns are L7–9 | Confirmed | 03 |
| ONB-004 | Quest tracker says "you're there, wait" inside the walls where nothing spawns | Confirmed | 03 |
| ONB-005 | Tutorial ends with "enter Khasar's Den now"; the gate needs chapter 4 (level 5) | Confirmed | 03 |
| ONB-006 | Permanent class picked with one click, no confirmation | Confirmed | 03 |
| ONB-007 | ~18 chat lines, 2 titles, 2 boss bars, 2 screens in the first 30 s; the card repeats on every join (measured) | Confirmed (measured) | 03 |
| ONB-009 | Bedrock (Geyser "core") has no pack, glyph or control path | Strongly Supported | 03 |
| PROG-002 | Chapter, login and task EXP use the Java curve; levels use `config.yml` | Confirmed | 04, 07 |
| PROG-003 | `dungeons.json` completion rewards and `discoveryExp` are dead data | Confirmed | 04 |
| PROG-004 | Dungeon gates are chapter *indexes*; a content edit can softlock | Strongly Supported | 07 |
| PROG-005 | Late discovery chapters pay ~1 % of their neighbours' EXP | Confirmed | 07 |
| GAME-001 | Default group has `/sethome` (multiple) and `/tpa`, bypassing travel design | Confirmed | 07 |
| SEC-001 | Removing an owner from config does not revoke auto-OP (pinned UUID) | Confirmed | 08 |
| SEC-002 | Personal account `qeevr_` is the shipped default owner | Strongly Supported | 08 |
| PERS-001 | Class-armour upgrade / Orb respec commit to the DB but not the player file; a crash refunds the materials | Confirmed (code) | 08 |
| OPS-002 | 23 third-party plugins unpinned, re-fetched on every update | Confirmed | 08 |
| TEST-001 | No CI; zero tests for the plugin glue of combat, dungeon, trade, death, relic, clan, quest, gui and command | Confirmed | 11 |
| ANA-001 | Funnel events `resource_pack_ready`, `first_objective`, `five_minute_active` missing; `RETENTION_*` never emitted; `FIRST_BOSS` fires every clear | Confirmed | 03 |
| DOC-001 | 22 doc-vs-code contradictions (18 vs 42 chapters, 4 vs 10 dungeons, pack required false vs true, "no historical claims", …) | Confirmed | 09 |
| HIST-007 / 019 / 021 | Deer-stone date doubled; the Black General (a defender) made a villain; Altai/Khangai/Otgontenger regions swapped | Confirmed (data) | 06 |
| LANG-002/003/004/007/008 | Tutorial typos; four spellings of «түмэн»; rarity names collide; overloaded «Цол/Зэрэг/Түвшин» | Confirmed | 06 |
| VIS-001/002/004/005/006 | Pagoda towers and blue trident banners in shipped menu art; generic emblem with ⚜; cherry groves; raw AI cards with Steve and a pumpkin | Confirmed (images viewed) | 06 |
| UX-1 | Credit balance always on the sidebar; ₮ used for both in-game coins and real money | Confirmed | 09 |

## P3 / Low

| ID | Title | Report |
|---|---|---|
| PERS-002 | Reward guards stored apart from rewards (ovoo/site PDC vs SQL; daily/task/level style row vs profile row) | 08 |
| PERS-003 | EXP crash window: ≤ 30 s for killers, ≤ 300 s otherwise. A level-up was lost live (E-09); raise to P2 if players report it | 08 |
| PERS-004 | Trade: items swapped before the coin transaction; the clamp-at-0 revert can mint coins | 08 |
| PERS-005 | Mailbox: file deleted before the player file is saved | 08 |
| PERS-006 | Clan `version` written but never compared | 08 |
| OPS-003 | `/suld reload` advertised but missing | 08 |
| OPS-004 | Self-host pack server has 2 threads and no timeouts (non-deploy installs) | 08 |
| OPS-005 | A failed enable leaks the pool and the H2 lock | 08 |
| OPS-006 | Grim punishments are kicks only | 08 |
| OPS-007 | No index for the leaderboards or the analytics purge | 08 |
| THR-001 | Async chat and style flush read Bukkit state | 08 |
| SEC-004 | Two admin commands rely only on plugin.yml | 08 |
| GAME-002 | Classless kills earn EXP | 07 |
| GAME-003 | Class-menu sets not cleared on quit | 07 |
| GAME-004 | DG-7, SP-3, DU-1, GR-5/6, EC-4 still open | 07 |
| PROG-006 | Wolf raid always pays an item at its maximum rarity | 07 |
| DATA-001 | `PlayerClass` stats hardcoded against its own javadoc | 04 |
| DATA-002 | Dungeon `region` wrong for 6 of 10 | 04 |
| PERF-002 | Multi-second tick stalls (2.9–10.5 s) in every measured run; cause not isolated | 05 |
| PERF-003 | 256 `FALLING_BLOCK` entities persisted during the runs; origin unverified | 05 |
| Remaining HIST/LANG/VIS Low | minor history, language and visual notes | 06 |

## Previous audit (2026-10-07, `f123253`): re-verified

| Status | IDs |
|---|---|
| **Fixed** | DG-1, DG-3, DG-9, XP-1, XP-2, PW-1, PW-5, GR-1, SB-1, SB-2, SB-3, XP-4, XP-5, DG-4, GR-2, PW-4, EC-2, DT-2, DT-3 |
| **Changed / mitigated** | DG-2 (repeat factor, no lockout), XP-3 (now a new-player risk, ONB-003), DG-5, GR-6 |
| **Still open** | DG-7, SP-3, DU-1, GR-5 |
| **Cannot determine** | GR-8 |

The full table with file:line evidence is in `07`.
