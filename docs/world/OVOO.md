# Ovoo (Овоо)

An ovoo is a cairn of stones, often with a pole and blue silk khadag. It is a sacred place of the steppe and the
mountains, and the custom is to walk around it **three times clockwise** (нар зөв, the way the sun goes) and add a
stone. This custom is VERIFIED as a living tradition. SÜLD's ovoo and their blessing are game fiction built on it.

* **Where:** one ovoo at the heart of each of the 24 named areas (docs/world/AREAS.md), at the same point as the
  pre-generator's P3 area hearts. That is 24 places worth finding.
* **The cairn:**
  * a cone of mixed stones, 5 blocks wide and 4 high;
  * a foundation under it on uneven ground;
  * a spruce pole with light-blue, blue and white wool;
  * a light-blue banner on top.

  It is built the first time a player loads its chunk (never a forced load) and is remembered in
  `plugins/SULD/ovoo.yml`.
* **The walk:** `OvooCircle` (pure, tested) adds up the turn of the compass bearing from the cairn to the walker:
  * three full clockwise turns, between 2 and 9 blocks out, complete it;
  * walking the other way unwinds the count;
  * leaving the ring or teleporting resets it.

  A hint appears on the HUD the first time someone comes within 10 blocks.
* **The blessing «Тэнгэрийн ивээл»:**
  * +5 % EXP for 30 minutes (through `ProgressionBoosts`, kept across relogs);
  * one minute of regeneration;
  * once per ovoo per day (UTC);
  * the first visit of each ovoo also pays 40 EXP.
* **Cost:** every 10 ticks, distance checks of the players near loaded ovoo only.

MANUAL_QA_REQUIRED in a real client: the look of the cairn on varied terrain.
