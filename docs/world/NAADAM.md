# Наадам · Сур харваа

The Three Manly Games (эрийн гурван наадам: wrestling, archery and horse racing) are a VERIFIED Mongolian tradition.
SÜLD stages the **archery** and the **horse race**. Archery:
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
