SÜLD MMORPG — CLAUDE CODE CLI MASTER EXECUTION PROMPT (AUDIT COMPLETION + FULL BUG REMEDIATION + GIT PUSH)



You are operating as the Principal Gameplay Architect, Senior PaperMC 1.21.11 Engineer, Forensic Code Auditor, Combat \& Animation Specialist, Quest \& Itemization Designer, and QA Lead for the SÜLD (СҮЛД) Minecraft MMORPG repository (https://github.com/tmorius-eng/Minecraft-Karkaron-Server, currently on main at commit 411842d on top of 24be54a).



A previous Claude Code web session (audit-2026-10-14) completed 3 deep forensic sub-audits (items.md, quests.md, weapons.md) before hitting the web session rate limit. In parallel, a Gemini forensic audit (SULD-F01 through SULD-F12) merged 18 hardened files in commit 411842d.



Your mission in this local CLI session is twofold:



IMPLEMENT \& FIX all verified gameplay, itemization, quest, weapon-animation, combat-feedback, and persistence defects across the codebase (listed with exact file/line references in Phases 1–5 below), plus Gemini's architectural \& gameplay enhancements.



COMPLETE \& WRITE the 8 required audit \& manual laptop playtest deliverables under docs/audit-2026-10-14/, verify everything with ./gradlew test simTest and python3 tools/validation/run\_all.py, commit all changes cleanly, and push directly to origin main.



MANDATORY TECHNICAL \& ARCHITECTURAL GUARDRAILS



Target Platform: Minecraft Java Edition 1.21.11, Paper API 1.21.11-R0.1-SNAPSHOT, Java 21, Resource Pack pack\_format: 75.



Zero Third-Party Gameplay Plugins: All gameplay logic must remain in suld-api (pure Java 21 domain, zero Bukkit imports) and suld-plugin (Paper runtime adapter).



Preserve All 24be54a \& 411842d Work: Do NOT revert or delete any existing features (Bonum payment store, Season Pass «Хуралдайн Зам», 4 Steppe Professions, Web Armory API, Ascension IV–X, Heroic Dungeons +1..+20, World Map «Сүлдийн Атлас», or the 411842d forensic fixes).



Never Weaken Tests: Every existing unit test, integration test, simulator golden test (simTest), and Python pack/registry/world validator must pass—and you must add new JUnit 5 regression tests for the fixes below.



PHASE 1: COMPLETE THE 3 REMAINING SURGICAL MERGES FROM SULD-F01 / F09 / F11 / F12



These 3 files had concurrent changes in 24be54a and were kept at 24be54a in commit 411842d. Apply these exact fixes first:



BowQuiver.java (SULD-F01 — Main-Thread saveData() Infinite Loop Guard):



In BowQuiver.quiver(Player p) (suld-plugin/src/main/java/mn/suld/plugin/item/BowQuiver.java), in the } else if (!stash.isEmpty()) { branch:



Compute total arrow count long before in stash before for (ItemStack s : stash) left.addAll(p.getInventory().addItem(s).values()); and long after in left afterwards.



If before == after (0 arrows fitted because all 36 inventory slots are full of non-arrow items), return; immediately without calling store(p, left) or PlayerDataSaves.now(p). This eliminates the 20+ calls/sec main-thread disk write DoS on hotbar scroll and the 40-tick timer.



Also add an @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) for EntityPickupItemEvent (SULD-F08) so if a player picks up an arrow while holding the MERGEN class bow, quiver(p) runs on the next tick.



DeathService.java (SULD-F09 \& SULD-F11):



In DeathService.onDeath(PlayerDeathEvent e) (suld-plugin/src/main/java/mn/suld/plugin/death/DeathService.java): when expLost > 0, after profile.progression(after);, immediately call services.profiles().save(profile); so a crash during death lock cannot roll back the EXP penalty while keeping the death lock.



In DeathService.onSoulDamaged(EntityDamageEvent e): if DeathTrialService.ownerOf(e.getEntity()) != null \&\& !(e instanceof EntityDamageByEntityEvent) \&\& e.getCause() != EntityDamageEvent.DamageCause.VOID \&\& e.getCause() != EntityDamageEvent.DamageCause.KILL, cancel the event (e.setCancelled(true); return;) so Spirit Wolves in /trial cannot be cheesed with lava, fire, cactus, drowning, or fall damage.



In DeathTrialService.java (SULD-F11): replace l.setY(l.getWorld().getHighestBlockYAt(l) + 1) with a vertical search within ±6 blocks of the player's blockY for a solid floor + 2 passable blocks above (falling back to player Y) so wolves never spawn on cave roofs above the soul.



CraftOrderService.java (SULD-F05 \& SULD-F12):



In constructor: replace this.mailbox = new Mailbox(plugin); with this.mailbox = services.mailbox(plugin); (using the shared singleton added to SuldServices.java in 411842d).



In fill(Player crafter, String id): when poster != null, check poster.isOnline() \&\& !poster.isDead() \&\& poster.getInventory().firstEmpty() >= 0 before calling services.itemService().give(...). If the online poster is dead or has a full inventory (firstEmpty() < 0), route the crafted item to mailbox.keep(o.poster(), List.of(services.itemService().stack(made, poster, 1)), "craft order " + o.id()) and notify the poster that their bag was full and the item is safely stored in their mailbox.



PHASE 2: FIX ARCHER (МЭРГЭН) BOW DRAW \& RELEASE ANIMATION + ALL 5 CLASS WEAPON FEEDBACK (WP-01..WP-10)



Verified Root Cause (from Bytecode \& Source Audit):



All 18 pulling models/textures (mergen\_1..6\_pulling\_0/1/2 and surgamj\_num\_pulling\_0/1/2) and the using\_item + use\_duration range dispatch in resourcepack/assets/minecraft/items/bow.json already exist and are valid.



Why the bow never visibly draws:



BowQuiver.java:116-131 stashes all arrows while holding the class bow. In Paper 1.21.11, vanilla BowItem.use checks for a projectile in inventory (Enchantment.INFINITY alone does not bypass the initial arrow check in survival mode) and returns InteractionResult.FAIL before the client ever enters using\_item = true.



SkillService.onBowUse (SkillService.java:264-281) sets e.setUseItemInHand(Event.Result.DENY) and immediately fires shoot(p) on right-click press to avoid vanilla bow-draw eating left-click combo inputs (L-R-L).



Required Implementation (WP-01 through WP-10):



True Visual Bow Draw \& Release (WP-01 \& WP-02 — Hybrid Tap-Combo / Hold-Draw Architecture):



Keep BowQuiver stashing arrows and keep e.setUseItemInHand(Event.Result.DENY) in SkillService.onBowUse.



When a MERGEN player right-clicks with their class bow and no combo is currently active (t == null || t.current(now).isEmpty()):



Check cooldown (SHOT\_MS = 1\_000). If on cooldown (WP-03), play a subtle dry-string click (Sound.BLOCK\_DISPENSER\_FAIL, volume 0.35f, pitch 1.6f) and show a brief action-bar cooldown indicator instead of failing silently.



Start the native item-use state on the server via Paper's p.startUsingItem(EquipmentSlot.HAND) (or if the player taps vs holds, record draw start timestamp drawStart.put(p.getUniqueId(), now) and call p.startUsingItem(EquipmentSlot.HAND) + p.setCooldown(Material.BOW, 20)).



Listen for io.papermc.paper.event.player.PlayerStopUsingItemEvent (and fallback tick scheduler if released early/quick-tap):



Calculate charge factor float force = Math.min(1.0f, Math.max(0.35f, ticksHeld / 15.0f)) (or full 1.0f after >= 10 ticks; if released within < 4 ticks as a quick tap, either fire a quick shot at 0.6f force or allow an immediate L click to cancel the draw and transition seamlessly into an R-L-... combo!).



Crucial Combo Compatibility Rule: In SkillService.onSwing (ARM\_SWING / Left Click), if the MERGEN player is currently drawing (p.isHandRaised() / drawStart.containsKey(id)), pressing Left Click cancels the bow draw (p.clearActiveItem()) and registers 'L' in ComboTracker after the initial 'R', so R-L-R and L-R-L combos work 100% reliably while holding right-click draws and animates the 3-stage bow model (pulling\_0 -> pulling\_1 -> pulling\_2)!



On release (shoot(Player p, float force)), swing main hand (p.swingMainHand()) and play Sound.ENTITY\_ARROW\_SHOOT.



Fix Hold-Right-Click Auto-Repeat Combo Spam (WP-06):



When a player holds down Right Click, the Minecraft client re-sends PlayerInteractEvent every 4 ticks (200 ms), which currently bypasses the 60 ms CLICK\_DEBOUNCE\_MS and auto-triggers R-R-R spells unintentionally.



Track PlayerStopUsingItemEvent / interaction cadence or require distinct click presses (or increase same-button R->R minimum interval / ignore 4-tick hold-repeat packets while p.isHandRaised()) so holding right-click to draw a bow or shield never spams R-R-R combos.



Fix Melee \& Shaman Right-Click Combo Visual Feedback (WP-05):



Currently, pressing Right Click ('R') with iron\_sword (Баатар / Хүлэгчин), iron\_axe (Дархан), or blaze\_rod (Бөө) sends no client animation at all.



In SkillService.click(Player p, char c), when c == 'R' is accepted into ComboTracker, call p.swingOffHand() (or p.swingMainHand() with a distinct pitch cue Sound.ENTITY\_PLAYER\_ATTACK\_SWEEP / class-specific chime for BOO staff) so every combo step has immediate first-person and third-person visual/audio confirmation.



Fix Мэргэн Release Ring Orientation (WP-04), Arrow Speed Decay (WP-08), and Empowered Arrow Visuals (WP-07):



In CombatFeel.java:305-308: orient the release ring ItemDisplay perpendicular to the shooter's look vector (billboard/facing shooter) or offset slightly below eye line (-0.18 Y) so it is never viewed edge-on as a flat line in first-person.



In CombatListener.java:229-235: store the initial launch force/multiplier on the AbstractArrow's PersistentDataContainer (or scoreboard tag) at launch time rather than multiplying by arrow.getVelocity().length() on impact (which penalizes long-range sniper shots due to air drag).



When Чонын Нүд (Wolf's Gaze) empowers the next arrow, attach a visible Particle.SOUL\_FIRE\_FLAME / Particle.CRIT trail task to the arrow and ensure the multiplier applies to all valid combat targets.



Fix validate\_resourcepack.py (WP-09):



Update tools/validation/validate\_resourcepack.py so condition nodes (on\_true / on\_false) and range\_dispatch entries in resourcepack/assets/minecraft/items/\*.json are recursively traversed and every referenced suld:item/\*\_pulling\_\* model and texture is verified on disk.



PHASE 3: FIX ALL ITEMIZATION, RARITY, BINDING \& CONSUMABLE DEFECTS (IT-01..IT-20)



Inspect and resolve every confirmed defect from the itemization audit:



Fix Broken ON\_PICKUP Soulbound Binding (IT-03 — High Priority):



Currently, BindType.ON\_PICKUP (used by ANCIENT / MYTHIC / RELIC / dungeon boss drops) is only checked in EquipmentService.onPickup(EntityPickupItemEvent) (EquipmentService.java:436-454), and bindOnEquip explicitly skips ON\_PICKUP (:216). Thus, items granted via ItemService.give(), dungeon rewards, mailbox delivery, or crafting never bind to the player and remain freely tradable!



Fix: In ItemService.give() and ItemService.stack(ItemInstance, Player, int) (when owner != null and def.bind() == BindType.ON\_PICKUP or rarity.forcedBind() == BindType.ON\_PICKUP), bind instance.boundTo(owner.getUniqueId()) immediately upon creation/delivery, AND in EquipmentService.bindOnEquip() also bind any unbound ON\_PICKUP item as a defense-in-depth fallback.



Restore tiers.json v2 Rarity Drop Bands (IT-02 — High Priority):



suld-plugin/src/main/resources/content/tiers.json currently has pre-v2 bands where BOSS drops 70% Legendary (Мөнх), allowing players to farm full Legendary gear in \~2 hours instead of the designed 30–60 hours.



Fix: Update tiers.json rarity weights to match the balanced v2 progression curve (e.g., BOSS: Common 0, Uncommon 25, Rare 45, Epic 24, Legendary 6; scale ELITE and DUNGEON bands proportionally) and verify LootEngineTest and SimTest pass.



Make Unobtainable Weapons \& Sets Obtainable (IT-01 \& IT-15):



weapon.talyn\_ild, weapon.khasar\_soyo, weapon.altai\_jad, and weapon.anchin\_num (required to complete set.talyn\_anchin) are currently unobtainable because no loot pool includes ItemType.WEAPON and they have no recipe or named drop entry.



loot.khasar, loot.quest.reward, and loot.chest.steppe have dead/unlinked entries.



Fix: Add named drop entries or WEAPON pool weights to the appropriate regional elite/boss loot tables (loot.khasar, regional steppe/taiga/altai tables) and crafting recipes so all 4 regional weapons and set.talyn\_anchin are obtainable in-game.



Fix Class Weapon Per-Level Scaling (IT-04) \& T4 Class Armor Scaling (IT-16):



ClassWeapons.java:228-280: Class weapon lore states that the weapon grows with the bearer's level, but perLevel is 0 between tier breakpoints (1, 12, 24, 36, 48, 60), causing a level 20 crafted sword to out-damage a Tier 2 class weapon by \~3x. Add smooth perLevel attack scaling to class weapons across levels 1–60 (and deterministic seeded affix rolls per player+tier in ClassWeapons.java, fixing IT-05).



ClassArmor.java / items.json (IT-16): Fix Tier 4 class armor perLevel armor growth so Tier 3 never overtakes Tier 4 at high armor levels.



Fix item.khasar\_zurkh Early Legendary Salvage Exploit (IT-17) \& tengeriin\_chuluu Sellability (IT-19):



Adjust item.khasar\_zurkh rarity/level or salvage yield so a Level 5 dungeon drop cannot be farmed and salvaged into 5 end-game tengeriin\_chuluu (Sky Stones).



Fix Menus.sellSlot / sellAll or ItemEconomy so items with explicit sell > 0 work consistently with their tooltip.



Implement Real Steppe Consumables \& Utility Items (IT-12 \& IT-20):



ItemType.CONSUMABLE is currently unused in the catalog.



Wire ItemType.CONSUMABLE in ContentLoader, ItemFactory, and a new ConsumableListener (or ItemService) with cooldown tracking, combat-safe usage, sound/particle feedback, and anti-dupe consumption ordering (PlayerDataSaves):



item.airag\_flask (Айраг — Fermented Mare's Milk): Restores 25% Stamina/Mana/Focus immediately and grants +10% resource regeneration for 60s (30s cooldown).



item.steppe\_salve (Талын Эмийн Ургамал — Steppe Herbal Poultice): Restores 20% Max HP over 5s and cleanses Poison/Wither/Slowness (20s cooldown).



item.ovoo\_incense (Арц Хүж — Sacred Juniper Incense): Reduces active Death Wound (Шарх) penalty or grants +8% Defense against regional spirits for 180s (cannot stack with itself; refreshes duration).



item.darkhan\_whetstone (Дарханы Билүү — Field Repair Kit): Restores 35% durability to the currently held weapon or worn class armor outside of Kharkhorum.



Add Missing PlayerDataSaves.now(p) on Reforge / Reroll / Repair / Accessory Equip (IT-07, IT-08, IT-09):



Ensure SmithMenu (reforge, reroll, repair) and EquipmentService (accessory equip/unequip) flush inventory/profile safely and use ItemEconomy.repairCost consistently.



PHASE 4: FIX QUEST ENGINE, PARTY KILL CREDIT, DUNGEON GATE CLARITY \& RICHER OBJECTIVES (QS-01..QS-08)



Party Kill Credit Sharing (QS-01 — Medium Priority):



In CombatListener.java:362-380: when a SÜLD mob is killed by killer, query services.parties().partyOf(killer.getUniqueId()). Credit services.quests().onMobKilled(member, mobId) (and daily hunting tasks) for every online party member in the same world within 48.0 blocks who is not dead/Soul-locked.



Dungeon Gate Readiness \& Refusal Clarity in Quest Tracker \& Menu (QS-02 — Medium Priority):



In QuestTracker.java and QuestMenu.java: when the active chapter is QuestType.DUNGEON, call DungeonService.soloEntryRefusal(p, targetDungeonId) (or inspect minLevel, required previous dungeon rung, and minimum gear power). Display the exact requirement in the /quest GUI card lore and tracker action-bar/boss-bar if the player does not yet meet the gate (instead of telling them "энд! Хаалгыг дар" when the gate will reject them).



Orphaned Quest ID Normalization (QS-03 — Low-Medium Priority):



In QuestChain.java:60-65 and QuestService.ensure(Player, PlayerProfile): if profile.questState().activeQuestId() is non-null but does not exist in QuestChain.byId(), normalize it to the next uncompleted chapter in QuestChain.chapters() (based on profile.questState().completed()) so a renamed/removed quest ID never soft-locks a player's story or chapter skill points.



Quest Dialogue, NPC Giver Alignment \& Save-Before-Pay Ordering (QS-04, QS-05, QS-07):



Align the chapter 1 giver label in quests.json (Анчдын Ахлагч) with NpcService and ensure QuestService updates and saves profile.questState(next) before granting EXP/coins.



Extend Quest Vocabulary with Maintainable Objective Types (Part 2.2 \& 2.4):



Extend QuestType in suld-api and QuestService / QuestListener in suld-plugin to support:



INTERACT\_PROP / INSPECT\_CLUE (investigating a named prop, stele, or shrine in a region),



CRAFT\_ITEM (crafting or upgrading a required item tier/recipe),



OVOO\_RITUAL (performing an Ovoo blessing in a target region).



Keep quests.json backward-compatible and validate all chapters in ContentDataTest.



PHASE 5: CLASS ABILITIES, INPUT DISCOVERABILITY \& COMBAT FEEL (PART 3 \& PART 6)



Ability HUD \& Control Discoverability:



Ensure HudComposer clearly displays the active 3-click combo progress (\[L - R - \_]), current class resource (Мана / Тэнхээ / Анхаарал / Дөл / Хурд), active spell cooldowns, and Ultimate readiness (F — Swap Hand Items key).



When a spell fails due to insufficient resource, active cooldown, or invalid target/out-of-range, provide a concise, non-spammy action-bar message and subtle audio cue (Sound.BLOCK\_NOTE\_BLOCK\_BASS) rather than failing silently.



Verify All 5 Classes \& 25 Spells + 15 Ultimates:



Audit and fix any spell in SkillService.java and Ultimates.java where resource cost is deducted when no valid target is hit (for single-target spells) or where visual particles fire without applying damage/status effects.



PHASE 6: GENERATE ALL 8 AUDIT \& PLAYTEST DELIVERABLE REPORTS (docs/audit-2026-10-14/)



Write the following 8 comprehensive, evidence-backed Markdown documents into docs/audit-2026-10-14/ (incorporating both the static/bytecode audit findings, the exact code fixes you applied, and the answers to all 20 Final Questions from Part 12):



docs/audit-2026-10-14/GAME\_LOGIC\_STATUS.md — End-to-end execution architecture (input -> validation -> combo/spell -> combat math -> display entity/particles -> SQL/PDC persistence) and verified status of all systems.



docs/audit-2026-10-14/QUEST\_AND\_TASK\_AUDIT.md — Complete 42-chapter verification matrix, narrative evaluation, daily/campaign/tutorial task audit, QS-01..QS-08 fixes, and new objective vocabulary specs.



docs/audit-2026-10-14/CLASS\_ABILITY\_AUDIT.md — Complete ability matrix for all 5 classes (Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин), exact keybinds (L/R 3-click combos, F Swap-Hands Ultimate, Sneak + Jump Dodge, Sneak + Right-Click Skill Sky), targeting, resource costs, cooldowns, and HUD feedback.



docs/audit-2026-10-14/ITEM\_AND\_RARITY\_AUDIT.md — Full 230+ item catalog audit, rarity table, IT-01..IT-20 fixes, new consumables (airag\_flask, steppe\_salve, ovoo\_incense, darkhan\_whetstone), and class equipment build analysis.



docs/audit-2026-10-14/WEAPON\_ANIMATION\_AUDIT.md — Deep audit of all 5 class weapons, bytecode-verified root cause and fix for Мэргэн bow draw/release (WP-01..WP-10), and 12-row animation test matrix.



docs/audit-2026-10-14/COMBAT\_AND\_FEEDBACK\_AUDIT.md — Damage formula verification, hit decals, floating damage numbers, sound/particle readability, and answers to all 20 Final Questions (Part 12).



docs/audit-2026-10-14/LAPTOP\_PLAYTEST\_PLAN.md — Step-by-step manual playtest guide for the owner's laptop (pre-flight checklist, class-by-class test sheets including 1st/3rd-person Мэргэн bow draw/release + L-R-L combo tests, quest/item/consumable test sheets, 20 highest-value manual tests, and defect recording template).



docs/audit-2026-10-14/GAMEPLAY\_IMPROVEMENT\_ROADMAP.md — Prioritized P0–P3 roadmap with effort estimates, dependencies, and acceptance criteria.



PHASE 7: BUILD, TEST, VALIDATE, COMMIT \& PUSH TO main



Regenerate resource-pack assets if any generator script was updated (python3 tools/pack/gen\_items.py, etc.) and run all validators:



python3 tools/validation/run\_all.py



python3 -I tools/plugins/test\_fetch\_plugins.py



Run the full Gradle test and simulation suite:



./gradlew test simTest --no-daemon (or .\\gradlew.bat test simTest --no-daemon on Windows)



Verify git status and git diff --stat to ensure zero untracked scratch/temp files or broken formatting.



Stage, commit with a detailed conventional commit message, and push to origin main:



git add -A



git commit -m "feat(gameplay): resolve all weapon animation, itemization, quest, and forensic audit findings (WP/IT/QS/SULD-F)"



git push origin main

