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

## The quest book (`/quest`, `gui/QuestMenu`)

`/quest` opens a quest book in the akuma style with the Ikh Mongol colours (navy, gold, red; `gen_ui.py
gui_questbook`). It has three tabs on top: Акт I (chapters 1–18), Акт II «Хөх Сүлдний Зам» (19–42) and Газрууд (the 16
historic sites). Below them is a grid of cards. It opens on the act of the current chapter.

Every card wears the pack tooltip frame `suld:mongol` (`gen_ui.tooltip_style`, a nine-slice navy panel with a gold
frame). Its lines, top to bottom:
* the story;
* «Төрөл» (Ан, Аялал, Цуглуулга, Өсөлт, Агуй);
* «Мэдээлэл», with ┃ bars for what to do, where (direction and distance), progress, who gave it and the land;
* «Шагнал», the EXP and ₮;
* a mouse-icon click hint;
* a status badge: ИДЭВХТЭЙ, ДУУССАН or ТҮГЖЭЭТЭЙ.

Locked cards show «???», the chapter that opens them and the land.

A site card shows the kind, the history label, the description, the area and the direction. Its badge is ҮЗСЭН or
ҮЗЭЭГҮЙ, and clicking it prints the way.

The bottom row: menu, quest bar, dungeons, the journey, party, and «Газрын зураг», which opens the act's painted map
below. The map's «Эрлийн Ном» button comes back.

**The owner's art, in HD.** The window, the tooltip frame, the quest badges, the mouse icon and the 23 rank, staff,
class and misc badges come from three paintings by the owner in `assets/art/source/`:
* `gui_questbook.webp`, the window with Акт I active;
* `tooltip_badges.webp`, the frame, the six quest pills and the mouse;
* `badges.webp`, the 23 pills.

`tools/pack/hd_art.py` turns them into textures four times larger than their font glyphs (`hd_art.SCALE`), so at GUI
scale 4 every texture pixel is one screen pixel. What it does with each:
* **Window:** warps the painting so its wells land on the chest slots (control points at the well centres; the
  toolbar has its own spacing). It makes the Акт II and Газрууд states by swapping the gold and blue tab backgrounds
  and recolouring the painted labels.
* **Tooltip frame:** a 40x37 nine-slice with a 12 px border. The client draws the sprite 12 px around the text, so
  the corner ornaments never cover it.
* **Badges:** cut from the sheets. The badge sheet misspells «АЮУЛГҮЙ» and «АЮУЛТАЙ» as «АМУЛГУЙ», «АМУЛТАЙ», so those
  two are re-lettered in the pixel font on their own fill.

The badges are the same glyphs the TAB list, chat and the name tags above the players' heads use, so all of them
changed together. Without the source files gen_ui falls back to the drawn versions.

How the tooltip frame looks in a real client is **MANUAL_QA_REQUIRED**. The bots check the window, the cards, the
`tooltip_style` component and the badges.

## The story map (painted, from the book's «Газрын зураг»)

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
