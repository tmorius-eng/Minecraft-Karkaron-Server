# Weapon mastery and professions

The owner's direction for the endgame (2026-10-13): a **small power and a name to be proud of** («бага хүч + нэр
хүнд»). This file is what the code does; the numbers come from `suld-api` (`mn.suld.api.mastery`).

## Зэвсгийн ур чадвар: weapon mastery (built)

| Track | Weapon kind (`ItemType`) |
|---|---|
| Илд | sword |
| Нум | bow |
| Таяг | staff |
| Алх | axe |
| Жад | spear |

* **Earning:** a basic hit (melee or arrow) on a SÜLD mob of the content earns points for the track of the held
  weapon: `damage ÷ the player's own basic hit` (`WeaponMastery.points`). A level-10 hit and a level-60 hit weigh the
  same. Training dummies, the training yard's stand-ins and players earn nothing.
* **Levels:** 1–50. Level L needs `round(40 · L^1.5)` points for the next one: 40 at 1, 1 265 at 10, 5 000 at 25, 13 720
  at 49, and 275 808 in all to reach 50 (that many basic hits).
* **Power:** +0.05 % damage a level with that weapon kind, **+2.5 % at 50** (`WeaponMastery.damageBonus`, applied in
  `CombatListener` to the hit). Nothing in the training yard: its kit is the measure there.
* **Titles:** «<Track>-ийн Шавь» at 10, «… Мэргэжилтэн» at 25, «… Мастер» at 50 (`WeaponMastery.title`); level 50 is
  announced to the server. Each level-up records the analytics event `weapon_mastery_level`.
* **Storage:** V25 `suld_weapon_mastery` (`player_uuid`, `track`, `points` ever earned, `updated_at`). The totals are
  read at join. Points earned since then are added to the row every 30 s, at quit and at shutdown, by an additive
  upsert (`JdbcWeaponMasteryRepository.add`), so concurrent writes commute. A player's first write waits for their
  read, so the read can never miss or double one.
* **Commands:** `/mastery` opens the page (five tracks, level, progress bar, bonus, next title). Staff can run
  `/suld mastery <player> SCIMITAR|COMPOSITE_BOW|SHAMANIC_STAFF|HAMMER|LANCE <1-50>`, which is audited as
  `admin.weapon_mastery`.
* **Tests:** api `WeaponMasteryTest`; `JdbcWeaponMasteryIT` (H2, PostgreSQL 16).

## Мэргэжил: professions (in the game)

Four steppe crafts. Each gathers at its own kind of place in the wild (`ProfessionService`, rules `mn.suld.api.mastery`):

| Craft | Gathers at | Tier I–II gives | Tier III–IV gives |
|---|---|---|---|
| Төмрийн Дархан | an ore vein («Төмрийн судал» / «Алтан судал») | Төмрийн Хүдэр | Алтан Хүдэр |
| Эмийн Бариач | a herb patch («Ганга» / «Алтан гагнуур») | Ганга | Алтан Гагнуур |
| Хүлэгч / Адуучин | a horse in a herder's pen | Хялгас | Хүлгийн Дэл |
| Эсгийч | a sheep in a herder's pen | Хонины Ноос | Нарийн Ноос |

### The places (`GatherPlan`)

* Every wild area holds 4 ore veins, 4 herb patches and one herder's pen («малчны хашаа»: 3 horses, 4 sheep).
* A place's tier is its area's band: the highest tier whose level (1, 13, 25, 37) is at most the area's first level.
  That makes 14 tier-I areas (the inner Хэрлэн, Говь and Орхон lands), 8 tier-II, 10 tier-III and the 8 outermost
  tier-IV.
* The spots follow from the area, the spawn and a fixed seed: the same on every start, moving with the spawn as the
  ovoos do.
* They keep clear of the map's places (gates, ovoos, sites, relays, the training yard) by 24 blocks (a pen by 41), of
  Kharkhorum's walls by as much (the outer wall reaches about 190 blocks out, deep into the inner areas), of each other
  by 40, and of their area's edges by 24. They keep inside the world border.
* Veins and patches are display models with a click box, never blocks of the world: nothing to dig out or grief.
  * They are placed while their chunk is loaded and a player is within 112 blocks, and are never saved with the chunk.
  * A spot on water moves to the nearest dry ground within 24 blocks, never within 12 blocks of one of the map's places
    or 4 of the city; with none, the place is skipped for that run.
  * Each carries a small label (seen within about 13 blocks): the name, the tier, the craft and level it needs, and
    the time left while it is spent.
* A pen is built once, the first time a player comes near, and kept in `professions.yml` (one line a pen:
  `pen.tuul~2 x y z`).
  * Its yard is the nearest dry 11 × 11 square within 64 blocks of its spot whose ground rises and falls at most 3
    blocks. Its spruce fences stand as many blocks high as the yard steps up beside them.
  * With no such square (a mountainside, dunes), it takes the most level dry square found, if that rises and falls at
    most 8 blocks, and levels it first: dirt into the hollows and the humps cut away, to the square's middle height.
  * Its yard keeps 30 blocks from the map's places (a dungeon gate, an ovoo, a site, a relay, the training yard, a world
    boss's ground) and 14 from the city, and never stands on another pen or in a village: nothing of theirs is fenced
    in or levelled.
  * When the ground at its spot holds no yard at all (a lake, a mountain), the pen moves to the next of its five other
    spots (`pen.<area>~1` … `~5`, `GatherPlan.PEN_ALTS`). Only one of them is ever built.
  * The gate is on the side facing the spawn and closes itself after 2 s.
  * The pen is protected like a village (`VillageProtection`).
  * Its animals are never saved with the chunk.
  * They cannot be hurt, ridden, tamed, shorn with shears, led or bred, and one that gets out is brought back.

### Gathering

* A right click on a place or an animal (a left click works on a vein or patch too) starts a 2-second gather, shown
  in the HUD.
* Moving more than 1.2 blocks, or being hit, breaks it.
* A finished gather gives `Profession.yield(level)` of the material (1, 2 from level 20, 3 from 40). It also gives the
  craft `Profession.points(level, tier)` points (10 a tier, half once the craft is 24 levels past the tier).
* The place or the animal is then spent for everyone for 300 s; a shorn sheep stays shorn until then.
* A place above the craft's level is refused, naming the level it needs.
* A soul, or a stand-in in the training yard, gathers nothing.
* Levels 1–50: level L needs `round(50 · L^1.4)` points for the next one, 243 098 in all.
* Titles come at 10, 25 and 50 («Төмрийн Дарханы Шавь», «… Мэргэжилтэн», «… Мастер»). Level 50 is announced to the
  server, and each level-up records the analytics event `profession_level`.

### What the materials are for

* **Selling.** The merchant buys them: ore 1 and 3 coins, herbs 2 and 6, hair 2 and 6, wool 2 and 4, felt 8.
* **Refining** (`/mergejil`, or `/mergejil refine <id> [all]`, anywhere). The rates keep near the merchant's prices:

| Refining | In | Out | Needs |
|---|---|---|---|
| `tumur` Төмөр хайлуулах | 3 Төмрийн Хүдэр | 2 Төмрийн Хэлтэрхий | Дархан 1 |
| `alt` Алт хайлуулах | 2 Алтан Хүдэр | 1 Алтан Тоос | Дархан 25 |
| `esgii` Эсгий хийх | 4 Хонины Ноос | 1 Эсгий | Эсгийч 1 |
| `esgii_nariin` Нарийн ноосноос эсгий | 2 Нарийн Ноос | 1 Эсгий | Эсгийч 25 |

* **Recipes.** Nine recipes of the smith's book have an alternative using the crafts' materials (`recipes.json`,
  `…_alt`). An alternative makes the same item in the same band, at the same coins and rarity
  (`RecipeCatalogTest`). The old recipes stay as they were.
  * Felt for the wolf pelts of the four Эсгий armour pieces.
  * Ганга for the scorpion venom of the Бөө staff and tome.
  * Алтан Гагнуур for the ice stone of the Онгон.
  * Хялгас for the pelts of the Морин Жад, and Хүлгийн Дэл for the ice stone of the Эзэн Жад.
* **The guard** (`MaterialGuard`). A SÜLD material is never placed, eaten, potted, composted, tied on as a lead, fed to
  an animal, used in the vanilla crafting grid, smelted, burnt as fuel or brewed. That covers the crafts' wool,
  flowers, string, raw ore and carpet, and the older materials' glowstone dust and fermented eye.

### Storage, commands, tests

* **Storage:** V27 `suld_professions` (`player_uuid`, `craft`, `points` ever earned, `updated_at`). The totals are
  read at join. Points earned since then are added every 30 s, at quit and at shutdown, by an additive upsert
  (`JdbcProfessionRepository.add`), as weapon mastery does.
* **Commands:**
  * `/mergejil` (also `/professions`) opens the page: the four crafts, the tiers open, the nearest place of each with
    a «➜» button that sets the map's waypoint, and the refinings with their buttons.
  * `/mergejil near` lists the nearest places in the chat.
  * The smith's page has a «Түүхий эд боловсруулах» card that opens the same page.
  * Staff: `/suld profession <player> [<craft> <1-50>]` (audited `admin.profession`), and
    `/suld gather list <player> [n] | tp <player> <id> | respawn`.
* **Tests:**
  * api: `GatherPlanTest`, `ProfessionTest`, `ProfessionMaterialsCatalogTest`, `RecipeCatalogTest`.
  * plugin: `PenFileTest` (`professions.yml` keeps a pen's whole id, dots and all), `JdbcProfessionIT` (H2,
    PostgreSQL 16).
  * Bot: `tools/loadtest/bots/professionbot.js` (in `regress.sh`; 16 checks, the last after a server restart).
  * Coverage on the dev world (2026-10-13, every pen visited with no pen remembered): all 40 areas' pens built, 32 at
    their first spot and 8 at another of their spots (one at its third); no gathering place inside the city.
