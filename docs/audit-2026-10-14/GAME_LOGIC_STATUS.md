# SÜLD — Game logic status (audit 2026-10-14)

Scope: end-to-end execution path of a player action and the verified status of every system touched by this audit.
Base: `main` @ `411842d` (on top of `24be54a`). All changes below are **uncommitted and uncompiled**: this CLI
session could not run `gradlew`, `java` or `python` (every invocation required an interactive approval the session
could not give). Status words are therefore precise:

* **Fixed (code)** — the change is written and reviewed by reading; not yet compiled or run.
* **Verified (read)** — confirmed correct by reading the source; no change needed.
* **Open** — known gap, not fixed here (see `GAMEPLAY_IMPROVEMENT_ROADMAP.md`).

## 1. The execution path of one action

```
client input (L/R click, F, Shift+F, Shift+R, hold R)
  └─ Paper events: PlayerInteractEvent / PlayerAnimationEvent(ARM_SWING) / PlayerInteractEntityEvent /
     PlayerSwapHandItemsEvent / PlayerStopUsingItemEvent (new, bow release)
      └─ validation: SkillService.click — paused sky window, class + class weapon held, 60 ms debounce,
         L-within-30 ms-of-R guard (SULD-F08), WP-06 held-R filter (new), draw-in-progress guard (new)
          └─ ComboTracker (suld-api, pure): first click R (melee) / L (Мэргэн), 3 clicks, 1 s window
              └─ SkillService.castDirect0: soul → level lock → 400 ms gap → cooldown → target (new, single-target)
                 → resource → cooldown start → riders/echo/shield/haste → skill-tree CAST trigger
                  └─ spell body (SkillService.run): hurt() → CombatListener.asSpell → EntityDamageByEntityEvent
                      └─ combat math: CombatListener.onHit0 (player → SÜLD mob): attackOf × scale (melee charge curve,
                         arrow draw force from PDC (new WP-08), volley, empowered ×1.5) × level gap × mastery
                         → CombatCalculator (crit) → Ascension lightning; onMobHitsPlayer / onMitigate (armour,
                         Арц Хүж ward (new))
                          └─ feedback: CombatFeel decals (swing, release ring upright (new WP-04)), damage numbers,
                             HUD (HudPanel: combo `L - R - _`, resource, slots, `F ✦ бэлэн`), sounds/particles
                              └─ persistence: profile row (SQL, `profiles().save`) + player file (`saveData`) in the
                                 PlayerDataSaves order — the side a crash could duplicate is written first
```

## 2. System status

| System | Status | Evidence |
|---|---|---|
| Bow quiver stash / restore (SULD-F01) | Fixed (code), found in working tree | `BowQuiver.restore` returns null when nothing fits → no save per scroll; `BowQuiverTest` |
| Arrow pickup to quiver (SULD-F08) | Fixed (code) | `BowQuiver.onPickup` (MONITOR, next tick) |
| Death EXP penalty saved with the lock (SULD-F09a) | Verified (read) | already at `DeathService.java:249-254` |
| Trial wolves immune to the world (SULD-F09b) | Fixed (code) | `DeathService.shieldsTrialWolf`; `DeathTrialServiceTest` |
| Trial wolves on the soul's floor (SULD-F11) | Fixed (code) | `DeathTrialService.floorY` ±6; `DeathTrialServiceTest` |
| Craft orders: shared mailbox, no ground drops (SULD-F05/F12) | Fixed (code) | `CraftOrderService` ctor + `toHand`; `CraftOrderServiceTest` |
| Мэргэн hold-to-draw / release (WP-01/02) | Fixed (code) | `SkillService.onBowUse/startDraw/onBowRelease/release`; `BowDrawTest` |
| Bow cooldown feedback (WP-03) | Fixed (code) | `SkillService.dryString` |
| Release ring orientation (WP-04) | Fixed (code) | `CombatFeel.ring`, `RING_DROP` |
| R-click visual feedback, all classes (WP-05) | Fixed (code) | `SkillService.rightClickCue` (off-hand swing) |
| Held-R combo spam (WP-06) | Fixed (code) | `SkillService.heldRepeat`; `BowDrawTest` |
| Empowered arrow trail (WP-07) | Fixed (code) | `SkillService.empoweredTrail`, `CombatListener.empowered` |
| Arrow damage decays with range (WP-08) | Fixed (code) | `CombatListener.DRAW_FORCE/markDraw/arrowScale/onBowShot`; `ArrowScaleTest` |
| Resource-pack validator: conditions / draw stages (WP-09) | Fixed (code) | `validate_resourcepack.py` `model_problems`, `draw_stage_problems` |
| ON_PICKUP binding on every delivery path (IT-03) | Fixed (code) | `ItemService.stackFor/bindForReceiver`; Dungeon/WorldBoss/WorldEvent/CraftOrder; `EquipmentService.bindOnEquip`; `PickupBindingTest` |
| Rarity bands (IT-02) | Fixed (data) | `items/tiers.json` BOSS/ELITE/DUNGEON = the simulator's v2 bands; simTest golden updated |
| Unobtainable regional weapons / set (IT-01/IT-15) | Fixed (data) | named rare drops in `items/loot.json`; `ItemObtainabilityTest` |
| Class weapon level scaling + fixed rolls (IT-04/IT-05) | Fixed (data+code) | `weapons.json` perLevel; `ClassWeapons.needsRefresh/rollSeed`; `ClassWeaponScalingTest`, `ClassWeaponRefreshTest` |
| T4 class armour growth (IT-16) | Fixed (data+generator) | `gen_class_armor.py`; `class_armor.json` T4; `ClassArmorScalingTest` |
| Low-level legendary salvage (IT-17) | Fixed (code) | `ItemEconomy.salvageRarity`; `SalvageAndSaleAuditTest` |
| Priced legendary material unsellable (IT-19) | Fixed (code) | `Economy.sellPrice`; `SalvageAndSaleAuditTest` |
| Reroll / accessory persistence (IT-08/IT-09) | Fixed (code) | `NpcService` reroll; `EquipmentService.equipAccessory/unequipAccessory` |
| Steppe consumables (IT-12/IT-20) | Fixed (data+code) | `api/item/Consumables`, `plugin/item/ConsumableService`; `ConsumablesTest` |
| Party kill credit (QS-01) | Fixed (code) | `CombatListener.partyNearby`, quest + daily credit |
| Dungeon gate clarity (QS-02) | Fixed (code) | `QuestTracker.gateRefusal`, `QuestMenu` |
| Orphaned quest ids (QS-03) | Fixed (code) | `QuestChain` aliases + `normalise(s, reached)`; `QuestChainOrphanTest` |
| Giver label (QS-04/05) | Fixed (data) | `quests.json` ch. 1–2 «Анчдын Ахлагч» = `NpcService.java:64` |
| Save-before-pay (QS-07) | Verified (read) | `QuestService.complete` moves the state before EXP; `SuldPlugin.java:376` `itemsForCoins` |
| New objective kinds | Fixed (code) | `QuestType.INSPECT_CLUE/CRAFT_ITEM/OVOO_RITUAL`; `QuestObjectiveVocabularyTest` |
| HUD combo / resource / cooldowns / ultimate | Verified (read) | `HudPanel.java:385-391, 561-597` |
| Refused spell feedback | Fixed (code) | `CastResult.NO_TARGET`; soul refusal sound; `Ultimates.hasTarget` |
| Bonum store, Season Pass, professions, Armory API, Ascension, Heroic, Atlas | Untouched | no file of these systems was modified |

## 3. Persistence rules applied (PlayerDataSaves)

| Change | Order now |
|---|---|
| Craft order filled for a full-bag / dead poster | item to the shared mailbox (never the ground) |
| Affix reroll | jade + new affixes → player file, then coins → SQL |
| Accessory equip | bag → player file, then slot → SQL |
| Accessory unequip | slot → SQL committed, then jewel → bag + player file (mailbox if the bag filled or the player left; kept worn if the save failed) |
| Consumable use | one item off the hand → player file, then the effect |
| Whetstone repair | item consumed (file) → durability changed → file again |

## 4. What could not be verified in this session

* Compilation of the changed and new main sources (suld-api, suld-plugin, sim) and of the 14 new test classes
  (7 in suld-api, 7 in suld-plugin; listed in `EXECUTION_CHECKPOINT.md`).
* `./gradlew test simTest` — in particular `SimulationTest.liveAndProposedReachSixtyAtTheSamePace` (130–170 h band)
  after the tiers.json, loot.json and class-gear changes.
* `python3 tools/validation/run_all.py` and `python3 -I tools/plugins/test_fetch_plugins.py`.
* Client-side behaviour of `Player#startUsingItem` on Paper 1.21.11 (the bow draw relies on the client mirroring the
  server's use flag; a repeat-gap fallback releases the shot if it does not).
