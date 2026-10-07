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
* **Display:** a boss bar «Наадам · Сур харваа — m:ss · Тэргүүн: name score» for everyone within 90 blocks;
  `/naadam` shows the top five.
* **End** after 5 minutes. The best three are proclaimed and paid:
  1. Улсын Мэргэн +300 ₮;
  2. Аймгийн Мэргэн +200 ₮;
  3. Сумын Мэргэн +100 ₮.
* **Cleanup:** every placed block is restored to what was there, also on shutdown. The targets cannot be broken
  while the contest runs.

The titles follow the real naadam rank names (улсын, аймгийн, сумын: national, province, district). They are given
here as contest titles only.

## Морин уралдаан (horse race)

Real naadam races run tens of kilometres across the open steppe, ridden by child jockeys (VERIFIED). SÜLD's race is a
game-scale loop around Kharkhorum (`worldevent/HorseRaceService`).

* **When:** every `naadam.race-every-minutes` (120), an hour after the archery cycle, with the same
  `naadam.min-players`. `/uraldaan start` (`suld.admin.event`) opens one at once.
* **Course:** 8 checkpoints in a ring 140 blocks from the spawn, starting at the south. They are drawn as columns of
  dust: green for checkpoint 1, gold for the rest. If a checkpoint's chunk is not loaded, the race does not open
  (nothing is force-loaded).
* **Who:** only a rider on their **own** SÜLD steppe horse (`/horse`) counts. Riding through checkpoint 1 starts the
  rider's clock. Each checkpoint must be passed in order, within 6 blocks. Back to checkpoint 1 closes the loop.
* **Display:** a boss bar with the time left, riders and finishers; the action bar shows `Цэг n/8 · m:ss.d`.
  `/uraldaan` lists the finishers.
* **Prizes** for the first three home: +300 / +200 / +100 ₮. The winner is hailed «Түрүү морь»: at a real naadam the
  winning horse is praised as the түрүү.
* **Open** for 5 minutes. Checks run every 5 ticks, mounted players only.

MANUAL_QA_REQUIRED in a real client: the field placement on the dev world's south gate, the arrow feel, the race
loop's terrain (rivers and walls on the ring).
