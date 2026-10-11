# 07: Progression, balance and replayability

- **Audited commit:** `main` @ `50d5138`.
- **Evidence:** static, from code and content, plus the live HP measurement (E-08).
- **Balance claims:** none is proven by play. The simulator (`suld-plugin/src/sim`, `docs/PROGRESSION_V2.md`) is a model, and no human has progressed past the first chapters on this build.

## Verdict

**The structure is coherent.** It runs:
- level →
- 42 strictly ordered chapters →
- a 10-rung dungeon ladder (gated by level, previous clear, chapter and, from rung 5, gear power) →
- six gear tiers →
- Ascension I–III at level 60.

**The worst exploits of the 2026-10-07 audit are fixed** (§4). That includes:
- the dungeon loot level;
- the ladder bypass;
- level-gap EXP;
- the boost cap;
- class health;
- vanilla armour saturation.

**No softlock exists in today's content.** Every chapter target exists and spawns. Discovery chapters re-check on start.

**What is unproven or wrong:**
1. **The very start (PROG-001 + GAME-005).** A fresh character is far weaker than the simulator's "par" player. This is the most important balance defect, and it sits in the first five minutes.
2. **The pace (C1).**
   - The simulator's own compliance check C1 fails: 223.8 h to level 60 against a 180–220 h target (`audit/progression-balance.json`, REPORTED).
   - Story EXP is 19.9 % of the total.
   - A ~200 h grind to cap is long for a new server's first season.
3. **The middle and late story pay unevenly.** Discovery chapters pay about 1 % of their neighbours (PROG-005).
4. **Content edits can softlock.** Gates are chapter indexes (PROG-004).
5. **Designer edits silently desync** (PROG-002/003).
6. **Travel design is bypassed** by default `/sethome` (multiple homes) and `/tpa` (GAME-001).

## 1. Progression dependency map

**Chain:** level → chapter (`content/quests.json`, 42, strictly sequential via `QuestChain`) → dungeon rung (`DungeonLadder`: JSON `minLevel` + previous clear + `chapterGate` + gear power from rung 5) → gear (dungeon band loot, mob-level drops, reforge ≤ player level) → Ascension (`Ascension`, ranks I–III at 60, gates on palace clears).

| Rung | Min level (JSON = ladder) | Needs chapters done | The chapter that asks for it | Consistent? |
|---|---|---|---|---|
| khasar_den | 3 | 4 (ch 4 = reach 5) | ch 5 | yes |
| govi_bulsh | 9 | 8 | ch 9 | yes |
| baavgain_uur | 15 | 13 | ch 14 | yes |
| mosun_orgil | 22 | 16 | ch 17 | yes |
| dalain_gun | 29 (+gear power ≥ 0.75·par) | 21 | ch 22 | yes |
| khar_khot | 36 (+GP) | 27 | ch 28 | yes |
| ulaan_khad | 42 (+GP) | 32 | ch 33 | yes |
| burkhan_agui | 48 (+GP) | 35 | ch 36 | yes |
| tengeriin_shat | 54 (+GP) | 39 | ch 40 | yes |
| tengeriin_ordon | 60, party ≥ 2 | 42 (incl. ch 42 "reach 60") | none (Ascension) | yes |

- No chapter targets a missing mob, region, site, dungeon or item. `ContentLoader` checks all five kinds (`plg/content/ContentLoader.java:364-373`), the wave and boss mobs (`:261,271`), loot tables, sites inside their areas (`:331-337`) and the ids the code relies on (`:50-61,445`). Every KILL_MOB target is in some region's spawn list, and the chapter target gets a 50 % spawn share wherever its region spawns it (`RegionSpawner.java:121`).
- Discovery chapters re-check the player's current region on chapter start (`plg/quest/QuestService.java:95-107`), and sites call `onRegion` on every visit, not only the first (`plg/region/HistoricSiteService.java:258-265`). So "already visited" cannot softlock a chapter. It does need the site to be *built* (`built.get(...)`, `:254`); otherwise it softlocks (runtime check).
- **Not validated:** the ladder ↔ `quests.json` mapping (PROG-004), ladder min ↔ JSON `minLevel` (they match today, but nothing checks), and gear-power reachability at the rung's level (`GearPower.par`, `api/balance/GearPower.java:41-44`; 0.75·par(29) ≈ 190, about eight rare items at level ~22: plausible but unverified).
- Chapter EXP oddities: PROG-005. Story EXP is 7.17 M of the 36.06 M needed to reach 60 (19.9 %).
- **Class balance (static):**
  - HP at level 1 is 24 (Бөө) to 40 (Баатар), a 1.67× spread (`PlayerClass.java:21-33`). Attack is 7–9.
  - At level 1, Бөө's and Мэргэн's first spells deal no damage (`api/skill/Spell.java:21,25`: multiplier 0.0). Combined with the lowest HP, they are the most fragile newcomers. Бөө also carries ★★★★★ difficulty.
  - The cooldown table is per slot, not per class (`Spell.java:51`: 1.5/4/5/10/18 s). Class identity therefore lives only in the multipliers and costs: for example, Мэргэн "Тэнгэрийн Сум" is ×3.0 for 50 and Бөө "Тэнгэрийн Хаалга" is ×4.0 for 70.
  - Simulated hours to 60 are within ±10 % (`docs/PROGRESSION_V2.md:15`): Хүлэгчин 191 h and Мэргэн 196 h are the fastest, after the mythic bow (Тэнгэрийн Нум).
  - The early lethality gap (PROG-001) is not in the simulator's par model.



## 2. Class identity and balance (static)

**Five classes, each a real Mongolian word:**

| Class | Role |
|---|---|
| Баатар | tank, «Хил» resource (rename, LANG-001) |
| Мэргэн | archer |
| Бөө | caster |
| Дархан | smith / support |
| Хүлэгчин | mounted |

**Level-1 numbers:**
- Base HP is 24 (Бөө) to 40 (Баатар), attack 7–9 (`suld-api/.../clazz/PlayerClass.java:21-33`, hardcoded; DATA-001).
- Live max HP at level 1 measured 43–82 because tree and gear health add on top. **Current HP after the pick was 23–24 for all three classes measured**, because nothing fills it.

**Spells:**
- Cooldowns are per *slot*, not per class (`Spell.java:51`: 1.5 / 4 / 5 / 10 / 18 s). Class identity therefore lives only in the multipliers and costs.
- At level 1, Бөө's and Мэргэн's first spells deal no damage (`Spell.java:21,25`, multiplier 0.0). They are the most fragile newcomers *and* have no burst.

**Simulated time to level 60** is within ±10 % across classes (REPORTED, `PROGRESSION_V2.md:15`). Хүлэгчин (191 h) and Мэргэн (196 h, after the mythic bow) are the fastest. The early lethality gap is not in the simulator.

**No dominant-strategy exploit was found statically.** These loops are all closed:
- farm fatigue (`ActivityTracker`);
- the +50 % boost cap;
- the dungeon repeat factor with a 25 % floor;
- the sell cap ×5, with nothing legendary+ bought (the salvage → craft → sell loop now loses money).

**Remaining open items:**
- **SP-3:** the orb respec costs a diamond and 4 pearls and ignores the cooldown.
- **DU-1:** same-inventory duplicates are caught only at join.
- **DG-7:** no enrage wipe; a slow party can always win before the 20-minute cap.

## 3. Player motivation and replayability

**Short term (first hour).**
- Clear goals: the quest tracker, the tutorial steps, the first level-ups.
- Broken by PROG-001, ONB-002 and ONB-005 (see `03`).

**Mid term (days).**
- Ten dungeons with boss phases.
- Gear tiers, reforge, salvage and craft.
- Daily reward and tasks; a 45-minute wolf raid.
- Ovoo blessings, historic sites, clans.
- This is a real loop, but **only the first chapters have been played**, and only by bots.

**Long term.**
- Ascension I–III.
- Two world-unique relics with server-wide broadcasts (a strong social hook).
- Ranks and cosmetics.
- **Missing compared with the docs:** achievements, a battle pass, resurrection quests. Without achievements there is no long-tail goal system apart from ranks.

**Social.**
- Party EXP share within 48 blocks, clans with levels, `/trade`, world events.
- Solo is viable for rungs 1–9; rung 10 needs a party of 2.

**Healthy incentives.**
- Mostly good: fatigue, level-gap EXP, the carry factor.
- Risky:
  - Wolf-raid loot at max rarity every 45 minutes (PROG-006) is a steady gear faucet.
  - `/tpa` lets high levels pull low levels into outer lands (GAME-001).

## 4. The previous exploit audit (2026-10-07, `f123253`), checked again

| ID | Old severity | Status now | Evidence (current code) |
|---|---|---|---|
| DG-1 | CRITICAL | **FIXED** | Loot is rolled at `rewardLevel` = `max(min, min(max, playerLevel))` (`api/dungeon/DungeonDefinition.java:49-51`, `api/balance/DungeonRules.java:15-17`). `max` comes from the ladder (`DungeonDefinition.java:40-43`). It is used at `plg/dungeon/DungeonService.java:554`. |
| DG-2 | CRITICAL | **CHANGED** (mitigated, no lockout) | A repeat factor of −15 % per same clear among the last 8, floor 25 % (`DungeonRules.java:20-25`). A gear-chance fatigue of ×0.5 / ×0.25 after 3 / 5 clears in 2 h, persisted to `dungeon-fatigue.yml` (`DungeonService.java:415-438`, `:557-561`). A dungeon can still be repeated forever at 25 % EXP and coins. |
| DG-3 | CRITICAL | **FIXED** | Completion gear uses `LootTier.DUNGEON` (`DungeonService.java:554-555`): legendary 3 %, no ancient or mythic (`items/tiers.json:27`). The boss is tagged `DUNGEON_TAG` (`DungeonService.java:291`), so its kill drops materials only (`plg/combat/CombatListener.java:301,353`). The BOSS band now applies only to BOSS-tier mobs killed outside a run. |
| DG-9 | CRITICAL | **FIXED** | Ten rungs gated by previous clear, story chapter and, from rung 5, gear power (`DungeonService.java:150-158`, `:333-351`; `DungeonLadder.java:31-40`). At level 20 only rungs 1–3 are open (3 of 10). |
| XP-1 | HIGH | **FIXED** | Level-gap factor: full value from −4 to 0, −15 %/level, 10 % at −10 (`api/balance/ExpRules.java:21-27`), applied at `CombatListener.java:434`. Mobs 10+ levels below roll no gear (`:352`). |
| XP-2 | HIGH | **FIXED** | Mobs now hit with their designed attack × the level gap (`CombatListener.java:131-150`, `api/balance/CombatRules.java:30-32`). Damage to higher mobs is cut by −4 %/level, floor 40 % (`CombatListener.java:240`, `CombatRules.java:25-27`). Bands are still advisory, but farming above your level is now unsafe and slow. |
| XP-3 | MEDIUM | **CHANGED, still a new-player risk** | Within 450 blocks, a player not hunting a chapter target gets 60 % Govi wolves, otherwise a level-fitted pick (`plg/mob/RegionSpawner.java:120-123`). The inner Altai/Gobi areas start at radius 51 (`content/world.json`: `area.ongi`/`taats` 5–8, `area.tamir`/`khangai_range` 18–22). So 40 % of spawns just outside the west/south walls are level 7–25 (see `03`, ONB-003). |
| XP-4 | MEDIUM | **FIXED** | Farming fatigue by mob type and position over 30 active minutes, floor ×0.25 (`api/activity/ActivityTracker.java:24-29`, `:96-100`). Applied to EXP, coins and loot (`CombatListener.java:296`, `:310`, `:346-349`). It survives a relog (PROJECT_STATE "Gameplay logic review"). |
| XP-5 | MEDIUM | **FIXED** (with notes) | Clan + relic + gear + tree bonuses are capped at +50 % (`api/balance/ExpRules.java:7,40-42`; `plg/progression/ProgressionBoosts.java:83-85`). Catch-up ×1.5 and the rested-pool doubling apply on top of the cap (`CombatListener.java:438-445`); this is by design. |
| PW-1 | HIGH | **FIXED** | Class base health × (1 + 0.04·(L−1)) (`CombatRules.java:35-37`). Applied to `MAX_HEALTH` (`plg/skill/SkillTreeService.java:416-423`) on class pick and level-up (`:502-516`). |
| PW-5 | HIGH | **FIXED** | Vanilla armour is zeroed for players (`SkillTreeService.java:439`). SÜLD mitigation is a/(a+10+2.5·attacker level), cap 75 % (`CombatRules.java:14-22`; `CombatListener.java:170-180`). |
| GR-1 | HIGH | **FIXED** (by context clamps) | `ItemGenerator.java:42` still takes the context level, but every context is now clamped: mob loot at the mob's level (`CombatListener.java:339`), dungeons to their band (DG-1), events to band + 5 (`plg/worldevent/WorldEventService.java:226`), reforge never above the player's level (`api/item/Reforge.java:34-36`). |
| SB-1 | HIGH | **FIXED** | `upgrade` skips items bound to someone else (`plg/item/ClassWeapons.java:246`). |
| SB-2 | HIGH | **FIXED** | `plg/item/SoulboundGuard.java:99-263` covers clicks, drags, hoppers, hopper pickup, entities/frames, crafting, pots, armour stands and bundles. Registered at `plg/SuldPlugin.java:138`. |
| SB-3 | HIGH | **FIXED** | `/item destroy` refuses class gear (`plg/command/ItemCommands.java:286-297`, `api/item/ItemEconomy.java` `classGear`). `/classgear recover` restores it (`ClassWeapons.java:180-197`). |
| DG-4 | MEDIUM | **FIXED** | Party members within 48 blocks share each kill (`CombatListener.java:305-328`, `ExpRules.java:35-38`). Run gear is paid once to every participant who is present and not downed (`DungeonService.java:519-555`). |
| DG-5 | MEDIUM | **CHANGED** | Carry factor: ×0.1 above the dungeon's max, ×0.5 when 10+ levels below the party's top (`DungeonRules.java:28-31`, `DungeonService.java:597-600`). This applies to EXP and coins only. A carried player still gets a full gear roll (clamped to the band). |
| DG-7 | MEDIUM | **STILL OPEN** (softened) | Enrage forces the final phase and ×1.25 damage (`plg/dungeon/BossService.java:103-115`, `:146-152`). There is no wipe. PROGRESSION_V2 lists it as "Deferred". The only fail state besides deaths is the 20-minute run cap (`DungeonService.java:61`, `:700-702`). |
| GR-2 | MEDIUM | **FIXED** | Reforge costs 0.6·L² + 25·L + 25 coins plus 2 materials, never above the player's level (`api/balance/Economy.java:24-26`, `api/item/Reforge.java:25-36`). Note: every level ≥ 22 uses `item.mosun_chuluu` (`Reforge.java:19-24`), so Act II (27–60) has one reforge material. |
| PW-4 | MEDIUM | **FIXED** | Melee is scaled by `0.2 + 0.8·charge²`; a sweep deals ×0.3 (`CombatListener.java:101-123`, `:205`). |
| EC-2 | MEDIUM | **FIXED** | Sell price = value × min(5, rarity) × (1 + L/30), and nothing legendary or above is bought (`api/balance/Economy.java:17-21`). `weapon.tengeriin_taiag` (sell 55, level 26) sells for about 513 ₮ against the 600 ₮ recipe (`items/recipes.json`), so the loop now loses money. |
| SP-3 | MEDIUM | **STILL OPEN** | The orb is still a diamond plus 4 ender pearls (`plg/skill/OrbOfOblivion.java:72-79`): a free refund that ignores the cooldown (`SkillTreeService.java:617-619`; `config.yml` skills.respec "an Orb of Oblivion ignores it"). It is now partly limited by scarce pearls, since overworld hostiles are off (`RegionSpawner.java:93-104`). |
| DU-1 | MEDIUM | **STILL OPEN** | `sweepTick` compares holders across players only (`plg/item/ItemService.java:370-392`). Two copies of one UUID in a single inventory are caught only by `sweepPlayer` at join (`plg/item/EquipmentService.java:368-375`, `ItemService.java:320`). |
| GR-5 | LATENT | **STILL OPEN** | The `QUEST` and `CHEST` bands (`items/tiers.json`) are never used. `CHAMPION`/`MYTHIC` mob tiers do not appear in `content/mobs.json`. Only `LootTier.CRAFT` is referenced (`plg/command/ItemCommands.java:479`). |
| GR-6 | LATENT-CRITICAL | **CHANGED** | `loot.world.rare` is now rolled on open-world kills (`CombatListener.java:344`). `loot.world_event.chonyn_dovtolgoo` is wired with the WORLD_EVENT tier (`WorldEventService.java:217-230`); the level is capped at band + 5. The rarity collapses to each item's maximum rarity (see PROG-006). `loot.quest.reward` and `loot.chest.steppe` are still never rolled (no reference in Java). |
| GR-8 | NEEDS_REBALANCE | **CANNOT DETERMINE** (changed) | The mob numbers are now fitted to the simulator's par player including gear (`api/balance/MobScaling.java:5-9`; `docs/DIFFICULTY_CURVE.md:20-35`). Gear still out-scales the class base (`weapon.tengeriin_taiag` 14–18 + 1.4/level, up to mythic, against class T6 36 flat in `items/weapons.json`). Whether that is still a problem needs the simulator, not static reading. |
| DT-2 | BROKEN | **FIXED** | The death lock is persisted (`suld_death_state`, migration `V12__death_state.sql`). It is loaded at pre-login (`plg/death/DeathService.java:134`). |
| DT-3 | BROKEN | **FIXED** | Revive/reset write `death.<state>` rows to the audit log (`DeathService.java:483-486`). `/revive` is an alias of `/deathrevive` (`plg/command/DeathCommands.java:26`). |



## 5. Tests needed (balance is not proven until these run)

1. **First-fight bot QA:** 20 first fights per class at L1–3, vs wolf, bandit and scorpion.
   - Report deaths, time to kill and HP left.
   - Accept < 5 % deaths.
2. **Simulator re-run** after any newbie-damage change (`simulate -Pquick`); C1–C8 compliance reported in the PR.
3. **`MobScalingTest`:** a level 1–5 normal fight costs ≤ 35 % of the lowest class base HP.
4. **Loader tests** for:
   - ladder-gate ordering (PROG-004);
   - one-curve consistency (PROG-002);
   - chapter EXP never dropping more than 50 % from one chapter to the next (PROG-005).
5. **Closed-alpha telemetry:**
   - time per level for levels 1–20;
   - first-clear time per rung;
   - deaths per hour by level;
   - class pick distribution;
   - drop-off level.
6. **Gear-power reachability:** natural drops at levels 22–29 reach 0.75·par(29) for rung 5 (simulator or playtest).

## 6. Findings (detail)

### PROG-001 — Level-1 players are far below the "par" the mob formulas assume, so the first fights can kill
- **Severity:** High · **Confidence:** Strongly Supported (static math) · **Type:** probable defect needing runtime check
- **Evidence:**
  - The mob damage is fitted to par HP `46.5 + 4.5L + 0.113L²` (51 at L1) and par armour `7 + 0.97L` (`api/balance/MobScaling.java:36-44,56-60`; `docs/DIFFICULTY_CURVE.md:48` "Par HP 51, Par armour 8" at level 1).
  - Live level-1 HP is the class base, 24–40 (`api/clazz/PlayerClass.java:21-33`, `api/balance/CombatRules.java:35-37`).
  - Vanilla armour is off (`plg/skill/SkillTreeService.java:439`), and level 1 has 0 skill points (`api/skill/tree/SkillPoints.java:17-18`).
  - Every mob hit is set to `scaledAttack × hitScale × gapTaken` (`plg/combat/CombatListener.java:148-149`), and nothing reduces damage for new players.
  - Computed from those formulas: Govi wolf (L2) 10.8 per bite at about 1/s; bandit (L4, pillager) 26.8 per bolt every 2.5 s; Gobi scorpion (L7) 21.0 per bite.
- **Measured correction (E-08, this audit):** live *max* HP at level 1 is higher than the class base (Мэргэн 43, Бөө 48, Баатар 82), because tree and gear health add on top. But *current* HP right after the class pick was 23–24 for all three. `applyBaseHealth` never fills health, so the lethality argument holds for the first fight. See GAME-005 in `02`.
- **Reproduction:** Pick Бөө (24 HP) and fight one Govi wolf in melee. It takes about 4 full-charge hits (43 HP, attack about 12) while the wolf deals about 27 in 2.5 s. Or take one bandit bolt: 26.8 damage is more than 24 HP.
- **Impact:** The intended "15 % of HP per normal fight" becomes over 100 % for the fragile classes in their very first fight. Combined with ONB-002 (a 5-minute lock), this is the most likely early quit.
- **Root cause:** The simulator's par player has more HP and armour at level 1 than a fresh live character, and there is no newbie damage curve.
- **Recommended fix:** Make `parHp`/`parArmor` at L1–5 match a fresh character. Or add `CombatRules.newbieTaken(level)` (for example ×0.5 at L1 rising to ×1.0 at L6) in `onMobHitsPlayer`, and heal to full on class pick. *Alternative:* give every class a T1 armour set with ARMOR (only Баатар has one today, `plg/item/ClassArmor.java:46`).
- **Regression test + acceptance:** A `MobScalingTest` checks that at L1–5, the incoming damage of a NORMAL fight at TTK is ≤ 35 % of the lowest class base HP. A bot QA run: 20 first fights per class with fewer than 5 % deaths.
- **Effort:** S · **Fix risk:** Medium (the simulator must be re-run, `simulate -Pquick`)

### PROG-002 — Quest EXP, daily login EXP and daily task EXP use the Java curve; everything else uses `config.yml`
- **Severity:** Medium · **Confidence:** Confirmed · **Type:** confirmed defect
- **Evidence:**
  - These read the config curve:
    - Level-up math: `ProgressionEngine(config.progression().toCurve())` (`plg/SuldServices.java:143`).
    - Dungeon EXP (`plg/dungeon/DungeonService.java:600-602`).
    - Region and area discovery (`plg/mob/RegionSpawner.java:268,304`).
    - Ovoo (`plg/region/OvooService.java:222`) and sites (`plg/region/HistoricSiteService.java:294`).
    - World events (`plg/worldevent/WorldEventService.java:257`).
    - The rested pool (`plg/SuldPlugin.java:72`) and Ascension cost (`plg/gui/AscensionMenu.java:64-80`).
  - These read `Balance.curve()` (242·L^2.2 in Java):
    - Chapter EXP (`plg/content/ContentLoader.java:351,386`).
    - Login EXP (`api/reward/DailyReward.java:38`).
    - Task EXP (`api/reward/DailyTasks.java:43`).
  - The config header promises "Every gameplay value is data-driven" (`config.yml:3`).
- **Reproduction:** Set `progression.curve.base: 300` and reload. Levels need 24 % more EXP, dungeon and discovery rewards grow 24 %, but story chapters still pay 0.35·242·L^2.2. That is 28 % of a level instead of 35 %.
- **Impact:** A designer's curve change quietly breaks the 35 % story share and the daily rewards, and the simulator's LiveRules parity.
- **Root cause:** `ContentLoader` runs where `SuldConfig` is not passed in, and the `api/reward` helpers take no curve argument.
- **Recommended fix:** Pass `LevelCurve` into `ContentLoader.load(...)` and `DailyReward.exp`/`DailyTasks`, or compute chapter EXP lazily from `services.curve()`. Mark `Balance.CURVE_BASE` as the *default only*.
- **Regression test + acceptance:** A test with a custom curve where the chapter EXP / need(level) ratio stays at 0.35 for every chapter.
- **Effort:** S · **Fix risk:** Low

### PROG-004 — Ladder gates are chapter indexes in Java; removing or reordering a chapter can softlock a dungeon chapter
- **Severity:** Medium · **Confidence:** Strongly Supported · **Type:** probable defect needing runtime check
- **Evidence:** `Rung.chapterGate` is a 0-based index (`api/balance/DungeonLadder.java:15,31-40`), compared with a count of finished chapters (`plg/dungeon/DungeonService.java:335-341`). `quests.json` is free to edit (`docs/CONTENT_DATA.md`), and `ContentLoader` never cross-checks the ladder against the chain.
- **Reproduction:** In a server override, delete chapter 2 (`quest.wolf_pelts`). The Khasar chapter becomes chapter 4. The gate still needs 4 finished chapters, so the player must finish the Khasar chapter to unlock Khasar: a softlock for anyone who has not cleared it yet (an earlier clear exempts, `DungeonService.java:158`).
- **Impact:** A content edit can permanently block story progress.
- **Root cause:** The gate refers to a position, not an id.
- **Recommended fix:** Express the gate as `requiresChapter: "quest.oath_5"` in `dungeons.json`, and validate that it comes *before* the chapter whose target is that dungeon. *Alternative:* keep the index but add a loader check that `chain.indexOf(chapterFor(dungeon)) > chapterGate`.
- **Regression test + acceptance:** A loader test: removing a chapter that a gate depends on produces an Issue.
- **Effort:** S · **Fix risk:** Low

### PROG-005 — Discovery-chapter EXP is priced at the target area's level: chapter 41 pays 1,788 EXP at about level 58
- **Severity:** Medium · **Confidence:** Confirmed · **Type:** confirmed defect
- **Evidence:** The chapter "level" for a site is the middle of its *area's* band; for a region it is the region's minLevel (`plg/content/ContentLoader.java:378-383`). Computed results:
  - ch 41 `site.burkhan_ovoo` is in `area.burkhan_khaldun` (3–6), so it pays 1,788 EXP + 4,800 ₮ when the player is about L58.
  - ch 24 `site.tangut_tower` (area 12–15) pays 23,909 at about L33.
  - ch 37 `region.otgon` (outer minLevel 27) pays 119,367 at about L52.
  - ch 19 `site.uushig` (area 17–20) pays 48,920 at about L24.
  - Neighbouring chapters pay 300–600 k.
- **Reproduction:** Run the loader over `quests.json` and print the `exp` values; see Part 4.
- **Impact:** Late-story chapters feel unrewarded, and the simulator's quest share is distorted.
- **Root cause:** The "chapter level" heuristic suits Act I but not the Act II backtracking design.
- **Recommended fix:** Price a chapter at `max(heuristic level, the previous chapter's level)`, or add an explicit `level` field to chapters.
- **Regression test + acceptance:** A test that chapter EXP never falls by more than 50 % from one chapter to the next.
- **Effort:** S · **Fix risk:** Low (needs a simulation re-run)

### PROG-006 — Every 45-minute wolf raid gives each defender an item at its maximum rarity
- **Severity:** Low · **Confidence:** Confirmed · **Type:** design concern
- **Evidence:**
  - The event loot uses `LootTier.WORLD_EVENT` (`plg/worldevent/WorldEventService.java:227`), whose band is ancient 75 / mythic 25 (`items/tiers.json`).
  - Items near level ≤ 10 cap at rare or legendary, so `RarityBand.pick` returns the item's top rarity every time (`api/loot/RarityBand.java:26-33`).
  - The table has `nothing: 0` (`items/loot.json` `loot.world_event.chonyn_dovtolgoo`).
  - The event fires every 45 minutes with min-players 1 (`config.yml:112-115`), and the reward needs 3 kills (`content/events.json`).
- **Reproduction:** Kill 3 wolves in a raid at L5. You get an item at its maximum rarity every time.
- **Impact:** Mild early gear inflation and a steady sell/salvage faucet. It is bounded by the level cap at band + 5.
- **Root cause:** The band was meant for real world bosses.
- **Recommended fix:** Give the event a `DUNGEON`-like band, or set `nothing: 50`.
- **Regression test + acceptance:** A loot-engine test at L5 over 1,000 rolls shows ≤ 10 % at max rarity.
- **Effort:** S · **Fix risk:** Low

### GAME-001 — Default players get `/sethome` (multiple homes) and `/tpa`, which bypasses travel, relays and exploration
- **Severity:** Medium · **Confidence:** Confirmed · **Type:** design concern
- **Evidence:**
  - `suld-plugin/src/main/resources/permissions/luckperms.txt:24-31` grants the default group `essentials.home`, `sethome`, `sethome.multiple`, `tpa` and `tpaccept`. It is applied automatically (`plg/auth/PermissionSetup.java:16-35`).
  - Teleports are blocked only for souls (`plg/death/DeathService.java:596-611`) and inside dungeons (`plg/command/CityCommands.java:47`).
  - SÜLD's own relays charge 10 ₮ (`plg/npc/NpcService.java:81`), and dungeon gates must be walked to (`plg/dungeon/DungeonService.java:167-172`).
- **Reproduction:** `/sethome gate` at the Khasar gate, then `/home gate` from the city. Or `/tpa` to a friend 4,000 blocks out.
- **Impact:** Travel, ovoo/site discovery pacing, the mount (level 5) and the relay coin sink lose their value. Low-level players can be pulled to outer lands.
- **Root cause:** Generic server plumbing overlaps with RPG travel design.
- **Recommended fix:** Remove `sethome.multiple` and `tpa` from default (or limit to 1 home inside Kharkhorum, by a SÜLD check on `/sethome` location). *Alternative:* a level gate on `/tpa`.
- **Regression test + acceptance:** A permission-file test: the default group lacks `essentials.tpa`.
- **Effort:** S · **Fix risk:** Low

### GAME-002 — A player without a class earns SÜLD EXP and coins and hits with Баатар's attack
- **Severity:** Low · **Confidence:** Confirmed · **Type:** confirmed defect (hard to reach)
- **Evidence:** No class check on the SÜLD kill path (`plg/combat/CombatListener.java:274-311`), unlike `vanillaKill` (`:399`). `attackOf` uses `PlayerClass.BAATAR` when no class is set (`:69`).
- **Reproduction:** Only reachable if the menu is closed by a non-PLAYER reason (death, another plugin opening an inventory) or by an admin class reset.
- **Impact:** Small; profiles without a class can level up.
- **Root cause:** The guard is missing.
- **Recommended fix:** In `onDeath`, return early with a hint "Эхлээд ангиа сонго: /class" when `playerClass().isEmpty()`.
- **Regression test + acceptance:** A listener unit test: a kill without a class grants 0 EXP.
- **Effort:** S · **Fix risk:** Low

### GAME-004 — Prior-audit items still open
- **Severity:** Low–Medium · **Confidence:** Confirmed · **Type:** confirmed defect / design concern
- **Evidence:**
  - DG-7: no enrage wipe (`plg/dungeon/BossService.java:103-115`).
  - SP-3: free orb respec (`plg/skill/OrbOfOblivion.java:72-79`).
  - DU-1: same-UUID duplicates in one inventory caught only at join (`plg/item/ItemService.java:370-392`).
  - GR-5 and GR-6: unused bands and tables.
  - EC-4: the shop label "8 ₮ / 25 ₮" (`plg/gui/Menus.java:599`) against the formula (bear pelt about 35 ₮: 12 × 2 × (1 + 14/30)).
- **Reproduction:** See the Part 2 table.
- **Impact:** DU-1 allows a duplicate to survive until the next relog. The others are pacing or cosmetic.
- **Root cause:** Deferred work.
- **Recommended fix:**
  - DU-1: in `sweepTick`, add `if (!mine.add(uuid)) removeCopy(...)`.
  - SP-3: a 24 h cooldown on the orb.
  - EC-4: compute the label from `Economy.sellPrice`.
  - DG-7: wipe at enrage + 60 s.
- **Regression test + acceptance:** An `ItemService` test: two copies of one UUID in one inventory, and one is quarantined within 30 s.
- **Effort:** S each · **Fix risk:** Low

### DATA-002 — Dungeon `region` is wrong for 6 of 10 dungeons, the T2 weapon level differs between JSON and code, and comments are stale
- **Severity:** Low · **Confidence:** Confirmed · **Type:** doc mismatch
- **Evidence:**
  - Wrong regions:
    - `dalain_gun` says region.khangai, but its site radius is 2,600 (khuvsgul).
    - `khar_khot` says gobi, but r3000 is zuungar.
    - `ulaan_khad` and `burkhan_agui` say kherlen, but r3300/r3700 are khentii.
    - `tengeriin_shat` says altai, but r4100 is otgon.
    - `tengeriin_ordon` says khangai, but r4500 is khuvsgul.
  - All six are in `content/dungeons.json`. The field drives the tracker fallback (`plg/content/DungeonContent.java:50-52`, `plg/quest/QuestTracker.java:75`) when the halls are not ready.
  - `weapon.class.*.2` has `level: 10` (`items/weapons.json`), while `WeaponTiers.LEVEL` is 12 (`api/classgear/WeaponTiers.java:12`).
  - The comment "10 / 25 / 45" at `plg/gui/ClassSelectionGui.java:165` is stale.
- **Reproduction:** Compare the files.
- **Impact:** The tracker points at the wrong region if the halls are missing, and data drift misleads designers.
- **Root cause:** Copy-paste when Act II was added.
- **Recommended fix:** Derive the region from the site bearing and radius (`regions.at`) in the loader, or validate it. Align the weapon levels.
- **Regression test + acceptance:** A loader check: a dungeon's region contains its site.
- **Effort:** S · **Fix risk:** Low
