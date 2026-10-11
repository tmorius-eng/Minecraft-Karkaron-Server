# SÜLD Ascension — Тэнгэрийн Зэрэг

**Ranks I–III are in the game** (status: MANUAL_QA_REQUIRED, see below). Ranks IV–X are the proposed design and wait
for their content (heroic and mythic modes, mastery tracks, a world boss).

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
* **Counting:** Тэнгэрийн Ордон clears are counted per character at completion (`Endgame.palaceClears`, an optional
  field of the version-1 endgame JSON, so older rows read as 0).
* **Staff/QA:** `/suld endgame <player> rank|points|palace <n>` (audited).
* **Simulator:** `Engine.ascensionReady` uses the same `Ascension.blocked` for the live rules.

Client QA to do: open /ascend below and at 60, check the checklist and the two-click rite, the priest at level 60,
the sidebar row, +1 skill point in /skills, and that T6 shows as open at rank III.

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
