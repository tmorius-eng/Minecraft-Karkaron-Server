# Tutorial (Заавар)

The guided first hour. A persistent state machine (`TutorialProgress`):

`NOT_STARTED → IN_PROGRESS(step 1..7) → COMPLETED`

It is stored in the player's data (`suld:tutorial` = `state:step:paidMask`) and survives restarts and relogs.

| # | Step | Done when | Reward (first time only) |
|---|---|---|---|
| 1 | Ангиа сонго | the profile has a class | — |
| 2 | Ангийн зэвсгээ барь | the class weapon is in the main hand | 40 EXP, 20 ₮ |
| 3 | Чадварын модоо нээ | the skill tree was opened (`/skills`, the menu or an NPC) | 60 EXP, 25 ₮ |
| 4 | Байгаа түгжих | a lock-on target is held (Q with the class weapon, docs/COMBAT_FEEL.md) | 60 EXP, 25 ₮ |
| 5 | Анхны ан | a SÜLD mob killed after the step began | 120 EXP, 40 ₮ |
| 6 | Аяны замаа хар | the quest screen was opened | 60 EXP, 25 ₮ |
| 7 | Хасарын Агуйн хаалгыг ол | within 24 blocks of the gate (docs/world/DUNGEON_HALLS.md); the bar shows an arrow and the distance | 200 EXP, 75 ₮ |
| — | Finish | all steps done | 300 EXP, 150 ₮ |

## Behaviour

* **First join only:** it starts by itself 6 s after the first login (title «ИХ МОНГОЛД ТАВТАЙ МОРИЛ»). A player in
  progress resumes on login.
* **Display:**
  * a yellow boss bar «Заавар n/7 · step», notched by progress;
  * the step's hint on the HUD notice line every 10 s;
  * a sound and chat line per step;
  * a final banner pointing to `/dungeon enter khasar_den`.
* **`/tutorial`** opens the guide menu and states where you are.
  * **`/tutorial restart`** replays every step.
  * **`/tutorial skip`** ends it.
* **No farming:** each step and the finish pay only while their bit in the paid mask is unset. A replay walks the
  steps but pays nothing again (tested in `TutorialProgressTest`).
* **Cost:** once a second, only for players who have the bar (are in the tutorial).

MANUAL_QA_REQUIRED in a real client: the bar's readability and the flow of the first hour.
