# 11: Evidence and reproducibility

This file records what was actually run, what it returned, what could not be run, and what remains unverified. The other reports cite entries here as **E-nn**.

## Setup

| Item | Value |
|---|---|
| Date | 2026-10-11 (UTC) |
| Commit | `main` @ `50d5138f3db0af12377e29d83580605feabeabb0` (2026-10-11 09:22 UTC) |
| Repository state | 151 commits: 150 authored "Claude", 1 by the owner, dated 2026-10-06 to 2026-10-11. Working tree clean at start and end |
| Sizes | `suld-api` 261 main Java files / 19,595 lines; `suld-plugin` 152 / 39,634 lines |
| Environment | Cloud container: 4 vCPU, 15 GB RAM, OpenJDK 21.0.12, Gradle 8.14.3, Node 22, Python 3.13, PostgreSQL 16.15. Outbound HTTPS goes through a proxy; several hosts are unreachable (see "Network limitations") |
| Phase A rule | No application code, configuration, deployment file or world in the repository was modified. Every server, database and bot ran in a session scratch directory outside the repository. `/tmp/suld-server` (an earlier dev server) was read and its Paper jar and libraries copied; it was not modified |

## How the audit was organised

**Lanes.** The audit ran in independent lanes:

| Lane | Scope |
|---|---|
| 1 | Build, tests, static checks |
| 2 | Security and persistence |
| 3 | Gameplay, onboarding, hardcoding |
| 4 | Live server and bots |
| 5 | History, docs, market |

**Who did what.**
- Lanes 2, 3 and 5 were run by sub-reviewers. Each re-opened every cited line and marked candidate findings CONFIRMED / REFUTED / PARTIAL.
- The primary reviewer ran lanes 1 and 4, reconciled duplicates, and re-checked the code lines behind every P1 by opening them:
  - `PlayerLifecycleListener.java:45-60`
  - `sites.json:23`
  - `mobs.json:25`
  - `PlayerClass.java:21`
  - `OwnerService.java:93-96`
  - `ClassArmor.java:418-433`
  - `DungeonService.java:574-582`
  - `config.yml:62-64,91-95,205-209`
  - `CosmeticCatalog.java:74`
  - `SkillTreeService.java:416-423`
  - `Content.java:48-59`
  - `ItemService.java:125`

**Refuted or downgraded during reconciliation** (so they do not appear as defects):
- "The plaza teleport closes the class menu": **refuted** by bytecode reading and live bot (E-06).
- "Config migration re-adds the default owner every boot": **refuted**; the migration only runs below v3.
- "Dungeon shutdown after the profile flush loses rewards": **refuted**.
- "ClanService schedules a task during disable": unreachable in practice.
- "PROG-001: level-1 max HP is 24–40": **corrected**. Measured max is 43–82; current HP is 23–24 (E-08).
- Lane-reported "25 players → TPS 13" (REPORTED in `docs/perf/WORLD_50.md`): **not reproduced** here. 25 bots held TPS 19.2 under different settings (E-10). Both are reported, with their conditions.

## Evidence entries

### E-01: Full build and tests

```
./gradlew build --rerun-tasks --console=plain --no-daemon
BUILD SUCCESSFUL in 58s   (real 0m59.0s)   20 actionable tasks: 20 executed
suld-api/build/test-results/test         classes 64  tests 381  fail 0  err 0  skip 0
suld-plugin/build/test-results/test      classes 27  tests  99  fail 0  err 0  skip 0
suld-plugin/build/test-results/simTest   classes  1  tests  13  fail 0  err 0  skip 0
```

- By default the JDBC ITs ran on in-memory H2 (MySQL mode).
- `:suld-plugin:simulate` was **not** run, because it rewrites docs (`TESTING.md:17`).

### E-02: JDBC integration tests on real PostgreSQL

**Setup.**
- A throwaway cluster was created with `initdb` under a helper user and started with `pg_ctl` on 127.0.0.1:5499 (trust auth, local only).
- Databases `suld_it` and `suld_live` were created.

**Command.**
```
SULD_TEST_PG_URL=jdbc:postgresql://127.0.0.1:5499/suld_it SULD_TEST_PG_USER=suld SULD_TEST_PG_PASS=x \
  ./gradlew :suld-plugin:test --tests '*IT' --rerun-tasks
BUILD SUCCESSFUL in 55s
```

**Results.**

| IT class | Tests |
|---|---|
| JdbcClanRepositoryIT | 2 |
| JdbcAnalyticsIT | 1 |
| JdbcRelicRepositoryIT | 4 |
| JdbcSkillStateIT | 1 |
| JdbcDeathRepositoryIT | 2 |
| JdbcProfileVersionIT | 2 |
| JdbcStyleRepositoryIT | 1 |
| JdbcProfileTransferIT | 3 |
| JdbcClassGearIT | 1 |
| JdbcEquipmentIT | 1 |
| JdbcProfileTopIT | 1 |
| **Total** | **19, 0 failures** |

**Proof it ran against PostgreSQL:** afterwards `suld_it` held 13 tables in `public`, created by the ITs.

### E-03: Asset and pack validation

`python3 tools/validation/run_all.py`:

| Validator | Output |
|---|---|
| registry | 12 assets, 11 custom_model_data ids |
| resource pack | format covers 75; 347 models; 85 item definitions; 30 equipment assets; 699 texture references |
| pack budget | uncompressed 10.1 MB, ZIP 6.6 MB, "pack budget OK" |
| world plan | 13 districts, 4 gates, 9 roads, 10 landmarks, 30 points |

Final line: `ALL VALIDATIONS PASSED`.

### E-04: Static greps (read-only)

| Check | Result |
|---|---|
| TODO/FIXME/XXX/HACK in Java | 0 |
| Empty `catch {}` | 0 |
| `printStackTrace` | 0 |
| Broad `catch (Exception/Throwable/RuntimeException)` in main | 33 (lane 2 reviewed them; all handled) |
| Blocking `.get(n, TimeUnit)` in plugin main | 7 sites, startup/shutdown/login only (`SuldPlugin:452`, `RelicService:97,99`, `ClanService:63`, `StyleService:115`, `DeathService:146-147`) |
| SQL built with `+` | 3 sites, each concatenating a constant `COLUMNS` string; values bound with `?` |
| Cyrillic string literals | api 584 in 43 files; plugin 2,401 in 99 files |
| `§` codes | 58 lines (96 codes) |
| `getConfig().get*/is*` | 61 |
| Async scheduler uses | 34 |
| `new Location(` literals | 16 |
| Secrets scan (`git ls-files` excluding binaries; patterns for passwords, API keys, private keys, Slack/Discord webhooks) | **no hits** (lane 2 also found only `$DB_PASS` env references) |
| `.github/` | absent |

### E-05: Live server boot

**Server.** Copied Paper 1.21.11-132 (`c5eb079`) plus the freshly built plugin jar into a scratch directory.
- `server.properties`: `online-mode=false`, port 25599, view 6, simulation 4.
- Plugin config: `auth.allow-insecure-offline-dev-mode: true` (**local dev only**) and `database.type: postgresql` pointing at the throwaway DB.
- JVM: `java -Xms2G -Xmx4G -XX:+UseG1GC -XX:StartFlightRecording=…,settings=profile`.

**Log lines:**
```
[SULD] Loading server plugin SULD v0.1.0-SNAPSHOT+50d5138
[SULD] [content] 32 mobs, 9 regions, 40 areas, 10 dungeons, 42 story chapters
[SULD] SULD storage ready (POSTGRESQL), migrations applied: 15
[SULD] SULD relics loaded: 2
[SULD]  INSECURE OFFLINE DEV MODE: accounts are NOT verified. ... NEVER expose this server publicly.
[SULD] Resource pack: self-hosting 6787 KiB (sha1 13567cefee04b509756f40a153ed0a20c1f9f3f5) on 0.0.0.0:8164
[SULD] [items] catalog: 220 items, 22 affixes, 33 sets, 44 loot tables
[SULD] model khasar: 26 bones, 25 displays, 14 clips      (30 rigs listed)
[SULD] [skills] 575 nodes loaded for 5 classes
[SULD] owners: logins are not verified (INSECURE), so owners are NOT auto-opped (they only get the cosmetic owner badge).
[SULD] Dungeon halls: world suld_halls ready; built slots 0.
[SULD] World border (start): 10000 x 10000 blocks centred on -16, 64 (radius 5000).
Done (57.101s)!
[SULD] [worldbuild] points_ok: 18/18 ... compiled_blocks_checked: 1268778 ... world_mismatches: 508
      (all 508 sampled were grass_block snowy=false vs snowy=true)
[SULD] Pre-generation: running 67 job(s), up to 22451 chunks.
```

- **Errors:** only `Failed to request yggdrasil public key … UnknownHostException: api.minecraftservices.com` (environment, see "Network limitations").
- **Restart after `kill -9`:** `migrations applied: 0`; `Done (13.875s)`.

### E-06: First join, menu behaviour (bot)

**Commands:**
- `node firstjoin.js Shine01 25599 0 70` (no pick)
- `node firstjoin.js Shine02 25599 close 45` (closes the menu at 5 s and 20 s)

**Trace (abridged):**
```
2.2 teleport -15,70,83
2.2 pack_offer http://127.0.0.1:8164/suld-13567cef….zip
2.2 chat «Дүрс багц илгээж байна…» / «Тавтай морил, Shine01! — SÜLD» / «Анхны алхам: ангиа сонгоно уу.»
2.2 open_window «Анги Сонгох — S…» type=2
2.5 teleport -15,70,83                 (plaza teleport; no server_close_window follows)
4.5 title «ТАВТАЙ МОРИЛ», subtitle «Урд байгаа хөвөгч самбар…», 7-line «ТОГЛОХ ЗААВАР» card
8.0 bossbar …, title «ИХ МОНГОЛД ТАВТАЙ МОРИЛ», subtitle «Заавар эхэллээ…»
4.2–62.2 state window=«Анги Сонгох…» (still open at every sample)
--- close test ---
5.8 client_close → 5.9 open_window «Анги Сонгох…»   (reopened)
20.8 client_close → 20.9 open_window «Анги Сонгох…»
```

**Limitation:** Mineflayer calls `acceptResourcePack()` and renders nothing. ONB-001 (prompt vs menu in a real client) is **not** testable this way.

### E-07: Class pick, `/daily` spam, relog

**Pick.** `CMDS="/daily@25|/daily@25.2|/daily@25.4|/balance@28|/profile@30" node firstjoin.js Shine03 25599 1 36`
```
20.7 class_slots 10,11,12,13,14 → click slot 10
20.7 server_close_window; «Та Баатар ангийг сонголоо!» «Эхний эрэл: Анхны Ан»
21.3 «Заавар: «Ангиа сонго» ✔»   22.3 «Заавар: «Ангийн зэвсгээ барь» ✔  +40 EXP, +20 ₮»
25.7/25.9/26.1 /daily → open_window ×3 (the GUI; no claim was clicked)
28.7 «Таны зоос: 20 ₮»
30.7 /profile: Анги: Баатар · Түвшин 1/60 · EXP 17% · HP 24/82 30% · Хуяг: 0 · Зоос: 20 ₮ · Эрэл: 1/42 Анхны Ан — 0/3
```

**Relog.** `node firstjoin.js Shine03 25599 0 18` with `/profile`:
```
0.7 «Анги: Баатар · Түвшин 1» … 3.0 the 7-line «ТОГЛОХ ЗААВАР» card again (repeats on every join)
4.7 /profile: identical values (EXP 17 %, 20 ₮, quest 1/42, HP 25/82)
```

**Analytics.** The log shows `session_start` / `logout_location` / `session_end` rows with per-session stats.

### E-08: Starting HP by class

```
PICK=1 … Hp01: «Та Мэргэн ангийг сонголоо!»  /profile 6 s later: HP 23/43 (55 %), Хуяг: 0
PICK=2 … Hp02: «Та Бөө ангийг сонголоо!»     /profile 6 s later: HP 23/48 (49 %), Хуяг: 0
(E-07) Баатар: 24/82 at +10 s, 25/82 after relog
```

**Cause** (`SkillTreeService.java:421-422`): max HP is raised, and current HP is only clamped down (`if (p.getHealth() > maxHp.getValue()) p.setHealth(...)`).

### E-09: Crash-loss test

**Steps.**
1. Player `Crash01` (Баатар) online.
2. Console `suld exp Crash01 150` → log `11:56:36 … Crash01: +150 EXP → түвшин 2 (+1)`.
3. 9 s later the DB row still read `level=1, exp_into_level=190, currency=20, version=10`.
4. `kill -9 <paper pid>` at 11:56:45, then restart.

**After restart:** `select name,level,exp_into_level,currency,version from suld_profiles where name='Crash01'` → `Crash01|1|190|20|10`.

**Result:** the level-up and +150 EXP were lost. The earlier tutorial EXP (40), the class and the coins had already been persisted.

**Not tested live:** the item-side windows (PERS-001/002/004/005); they need a scripted upgrade, ovoo visit or trade before the kill.

### E-10: Load tests (10 / 25 / 50 bots)

**Harness.** `mmo.js`: 211 lines, reused from an earlier session's scratchpad, port changed. **It is not in the repository**; see `10` item 10.

**Roles:**
- city: plaza walk, GUIs, chat
- explore: teleports outward
- combat: steppe-ring fights, skill clicks
- quest
- idle

The harness grants EXP, resistance, regeneration and strength by console so that bots survive.

**Runs:**
```
/suldperf start audit-10 ; node mmo.js ctl.sh 240 city=3,explore=2,combat=3,quest=1,idle=1 T ; /suldperf entities ; /suldperf stop
/suldperf start audit-25 ; node mmo.js ctl.sh 240 city=7,explore=5,combat=7,quest=3,idle=3 U ; …
/suldperf start audit-50 ; node mmo.js ctl.sh 240 city=14,explore=8,combat=14,quest=6,idle=8 V ; …
```

- **Pre-generation:** paused first (`/suldworld pregen pause` → «Түр зогслоо…»).
- **Reports:** `plugins/SULD/perf/audit-{10,25,50}.json`. The full numbers are in `05`.
- **Bot summaries:**
  - 10: joined 10, failed 0, kicked 0
  - 25: joined 25, failed 0, kicked 5 ("floating too long", explore bots at y=200)
  - 50: joined 50 in 51 s, failed 0, kicked 8 (same reason)
- **Server TPS line before the load:** `TPS from last 1m, 5m, 15m: 20.0, 20.0, 19.9`.

### E-11: JFR

`jcmd <pid> JFR.dump filename=load.jfr` (75 MB) after the three runs.
- `jfr view hot-methods`: the top entries are vanilla world-generation and palette code (`SimplexNoise.dot` 5.28 %, `ImprovedNoise.gradDot` 4.43 %, `Mth.lerp3` 4.14 %, `PerlinNoise.getValue` 2.87 %, …).
- `jfr view gc-pauses`: 612 pauses, total 17.7 s, median 21.4 ms, p95 73.8 ms, p99 218 ms. The three longest were 226, 233 and 390 ms.

### E-12: Prior audits re-verified

| Source | Rechecked in |
|---|---|
| `docs/PROGRESSION_EXPLOIT_AUDIT.md` (2026-10-07, `f123253`), 27 IDs | Each rechecked against current code; table in `07` §4 |
| `docs/ITEM_SYSTEM_AUDIT.md` (baseline `59ce447`) | Superseded by the item engine; its open items (GR-5, GR-6, DU-1) rechecked in `07` |
| `audit/*.json` status files (2026-10-07) | Treated as hypotheses. Several are stale: `visual-content-status.json` pack size 285.8 KiB vs 6.6 MB today; `progression-balance.json` "SPEC, not implemented" vs live |

### E-13: Web sources

- History and culture sources: `06`. Market and competitor sources: `09`.
- **Every** external fact is cited as "per WebSearch result snippet, <URL>, retrieved 2026-10-11".
- The search tool returns its own summary of result snippets. The cited pages were **not visited**.
- No page number or section number is given unless the snippet itself named it.

### E-14: Network limitations (exact errors)

| Target | Error | Affects |
|---|---|---|
| `https://whc.unesco.org/en/list/1081` (WebFetch) | `getaddrinfo ENOTFOUND whc.unesco.org` | UNESCO text cited via snippets only |
| `https://www.minecraft.net/en-us/usage-guidelines` (WebFetch) | `getaddrinfo ENOTFOUND www.minecraft.net` | Usage Guidelines **not read**; flagged for manual review (`09` §3.8) |
| `https://docs.papermc.io/paper/profiling/` | DNS/403 in recon (not retried; spark's in-server notice was seen in the boot log) | Profiling done with JFR and `/suldperf` instead of spark |
| `api.minecraftservices.com` (Paper boot) | `UnknownHostException` | **Online-mode authentication could not be tested**; only offline dev mode ran |

## Reproduce this audit

1. `git checkout 50d5138`
2. `./gradlew build --rerun-tasks` (E-01) and `python3 tools/validation/run_all.py` (E-03).
3. Start a throwaway PostgreSQL 16 on a free port and run the ITs with `SULD_TEST_PG_URL` (E-02).
4. Copy a Paper 1.21.11 jar and the built plugin to a scratch directory **outside the repo**. Set `online-mode=false` and `allow-insecure-offline-dev-mode: true` **for local testing only, never on a reachable host**. Point `database` at the throwaway DB and start it.
5. `npm i mineflayer@4` in a separate directory, save the script below as `firstjoin.js`, and run E-06 to E-09.
6. For load: a role-based bot script (`mmo.js`, to be committed under `tools/loadtest/`) plus `/suldperf start|stop` (E-10).

### firstjoin.js (as used)

```js
// Audit bot (local offline-mode dev server only). Records the first 90 s of a brand-new player:
// every chat/actionbar/title line, every window open/close, position, and whether a class menu is still open.
//   node firstjoin.js <name> <port> [0|1|close] [seconds]     env: PICK=<slot index>, CMDS="/cmd@sec|…"
const mineflayer = require('mineflayer');
const [name, port, pickS, secsS] = process.argv.slice(2);
const pick = pickS === '1', closeIt = pickS === 'close', secs = Number(secsS || 90);
const t0 = Date.now(); const ts = () => ((Date.now() - t0) / 1000).toFixed(1);
const ev = [];
const log = (k, v) => ev.push([ts(), k, v]);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(port), username: name, version: '1.21.11', auth: 'offline', hideErrors: true });
bot.on('resourcePack', (url) => { log('pack_offer', String(url).slice(0, 80)); try { bot.acceptResourcePack(); } catch (e) {} });
bot.on('messagestr', (m, pos) => log(pos === 'game_info' ? 'actionbar' : 'chat', m.slice(0, 200)));
bot._client.on('set_title_text', (p) => log('title', JSON.stringify(p.text).slice(0, 160)));
bot._client.on('set_title_subtitle', (p) => log('subtitle', JSON.stringify(p.text).slice(0, 160)));
bot._client.on('boss_bar', (p) => { if (p.title) log('bossbar', JSON.stringify(p.title).slice(0, 120)); });
bot._client.on('open_window', (p) => log('open_window', JSON.stringify(p.windowTitle).slice(0, 120) + ' type=' + p.inventoryType));
bot._client.on('close_window', () => log('server_close_window', ''));
bot._client.on('position', (p) => log('teleport', [p.x, p.y, p.z].map(v => Math.round(v)).join(',')));
bot.on('kicked', (r) => log('kicked', String(r).slice(0, 200)));
bot.on('error', (e) => log('error', String(e.message || e)));
bot.once('spawn', async () => {
  for (const c of (process.env.CMDS || '').split('|').filter(Boolean)) setTimeout(() => { const t = c.split('@')[0]; log('cmd', t); bot.chat(t); }, Number(c.split('@')[1] || 0) * 1000 || 30000);
  log('spawn', [bot.entity.position.x, bot.entity.position.y, bot.entity.position.z].map(Math.round).join(','));
  for (const at of [2, 5, 10, 20, 40, 60, 85]) {
    if (at >= secs) break;
    setTimeout(async () => {
      log('state', 'window=' + (bot.currentWindow ? JSON.stringify(bot.currentWindow.title).slice(0, 80) : 'none') + ' held=' + (bot.heldItem ? bot.heldItem.name : 'none'));
      if (closeIt && (at === 5 || at === 20) && bot.currentWindow) { log('client_close', ''); bot.closeWindow(bot.currentWindow); }
      if (pick && at === 20 && bot.currentWindow) {
        const slots = bot.currentWindow.slots.slice(0, bot.currentWindow.inventoryStart).map((s, i) => s ? i : -1).filter(i => i >= 0);
        log('class_slots', slots.join(','));
        await bot.clickWindow(slots[Number(process.env.PICK || 0)], 0, 0).catch(e => log('click_err', String(e)));
      }
    }, at * 1000);
  }
});
setTimeout(() => { log('end', ''); bot.quit(); setTimeout(() => { console.log(JSON.stringify({ name, ev }, null, 0)); process.exit(0); }, 800); }, secs * 1000);
```

## What could not be verified, and what evidence is still needed

| Claim / question | Why not verified | Evidence needed |
|---|---|---|
| ONB-001: the pack prompt survives the class menu | Bots don't render or prompt | One vanilla 1.21.11 client join with "Prompt" |
| HUD, menu and painted-GUI legibility at GUI scales 1–4; no-pack rendering | No client | `docs/HUD_MANUAL_QA.md` 16-row pass at 1366×768 and 1920×1080 |
| Online-mode (Microsoft) authentication end to end | Mojang APIs unreachable here | A genuine account join on the deployed host; an offline client refused |
| Capacity at 25/50/100 on the target host | No target host; bots shared the CPU | `05` procedure |
| Cause of the 2.9–10.5 s stalls | Not isolated | spark/JFR thread samples around a teleport burst |
| Origin of 256 falling-block entities | Not traced | Entity data dump |
| Item-side crash windows (PERS-001/002/004/005) | Not scripted live | `kill -9` drills per scenario |
| First-fight lethality per class | Computed from formulas, not fought | First-fight bot QA (20 × 5 classes) |
| Balance pace to level 60 (~200 h) | Simulator only | Closed-alpha telemetry |
| Cultural reception of HIST-023/024/025/027 | Needs native reviewers | Review panel + usability test (`03`) |
| *Secret History* §202 and colophon wording | Snippets only | Primary text or a scholarly translation |
| Mongolian law on commercial use of Chinggis Khaan's name | Status unknown | legalinfo.mn / a Mongolian lawyer |
| Minecraft Usage Guidelines compliance of the credit store | Page unreachable | The owner reads the current Guidelines and EULA |
| Bedrock (Geyser) experience | No Bedrock client | A Bedrock join test |
| Competitor details beyond search snippets | Pages not fetched | Direct reading of the cited pages |
| Real player retention | No players | Funnel events (`03`) during the closed alpha |
