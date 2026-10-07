# Наадам · Сур харваа

The Three Manly Games (эрийн гурван наадам: wrestling, archery and horse racing) are a VERIFIED Mongolian tradition.
SÜLD stages the **archery**:
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

MANUAL_QA_REQUIRED in a real client: the field placement on the dev world's south gate, the arrow feel.
