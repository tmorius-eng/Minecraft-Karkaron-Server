# 08: Security, persistence and operations

- **Audited commit:** `main` @ `50d5138`.
- **Evidence:** the code path is read end to end unless a finding says otherwise. Live checks are marked **MEASURED** (E-nn in `11`).

## Verdict

**The data-integrity core is sound and better than typical.** All of the following were confirmed:
- PostgreSQL schema V1–V15, with true compare-and-set for relics and death state.
- "Newer version wins" profile upserts, serialised per UUID.
- Transactional `/pay`.
- An atomic credits balance with `CHECK ≥ 0`.
- A single dungeon completion per run.
- Menus that cancel every click and drag.
- Fail-closed login on storage errors.
- No SQL injection; no committed secrets.

**MEASURED this audit:**
- the 19 JDBC ITs pass on real PostgreSQL 16.15;
- a live boot applied 15 migrations;
- a relog kept class, EXP, coins and quest;
- insecure dev mode refused to auto-OP owners.

**The weaknesses are at the seams:**
- **Item side vs DB side.** The vanilla player file and SQL are separate stores. That leaves crash windows (PERS-001/002/004/005), and EXP is lost on a crash (PERS-003). A level-up 9 s before `kill -9` was lost live (E-09).
- **Operator-facing defaults:** the shipped owner name `qeevr_`, irrevocable owner OP, a non-idempotent credit grant.
- **Operations that have never run for real:** no deployment exists, backups are skewed and on-box, the restore drill was done at schema v2, third-party plugins are unpinned, and monitoring is only `health-check.sh`.

No P0. Two items are launch gates (P1):
- **OPS-001** (backup/restore);
- **SEC-003** (credits idempotency), before any store goes live.

## Operations overview (STATIC + docs)

| Area | State | Evidence |
|---|---|---|
| Deployment | **Never deployed:** "Nothing here has been deployed yet: no VPS exists" | `DEPLOYMENT.md:3-4` |
| Deploy automation | `deploy/deploy.sh` (Ubuntu 24.04, idempotent, `--dry-run`): refuses `ONLINE_MODE≠true`; forces `auth.allow-insecure-offline-dev-mode=false`; nginx for the pack (5 r/s); ufw, fail2ban, systemd, backup timer, health check | `deploy/*`; not executed in this audit |
| Authentication | Microsoft-verified online mode is required in production; offline dev mode prints a red banner and grants no OP (MEASURED E-05). Online mode could not be exercised here: `api.minecraftservices.com` does not resolve | E-05, E-14 |
| Permissions | Every `/suld` admin sub checks `suld.admin`; `itemsadmin`/`suldperf` rely on plugin.yml (SEC-004). Default group gets EssentialsX `sethome.multiple`/`tpa` (GAME-001, `07`) | §Step A #10 below |
| Anti-cheat | GrimAC 2.3.74 via a reflection bridge. Flags are forgiven only inside SÜLD movement windows, with a budget of 60 then 6/s. Punishments are kicks only (OPS-006). Version pinned in docs, not in `plugins.json` (OPS-002). No permanent punishment is automated, which is good | `docs/ANTICHEAT.md`, `deploy/grim/punishments.yml` |
| Monitoring | `deploy/health-check.sh` only. No metrics export, no alerting, no log shipping. Analytics are rows in `suld_analytics_events` | `deploy/` |
| External dependencies | 23 third-party plugins (core, hardening, optional, dev profiles), fetched as "latest compatible". Gameplay does not depend on any of them (matches `ARCHITECTURE.md`) | `deploy/plugins/plugins.json` |
| Payments | Not built. Credits are delivered only by the console `/credits give` (SEC-003). Instant revive is design-only and correctly not built | `docs/PAYMENT_ARCHITECTURE.md:3-8` |
| Data retention / privacy | Analytics store UUIDs and events; no privacy notice for players (minors are a likely audience) | `docs/ANALYTICS.md`; see `09` |

## Exploit scenarios (summary; full detail below)

| Scenario | Outcome today | Finding |
|---|---|---|
| Crash right after a class-armour upgrade or an Orb respec | Tier or respec kept **and** materials or Orb returned | PERS-001 |
| Crash right after an ovoo or site first visit | EXP can be paid twice | PERS-002 |
| Store retries `/credits give` | Credits granted twice | SEC-003 |
| DB failure during a trade commit | Items swapped, coins reverted; the clamp revert can mint coins | PERS-004 |
| Restore from a nightly backup | World and DB from different instants: duplicates or losses | OPS-001 |
| Former owner rejoins after removal | Re-opped automatically | SEC-001 |
| Two stalled HTTP readers on the self-hosted pack port | New players cannot download the pack (non-nginx installs) | OPS-004 |
| Dupe via menus, drag, number keys or hotbar swap | **Blocked:** all cancelled | Verified OK |
| Relic duplicated across a relog or crash | **Healed** on join/validate from the DB record | Verified OK |
| Negative or overflowing `/pay` | **Rejected** | Verified OK |

## Detailed review

Paths are relative to the repo root. `P/` = `suld-plugin/src/main/java/mn/suld/plugin/`, `R/` = `suld-plugin/src/main/resources/`.

### Summary table

| ID | Severity | Confidence | Title |
|---|---|---|---|
| SEC-001 | Medium (P2) | Confirmed | Removing an owner from `config.yml` does not revoke OP. The pinned UUID in `owners.yml` keeps re-granting OP on every join |
| SEC-002 | Medium (P2) | Strongly Supported | Default owner `qeevr_` ships in the jar defaults. Deleting the `owners:` key falls back to it. Only `owners: []` disables it |
| SEC-003 | Medium (P2; P1 once a store is live) | Confirmed | `/credits give` has no idempotency key, no DB audit row and a name-based target; a store retry grants twice |
| PERS-001 | Medium (P2) | Confirmed | Item-consuming upgrades commit the DB side but never write the player file. A crash returns the materials (class armour) or the Orb (respec) |
| PERS-002 | Low (P3) | Confirmed | Reward and "claimed" guard stored separately (PDC vs SQL, style row vs profile row). A crash or partial failure can pay twice or pay nothing |
| PERS-003 | Low (P3) | Confirmed (PARTIAL vs. claim) | EXP/quest crash-loss window: about 30 s for the killer, up to 300 s for party sharers and vanilla kills; every save writes the whole row |
| PERS-004 | Low (P3) | Confirmed | Trade: items are swapped before the coin transaction. A failed transaction reverts only the coins, and the clamp-at-0 revert can mint coins |
| PERS-005 | Low (P3) | Confirmed | Mailbox: delivery deletes the file before the player file is saved (crash means loss). A failed async rewrite re-delivers (dup) |
| PERS-006 | Low (P3) | Confirmed | Clan `version` column is written but never compared; safe only because there is a single writer |
| OPS-001 | **High (P1 launch gate; lane rated P2)** | Confirmed | Backups: DB dump taken before the world flush, no SÜLD flush, stored on-box only, restore verified only at schema v2 |
| OPS-002 | Medium (P2) | Confirmed | 23 third-party plugins fetched "latest compatible" on every `deploy.sh`/`update.sh`, with no lock file; some Hangar downloads skip the hash check |
| OPS-003 | Low (P3) | Confirmed | `config.yml` header advertises `/suld reload`, which does not exist. `/suldpack reload` reloads the YAML but rebuilds only the pack settings |
| OPS-004 | Low (P3) | Strongly Supported | Built-in pack HTTP server (non-deploy installs): 0.0.0.0:8164, 2 threads, no timeouts or rate limit. Two stalled readers block pack serving |
| OPS-005 | Low (P3) | Confirmed | A failure while building `SuldServices` leaks the Hikari pool and executors; with H2 the database file lock stays held |
| OPS-006 | Low (P3) | Confirmed | Grim punishments are kicks only (no ban/escalation). Design choice, documented |
| OPS-007 | Low (P3) | Confirmed | No index serves the analytics purge (`at < ?`) or the leaderboards (`ORDER BY level/currency`) |
| THR-001 | Low (P3) | Confirmed | Async threads read Bukkit state: chat reads PDC/`isOp`, style flush calls `Bukkit.getPlayer` |
| SEC-004 | Low (P3) | Confirmed | `/itemsadmin` and `/suldperf` rely only on plugin.yml `permission:` (enforced by Paper). Defense-in-depth only |

Candidates refuted or reduced (details in Step A): #1 "migration re-adds the owner every boot" is refuted. #5 "ClanService runTask during disable" is effectively not reachable. #6 "dungeon shutdown after the profile flush loses rewards / strands players" is refuted.

---

### Step A: verification of the candidate list

| # | Verdict | Real location and decisive code |
|---|---|---|
| 1 | **PARTIAL** | Default `R/config.yml:207-208` `owners:\n  - qeevr_`. The migration is `P/SuldPlugin.java:530` `if (!getConfig().isSet("owners")) getConfig().set("owners", List.of("qeevr_"));`. It is inside `migrateToV4`, so it runs only for `config-version < 3`; a fresh install already has v6. **Migration does not re-add it every boot**, and `owners: []` persists. **But** see SEC-002: deleting the key falls back to the jar default. The name is pinned to the first verified UUID (`P/auth/OwnerService.java:98-107`). OP is granted only when `mode.verified()` (:84). See SEC-001 for the revocation bug at :95. |
| 2 | **CONFIRMED** | `P/command/MenuCommands.java:58-91` → `P/style/StyleService.java:418-438`. The only record is `Bukkit.getLogger().info("[SULD] credits ...")` (MenuCommands:87); there is no `services.audit()` call. Intended caller: "a store (e.g. Tebex) runs from the console after payment" (StyleService:416). `docs/PAYMENT_ARCHITECTURE.md:3-8` says payment is DESIGN ONLY and credits are "delivered by a console command (`credits give`)". The DB update itself is atomic (`JdbcStyleRepository.java:113-154`), and the style upsert never writes `credits` (:158-166), so a flush cannot overwrite the balance. |
| 3 | **PARTIAL** | `AUTOSAVE_SECONDS = 300L` (`P/SuldPlugin.java:27,398-399`). Every save writes the **whole row**: `JdbcProfileRepository.writeRow` binds level, exp, currency, quest, skill, equipment, class gear and endgame (`P/persistence/JdbcProfileRepository.java:228-261`). So any coin save also saves EXP. The killer's mob-kill EXP is saved at most every 30 s (`P/combat/CombatListener.java:378-385`); party sharers (:316-327) and vanilla kills (:395-408) only on level-up. kill -9 loses at most 30 s for an active killer, up to 300 s for others. See PERS-003. |
| 4 | **CONFIRMED, low risk** | `P/persistence/SqlDialect.java:92-111`: `exp = VALUES(exp), version = VALUES(version)` with no `WHERE version`. All writes go through the single-thread `suld-clan-io` (`P/SuldServices.java:158-162`). Snapshots are taken on the main thread (`P/clan/ClanService.java:324-330`). A second server is the only real concurrent writer. See PERS-006. |
| 5 | **CONFIRMED (low) / REFUTED (runTask)** | The async chat path is `P/clan/ChatListener.java:44-61` → `StyleFormat.staffOf` (`P/ui/StyleFormat.java:89-97`), which reads PDC and `p.isOp()`. `StyleService.flush` (async, `P/style/StyleService.java:76,159-174`) calls `Bukkit.getPlayer(id)` at :165. `ClanService.saveNow` → `runTask` in `exceptionally` (`P/clan/ClanService.java:324-330`): shutdown uses `ClanService.shutdown()` (:313-322), which calls `repository.save` directly, not `saveNow`. No listener fires after disable, because Paper disables plugins before kicking players. That leaves a save already in flight that fails during disable: the `IllegalPluginAccessException` is swallowed inside the future, and the retry is lost (the clan was already removed from `dirty`). Negligible. |
| 6 | **REFUTED (rewards/strand) / PARTIAL (construction leak)** | `DungeonService.shutdown` (`P/dungeon/DungeonService.java:684-692`) only `fail()`s runs. Failing grants and refunds nothing, and entry has no cost, so running after the profile flush loses nothing. During disable `cleanup` calls `halls.release(hall)` directly (:668), which tries `SafeTeleport` (`P/dungeon/DungeonHalls.java:298-309`). If that does not land, `DungeonHalls.onJoin` (:621-632) rescues players found in the halls world on the next join (runs are in memory, so `inRun` is false). Halls `shutdown()` (:188-195) only stops the state writer and removes gate entities; `release()` does not use the writer. The partial-construction leak is real: `P/SuldPlugin.java:66-78` plus :437-439 `if (services == null) return;`. See OPS-005. |
| 7 | **CONFIRMED** | `R/config.yml:3` "Edit here, then /suld reload". `SuldCommand` switch `P/command/SuldCommand.java:36-86` has no `reload`. `/suldpack reload` (`P/command/ResourcePackCommand.java:42-51`) does `plugin.reloadConfig(); SuldConfig cfg = SuldConfigFactory.load(...); packs.updateSettings(cfg.resourcePack());`. It rebuilds the config but applies only the pack settings, and the ~55 direct `getConfig()` readers pick up new values ad hoc. See OPS-003. |
| 8 | **CONFIRMED (low)** | Ovoo: the guard is player PDC `ovoo_<id>` (`P/region/OvooService.java:199-226`). `set(dayKey…)` is at :212 and EXP + `profiles().save(pr)` at :218-226. `ovoo.yml` holds only ovoo locations. Historic site: the guard is PDC `seenKey` (`P/region/HistoricSiteService.java:266-269`) with EXP + save at :294-296. Daily: style `claimDaily` + `profiles().save` + `styles().saveNow` are separate writes on separate executors (`P/reward/DailyService.java:113-137`); the comment "goes to storage together" is not true. Tasks: same pattern at `P/reward/TaskService.java:76-104`. Level rewards: `P/style/StyleService.java:386-410` (`changed(p)` → style save; `saveProfile`). Region discovery: `P/mob/RegionSpawner.java:262-319` saves nothing immediately (style flush 60 s, profile 30/300 s). See PERS-002. |
| 9 | **CONFIRMED, scoped** | `P/resourcepack/ResourcePackService.java:59-110`: `HttpServer.create(new InetSocketAddress(settings.hostBind(), settings.hostPort()), 16)` with `newFixedThreadPool(2…)`. It starts only when `url` is blank and `self-host.enabled` (:60). **`deploy.sh` sets `resource-pack.url` to nginx** (`deploy/lib.sh:240-245`), and nginx has `limit_req … rate=5r/s` (`deploy/templates/nginx-pack.conf.tpl:2,14`). So production is not exposed; jar-only installs are (config default `bind: 0.0.0.0`, `port: 8164`, `R/config.yml:95-98`). The status map `ConcurrentHashMap` (:47) is never cleared on quit: tiny growth, plus a stale `SUCCESSFULLY_LOADED` for a moment after rejoin. See OPS-004. |
| 10 | **CONFIRMED OK** | `itemsadmin` → `suld.admin.items` and `suldperf` → `suld.admin.perf` in plugin.yml. Registered through `getCommand(name).setExecutor` (`P/SuldPlugin.java:571-584`), so Bukkit's `PluginCommand.execute` `testPermission` applies. Every in-code `suld.admin*` gate checked: `SuldCommand` (13 checks; every mutating sub gated: exp, coins, badge, setrank, quest, content, endgame, spawnmob, props, grim, guide, auth), `credits give/take`, `dungeon grant/abort`, `relic` admin subs, `suldpack reload/force`, `suldevent`. **No admin command is reachable without permission.** See SEC-004. |
| 11 | **CONFIRMED** | `deploy/plugins/plugins.json` has 23 `"id"` entries and no versions. `tools/plugins/fetch_plugins.py:116-175` picks the newest listed build; the Hangar hash is optional (`"hash": … if info.get("sha256Hash") else None`, :175). Fetch runs on deploy **and on every update** (`deploy/deploy.sh:175`, `deploy/update.sh:150`). Backups go to `BACKUP_DIR=/opt/suld/backups` (`deploy/suld.env.example:14`), daily 04:30, on-box. Restore was verified only as "6 tables, schema v2" (`DEPLOYMENT.md:287`, current schema is V15). Grim: `deploy/grim/punishments.yml` uses `kick` commands only. See OPS-001, OPS-002, OPS-006. |

---

### Findings

#### SEC-001: Owner OP cannot be revoked by editing `config.yml`; a pinned UUID is an owner forever
- **Severity:** Medium (P2). It becomes an incident the day a staff owner is offboarded.
- **Confidence:** Confirmed.
- **Type:** confirmed defect.
- **Evidence:**
  - `P/auth/OwnerService.java:93-95`:
    ```java
    UUID id = p.getUniqueId();
    if (uuids.contains(id) || pinned.containsValue(id)) return true;
    ```
    `pinned` is loaded from `plugins/SULD/owners.yml` regardless of the current `owners:` list (:60-67). `apply()` re-grants OP on every join (:110-117). The `config.yml` comment (`R/config.yml:203-206`) never says that removal also needs an `owners.yml` edit.
- **Reproduction:**
  1. Online-mode server, `owners: [Alice]`; Alice joins, gets pinned and opped.
  2. The admin sets `owners: []`, runs `deop Alice` and restarts.
  3. Alice rejoins and is OP again (log: "owners: Alice is an owner, granted OP").
- **Impact:** A removed owner keeps full OP. `deop` and `op.sh --deop` are silently undone on the next join. The `suld.badge.owner` attachment is also re-added.
- **Root cause:** The pin file acts as an allow-list instead of a name→UUID map that is consulted only for names still configured.
- **Recommended fix:** Count a pin only while its name is still in `names`:
  ```java
  if (uuids.contains(id)) return true;
  String name = …; if (!names.contains(name)) return false;
  ```
  Check `pinned.get(name)` for equality and drop pins for names no longer configured (rewrite `owners.yml`). **Alternative:** Make UUID entries the only supported form. Have `deploy.sh` resolve `ADMIN_PLAYERS` to UUIDs once, and drop name pinning.
- **Regression test:** Unit-test `OwnerService` with a fake Plugin/Player. Pin "alice"→U1, construct with `owners=[]`, and assert `isOwner(U1)==false`. **Acceptance:** removing an entry from `owners:` revokes auto-OP on the next start; documented in `config.yml`.
- **Effort:** S. **Fix risk:** low. The owner should re-check their own entry after upgrading.

#### SEC-002: Default owner `qeevr_` comes back from the jar defaults when the key is removed
- **Severity:** Medium (P2).
- **Confidence:** Strongly Supported. Bukkit `MemorySection.getList(path)` falls back to `getDefault(path)`, and `JavaPlugin.reloadConfig()` installs the jar's `config.yml` as defaults. That is standard Bukkit behaviour, but not exercised at runtime here.
- **Type:** design concern.
- **Evidence:**
  - Default value at `R/config.yml:207-208`.
  - Read at `P/SuldPlugin.java:348-349`: `getConfig().getStringList("owners")`.
  - Also `deploy/suld.env.example:49` `ADMIN_PLAYERS="qeevr_"` and `scripts/run-test-server.ps1:59`.
- **Real exposure:**
  - Only on verified logins (online mode or HMAC Velocity, `OwnerService.java:73-89`), so the account actually named `qeevr_` must log in. On offline/dev servers it gets only a cosmetic badge.
  - The name is pinned to the first UUID (:98-107), so a later rename-squatter cannot take it.
  - What remains:
    - (a) Every third-party or copied install of this jar auto-OPs the project owner's account (a backdoor from the operator's point of view).
    - (b) Before the first pin, whoever holds the name `qeevr_` at that moment gets OP. That could be someone else if the owner ever renames.
    - (c) An admin who "removes" the entry by deleting the key still has it.
- **Reproduction:**
  1. Delete the `owners:` block from `plugins/SULD/config.yml` and restart.
  2. The `qeevr_` account joins and is opped.
- **Impact:** Unintended operator access on servers not run by the owner. Owner-controlled on the official server, so low there.
- **Root cause:** A personal account name hard-coded as the shipped default, in three places (resource, migration, env example).
- **Recommended fix:** Ship `owners: []`, remove the name from the migration at :530, and have `deploy.sh` write `owners:` (ideally UUIDs) from `ADMIN_PLAYERS`. **Alternative:** Keep the default but log a SEVERE "default owner active" warning until an owner is explicitly configured.
- **Regression test:** A resource test asserting that `config.yml` `owners` is empty. A migration test asserting that v1→v6 never adds a name. **Acceptance:** a fresh jar install grants OP to nobody.
- **Effort:** S. **Fix risk:** low. Make sure deploy still sets the real owner.

#### SEC-003: `/credits give` is not idempotent or audited and resolves players by name
- **Severity:** Medium (P2). P1 the moment a store or webhook drives it.
- **Confidence:** Confirmed.
- **Type:** missing feature / design concern.
- **Evidence:**
  - `P/command/MenuCommands.java:68-90`: permission check, then `UUID id = resolve(a[1]); … long amount = Long.parseLong(a[2]); … Bukkit.getLogger().info("[SULD] credits " + …); services.styles().giveCredits(id, delta, s::sendMessage);`.
  - There is no transaction ID argument and no `services.audit().record`. Compare `/item` admin give, which audits (`ItemCommands.java:694`).
  - `resolve()` (:100-110) accepts a name and uses `getOfflinePlayerIfCached`, so an unknown name prints "unknown player…" and grants nothing.
  - Documented caller: `P/style/StyleService.java:414-417` "a store (e.g. Tebex) runs from the console after payment".
  - `docs/PAYMENT_ARCHITECTURE.md:3-8,61` plans idempotency, but it is unbuilt.
- **Reproduction:**
  1. From the console, run `credits give <uuid> 600` twice (as a store retry after a timeout or crash would).
  2. The balance rises by 1200.
  3. `credits give NeverJoined 600` grants nothing, and the store side still sees "command executed".
- **Impact:**
  - Double grants of paid currency on retry or replay.
  - Lost purchases for buyers who never joined, or a renamed buyer resolving to a cached old account.
  - No DB audit trail to reconcile chargebacks (`credits take` is also refused when the balance is insufficient: `StyleService.java:424-426`).
- **Root cause:** The interim console-command delivery skips the ledger the payment design requires.
- **Recommended fix:** `/credits give <uuid> <amount> <txid>`, plus a `suld_credit_grants(txid PK, player_uuid, amount, at, actor)` insert inside the same transaction as the `UPDATE` in `JdbcStyleRepository.addCredits`. A duplicate `txid` becomes a no-op that reports "already applied". Accept UUIDs only from the console, and record an `AuditEvent`. **Alternative:** Until a store exists, keep the command OP-only, document "UUID + manual only", and add the audit record now.
- **Regression test:** Repository test on H2: `addCredits(id, 600, "tx1")` twice → balance 600, second call reports a duplicate. Command test: a name argument from the console is rejected. **Acceptance:** replaying any store command is harmless, and every grant has a DB audit row.
- **Effort:** M (migration V16 + both dialects). **Fix risk:** low.

#### PERS-001: Item-consuming upgrades write the DB side but never the player file (materials/Orb restored after a crash)
- **Severity:** Medium (P2).
- **Confidence:** Confirmed (code path). The crash window depends on the vanilla autosave, which I did not check at runtime.
- **Type:** confirmed defect (violates the project's own `PlayerDataSaves` rule, `P/item/PlayerDataSaves.java:13-26`).
- **Evidence:**
  - `P/item/ClassArmor.java:418-433`:
    ```java
    pr.addCurrency(-next.coins());
    if (next.material() != null) take(p, next.material(), next.materials());
    pr.classGear(g.withTier(next));
    services.profiles().save(pr);
    ```
    There is no `PlayerDataSaves.now(p)` (grep finds none in ClassArmor).
  - Orb respec: `P/gui/SkillMapMenu.java:628-645`, `used = OrbOfOblivion.consumeOne(pl); … st().reset(pl, category, used)` → `SkillTreeService.afterChange` → `profiles().save(pr)` (`P/skill/SkillTreeService.java:384-392`). There is no player-file write.
  - The deploy does not tune the vanilla player save (`deploy/paper-tuning.conf` has no save keys), so the item side reaches disk only on the next world autosave (~5 min default) or on quit.
- **Reproduction:**
  1. Hold the materials and coins for the next class-armour tier.
  2. Upgrade at the smith; the tier is committed in `suld_profiles.class_gear`.
  3. Within a few minutes, `kill -9` the server (or crash it).
  4. Restart: the tier is kept and the materials are back in the inventory. Same with an Orb respec: respec kept, Orb back.
- **Impact:** Free repeat of the most expensive gear-progression sink (and of Orbs) on every crash. Not player-triggerable unless a player can crash or lag-kill the server, but every real crash pays out.
- **Root cause:** Items-consumed-for-DB-state paths were not routed through `PlayerDataSaves.itemsForCoins`.
- **Recommended fix:** Call `PlayerDataSaves.now(p)` right after `take(...)` / `consumeOne` and before `profiles().save`. Better: route both paths through `itemsForCoins(...)` (it does exactly that). Grep for every `take(`/`setAmount(` + `profiles().save` pair. **Alternative:** Represent upgrade materials in SQL (a material ledger) so both sides commit in one transaction.
- **Regression test:** A MockBukkit-style test with a `PlayerDataSaves` spy: `ClassArmor.upgrade` and the Orb reset must call `Player.saveData()` before `ProfileService.save`. **Acceptance:** no code path both removes items and commits DB state without the player-file write first.
- **Effort:** S. **Fix risk:** low (one synchronous `saveData` per upgrade).

#### PERS-002: Reward guards are stored apart from the reward (double pay or no pay after a crash or partial failure)
- **Severity:** Low (P3). Small amounts; needs a crash or DB failure at the right moment.
- **Confidence:** Confirmed.
- **Type:** design concern.
- **Evidence:**
  - Ovoo first visit: guard in player PDC (`P/region/OvooService.java:212`), EXP saved to SQL immediately (:218-226).
  - Historic site: PDC `seenKey` (`P/region/HistoricSiteService.java:269`), then `profiles().save(pr)` (:296).
  - Daily, tasks and level rewards: guard in `suld_player_style` (`claimDaily`, `taskProgress`, `claimLevel`), coins/EXP in `suld_profiles`, each written by a separate executor. Sites: `P/reward/DailyService.java:120-127`, `P/reward/TaskService.java:89-103`, `P/style/StyleService.java:394-409`. A failed style write is only `markDirty` + retry on the next 60 s flush (`StyleService.java:202-215`).
  - Region and area discovery: no immediate save of either side (`P/mob/RegionSpawner.java:263-319`).
- **Reproduction:**
  1. Ovoo: first-visit an ovoo; the profile row is committed at once.
  2. `kill -9` before the next vanilla player save.
  3. Restart: the PDC is gone, so walk the ovoo again and get first-visit EXP again (plus a second daily blessing).
  4. Daily: block the DB for the style write only (e.g. a lock on `suld_player_style`), claim, then crash before the retry. After restart you can claim again.
- **Impact:** Minor EXP/coin inflation or loss, a few times per crash.
- **Root cause:** Guards live in whichever store the feature started in.
- **Recommended fix:**
  - Ovoo/HistoricSite: call `PlayerDataSaves.now(p)` after setting the PDC guard and before the profile save (guard first).
  - Daily/task/level: move the claim fields into the profile row, or write both rows in one transaction (a `saveTogether`-like profile+style method).
  - **Alternative:** Keep the stores but persist the guard first and grant only after its write completes (the same pattern as `coinsForItem`).
- **Regression test:** A test with a fake repository that fails the style save. Assert the profile save is not committed, or that the reward is withheld. **Acceptance:** for each reward, the guard is durable no later than the reward.
- **Effort:** M. **Fix risk:** low to medium (schema change for the transactional variant).

#### PERS-003: EXP/quest-progress crash-loss window
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** design concern.
- **Evidence:**
  - Whole-row upsert at `P/persistence/JdbcProfileRepository.java:228-261`.
  - Killer save throttle: `CombatListener.java:378-385`, "at most one per 30 s per player".
  - Party sharer EXP is saved only on level-up (:316-327). Vanilla-mob EXP likewise (:404-407).
  - Autosave every 300 s (`SuldPlugin.java:27,398-399,557-569`).
- **MEASURED (E-09):** `/suld exp Crash01 150` levelled the player from 1 to 2 at 11:56:36. `kill -9` at 11:56:45, then a restart: the DB still showed level 1, exp 190, profile version 10. The +150 and the level-up were lost. Class, coins and quest were already saved.
- **Reproduction:** Kill mobs as a party member for 4 minutes without levelling, then `kill -9`. That EXP and any quest progress tied to it is gone.
- **Impact:** Up to 5 min of EXP for non-killers; ≤30 s for killers. Coins and rewards are saved immediately, and they carry EXP too.
- **Root cause:** The interval was chosen to cap write load.
- **Recommended fix:** Lower `AUTOSAVE_SECONDS` to 60 (writes are one upsert per dirty profile, off-thread), or apply the same 30 s throttle to sharers. **Alternative:** Make it a config key.
- **Regression test:** Unit-test that the sharer path schedules a save within N seconds. **Acceptance:** worst-case EXP loss ≤ 60 s for every source.
- **Effort:** S. **Fix risk:** low.

#### PERS-004: Trade swaps items before the coin transaction; a failed transaction leaves a one-sided trade and the revert can mint coins
- **Severity:** Low (P3). Needs a DB failure.
- **Confidence:** Confirmed.
- **Type:** design concern.
- **Evidence:**
  - `P/trade/TradeService.java:405-420`: items are given (`giveBack`) and both player files written (`PlayerDataSaves.now`), then `services.profiles().transfer(payer, payee, …)`. On error the log says "NOT saved, balances restored" and the message says "coins returned (items were swapped)".
  - Revert in `suld-api/.../service/ProfileService.java:43-53`:
    ```java
    from.addCurrency(amount); to.addCurrency(-amount);
    ```
    `addCurrency` clamps at 0 (`suld-api/.../profile/PlayerProfile.java:183-187`). If the payee spent the coins before the transaction failed, the clamp keeps them, and the payer is refunded in full.
  - The same revert backs `/pay` (`P/command/CityCommands.java:355-363`).
- **Reproduction:**
  1. Trade item X for 5,000 coins.
  2. Make the transaction fail (DB connection drop or lock timeout during commit).
  3. The seller has lost X and has no coins.
  4. Variant: the payee spends the coins within the commit window, the transaction then fails, and the payer's refund plus the payee's purchase both stand.
- **Impact:** Rare loss or creation of value during DB incidents. The audit log line exists.
- **Root cause:** Items live in the player file and coins in SQL, with no compensation for the items. The revert is not conditional.
- **Recommended fix:** On transaction failure, reverse the item swap too while both players are online (the inverse `giveBack` + `now`). Otherwise queue the stacks to the mailbox. Make the revert `to.addCurrency(-min(amount, to.currency()))` and log any shortfall at SEVERE. **Alternative:** Commit coins first, then swap items (COINS → ITEMS, like the shop), refunding on failure.
- **Regression test:** A fake `ProfileRepository.saveTogether` that fails. Assert both inventories and balances equal the pre-trade state. **Acceptance:** no trade outcome where only one side moved.
- **Effort:** M. **Fix risk:** medium.

#### PERS-005: Mailbox delivery ordering (loss on crash, dup on a failed rewrite)
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** confirmed defect (edge).
- **Evidence:** `P/trade/Mailbox.java:43-55,70-85`:
  - `deliver` adds the items to the inventory, then `write(left)`. With `left` empty this is `Files.deleteIfExists` (sync); otherwise `AtomicYaml.write`, which is async when enabled (`P/persistence/AtomicYaml.java:22-36`). An `IOException` is only logged.
  - The player file is not saved after delivery.
  - `keep()` reads, then writes asynchronously, so two runtime `keep`s for one player before the first write lands lose the first batch (`Mailbox.java:34-40`). Runtime `keep` is rare (respawn hand-back to an offline player, `TradeService.java:529-533`); at shutdown it is synchronous.
- **Reproduction:**
  1. (Loss) Have a mailbox entry, join, receive it, then `kill -9` within the autosave window. The file is deleted and the inventory reverts, so the items are gone.
  2. (Dup) Make the `mailbox/` directory read-only after delivery with leftovers. The next join re-delivers the stacks already given.
- **Impact:** Rare item loss or duplication.
- **Root cause:** File-store and player-file writes are not ordered.
- **Recommended fix:** In `deliver`, call `PlayerDataSaves.now(p)` and then rewrite the mailbox synchronously (small file), only deleting after the player file is saved. Make `keep` read-modify-write under a per-UUID lock. **Alternative:** Move the mailbox into SQL (`suld_mailbox` rows deleted on delivery after `saveData`).
- **Regression test:** File-system test: a failing `write` must not leave delivered stacks in the file. **Acceptance:** every mailbox stack exists in exactly one place after any crash point.
- **Effort:** S. **Fix risk:** low.

#### PERS-006: Clan `version` written but not compared
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** design concern.
- **Evidence:**
  - `P/persistence/SqlDialect.java:92-111` (unconditional overwrite).
  - `JdbcClanRepository.save` deletes and re-inserts all members (`P/persistence/JdbcClanRepository.java:90-133`).
  - Single writer `suld-clan-io` (`SuldServices.java:158-162`).
- **Risk:** None on one server. Members have PK `player_uuid` (V3), so if leaving clan A fails to save and then joining B saves, B's insert hits a PK conflict and B's save fails until A is retried. This self-heals on the next `flushDirty` (60 s). Real risk appears only with two servers or a manual DB edit.
- **Recommended fix:** Add `WHERE suld_clans.version <= EXCLUDED.version` (PostgreSQL) or a read-back (MySQL), as profiles already do. **Alternative:** Document "single server per database".
- **Regression test:** Repository test: save v5, then v4 → v4 refused. **Acceptance:** a stale clan snapshot cannot overwrite a newer row.
- **Effort:** S. **Fix risk:** low.

#### OPS-001: Backups are skewed, not plugin-flushed, on-box only, and the restore is untested at the current schema
- **Severity:** Medium (P2).
- **Confidence:** Confirmed.
- **Type:** ops limitation.
- **Evidence:** `deploy/backup.sh`:
  - :30-36 runs `pg_dump` **first**.
  - :38-49 then runs `save-all flush` / `sleep 6` / `save-off` / `tar`.
  - SÜLD's dirty in-memory state (profiles ≤300 s, styles/clans ≤60 s) is not flushed before the dump, and no `suld flush` command exists.
  - `BACKUP_DIR=/opt/suld/backups` (`deploy/suld.env.example:14`), timer daily 04:30 (`deploy/templates/suld-backup.timer.tpl`). Nothing is copied off-host.
  - `DEPLOYMENT.md:287` restore verified only with "6 tables, schema v2".
- **Reproduction:**
  1. During a backup, sell items between the `pg_dump` and the world `save-all`.
  2. Restore: the world lacks the items and the DB lacks the coins. A purchase in that window yields item + coins (dup).
- **Impact:** A restore produces item/coin inconsistencies. Disk or VPS loss destroys all backups.
- **Root cause:** Two stores captured at different instants, with no off-site target.
- **Recommended fix:** Order the steps as: console `suld flush` (new admin sub that saves all profiles/styles/clans and waits), then `save-all flush` + `save-off`, then `pg_dump`, then `tar`, then `save-on`. Add an optional `BACKUP_REMOTE` (restic/rclone/S3) step. Re-run the restore drill at V15. **Alternative:** Take the backup with the server stopped for a minute (nightly restart window).
- **Regression test:** A scripted restore drill in CI (PostgreSQL container: apply V1-V15, dump, restore, compare row counts and `max(version)`). **Acceptance:** a documented, timed restore at the current schema; backups exist off-host.
- **Effort:** M. **Fix risk:** low.

#### OPS-002: Third-party plugins are unpinned and re-fetched on every update
- **Severity:** Medium (P2).
- **Confidence:** Confirmed.
- **Type:** ops limitation.
- **Evidence:**
  - `deploy/plugins/plugins.json` has 23 entries with no version fields.
  - `tools/plugins/fetch_plugins.py:116-133` (newest listed Modrinth version), :147-158 (GeyserMC `latest`), :163-175 (Hangar, hash `None` when not published).
  - Invoked by `deploy/update.sh:150` as well as `deploy/deploy.sh:175`.
- **Reproduction:** Run `update.sh` the day after a LuckPerms/EssentialsX/ViaVersion release. A new, untested version lands in production together with the SÜLD update.
- **Impact:** Uncontrolled upgrades: behaviour or permission changes, or a supply-chain compromise of any listed project. A rollback (`record_release`) does not restore plugin versions.
- **Root cause:** Resolution is done at install time instead of from a lock file.
- **Recommended fix:** Generate `plugins.lock.json` (version_id + sha512) with an explicit `fetch_plugins.py --update-lock`. Install strictly from the lock, and refuse unverified hashes. **Alternative:** Skip plugin fetching on `update.sh` unless `--plugins` is passed.
- **Regression test:** Unit-test `fetch_plugins.py` with a stubbed API: when a lock exists, a newer upstream version is ignored; a missing hash fails. **Acceptance:** two deploys a week apart install byte-identical plugin jars.
- **Effort:** M. **Fix risk:** low.

#### OPS-003: `/suld reload` advertised but absent; `/suldpack reload` is a partial reload
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** doc mismatch.
- **Evidence:**
  - `R/config.yml:3` advertises the command.
  - `P/command/SuldCommand.java:36-86` has no `reload` case; it falls to `help`.
  - `P/command/ResourcePackCommand.java:42-51` reloads the YAML and calls `SuldConfigFactory.load`; only `packs.updateSettings` is applied.
  - A `SuldConfigFactory` `IllegalArgumentException` there is uncaught ("internal error").
  - After it runs, any of the ~55 direct `getConfig()` readers that read lazily see new values while the `SuldConfig` record keeps old ones.
- **Reproduction:**
  1. Edit `death.exp-loss-fraction`, then run `/suld reload`: the help text appears.
  2. Run `/suldpack reload`: chat-guard limits read lazily change, death settings do not.
- **Impact:** Admin confusion; a mixed config state.
- **Recommended fix:** Change the header to "restart to apply", or implement `/suld reload` for the hot-reloadable subsets (content/items already have reload paths). Catch `IllegalArgumentException` in `/suldpack reload`. **Alternative:** Document which keys are live.
- **Regression test:** Check `config.yml` text against the command list. **Acceptance:** every command mentioned in the docs exists.
- **Effort:** S. **Fix risk:** low.

#### OPS-004: Built-in resource-pack HTTP server (non-deploy installs)
- **Severity:** Low (P3). Production uses nginx with rate limits.
- **Confidence:** Strongly Supported. The JDK `HttpServer` has no response timeout by default (`sun.net.httpserver.maxRspTime=-1`); I did not test this at runtime.
- **Type:** probable defect needing a runtime check.
- **Evidence:**
  - `P/resourcepack/ResourcePackService.java:73,97-103`: backlog 16, 2 worker threads, a full ~6.9 MB write per request.
  - The pack is `required: true` (`R/config.yml:93`). The status map is never pruned (:47,171).
- **Reproduction:** Open 2 TCP connections to `:8164`, send `GET /suld-resourcepack.zip`, and never read. New joiners' pack download stalls, so required-pack players cannot get the UI.
- **Impact:** Cheap denial of service for jar-only hosts.
- **Recommended fix:** Bind `127.0.0.1` by default when a public URL is configured. Set `-Dsun.net.httpserver.maxRspTime`/`maxReqTime` (or use a cached thread pool with a per-IP concurrency cap). Remove the status on quit. **Alternative:** Document "self-host is for local testing; production uses nginx".
- **Regression test:** An integration test opening 3 stalled clients: a 4th GET completes within 5 s. **Acceptance:** the pack server stays available under slow clients.
- **Effort:** S. **Fix risk:** low.

#### OPS-005: A partial `SuldServices` construction leaks the pool, executors and the H2 lock
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** confirmed defect.
- **Evidence:**
  - `P/SuldPlugin.java:66-78`: on a constructor exception, `services` stays null and the plugin is disabled.
  - `onDisable` → `if (services == null) return;` (:437-439).
  - The constructor already created `ioExecutor` and the Hikari `dataSource` before `SchemaMigrator.migrate()` (`P/SuldServices.java:101,121-124`), with no try/finally close.
  - `SuldPlugin.java:75` logs only `ex.getMessage()`, with no stack trace.
- **Reproduction:** Make a migration fail (e.g. a pre-created conflicting column), then start. The pool threads stay alive and the H2 file stays locked until the JVM exits.
- **Impact:** Only a full restart recovers (it is needed anyway). Harder diagnosis.
- **Recommended fix:** Wrap the constructor body. On failure, close what exists (pool, executors) and rethrow; log with the throwable. **Alternative:** Build the storage in a separate factory that owns cleanup.
- **Regression test:** Construct with a failing `SchemaMigrator` stub and assert the `dataSource` is closed. **Acceptance:** no `suld-io-*` threads after a failed enable.
- **Effort:** S. **Fix risk:** low.

#### OPS-006: Grim punishments are kicks only
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** design concern (deliberate, see `docs/ANTICHEAT.md`).
- **Evidence:** `deploy/grim/punishments.yml`, e.g. `"250:0 kick %player% …"`, `"40:0 kick …"`. There are no `ban`/`tempban` commands; LibertyBans is installed (hardening profile) for manual bans.
- **Impact:** A persistent cheater is kicked repeatedly and can rejoin at once.
- **Recommended fix:** After calibration, add an escalating `tempban` through LibertyBans for repeat kicks (e.g. 3 kicks in 30 min means 1 h). **Alternative:** Alert-to-Discord on the second kick.
- **Regression test:** A config lint that every group has a terminal action. **Acceptance:** documented escalation policy.
- **Effort:** S. **Fix risk:** false bans if done before calibration.

#### OPS-007: Missing indexes for the leaderboard and the analytics purge
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** design concern.
- **Evidence:**
  - `P/persistence/JdbcProfileRepository.java:131-139`: `WHERE class_id IS NOT NULL ORDER BY level DESC, exp_into_level DESC` / `currency DESC`, refreshed every 60 s (`P/gui/LeaderboardService.java:51,56`). There is no index on `suld_profiles` beyond the PK (V1).
  - The purge `DELETE … WHERE at < ? LIMIT …` (`P/analytics/JdbcAnalyticsSink.java:144-150`) has only `idx_analytics_type_at (type, at)` (`db/migration/*/V1__init.sql`). A leading `type` column does not serve `at`-only ranges.
  - MySQL and PostgreSQL index sets otherwise match (V1, V3, V4, V12, V14 checked). There are no destructive migrations: no DROP/TRUNCATE; the only DELETE is the V3 FK cascade.
- **Impact:** Full scans every minute and on the daily purge. Fine at hundreds of players, slow at millions of analytics rows.
- **Recommended fix:** V16: `CREATE INDEX idx_profiles_level ON suld_profiles(level DESC, exp_into_level DESC)`, `idx_profiles_currency(currency DESC)`, `idx_analytics_at(at)`, for both dialects. **Alternative:** Cache the leaderboard for 5 min.
- **Regression test:** An `EXPLAIN` test on PostgreSQL showing index use. **Acceptance:** purge time is O(batch).
- **Effort:** S. **Fix risk:** low.

#### THR-001: Async threads read Bukkit/PDC state
- **Severity:** Low (P3).
- **Confidence:** Confirmed (code). The impact is a probable rare CME or stale read.
- **Type:** probable defect needing a runtime check.
- **Evidence:**
  - `P/clan/ChatListener.java:44-61`, inside `AsyncChatEvent`, calls `StyleFormat.name/badges` → `staffOf(p)` → `p.getPersistentDataContainer().get(BADGE_KEY…)` and `p.isOp()` (`P/ui/StyleFormat.java:89-97`).
  - `StyleService.flush` runs on the Bukkit async scheduler (`StyleService.java:76`) and calls `Bukkit.getPlayer(id)` (:165). A wrong `null` evicts a live player's style (`cache.remove`) after persisting it. The next `of()` then re-loads it as an "unsafe" placeholder (:226-245), which briefly blocks purchases.
- **Reproduction:** A load test: 50 bots chatting while an admin repeatedly runs `/suld badge` on them, and players join and quit during style flushes. Watch for `ConcurrentModificationException` or "style of … was not cached".
- **Impact:** Rare chat-render exceptions or a brief "data not loaded" message.
- **Recommended fix:** Cache each player's badge and op flag on the main thread (join, `/suld badge`, op change) in a `ConcurrentHashMap` read by chat. In `flush`, compute the "online" set on the main thread, or use the plugin's own online-UUID set. **Alternative:** Render the badges on the main thread and pass components into the event.
- **Regression test:** A unit test that `StyleFormat` badge lookup takes a snapshot, not a `Player`. **Acceptance:** no Bukkit entity API calls from `suld-*`/async threads (static check with a grep rule).
- **Effort:** S. **Fix risk:** low.

#### SEC-004: `/itemsadmin` and `/suldperf` gated only by plugin.yml
- **Severity:** Low (P3).
- **Confidence:** Confirmed.
- **Type:** design concern (defense in depth).
- **Evidence:**
  - `R/plugin.yml` `itemsadmin: permission: suld.admin.items`, `suldperf: permission: suld.admin.perf`. Bukkit `PluginCommand.execute` enforces these.
  - The executors (`P/command/ItemCommands.java` `admin()` at ~:580-610, `P/perf/PerfService`) do no in-code check, unlike every other admin command.
- **Impact:** None today. Exposure appears if the executor is ever reused by another command or registered via Brigadier.
- **Recommended fix:** Add `if (!s.hasPermission(...)) return` in both executors. **Alternative:** None needed.
- **Regression test:** A command test calling the executor directly with a non-permitted sender. **Acceptance:** refused.
- **Effort:** S. **Fix risk:** none.

---

### Verified OK (with evidence)

- **SQL injection:** none. Every concatenation is a constant or enum:
  - `JdbcProfileRepository.top` ORDER BY from a switch (:133-138).
  - `COLUMNS` constants in the relic and death repositories.
  - Style upsert column list (`JdbcStyleRepository.java:158-166`).
  - Purge table and column from a fixed `List.of` plus a constant batch (`JdbcAnalyticsSink.java:144-150`).
  - All values are bound with `?`.
- **Secrets:** the prescribed grep (`git ls-files … password/api_key/PRIVATE KEY/webhook`) finds only `PGPASSWORD="$DB_PASS"` env references (`DEPLOYMENT.md:206`, `deploy/backup.sh:32`, `deploy/deploy.sh:141`, `deploy/health-check.sh:52`). No credentials are committed.
- **`/pay`:** player-only, amount 1..1,000,000, online non-self target, both need a class. The check and the in-memory move happen on the main thread, then a transactional `saveTogether` sequenced on both UUIDs in UUID order (`P/command/CityCommands.java:311-367`, `P/profile/DefaultProfileService.java:99-105`, `JdbcProfileRepository.java:195-221`). Negative and overflow amounts are rejected. The only caveat is the clamp revert in PERS-004.
- **Profile "newer wins":**
  - The PostgreSQL upsert has `WHERE suld_profiles.version <= EXCLUDED.version`, and MySQL/H2 reads the version back (`JdbcProfileRepository.java:228-261`).
  - The version is bumped on every mutation (`PlayerProfile.java:295-298`).
  - Saves are serialised per UUID by `KeyedSequencer`.
  - Eviction happens only after a successful save (`DefaultProfileService.java:108-123`).
- **Credits balance integrity:** the conditional `UPDATE … credits + ? >= 0` in a transaction, plus `CHECK credits>=0`. The style upsert never touches `credits`. The spend path has `purchasing` set guards and a refund if the item became owned in flight (`StyleService.java:255-300`).
- **Relic:** the DB is the source of truth with true CAS (`JdbcRelicRepository` `WHERE item_key=? AND version=?`) and `UNIQUE(owner_uuid)`. `RelicService.validate` (`P/relic/RelicService.java:153-185`) reissues the item from the DB record and removes extra copies, including from bundles and containers (:196-233). A crash between CAS and the item give is healed on the next join or validate pass. Relics are stripped from death drops (:523).
- **Dungeon completion:** a single completion, because `runsByEntity.remove(entity)` plus the `run.isActive()` guard and `complete()` call `run.complete()` first (`P/dungeon/DungeonService.java:441-456,515-517`), all on the main thread. Rewards skip players who are downed, absent, or removed from `participants` on leaving the party (:469-483,521-529). There is one `bossId` per run. Items are given directly, not dropped.
- **SÜLD menus:**
  - `MenuListener` cancels every `InventoryClickEvent` whose top holder is `Menu` (both inventories, all click types, before rate limiting) and every `InventoryDragEvent` (`P/gui/MenuListener.java:61-79,120-123`).
  - Trade cancels all clicks and drags plus `COLLECT_TO_CURSOR`/`HOTBAR_SWAP` (`TradeService.java:293-358`).
  - `ClassSelectionGui` cancels clicks but has no drag handler (`P/gui/ClassSelectionGui.java:111-127`). That is harmless: the cursor cannot get an item while every click in that view is cancelled, and a drag would only lose the player's own item into a discarded GUI.
  - Display items cannot be taken.
- **Trade item side:**
  - Offered stacks leave the inventory into memory, and close, quit, death, teleport and shutdown all return them (`TradeService.java:436-460,467-565`).
  - On death, offered stacks enter the drop list before `DeathService` (HIGHEST) and kept ones come back on respawn. With `keepInventory` they are removed from the drops (:491-522).
  - Before the swap, room is checked on a copy (:362-368,391-397).
  - Untradable: relics (including inside shulkers and bundles), soulbound, bound, forged and menu items (:258-283).
- **Sell:** price = unit × amount from the current stack. The bag slot is re-validated (`isSimilar` and amount) before selling (`P/gui/Menus.java:700-737`). Soulbound, class gear, relics, forged items and Legendary-or-better gear are refused (`ItemEconomy.sellPrice`, `SoulboundGuard.soulbound`). Item off disk first, then coins (`PlayerDataSaves.itemsForCoins`).
- **Shop (coins → items):** charged in memory first (blocks double clicks), the item is given only after the DB commit, refunded on failure or quit (`PlayerDataSaves.java:51-68`, `Menus.java:615-634`).
- **Quest chapter reward:** it takes collect items and gives no items, and is routed through `itemsForCoins` (`P/SuldPlugin.java:291-293`, `P/quest/QuestService.java:164-181`). This is the correct order.
- **Admin commands:** every mutating `/suld` subcommand checks `suld.admin`. `suld.admin*` defaults to `op`, and player-facing permissions (`suld.items`, `suld.skills`, `suld.classgear`) default to `true` (`R/plugin.yml` permissions). No player command changes another player's state without their consent: trade, party and clan all need acceptance or rank checks.
- **Authentication:** fail-closed. `DeathService` and `StyleService` refuse the login on a storage error (`DeathService.java:133-160`, `StyleService.java:122-136`).

---

### Not determinable statically: runtime tests that would settle it

1. **Paper `PlayerDeathEvent#getItemsToKeep` matching after `DeathService.wear()` mutates a kept stack.** Test: die with a damageable non-soulbound kept item and check it is kept exactly once, without being dropped or vanishing. It interacts with TradeService identity checks (`d == it`).
2. **Vanilla player save cadence on the deployed server** (`player-auto-save-rate`, `autosave-interval`). This sets the PERS-001 and PERS-002 windows. Test: upgrade armour, wait N seconds, `kill -9`, and see whether the materials return.
3. **Death-lock relog race:** death, then an instant quit and rejoin while a previous lock `update` is still pending (`DeathService.lock`, `:281-299` chain). Can `preload` read "no lock" before the `insert` commits? Test with an artificially slowed death executor.
4. **Async chat PDC access under load (THR-001):** a CME or not. Needs a bot load test.
5. **Pack HTTP server slow-reader DoS (OPS-004):** confirm JDK `HttpServer` default timeouts on the Java 21 runtime in use.
6. **MySQL migration atomicity:** a failed multi-statement migration on MySQL/MariaDB (DDL auto-commits) leaves partial DDL that is not recorded in `suld_schema_version`, and the rerun then fails. Production uses PostgreSQL (transactional DDL), so this only matters for MySQL users. Test: inject a failure in V15 on MySQL.
7. **Tebex (or the chosen store) retry semantics** for console commands. This sets the real SEC-003 frequency.
