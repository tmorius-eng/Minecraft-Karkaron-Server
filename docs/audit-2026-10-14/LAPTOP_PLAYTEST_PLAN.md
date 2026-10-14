# SÜLD — Laptop playtest plan (audit 2026-10-14)

For the owner's laptop (Windows, PowerShell). Server setup follows `docs/LOCAL_TEST.md`; this sheet adds the checks
for the audit's changes. Mark each row PASS / FAIL / BLOCKED and fill the defect template (§6) for every FAIL.

## 0. Pre-flight checklist

| # | Step | Command / action | Expected |
|---|---|---|---|
| P-1 | Unit + simulation tests | `.\gradlew.bat test simTest --no-daemon` | BUILD SUCCESSFUL; watch `SimulationTest.liveAndProposedReachSixtyAtTheSamePace` (130–170 h) and `liveBossBandIsTheV2Band` |
| P-2 | Validators | `py tools\validation\run_all.py` | ALL VALIDATIONS PASSED (incl. the new draw-stage check) |
| P-3 | Plugin fetcher test | `py -I tools\plugins\test_fetch_plugins.py` | OK |
| P-4 | Class armour generator agrees with the data | `py tools\content\gen_class_armor.py`, then `git diff suld-api/src/main/resources/items/class_armor.json` | only T4 armour values, at most second-decimal rounding |
| P-5 | Build and deploy | `.\gradlew.bat build -x test`; copy `suld-plugin\build\libs\suld-plugin-0.1.0-SNAPSHOT.jar` to `run\plugins\` | server log: no SEVERE at enable; `[items] catalog: 234 items …` |
| P-6 | Resource pack | the server's pack prompt accepted (pack_format 75) | class weapons show their models |
| P-7 | Two clients | a second account (or `docs/LOCAL_TEST.md` §8) for third-person and party checks | — |
| P-8 | Owner rights | `config.yml` owner set (LOCAL_TEST §5) | `/itemsadmin`, `/skillsadmin` available |

## 1. Class-by-class sheets

### Мэргэн (bow) — first and third person (press F5 to switch view)

| ID | Test | Steps | Expected |
|---|---|---|---|
| W-01 | Full draw | hold R ~1.2 s, release, at a mob | pulling_0 → 1 → 2, upright gold ring below the crosshair, arrow flies fast |
| W-02 | Tap | quick R | short pull, slower arrow (0.6 force), no ring |
| W-03 | Partial | hold ~0.75 s | stage 1 visible, medium arrow |
| W-04 | Not ready | R again within 1 s | dry click + «Нум — x сек», no arrow |
| W-05 | Combo | L-R-L | swing, off-hand swing + tick, L → «✦ Чонын Нүд»; no draw starts |
| W-06 | L during draw | hold R, press L | either the draw continues (client drops L) or it ends and HUD shows `L - _ - _`; never a stuck pose |
| W-07 | Long hold | hold R 6 s | auto-release at 5 s |
| W-08 | Empowered | Чонын Нүд, then 3 shots | soul-fire trail on each, HUD buff `x3 → 0` |
| W-09 | Range | hit a SÜLD mob from 5 and from 40 blocks | similar damage numbers |
| W-10 | Third person | second client watches W-01 | draw pose and stages visible |
| W-11 | Hotbar switch mid-draw | hold R, scroll away | draw cancels, no arrow, no stuck state |
| W-12 | Arrows in bag | pick up an arrow while holding the bow | it goes to the quiver next tick; no vanilla arrow is shot on release |

### Баатар / Дархан / Хүлэгчин / Бөө

| ID | Test | Expected |
|---|---|---|
| C-01 | R-L-R with each melee class | off-hand swing + sweep cue on R; cast once |
| C-02 | Hold R 2 s (sword, axe, spear, staff) | HUD at most `R - R - _`, never a cast |
| C-03 | Бөө R | amethyst chime |
| C-04 | Онгоны Дуудлага (R-R-R, Бөө lvl 10+) facing nothing | «… — зорилго алга», bass; resource and cooldown unchanged |
| C-05 | Онгоны Дуудлага at a mob | spirit flies and hits; with an echo build the echo never adds resource |
| C-06 | Тэнгэрийн Шийтгэл (Бөө ultimate, F) with no enemy in 20 m | «ойролцоо дайсан алга», bass; 60 resource kept, no cooldown |
| C-07 | Any spell while a soul | refusal toast + bass |
| C-08 | Shift+F at level 50 | fifth spell |

## 2. Item and consumable sheet

| ID | Test | Steps | Expected |
|---|---|---|---|
| I-01 | ON_PICKUP binding | `/itemsadmin` give an ancient item; or clear a dungeon with an ancient reward | tooltip «bound to you» at once; cannot be traded |
| I-02 | Craft order to a full bag | fill the poster's bag, fill their order | item in the mailbox, message says so |
| I-03 | Class weapon growth | level up 12 → 13 (`/skillsadmin` or EXP) | weapon damage +1 (Баатар), same affixes |
| I-04 | Recover | `/classgear recover` twice after dropping the weapon | same affixes both times |
| I-05 | Salvage Саарлын Зүрх (level 5) | salvage it | epic material, not Тэнгэрийн Чулуу |
| I-06 | Sell Тэнгэрийн Чулуу | `/item sell` / sell menu | price shown in tooltip and paid |
| I-07 | Айраг | spend resource, right-click | +25 % pool, 60 s faster regen; second use within 30 s refused |
| I-08 | Талын Эмийн Ургамал | take poison damage, use | poison gone, HP rises 5 s |
| I-09 | Арц Хүж | use near an Элсний Сүнс | lower spirit hits; using again only refreshes |
| I-10 | Дарханы Билүү | damage a SÜLD weapon, use outside the city; then in Kharkhorum; then right after being hit | repaired 35 %; refused in the city; refused within 5 s of damage |
| I-11 | No vanilla use | hold right-click with Айраг / plant the reed | no drinking, no planting |
| I-12 | Regional drops | `/loot` simulate or farm Говийн Чоно / Хангайн Баавгай | Талын Илд / Анчны Нум can drop |

## 3. Quest sheet

| ID | Test | Expected |
|---|---|---|
| Q-01 | Chapter 1 giver | the quest text and book say «Анчдын Ахлагч» |
| Q-02 | Chapter 2 completion with a full crash test (kill the server right after) | after restart: chapter 3 active, pelts gone, coins paid once |
| Q-03 | Party credit | two players in a party, 20 m apart, one kills Говийн Чоно | both advance chapter 1 and their daily hunt |
| Q-04 | Gate refusal | reach chapter 9 (Говийн Булш) below level 9 | tracker «⛔ «Говийн Булш» 9-р түвшинд нээгдэнэ.»; book card «Хаалга: ⛔ …»; at level 9 the normal gate arrow returns |
| Q-05 | Orphaned id | set a player's quest id to `quest.removed` in the DB (staging only), log in | placed at the first chapter above their level; warning in the log |

## 4. The 20 highest-value manual tests

W-01, W-02, W-05, W-06, W-09, W-10, W-12, C-01, C-02, C-04, C-06, I-01, I-02, I-03, I-07, I-10, Q-02, Q-03, Q-04, and
P-1 (the automated suites) — they cover every behaviour no unit test can reach (client animation, server events,
persistence under a crash, multiplayer).

## 5. Exit criteria

All P rows PASS; no FAIL in §4; any other FAIL has a defect report and an owner decision.

## 6. Defect template

```
ID:            (e.g. W-06-1)
Build:         (git rev-parse --short HEAD) + jar timestamp
Client:        (Minecraft 1.21.11, pack version, first/third person)
Steps:         1. … 2. … 3. …
Expected:      (from this sheet)
Actual:        (what happened; screenshot / video path)
Server log:    (lines around the time, SEVERE/WARN)
Frequency:     always / sometimes (n of m) / once
Severity:      P0 blocker · P1 major · P2 minor · P3 cosmetic
Suspected area: (file:line if known)
```
