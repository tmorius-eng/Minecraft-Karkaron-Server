# SÜLD Ascension — Тэнгэрийн Зэрэг

**Ranks I–X are in the game.** I–III are bought with Тэнгэрийн оноо (status: MANUAL_QA_REQUIRED, see below);
IV–X are the honour ranks of the owner's «бага хүч + нэр хүнд» (small power and a name), `AscensionHonor`, below.
The full proposed design further down is kept for its history and its simulation.

## In the game: ranks I–III

* **Where:** `/ascend` (also `/zereg`, `/ascension`; `/ascend` is routed to SÜLD even with WorldEdit installed), or the
  Тэнгэрийн Тахилч in Kharkhorum once a character is level 60 (sneak + click still gives the blessing).
* **Тэнгэрийн оноо:** every EXP point earned at level 60 is added to the character's оноо
  (`DefaultProgressionService`); they are kept in `suld_profiles.endgame`.
* **The rite** (`Ascension.rite`, all-or-nothing): every gate met, then the оноо cost and the coin fee are spent
  together. Two clicks on the same button. Audited (`ascension.rite`), announced to the server.
* **Gates** (`Ascension.gates`, the checklist in the window). The heroic, world-boss and other mastery tracks do not
  exist yet, so the live game uses stand-ins:

| Rank | Gates |
|---|---|
| I | level 60 · 9 different dungeons cleared · every story chapter · gear power ≥ 90 % of par 60 |
| II | level 60 · the whole ladder cleared · Тэнгэрийн Ордон cleared 2 times · armour mastery 5 |
| III | level 60 · Тэнгэрийн Ордон cleared 4 times · gear power ≥ par 60 · armour mastery 7 |

* **What a rank gives:** +1 skill point (`SkillEngine.total`), +1 % attack for hits, spells and arrows
  (`CombatListener.attackOf`); rank III opens T6 class armour (`ClassArmor.holdings`).
* **What it costs in hardcore:** an ascended character's death lock is the longest one (`DeathLock`, `DeathService`).
* **Shown:** the sidebar's «Зэрэг» row at the cap (rank and оноо); the /ascend window.
* **Gate steps are milestones** (`Ascension.steps`/`totalSteps`): every dungeon, chapter, palace clear and armour
  mastery rank counted by a gate (and each met yes/no gate) is a step. `AscensionMenu.watch` looks every 10 s and a
  level-60 player whose steps rose hears «✔ Тэнгэрийн Зэрэг II-ийн нөхцөл: 7/18 (/ascend)».
* **«Тэнгэрийн Од» after rank III** (`Ascension.stars`, `starCost` = the cost a rank IV would have,
  0.6 · need(59) · 4 ≈ 4.57 M оноо): the оноо keep coming at the top and each `starCost` of them is a star — a count
  in the window («Тэнгэрийн Од: N», bar to the next) and a line when it rises. Derived from the unspent оноо (nothing
  stored, nothing spent), **no power**. Before it, a hardcore player who reached rank III around day 17–19 had no
  measurable progress for the rest of the first 30 days (L9 87 %).
* **Counting:** Тэнгэрийн Ордон clears are counted per character at completion (`Endgame.palaceClears`, an optional
  field of the version-1 endgame JSON, so older rows read as 0).
* **Staff/QA:** `/suld endgame <player> rank|points|palace <n>` (audited).
* **Simulator:** `Engine.ascensionReady` uses the same `Ascension.blocked` for the live rules; `Engine.ascensionSteps`
  counts a rise of `steps` (or of `stars` at rank III) as the day's milestone, and the day's level fraction at the cap
  is the rank plus the share of the next rank's оноо (then the stars), the /ascend bar.

Client QA to do: open /ascend below and at 60, check the checklist and the two-click rite, the priest at level 60,
the sidebar row, +1 skill point in /skills, and that T6 shows as open at rank III.

## In the game: ranks IV–X (the honour ranks)

* **Gates** (`AscensionHonor.Rank`): Тэнгэрийн Од (the stars the оноо keep making after III, never spent), a heroic key
  timed (docs/HEROIC_DUNGEONS.md) and a weapon mastery level (docs/MASTERY_AND_PROFESSIONS.md); then the rite fee,
  25 000 × the new rank. No оноо, no skill point, no flat power. The shrine window lists the gates and the rite.
* **What each rank gives**, on top of the ones below it (the shrine window and the rite's message say the same):

| Rank | Title | Perk | Where in the code |
|---|---|---|---|
| IV | Атаа Тэнгэрийн Сахиус | damage taken from players in a consented fight (duel, fighting ground) × 0.97 | `DuelService.onLethalConsented` |
| V | Заяа Тэнгэрийн Хишиг | rare-loot luck +3 (the loot-chance stat, every mob, boss and dungeon roll) | `SkillTreeService.lootPct` |
| VI | Хурмаст Тэнгэрийн Цахилгаан | 2 % of the hits on a SÜLD mob call the sky's lightning: the same mob takes half the hit again (+1 % power on average, the most IV–X add) | `CombatListener` |
| VII–IX | Дээд Тэнгэрийн Баатар (I–III) | «Нурууны туяа», a back aura seen by all: gold motes (VII), gold and blue (VIII), a white glow among them (IX) | `HonorAura` |
| X | Мөнх Тэнгэрийн Элч | the name on the «Мөнх Тэнгэрийн Элч» board, which stands on the «Анги ба тулаан» guide board at the arrival point (the first envoy at the top), and in the Chronicle (`HEAVENLY_ENVOY`, once each) | `HologramService` (board `envoy`, `content/holograms.json`), `ChronicleService.heavenlyEnvoy` |

* The lightning is drawn, never a real bolt: a column of sparks, a burst and a near thunder clap (a real bolt's thunder
  carries far across the server, and its fire burns).
* The aura hides while the player is invisible, dead, spectating or a training stand-in.
* Staff set a rank with `/suld endgame <player> rank <n>`, and only the real rite makes an envoy of X. A staff
  `/chronicle hide <id>` takes an envoy off the monument too; the console's `/chronicle envoy` lists them with their ids.
* **Bot:** `tools/loadtest/bots/honorbot.js`, 7 checks. No aura at VI and gold motes at VII; the glow at IX. In a
  duel the same staff hit (`/damage … by` the other fighter) takes 0.97 as much of A's health at IV as at III. No
  lightning at V in 80 hits, and some at VI in 300 hits (2 % expects about 6; 6, 9 and 5 in three dev-server runs). X by the
  real rite: the name on the plaque and in the Chronicle, then gone from the plaque once the entry is hidden. V (loot
  luck) is a one-line use of the tested rule (`AscensionHonorTest`) and is not measured by the bot: a 3-point luck
  shows only over thousands of rolls.

## The full design (ranks I–X, proposed)

Requirements are in `Engine.ascensionReady`, costs in `ProposedRules`.

## Design

Level 60 is the first cap, not the end (directive §4, §14, §24):

* **Ascension is a separate layer, not levels 61–70.**
* **Every rank has requirements of a different kind**: story, ladder, mastery, exploration, crafting, a class trial,
  mythic dungeons, a deathless trial and the raid. They get harder rank by rank.
* **Nothing is time-based.** There are no weekly locks and no "one rank per week". Progress comes from content and from
  **Тэнгэрийн оноо**, the EXP earned at the cap, which used to be thrown away (XP-6).
* **Power stays small.** Each rank gives +1 % power and +1 skill point. The rest is horizontal: the Ascension node
  choice, titles and cosmetics.

## Requirements

| Rank | Requirements (all, plus the cost below) | Kind |
|---|---|---|
| I | level 60 · every story chapter (42) · 9 different dungeons cleared · gear power ≥ 90 % of par 60 | entry |
| II | the whole ladder (10) + 1 heroic · 20 mastery ranks in total | dungeons |
| III | 3 heroics · a world boss kill · gear power ≥ par 60 · combat mastery 5 | bosses |
| IV | all 8 regions · 80 % of landmarks and hidden places · exploration mastery 5 | exploration |
| V | crafting mastery 6 · gear tempered to +5 | crafting |
| VI | class mastery 7 · solo class trial (power rating ≥ 1.1 × the reference) | class |
| VII | mythic tier 1 cleared · gear power ≥ 110 % of par 60 | mythic |
| VIII | mythic tier 2 cleared **without a death wound** (deathless trial) | hardcore |
| IX | every mastery track ≥ 5 and 60 ranks in total · mythic tier 3 | mastery |
| X | mythic tier 4 · Тэнгэрийн Ордон raid cleared 3 times · gear power ≥ 125 % of par 60 | raid |

Cost per rank (Тэнгэрийн оноо + rite coins):

<!-- spec:begin ascension-cost -->
| Rank | Тэнгэрийн оноо (EXP earned at the cap) | Rite coins |
|---|---|---|
| I | 1487086 | 25000 |
| II | 2974172 | 50000 |
| III | 4461259 | 75000 |
| IV | 5948345 | 100000 |
| V | 7435431 | 125000 |
| VI | 8922517 | 150000 |
| VII | 10409603 | 175000 |
| VIII | 11896690 | 200000 |
| IX | 13383776 | 225000 |
| X | 14870862 | 250000 |
<!-- spec:end ascension-cost -->

## Power model (directive §15)

* **Points**: +1 skill point per rank, so 87 points at Ascension X against the ~95 needed to max one class tree plus
  the universal tree. Builds remain choices.
* **Nodes**: one choice of three per rank. They are horizontal: a class-mechanic variant, a utility, or a mastery
  multiplier. No more than +1 % raw power per rank.
* **Unlocks**:
  * rank III → armour tier T6 eligibility;
  * VII → mythic tiers 5+ and their trophies;
  * X → the Тэнгэрлэг title and the endgame silhouette variant of the class set.
* There is no infinite vertical scaling. Past X, progress continues through mastery, collection and mythic tiers
  (leaderboards) only.

## Pacing (simulated, hardcore p50)

See `docs/PROGRESSION_SIMULATION.md` §Time to milestones:

* rank I at about 200–250 h, right after level 60;
* rank V around 680–710 h;
* rank X around 1,110 active hours, i.e. **~907 h after reaching 60** (C8).

At day 90 a 10 h/day player is around rank VIII, an active 5 h/day player around rank IV. A casual player has rank II
at day 180.

## Dead ends the simulation found and the spec fixed

The first simulated design had three unreachable ranks:

* **Rank IV** (exploration mastery 6): the world only holds ~3,800 exploration XP. Fixed with 12 points per region and
  rank 5 required.
* **Rank IX** ("every track ≥ 7"): collection mastery stalled at rank 1. Fixed with collection sources and the "≥ 5 and
  60 total" rule.
* **Rank X** (mythic tier 5): above the power ceiling at full temper. Fixed to tier 4. Tiers 5–10 stay as optional
  leaderboard content.

Re-run the simulation whenever ranks or content change.
