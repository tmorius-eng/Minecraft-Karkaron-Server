# Wild mobs and the story map

## Who lives in the wild (`mob/RegionSpawner`)

* Each region's own SÜLD mobs are its monsters, **day and night**. The spawner keeps `world.region-mobs-per-player`
  (4) of them around every player out in the wild, half again as many at night (6).
* Vanilla hostile mobs (zombies, skeletons, creepers, spiders, phantoms, …) **do not spawn naturally** in the
  overworld (`world.vanilla-hostiles: false`, the default). Spawners, the Nether and the End are untouched.
* A vanilla hostile that is killed anyway (from a spawner, or with `vanilla-hostiles: true`) is worth a quarter of a
  same-level SÜLD mob in EXP; no SÜLD loot, no quest progress.
* SÜLD mobs on undead hosts (stray, husk, drowned, zombie) do not burn in daylight (`MobService.onSunburn`); fire
  from lava, blocks, weapons and spells still burns them.
* Spawn spots ignore leaves (`HeightMap.MOTION_BLOCKING_NO_LEAVES`) and try 14 spots, so forests and river banks
  get their mobs. A player whose chapter asks for kills of one of the region's mobs gets that mob for half the
  spawns, and the quest bar points at the nearest one («⬊ ⚔ Дээрэмчин · 18м»).

## The story map (`/quest`, `gui/QuestMenu`)

The owner's painted map of the four lands (`assets/art/source/gui_quests.webp`, box-filtered to the chest by
`gen_ui.py gui_quests`) carries the eighteen chapters on a road: Хэрлэн (1–5) and Говь (6–9) along the bottom, up the
right edge, Хангай (10–14) and Алтай (15–18) along the top, Kharkhorum's ger on the Orkhon in the middle. The stack
count is the chapter number. Each card: the chapter's story (`QuestContent.story`: real places and institutions, the
relay posts, the decimal army, the caravan roads, the Altai's stone statues; the creatures and people are SÜLD
fiction), what to do, where (direction and distance from the player), the progress bar, who gave it and the reward.
Locked chapters show only their land. Clicking the active chapter turns the quest bar on. Bottom row: menu, quest
bar on/off, the dungeon window, the journey (x/18, current land), party, close.

## Protection

* **Dungeon gates** (`DungeonHalls.atGate`): the entrance's square (±8 blocks around the gate, from its foundation to
  above the arch) cannot be broken, built on, poured into, burned, pushed by pistons or blown up; the halls world
  never could. `suld.admin.world` bypasses.
* **Villages** (`worldbuild/VillageProtection`): in the overworld nobody can hurt a villager or a wandering trader
  (players, mobs, fire, falls; only the void and /kill), zombies cannot turn them, and the blocks inside every
  generated village's bounding box cannot be broken, built over, flooded, burned, griefed by mobs or blown up.
  Trading, doors and crops work as usual.

## Name tags and danger

* Every SÜLD mob carries «Lv 12 Хангайн Саарал Чоно ❤ 140/180» (`MobService.nameplate`): grey level, the name in its
  tier's colour (white normal, gold elite, orange champion, red boss), the hearts green → yellow → red with the
  health left, redrawn after every hit and heal. 3D-model mobs keep it too.
* The sidebar names the place (the area, else the region) and its level band, and rates the danger against the
  player's level: Хялбар (≤ −6), Бага (−5…−2), Тохиромжтой (−1…+2), Өндөр (+3…+5), Үхлийн! (≥ +6).
