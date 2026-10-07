# Наадам · Сур харваа, Морин уралдаан, Бөх барилдаан

The Three Manly Games (эрийн гурван наадам: wrestling, archery and horse racing) are a VERIFIED Mongolian tradition.
SÜLD stages all three: the **archery**, the **horse race** and the **wrestling**. Archery:
* Mongolian archers shoot at small сур targets set on the ground (VERIFIED);
* the distances here are game-scale.

## How it works

* **When:** every `naadam.every-minutes` (120), if at least `naadam.min-players` (2) are online. `/naadam start`
  (`suld.admin.event`) opens one at once.
* **Where:** the field outside Kharkhorum's south gate, past the city walls.
  * The shooting line is a white lane between two light-blue banners.
  * **Five сур** (target blocks) lie at each of 20, 30 and 40 blocks.
  * If the field's chunks are not loaded, the contest waits: nothing is ever force-loaded.
* **Scoring:** 3 / 5 / 8 points by range, +2 for the centre of the face. Shots must come from behind the line.
  Each archer has a quiver of **20 arrows** per contest: a multishot crossbow uses three, and arrows past the 20th
  do not score. Spell arrows (the Mergen's volley) never score.
* **Display:** a boss bar «Наадам · Сур харваа — m:ss · Тэргүүн: name score» for everyone within 90 blocks;
  `/naadam` shows the top five.
* **End** after 5 minutes. The best three are proclaimed and paid:
  1. Улсын Мэргэн +300 ₮;
  2. Аймгийн Мэргэн +200 ₮;
  3. Сумын Мэргэн +100 ₮.
* **Cleanup:** every placed block is restored to what was there, also on shutdown. The targets cannot be broken
  while the contest runs.
  * While a contest runs, the replaced blocks are also kept in `plugins/SULD/naadam-restore.yml`. After a crash the
    next start puts them back (verified with `kill -9` mid-contest: 28 blocks restored).
  * The field never replaces a block with contents (a chest, a sign, a banner), and the сур and banners only go
    into air.
  * Every chunk the field touches is loaded asynchronously before it is built, so nobody has to stand at the gate.
* **Shooting position:** an arrow scores only if it was loosed from behind the line. Its launch position counts, not
  where the archer stands when it lands.
* **Prizes** go to the archers still online at the closing ceremony.

Live bot check (dev Paper):
* the field and its 15 сур are placed outside the south gate;
* bow hits from the shooting line score (the board showed 8, 13 and 11 points in three runs);
* the quiver-empty notice comes after the 20th arrow.

The titles follow the real naadam rank names (улсын, аймгийн, сумын: national, province, district). They are given
here as contest titles only.

## Морин уралдаан (horse race)

Real naadam races run tens of kilometres across the open steppe, ridden by child jockeys (VERIFIED). SÜLD's race is a
game-scale loop around Kharkhorum (`worldevent/HorseRaceService`).

* **When:** every `naadam.race-every-minutes` (120), an hour after the archery cycle, with the same
  `naadam.min-players`. `/uraldaan start` (`suld.admin.event`) opens one at once.
* **Course:** 8 checkpoints in a ring 140 blocks from the spawn, starting at the south. They are drawn as columns of
  dust: green for checkpoint 1, gold for the rest. The ring lies past the city's view distance, so its 8 chunks
  are loaded asynchronously (pre-generated ground, never a main-thread load) before the race opens.
  A checkpoint stands on natural ground only (dirt, grass, sand, stone, gravel, snow…), never in water (kelp and
  waterlogged blocks count), on a trunk, a roof or a city wall. If its whole chunk is a lake, it slides along the
  ring (±6°, ±12°, ±18°) to the nearest dry chunk.
* **Who:** only a rider on their **own** SÜLD steppe horse (`/horse`) counts. Riding through checkpoint 1 starts the
  rider's clock. Each checkpoint must be passed in order, within 6 blocks on the map (and 8 in height). Back to
  checkpoint 1 closes the loop.
  A teleport mid-race (`/tpa`, `/home`, the relay, a pearl) or logging out puts the rider back to the start.
* **Display:** a boss bar with the time left, riders and finishers; the action bar shows `Цэг n/8 · m:ss.d`.
  `/uraldaan` lists the finishers.
* **Prizes** for the first three home: +300 / +200 / +100 ₮. The winner is hailed «Түрүү морь»: at a real naadam the
  winning horse is praised as the түрүү.
* **Open** for 5 minutes. Checks run every 5 ticks, mounted players only.

The race rules (gates in order, start, finish, places, prizes, reset) are the pure `mn.suld.api.worldevent.HorseRace`,
covered by `HorseRaceTest`. On the dev Paper, a bot riding its steppe horse by `vehicle_move` packets verified:
* the async opening;
* gates on natural ground (none in a lake, on kelp, a roof or a wall);
* the start and checkpoints 1–6 in order;
* the reset on a teleport;
* the locked saddle, against a control vanilla horse whose inventory opens.

The scripted rider could not find its way through the walled canal of the east quarter, so the full loop on that
world is not bot-verified.

On the dev world the ring at 140 blocks runs through the outer east quarter of Kharkhorum (houses and a walled
canal). Riders take the streets there.

MANUAL_QA_REQUIRED in a real client: the archery field placement on the dev world's south gate, the arrow feel, and
riding the whole loop.

## Бөх барилдаан (wrestling)

Mongolian wrestling has no weight classes and no ring. A wrestler loses when any part of the body other than the
feet touches the ground. The winner performs the eagle dance (дэвэх). The titles Начин, Харцага, Заан, Гарьд, Арслан
and Аварга are real naadam titles, in that order. All of this is VERIFIED tradition.

SÜLD's game version is `worldevent/BokhService`, with the pure `WrestlingTitle` (tested).

* **Challenge:** `/barildaan <player>`, then `/barildaan accept` (or `deny`) within 30 s.
  * Both players must be within 10 blocks of each other.
  * Neither may be a soul, in a dungeon run, mounted or flying.
* **The ring:** 4.5 blocks across, drawn in blue dust where the challenger stands.
  * It needs open, level ground (9×9, head room, solid floor). The world is never changed.
  * The two are placed on opposite sides of the ring, and after 3 seconds: «Барь!».
* **Rules:**
  * Nobody takes damage of any kind during a bout.
  * A hit pushes instead of hurting, harder the fuller the attack charge. A swing costs its charge, so spam-clicking
    is weak.
  * Third parties cannot hit the wrestlers.
  * A wrestler loses by leaving the ring or falling 1.5 blocks below its floor; the ground-touch rule of real
    wrestling is not modelled.
  * A teleport, logout or death forfeits.
  * After 90 s the bout is a draw.
* **The winner** does the eagle dance: cloud wings beating for two seconds.
* **Titles by wins** (the thresholds are game fiction):

  | Title | Wins |
  |---|---|
  | Начин | 3 |
  | Харцага | 6 |
  | Заан | 10 |
  | Гарьд | 15 |
  | Арслан | 25 |
  | Аварга | 40 |

  A new title is announced to the server. `/barildaan stats` shows wins, losses, the title and the next one. Wins
  and losses are kept on the player.
* **No coins**, so two accounts trading wins gain only a title.

Live bot check (dev Paper): on a flat 13×13 platform, a challenge and acceptance, charged hits pushing the opponent
out (the server sent ~1.1 blocks/tick knockback), the win announced with the eagle dance, the opponent's health
unchanged, and the win counted in `/barildaan stats`. MANUAL_QA_REQUIRED: the feel of the push and of the eagle dance
in a real client.
