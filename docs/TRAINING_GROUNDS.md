# «Дайчдын Дасгалын Талбай» — the training grounds

**Status:** implemented 2026-10-13 (owner's request and choices of that day). Bots: `trainingbot.js` 24/24,
`trainingcrash.js` 6/6 (SIGKILL during training) on the dev Paper 1.21.11 + PostgreSQL. A real-client check is owed by the owner
(`docs/qa/LAPTOP_TEST_2026-10.md` §6).

A new player — even one who has not chosen a class yet — tries any of the five classes **at level 60 with the top gear**
on the training Wardens in Хархорум, and sees what every hit, spell and passive does. Nothing of it stays.

## Where and what

* **Place:** the middle of the walled field in Хархорум, on the spot the owner stood on 2026-10-13 (the big Warden at
  `world -29.5 69 -78.5`: config `training.*` = `-30 69 -79`, the Warden's block). Config v9 moves a yard still on the
  first default (`-63 72 -92`, beside the shrine) there; a centre staff set is kept. Staff move it with
  `/dasgal yard set` (the centre where they stand).
* **The training Warden** («Дасгалын Warden ∞») in the middle, facing the south gate:
  * no AI: it never moves, digs, roars, attacks or gives Darkness;
  * ×1.6 (`Attribute.SCALE`), knockback-proof, silent (a grunt for the trainee on a big hit);
  * a SÜLD target to the combat code (`MobService.tagAs`, id `mob.dasgalyn_warden`, not content): the SÜLD hit formula,
    spells, lock-on and passives work on it exactly as on a level-60 mob (no level gap), and it gives no loot, EXP or
    credit;
  * never dies: design health 10 000 000 (MobService's HP-ceiling scale divides every hit by ~9 766) and a top-up a tick
    after every hit; the target bar shows ∞;
  * never despawns: not saved with the world (so no copy can pile up), held by a plugin chunk ticket on the yard's chunks,
    and a watchdog (every 5 s) puts back any entity of the yard that went missing.
* **One centre, three rings** (owner, 2026-10-13):
  * **the class weapons:** the five classes' T6 weapons float round the big Warden's shoulders (radius 2.4, 3.3 above
    its ground), each spinning, the ring turning once in 36 s, each with its name in the class colour and its role. A
    click on a weapon (its box, 1.0 × 1.3, moves with it) opens the class page: role, resource, spells with their keys,
    build A and B (keystone, ultimate, passives), and «⚔ Build A / B-ээр турших»;
  * **the group:** five small Wardens (×0.6) round its feet (radius 3.4, facing out): any area spell of 3.5 blocks or
    more cast on the big one reaches all six. The ring is open to the south, where the way in and Мэргэн's line are;
  * **the walking one:** a Warden (×0.85, «Хөдөлгөөнт бай ∞») walking a 7-block circle round all of it at ~2.2 blocks/s
    (aim and spells on the move).
* **Labels:** the gate board at the south (what each ring is for), the 10 m and 15 m marks for Мэргэн.
* **HD Warden:** the pack's Warden textures are the vanilla sheets at 4× with a voxel bevel (`tools/pack/hd_warden.py`);
  every Warden on the server looks this way.
* **Blocks:** the city is built by hand, so the plugin places none on its own. `/dasgal yard build` lays the pedestal
  (an octagon of radius 5 under the big Warden and the group, four lights under the weapons' ring), the walking
  Warden's track (a ring of polished andesite) and the range marks, **on open ground only**
  (air, grass, flowers, dirt; a built block is kept and reported), after writing every changed block to
  `plugins/SULD/training-yard-undo.txt`; `/dasgal yard undo` puts each one back. Without the blocks the yard works on the
  grass.

## Entering

* `/dasgal` (`/дасгал`, `/training`): the five classes; `/dasgal <class> [a|b]` directly.
* A station click.
* The class window's confirm page: **«⚔ Туршиж үзэх»** (slot 22) — a player without a class tries the class first. On
  leaving, the class window opens again on that class's confirm page («Батлах» one click away).
* Refused: a soul, «Тэнгэрийн сорил», a class trial, a dungeon run, a duel, a relic bearer, a world event fighter, and for
  60 s after combat (the class-change rule). The player is moved only if they are not in the yard already; the spot they
  came from is where they go back.

## The max character

`TrainingKit` + `TrainingBuilds` (api, pure):

| Part | Value |
|---|---|
| Level | 60, Тэнгэрийн Зэрэг III (+3 % power, +3 points) |
| Skill points | 80: 59 levels + 14 story + 4 regions + 3 ranks; the missing ones granted to the stand-in |
| Build A / B | the class's first / second keystone, one ultimate each (B a different one), then passives (worth most), spell modifiers and attack stats; every step through `pathTo`/`canUnlock`; all 5 classes × 2 builds spend 80, hold all 5–6 passives (`TrainingBuildsTest`) |
| Weapon | the class's T6 weapon at level 60 |
| Armour | T6, armour level 60, enhancement 5, mastery 10 |
| Accessories | `jewel.altan_bugj` (ring) and `jewel.chingis_tamga` (amulet), mythic |
| Rolls | every stat at the top of its range, the most affixes the rarity allows, the combat affixes preferred (24 seeded draws, the one with the most damage/crit/spell/cooldown affixes); the same kit for everyone |
| Coins | 0 (the yard sells nothing) |

## Nothing stays

| What | How |
|---|---|
| The character | a stand-in `PlayerProfile.ephemeralCopy()` that `DefaultProfileService` serves in place of the real one (`standIn` / `putBack`, an atomic cache swap). Every save of it is a no-op (service, JDBC and in-memory repositories), a transfer with it fails and is undone on both sides, a stand-in still cached at quit is dropped unwritten (`DefaultProfileServiceTest`). The real profile is saved right before the swap and is untouched until it comes back. |
| The bag and the spot | written into the player's own data (`suld:training_stash`, `suld:training_return`) and to disk (`PlayerDataSaves.now`) before the kit is given. Leaving clears the kit and puts the stash back; anything else that reached the bag meanwhile is kept (dropped at the player's feet, owner-only, if it does not fit). |
| A crash or a restart | the next join finds the stash before anything else reads the bag (`PlayerJoinEvent` LOWEST), restores it and the spot; the profile loaded is the real one (`trainingcrash.js`). |
| Spell waits, resource, ultimate and passive timers | kept aside at entry (`SkillService.hold`, `SkillTreeService.timers`) and put back at the end: the yard cannot refresh a cooldown. |
| Effects, health, food | the effects the player had come back; health at the same share of the maximum; food as it was. |
| Side effects | the stand-in completes no tutorial step, counts no active minute (no wound healing, armour XP or retention mark), stays off `/top` (the stored row stands), shows «⚔ дасгал» in TAB instead of mastery marks; a vanilla advancement the kit earns (a blaze rod's «Into Fire») is revoked unannounced; hits on the Wardens are no fight (no combat tag). |
| Guards | no damage reaches a trainee, and a trainee's blows (a swing, an arrow, a spell, a passive) reach the Wardens only: the borrowed level-60 power never hurts a mob, an event wolf or a boss; only `/dasgal` and `/mori` (the horse, for Хүлэгчин) work; nothing can be dropped, picked up or opened (dialogs are not inventories); the kit does not wear; stepping (or being teleported) out of the yard ends training; a trainee touches no NPC. Others cannot hit the Wardens (they are told how to start). |

## What comes due while training

Everything the rest of the game reads from a trainee's cached profile would see the stand-in (level 60, top gear) and
everything it writes would be dropped with it. Each path that reaches a trainee from outside is handled:

| Path | While training |
|---|---|
| The week's campaign tier (`CampaignService.payRewards`) | the tier is written to the database as owed first, then paid to the real character the moment training ends (`TrainingService.defer`); the player sees «⏳ Аяны шагнал «…»: дасгалаас гарахад олгоно.». A crash in the yard leaves it owed: paid on the next join like any offline player's (`reward-claim-days`). The same holds when the login claim lands after a quick `/dasgal`. |
| A Дархан order refund (`CraftOrderService.settleRefund`) | stays owed on the board and is paid when training ends. |
| A Дархан order filled for a trainee | made at the level the order was posted at (never the stand-in's 60), kept in the mailbox (the bag in the yard is the kit) and handed over when training ends; a crash: on the next join. |
| The mailbox at join (`TradeService`) | a delivery due while the player is already in the yard waits for the end of training. |
| A world boss | a trainee neither sizes the boss nor is paid as its fighter; a fighter of the standing boss cannot start training until the fight ends (like a world event's contributor). |
| A world event | the yard draws no raid wolves and its trainee is not a participant. |
| A party | a trainee takes no share of a party kill; a party with a member in the yard cannot enter a dungeon (the stand-in would pass the level and gear gates). |
| `/pay` to a trainee | refused with a line (the transfer would fail and be undone anyway). |
| Staff changes (`/suld` exp, coins, classreset, quest, endgame; `/classgear` set and recover; `/skillsadmin` except inspect) | refused with «… дасгалын талбайд байна (/dasgal) — гарсны дараа»: a change to the stand-in would be dropped, and `/classgear recover` would hand out the kit's T6 armour as real items. |
| Discoveries (regions, areas, historic sites, ovoos) | nothing is discovered from the yard (the yard can be set up anywhere): the mark would stay and its EXP would go to the stand-in. |

## What the trainee sees (`TrainingCoach`, personal only)

* **Numbers** over the Warden in their source's colour (CombatFeedback, shown to the attacker only): `1 206` a weapon
  hit, `✦ 2 412` a critical hit (bigger, gold), `Тэнгэрийн Цавчилт 1 930` a spell, `⚡ Цавчин Тайлах 640` a passive,
  `★ Бүхний Нурал 4 610` the ultimate, `🔥 85` a burn tick. The source comes from `HitSource`, set where SÜLD deals the
  damage (`SkillService.hurt`, `SkillTreeService.fire`, `Ultimates.hit`).
* **Chat**, built from the data (`TrainingText`): on entry the build (keystone, ultimate, spells with keys, passives);
  after each spell lands «Тэнгэрийн Цавчилт III (R-L-R): Урд талын бүх дайснаа цавчина. ATK ×2.94 · 25 Уур хилэн ·
  0.9 сек — 1 бай, 382 хохирол» (in full the first time a spell is cast, short after); the first time each passive
  fires «Цавчин Тайлах: Чухал цохилтоор 35% магадлалтайгаар … Хүлээлт 3 сек. Энд 1/1 (100%)», then a toast; the ultimate
  with its rule and wait, then its total.
* **Sidebar** (`HudService.sidebar`): class · build, DPS (10 s, or since the fight began), total, biggest hit, hits and
  crit rate (weapon hits), the top three sources with their share, the top three passives with fired× observed% /
  design%.
* **Boss bar:** «ДАЙЧДЫН ДАСГАЛЫН ТАЛБАЙ · БААТАР 60 · Build A · DPS 1 842 · гарах: /dasgal leave».
* `/dasgal stats`: every source and every passive in full (a dialog); «Тоо тэглэх» starts the count again.
* Hotbar: slot 8 the menu book (switch class or build, numbers, leave), slot 9 the leave barrier.

## Staff

| Command | Does |
|---|---|
| `/dasgal yard info` | the centre, trainees, Wardens standing |
| `/dasgal yard set` | the centre where the staff member stands (config saved; trainees are sent back first) |
| `/dasgal yard respawn` | puts every entity of the yard up again |
| `/dasgal yard build` / `undo` | the blocks, on open ground only, with the undo log |

Permission `suld.admin.training` (in `suld.admin`); every change is audited (`training.yard.*`, `training.enter`,
`training.leave`).

## The Academy's practice dummies (fixed the same day)

The class trial's mannequins (`ClassTrialService`) were plain entities: the SÜLD hit formula, spells, lock-on and the
damage numbers skipped them, and any hit bigger than their health was zeroed. They are SÜLD-tagged targets now
(`mob.sorilyn_dures`, not content), with the same design health and top-up as the Wardens; their numbers name the spell.

## Tests

* api: `TrainingBuildsTest` (80 points, both builds legal for every class, different keystones and ultimates, survive
  the join check of a brand-new player), `TrainingStatsTest` (DPS window, pause, shares, crit rate, passive rates,
  Mongolian numbers and texts).
* plugin: `DefaultProfileServiceTest.aTrainingStandInIsNeverWritten`, `aStandInLeftAtQuitIsDropped`.
* api: `WorldBossTest.involvedOnceTheyHitOrStoodThere` (who may not start training during a world boss).
* bots (dev server): `trainingbot.js` (in `regress.sh`), `trainingpay.js` (`DEV_CAMPAIGNS=1`: a campaign resolved during
  training pays the real character when it ends, the database coins grow by the paid amount and the tier is claimed; a
  trainee's swings leave a 2-hp husk alive and the same swings kill it after training), `trainingcrash.js`
  (`DEV_CRASH=1`, kills the server).
