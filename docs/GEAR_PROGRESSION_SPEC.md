# SÜLD gear progression

Part of the proposed balance. **Status: progression v2 is in the game; see `docs/PROGRESSION_V2.md` for what is live and what is deferred.** This page keeps the full design.

**The item engine stays as built** (`docs/ITEM_SYSTEM.md`): definitions, 8 rarities, 16 stats, 22 affixes, sets,
`ItemGenerator`, `LootEngine`, `Equipment`. The proposal changes **data** (`items/loot.json`, `items/tiers.json`,
vendor prices), one **formula** (item power), and adds the **class gear** layer (`docs/CLASS_GEAR_SYSTEM.md`). Bands
are generated from `ProposedRules.catalog`.

## Level is not gear (directive §10)

Gear progression has three axes, and none of them is the player level:

| Axis | What raises it | Cap |
|---|---|---|
| **Item level** | Class gear: armour level (`docs/ARMOR_PROGRESSION.md`). Loot: the source's band, clamped to content (`DUNGEON_PROGRESSION_SPEC`) | 60 |
| **Rarity** | Class gear: armour tier T1–T6 (uncommon → mythic). Loot: the source's rarity band | mythic |
| **Quality** | Where each rolled stat lands inside its range (`Gear.quality`, read from the real rolls). Floors rise with rarity (`ItemRarity.rollFloor`) | 1.0 |
| **Enhancement / temper** | +2 % per enhancement step inside a tier (max +5). After 60: tempering +1…+10 at 3 % each | +10 % / +30 % |

A level-60 player in T3 epic class gear with mediocre loot sits around 0.9× par gear power. Dungeon gates, Ascension
and mythic tiers all check gear power, not level.

## Item power and gear power (directive §11)

**item power = item level × rarity stat multiplier × (0.75 + 0.25 × quality)**

* Rarity stat multipliers: common 1.00 · uncommon 1.10 · rare 1.25 · epic 1.40 · legendary 1.60 · ancient 1.80 ·
  mythic 2.00.
* A **badly rolled legendary** (quality 0.4 → 1.60 × 0.85 = 1.36) is worse than a **well rolled epic** (quality 1.0 →
  1.40), as the directive asks.

**Gear power** = Σ item power of the 8 worn slots, with class pieces × 1.08 (class set bonus) × (1 + 0.02 ×
enhancement), × (1 + 0.03 × temper).

The live `gearScore` (`Equipment.java:177-184`, item level × (1 + 0.25 × rarity)) stays on the HUD. Gear power is the
gate and balance number. Build quality still matters beyond it: stats feed the real `Equipment.compute` → `SkillBuild`
pipeline, and the simulator measures power from those stats, not from gear power.

Par gear power is what the game expects at each level (≈ 8 slots × level × par rarity × 0.875):

<!-- spec:begin par -->
| Level | Par gear power | Typical worn rarity |
|---|---|---|
| 5 | 37 | common–uncommon |
| 10 | 79 | uncommon–rare |
| 20 | 175 | rare |
| 30 | 278 | rare–epic |
| 40 | 392 | epic |
| 50 | 525 | epic–legendary |
| 60 | 672 | legendary |
<!-- spec:end par -->

## Rarity bands by source

<!-- spec:begin bands -->
| Source (loot tier) | Live band (items/tiers.json) | Proposed band |
|---|---|---|
| NORMAL | Com 75 · Unc 25 | Com 70 · Unc 25 · Rar 5 |
| ELITE | Unc 65 · Rar 35 | Unc 55 · Rar 35 · Epi 10 |
| CHAMPION | Rar 70 · Epi 30 | Rar 55 · Epi 35 · Leg 10 |
| MYTHIC | Epi 75 · Leg 25 | Epi 30 · Leg 50 · Anc 15 · Myt 5 |
| BOSS | Leg 70 · Anc 25 · Myt 5 | Rar 50 · Epi 38 · Leg 10 · Anc 2 |
| WORLD_EVENT | Anc 75 · Myt 25 | Epi 40 · Leg 45 · Anc 12 · Myt 3 |
| DUNGEON | Unc 40 · Rar 40 · Epi 17 · Leg 3 | Unc 35 · Rar 45 · Epi 17 · Leg 3 |
| QUEST | Unc 60 · Rar 35 · Epi 5 | Unc 50 · Rar 40 · Epi 10 |
| CHEST | Com 50 · Unc 35 · Rar 15 | Com 50 · Unc 35 · Rar 15 |
| CRAFT | Unc 60 · Rar 30 · Epi 10 | Unc 60 · Rar 30 · Epi 10 |
<!-- spec:end bands -->

Gear chance per kill (one roll per kill, plus the LOOT% extra-roll chance):

| Source | Proposed | Live |
|---|---|---|
| Normal mob | 6 % | 6–8 % |
| Elite | 25 % | 20–80 % |
| Champion | 60 % | none exist |
| Dungeon boss | 100 % personal (every member) | 50 %, killer only |
| Completion chest | 70 % | 60 % |
| World boss | 100 % | none exist |

Every table also drops band materials (50–100 %), which are the input of class gear upgrades.

## Rarity acquisition targets (directive §20)

These are first-usable-drop times in active hours, p50, from the simulation. The exact current values are in
`docs/PROGRESSION_SIMULATION.md` §Time to milestones.

| Rarity | Where it first comes from | Target (hardcore active hours) | Live today |
|---|---|---|---|
| Rare | normal mobs (5 %), elites | < 1 h | < 0.1 h |
| Epic | elites (10 %), the first dungeons | 1–5 h | ~1 h |
| Legendary | dungeon bosses (10 %), champions, chests (3 %) | 30–60 h | ~2 h |
| Ancient | dungeon bosses (2 %), heroics, mythic chests | 100–150 h | ~4–8 h |
| Mythic | world boss / heroic (3 %), mythic chests (5 %) | ≈ level 60 (~200 h) | ~15–40 h |
| Unique | relics only (never loot) | — | — |

One fixed-rarity definition, `item.khasar_zurkh` (legendary-only, level 5), lets a legendary drop at about 10–50 h.
The spec recommends making it rare–legendary, so legendary means what the table says.

## Vendor and salvage (anti-inflation)

* Legendary and above cannot be sold to vendors. They are salvage-only and feed upgrades, not coins. Other items sell
  for `sell × min(5, rarity multiplier) × (1 + level/30)`. The live formula is `× rarity multiplier (up to 120) ×
  (1 + level/10)`, which made gear sales 75 % of all coin income in the first simulation pass.
* Looted armour and weapons are salvaged into band materials by default, because the class gear owns those slots.
  Loot armour and weapons still matter: as material quality, for collection, and as set pieces in their own slots once
  class sets carry the set bonus.

## The three loot slots

Class gear owns head, chest, legs, feet and main hand. Loot fills off-hand and the two accessories, where rarity,
quality and affixes decide the build:

* crit / crit damage rings;
* spell-damage tomes;
* health-regen totems;
* LOOT% / EXP% jewellery (EXP% capped by the boost cap).

## Implementation notes

* `items/tiers.json`: the proposed bands. `items/loot.json`: per-band tables `loot.p.<region>.{normal,elite,champion}`,
  `loot.p.boss.<n>`, `loot.p.chest.<n>`, `loot.p.world_boss`. The simulator builds exactly these
  (`ProposedRules.catalog`).
* `ItemEconomy.sellPrice` gets the proposed formula and the legendary+ salvage-only rule. This is a verified economy
  change, not an engine rewrite.
* Gear power: a new pure function next to `Equipment.gearScore`, plus the quality read.
* Dead tables (`loot.world_event.chonyn_dovtolgoo`, `loot.quest.reward`, `loot.chest.steppe`, `loot.world.rare`) are
  re-banded before anything rolls them (GR-6).
