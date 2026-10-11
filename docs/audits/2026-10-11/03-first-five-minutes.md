# 03: The first five minutes

## How this verdict was reached

This verdict rests on three kinds of evidence, all from 2026-10-11 against `50d5138`:
1. **Code reading.**
2. **Mineflayer bots** on a live offline-mode Paper 1.21.11 server (E-06 to E-08).
3. **Numbers derived from the content and balance formulas.**

**No human played. No real client was used.** Mineflayer does not render screens and auto-accepts resource packs, so anything a client draws was not observed:
- the pack prompt
- menu art
- HUD legibility
- titles over the class menu

## Verdict

**Not convincing yet.**

The *ingredients* are strong:
- a painted Mongol welcome
- an immediate, meaningful class choice
- a first quest with a direction
- rewards inside the first minute
- a city that reads as a capital

But four mechanical problems sit right on the critical path, any one of which can end the session:
1. **The pack prompt is probably lost** under the class menu (ONB-001). This is the biggest unknown.
2. **The player leaves town at about 23 HP** against mobs tuned for 51 (PROG-001 + GAME-005).
3. **A level-1 death costs 5 minutes** as a soul (ONB-002).
4. **Directions contradict each other:**
   - the main avenue leads south into level 7–9 Gobi spawns, while the quest says east (ONB-003);
   - inside the walls the tracker already says "you're there, wait" (ONB-004).

Fix these four, cut the first-30-second noise, and the first five minutes should hold. That still has to be shown with the human test below.

## The actual sequence

Times are from the bot trace (E-06) unless marked *(static)*.

| Time | What happens (verbatim) | Source |
|---|---|---|
| 0.0 s | Pack offered: `http://…:8164/suld-13567cef….zip`. Chat: «Дүрс багц илгээж байна…», «Тавтай морил, <name>! — SÜLD», «Анхны алхам: ангиа сонгоно уу.». **Class menu "Анги Сонгох — SÜLD" opens in the same tick.** Broadcast «+ <name> ирлээ». | `PlayerLifecycleListener.java:49-58` |
| 0.3 s | One-time teleport to the plaza point. The class menu **stays open**: no server close packet, which the bytecode reading in `07` confirms. | E-06 |
| 2.3 s | Title «ТАВТАЙ МОРИЛ» / «Урд байгаа хөвөгч самбаруудыг уншаарай ➜». A 7-line card «━━━━━ ТОГЛОХ ЗААВАР ━━━━━ … [/help] [/tutorial] [/commands]» arrives *behind* the open menu. | `OnboardingService.java:83-113` |
| 5.8 s | Boss bar «Заавар 1/7 · Ангиа сонго». A second title, «ИХ МОНГОЛД ТАВТАЙ МОРИЛ». | `TutorialService.java:103,220` |
| ESC | The menu **reopens 0.1 s after the player closes it**. A new player cannot skip the class choice. | E-06 |
| ≈20 s (choice) | «Та Баатар ангийг сонголоо!», «Эхний эрэл: Анхны Ан», two skill-tree lines; quest boss bar «✦ Анхны Ан 0/3 ➡ Хэрлэнгийн Тал · зүүн · ~220м». The weapon lands in hand. | `ClassSelectionGui.java:185-186` |
| +1–2 s | «Заавар: «Ангиа сонго» ✔», then «Заавар: «Ангийн зэвсгээ барь» ✔ +40 EXP, +20 ₮». **First reward at about 22 s.** | E-07 |
| same | **HP 23–24 of 43–82, armour 0** (measured: Мэргэн 23/43, Бөө 23/48, Баатар 24/82). | E-08 |
| 1–3 min *(static)* | Walk east about 210 blocks to the spawn zone (≈49 s on foot). From x ≈ 51, still inside the walls, the tracker says «✔ … ойролцоо гарч ирнэ, хүлээ», but nothing spawns until x ≥ 207. If the player follows the main avenue **south** instead, the spawns there are 40 % level 7–9. | `QuestTracker.java:168-181`, `RegionSpawner.java:41,88,122` |
| 2–3 min *(static)* | First wolf: 43 HP, ~4 hits; it bites for ~10.8. Kill: «+51 EXP · +2 ₮», «✦ Анхны Ан 1/3». | `CombatListener.java:312-315` |
| 2.5–4 min *(static)* | Level 2 (242 EXP) through tutorial steps plus the first wolf, or chapter 1 done at the 3rd wolf: «Эрэл дууслаа: Анхны Ан (+389 EXP, +50 ₮)», title «ЭРЭЛ ДУУСЛАА 1/42». | `QuestService.java:171-178` |
| 3–5 min *(static)* | Chapter 3: five L4 bandits whose bolts deal ~27, which is more than the ~23 HP the player has. The tutorial's last step points to Khasar's Den, which is gated for another 20–40 minutes (ONB-005). | `TutorialService.java:202`, `DungeonLadder.java:31` |
| relog | The 7-line "how to play" card **is shown again on every join** (measured on a returning player). | E-07 |

**Counted in the first 30 s:**
- about 18 chat lines
- 2 titles
- 2 yellow boss bars
- 2 screens (pack prompt, class menu)
- 5 floating boards
- an action-bar hint

### Phase by phase

| Phase | What works | What fails |
|---|---|---|
| **0–15 s** | Distinctive and Mongolian: welcome title, painted art, a menu that forces a meaningful choice | The pack prompt (ONB-001) and the information wall (ONB-007) |
| **15–60 s** | The choice is real and permanent, the weapon is in hand, and the first reward arrives in ~22 s | One misclick is permanent (ONB-006). The "go east" hint for chapter 1 is never printed (ONB-011) |
| **1–3 min** | Clear cause and effect once the player is outside: kill, EXP, coin, quest counter | Wrong-direction exits and a false "wait" (ONB-003/004). Low HP (PROG-001) |
| **3–5 min** | First level-up and chapter complete: a memorable beat. A relic teaser at about 5 min | A probable first death, then 5 minutes as a soul (ONB-002). The tutorial ends on a refusal (ONB-005) |

## Likely quit points (ranked)

| # | Quit point | Sev. | Low-cost fix | How to test |
|---|---|---|---|---|
| 1 | Pack prompt lost, so menus show boxes (ONB-001) | High | Open the class menu after the pack status settles (or after 15 s) | Three client joins (Prompt, Enabled, Disabled) |
| 2 | First fight kills a 23-HP character (PROG-001, GAME-005) | High | Heal to full on class pick; ×0.5 damage taken at L1–5 | 20 bot fights per class; < 5 % deaths |
| 3 | 5-minute soul lock at L1 (ONB-002) | High | `newbie-levels: 5` → 30–60 s lock | `DeathLockTest` |
| 4 | Main avenue leads south into L7–9 (ONB-003) | Med | Outskirt spawns capped at player level + 2; an "East: Хэрлэн" sign at the gate | Pure spawner test |
| 5 | Tracker says "wait" inside the walls (ONB-004) | Med | Use the city footprint check the spawner already uses | Unit test |
| 6 | Information wall (ONB-007) | Med | One onboarding owner. After the class pick, one title («Зүүн хаалгаар гараад 3 чоно ан») and one bar. No card on rejoin | Bot counts ≤ 6 chat lines in 30 s |
| 7 | One-click permanent class (ONB-006) | Med | A confirm screen with HP / ATK / difficulty | GUI test |
| 8 | Tutorial ends on a closed gate (ONB-005) | Med | Conditional final banner | Unit test |
| 9 | Declining the pack disconnects the player (ONB-008) | Med | `required: false` plus a fallback hint, or keep required and say so before the prompt | Manual |
| 10 | Bedrock: no pack, Java-only controls (ONB-009) | Med | Detect Floodgate players: text HUD, skippable lock-on step; or mark the server Java-only | Bedrock client test |
| 11 | Typos in the very first instructions (LANG-002/003); «Хил» on the HUD (LANG-001) | Med | Text fixes | Native read-through |

## Does it say "SÜLD", or a generic server with Mongolian names?

**What says SÜLD:**
- painted Mongol menus
- Kharkhorum as the hub
- class names that are real words (Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин)
- the first quest on the Kherlen
- ovoo circling clockwise as a real mechanic

**What undercuts it:**
- The first boss is «Хасар» (a historical person used as a monster; `06`).
- Pagoda-like towers in the welcome art (VIS-001).
- "Gobi wolves" on the Kherlen (LANG-010).
- A HUD label that means "border" (LANG-001).

A Mongolian player will notice these within minutes.

## Concrete redesign (smallest change that fixes the path)

1. **Join (0–10 s).** Pack first: «Дүрс багц ачаалж байна…» title, wait for the status. Then the class menu with a confirm step. Nothing else on screen.
2. **Class chosen.** Full heal. One title, «Хархорумын зүүн хаалгаар гараад 3 чоно ан», plus the waypoint bar. Merge the tutorial bar into the quest bar.
3. **Outside the east gate.** Wolves within 30 s. ×0.5 damage taken until L6. The first kill pays and shows the quest counter.
4. **3–5 min.** Level 2 with a short Mongolian voice-line or toast. Chapter 1 complete. The relic teaser as the long-term hook. A 30–60 s soul lock if they die.
5. **Remove from the first session:**
   - the repeated welcome card
   - the second title
   - the credit balance on the sidebar
   - any pointer to a gated dungeon

## Funnel events to measure

Mapped against `suld-api/.../analytics/AnalyticsEventType.java`:

| Step | Existing event | Status / action |
|---|---|---|
| join_attempt | – | **Add** at `AsyncPlayerPreLoginEvent`, with the outcome |
| join_success | `SESSION_START`, `FIRST_LOGIN` | exists (seen in the live log, E-06) |
| resource_pack_ready | – (status in memory only) | **Add** `resource_pack_status` (ACCEPTED / DECLINED / FAILED / LOADED + ms) |
| class_selected | `CLASS_SELECTED` | exists |
| first_action | – | **Add** `tutorial_step` (step index) |
| first_objective | – | **Add** `quest_complete` |
| first_combat_or_discovery | `FIRST_MOB_KILL` (kill, not hit) | partial |
| first_reward | `FIRST_ITEM`, `FIRST_RARE_ITEM` | exists (loot only) |
| five_minute_active | `RETENTION_*` defined but never emitted | **Add** `active_5m` from `ActivityTracker` (active time, not wall time) |

Also fix:
- `FIRST_BOSS`, which is emitted on every boss clear (`DungeonService.java:580`).
- `FIRST_DEATH`, which is defined but unused.

**Privacy:** store UUID + event + timestamp only. No IP, no chat text. Publish a short Mongolian privacy note (`docs/ANALYTICS.md`).

**Provisional targets (hypotheses, not industry benchmarks):**
- ≥ 90 % of join_success reach class_selected.
- ≥ 70 % reach first_combat.
- ≥ 50 % reach five_minute_active.

Compare before and after each change; do not claim an improvement without both measurements.

## Human usability test (5 Mongolian players, 60 min each)

**Recruit:**
1. a vanilla-only teen
2. a Wynncraft or Hypixel player
3. a casual player
4. a history enthusiast
5. a laptop player at 1366×768

**Setup:**
- Think-aloud, with screen and voice recorded with consent (a parent's for minors).
- A Mongolian-speaking moderator.
- Fresh accounts, default GUI scale.
- Funnel events running.

**Tasks** (no help unless stuck for 2 minutes):

| # | Task | Success when |
|---|---|---|
| 1 | Join and get ready to play | pack loaded, class menu reached in ≤ 3 min |
| 2 | Choose a class and say why | can explain the role |
| 3 | Find your next goal | `/quest` or tracker read in ≤ 2 min |
| 4 | Use one ability on an enemy | combo cast within 5 min of leaving the gate |
| 5 | Lock on and fight | Q used |
| 6 | Read HP, level, coins and skill points aloud | all four correct |
| 7 | Spend a skill point | node bought |
| 8 | Find the first dungeon's gate | gate reached |
| 9 | Visit a historic site; say whether its text is history or legend | correct answer |
| 10 | Change your name colour | finds cosmetics; understands coins vs credits |

**Interview** (in Mongolian):
- What was confusing in the first 5 minutes?
- Which words looked wrong? Show «Хил», «Ховор», «Зэрэг», «Цол».
- Did anything about Chinggis, Khasar, Burkhan Khaldun or the ovoo feel disrespectful?
- Did the city and menus feel Mongolian, or like another country?
- Is the HUD too busy?
- What would you tell a friend SÜLD is?
- What, if anything, would you pay for?

**Observe:**
- time to each funnel step
- deaths
- where they look on screen
- every "what is this?" moment

**Success:**
- ≥ 4 of 5 finish tasks 1–4 unaided.
- ≥ 3 of 5 finish tasks 6 and 9.
- No valid cultural-offence report.
- Mean clarity ≥ 3.5 / 5.

**Change to test:** run the session once on the current build and once after fixes 1–3 above. Compare the funnel step times.
