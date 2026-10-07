# SÜLD HUD — manual QA in a real Minecraft client

Everything the server can verify is automated (unit tests + the live bot that decodes the HUD from the real packets,
see `audit/hud-status.json`). What only a person with a real 1.21.11 client can judge is listed here. Use the local
dev server, accept the resource pack, choose a class.

| # | Check | How | Expected |
|---|---|---|---|
| 1 | Panel position at every GUI scale | Options → Video → GUI Scale 1, 2, 3, 4, Auto | Two rows of bars directly above the hotbar, the diamond centred on the hotbar's middle, nothing overlapping the hotbar or the item names |
| 2 | Vanilla HUD hidden | look at the hearts/food/armour/XP area; get absorption (golden apple), poison, wither, freeze in powder snow, ride a horse, go under water | no vanilla hearts, food, armour, bubbles or XP bar anywhere; the panel shows absorption (gold), mount health and breath instead |
| 3 | Level number hidden | gain vanilla XP (break ore / kill mobs) | the green vanilla level number never shows through the diamond/cartouche |
| 4 | Pixel crispness | GUI scale 2 and 4 | frames, diamond, digits and icons sharp (no blur); numbers readable on every bar colour |
| 5 | Hotbar skin | scroll the hotbar, use the off-hand | dark felt slots in bronze, gold/turquoise selection, off-hand slot matches |
| 6 | Health | take damage, heal, drop below 25 % | bar and numbers follow, pale chip after each hit, frame pulses red below 25 % |
| 7 | Class resource | each of the 5 classes | bar colour of the class, numbers follow casts and regeneration |
| 8 | Spells | cast with the combos; reach the unlock levels 10/20/35 | slot shows the countdown and turns back; locked slots show a lock; red numeral when the resource is short; the ultimate slot appears when the build has one |
| 9 | Combo line | click a combo slowly | `R - L - _` appears above the panel while typing |
| 10 | Notices | walk into Kharkhorum's protection (place a block), finish a task kill, ride a horse | the text line shows the notice for ~2.5 s, the panel stays |
| 11 | Status line | idle | class · ₮ coins · ◆ unspent points · ⚔ gear score |
| 12 | Target frame | look at / hit a wolf, an elite, a boss, a player | frame at the top with name, level, health, bronze/silver/gold frame; switches with the target; disappears when it dies or 3 s after you look away (12 s after a hit) |
| 13 | Other boss bars | start a dungeon, a world event, die (soul bar), follow a quest | their bars still show normally (only white bars are transparent) |
| 14 | Shift + right click | hold the class weapon, sneak, right click | the skill-tree map opens; a normal right click still starts a combo |
| 15 | F1 / spectator | press F1; switch to spectator | HUD hidden with F1; no panel in spectator |
| 16 | Without the pack | (admin) disable the pack or test a client that failed to load it | the plain text bar is shown instead, no boxes |

Report anything misaligned with a screenshot and the GUI scale/resolution.
