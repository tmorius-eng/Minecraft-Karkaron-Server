package mn.suld.plugin.combat;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.combat.CombatCalculator;
import mn.suld.api.combat.DamageResult;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.hud.HudService;
import mn.suld.plugin.item.ItemFactory;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.quest.QuestService;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;


/**
 * Applies the SÜLD combat engine to real Bukkit combat: player hits on SÜLD
 * mobs use {@link CombatCalculator}; SÜLD mob deaths grant EXP, advance quests,
 * and drop custom loot. All numbers come from class/mob/item data — never
 * hard-coded in the listener.
 */
public final class CombatListener implements Listener {

    /** Carried by mobs a boss summons mid-fight (no loot, a fifth of the EXP). */
    public static final String SUMMON_TAG = "suld_summon";
    static final String WORLD_RARE_TABLE = "loot.world.rare";

    private final SuldServices services;
    private final MobService mobs;
    private final QuestService quests;
    private final HudService hud;
    private final ItemFactory items;
    private final CombatCalculator calculator = new CombatCalculator(50.0);

    public CombatListener(SuldServices services, MobService mobs, QuestService quests,
                          HudService hud, ItemFactory items) {
        this.services = services;
        this.mobs = mobs;
        this.quests = quests;
        this.hud = hud;
        this.items = items;
    }

    /** True while SÜLD itself applies spell damage (its amount must not be replaced by the hit formula). */
    public static boolean spellDamage;

    /**
     * The SÜLD attack of a player: class base + level growth + the flat damage of everything they wear (weapon,
     * affixes, set bonuses), times the attack bonus of the skill tree and +1 % per Тэнгэрийн Зэрэг rank (the shared
     * hit/spell/arrow formula).
     */
    public static double attackOf(SuldServices services, Player player) {
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) return 1;
        PlayerClass clazz = profile.playerClass().orElse(PlayerClass.BAATAR);
        double attack = clazz.baseAttack() + (profile.progression().level() - 1) * 0.75;
        mn.suld.plugin.item.EquipmentService eq = services.equipment();
        if (eq != null) attack += eq.bonus(player).flatDamage();
        mn.suld.plugin.skill.SkillTreeService tree = services.skillTree();
        attack *= 1 + mn.suld.api.balance.Ascension.powerBonus(profile.ascension());
        return tree == null ? attack : attack * tree.attackMultiplier(player);
    }

    /** 5% base, plus the crit chance of the build (skill tree and equipment are one build). */
    private double critOf(Player player) {
        mn.suld.plugin.skill.SkillTreeService tree = services.skillTree();
        return 0.05 + (tree == null ? 0 : tree.critChance(player));
    }

    /** Victim id -> time of the critical hit just dealt (the skill tree's crit passives read and clear it). */
    public static final java.util.Map<java.util.UUID, Long> CRIT_AT = new java.util.concurrent.ConcurrentHashMap<>();

    /** Victims of a critical SÜLD hit this tick (read and cleared by the damage-number display). */
    public static final java.util.Set<java.util.UUID> CRIT_HITS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Arrow damage multiplier set by spells (Чонын Нүд) — consumed per arrow. */
    public static final java.util.Map<java.util.UUID, Integer> EMPOWERED_ARROWS = new java.util.concurrent.ConcurrentHashMap<>();
    /** When the empowered arrows run out even if unused. */
    public static final java.util.Map<java.util.UUID, Long> EMPOWERED_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();

    /** Player id -> {attack charge 0..1, server tick} of the melee swing in progress (vanilla resets it before the hit). */
    private record Charge(float value, int tick) {
    }

    private final java.util.Map<java.util.UUID, Charge> charge = new java.util.HashMap<>();

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwingCharge(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        charge.put(e.getPlayer().getUniqueId(), new Charge(e.getPlayer().getAttackCooldown(), Bukkit.getCurrentTick()));
    }

    @EventHandler
    public void onChargeQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        charge.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Melee scale: vanilla's attack-strength curve (0.2 + 0.8·charge²), so spam-clicking is weak and ATTACK_SPEED
     * matters; a sweep hit deals 30 %; punching with a bow or crossbow deals 30 % (the Mergen's attack is the draw).
     */
    private double meleeScale(Player p, EntityDamageByEntityEvent event) {
        Charge c = charge.get(p.getUniqueId());
        double ch = c != null && Bukkit.getCurrentTick() - c.tick() <= 1 ? Math.max(0, Math.min(1, c.value())) : 1.0;
        double s = 0.2 + 0.8 * ch * ch;
        if (event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) s *= 0.3;
        org.bukkit.Material hand = p.getInventory().getItemInMainHand().getType();
        if (hand == org.bukkit.Material.BOW || hand == org.bukkit.Material.CROSSBOW) s *= 0.3;
        return s;
    }

    /**
     * A SÜLD mob hitting a player deals its designed attack (MobDefinition.scaledAttack: base × tier), melee or
     * projectile, instead of the vanilla entity's damage, so a level-50 elite hits like one, plus 8 % per level it is
     * above the player (CombatRules.gapTaken). Bosses set their own phase-scaled hit (BossService). NORMAL priority:
     * armour ({@link #onMitigate}), reductions and dodge apply afterwards.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobHitsPlayer(EntityDamageByEntityEvent event) {
        if (spellDamage || !(event.getEntity() instanceof Player)) return;
        org.bukkit.entity.Entity src = event.getDamager();
        boolean projectile = false;
        if (src instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity shooter) {
            src = shooter;
            projectile = true;
        }
        if (src instanceof Player || !mobs.isSuldMob(src)) return;
        MobDefinition def = mobs.mobId(src).map(SuldContent::mobFor).orElse(null);
        if (def == null || def.tier() == mn.suld.api.mob.MobTier.BOSS || def.tier() == mn.suld.api.mob.MobTier.WORLD_BOSS) return;
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK && !projectile) {
            return; // explosions, thorns… keep their vanilla amount
        }
        // per hit × host swing interval / design interval: the damage per second is the design's (MobScaling.hitScale)
        event.setDamage(def.scaledAttack() * mn.suld.api.balance.MobScaling.hitScale(def.backingEntity(), false)
                * mn.suld.api.balance.CombatRules.gapTaken(def.level() - levelOf((Player) event.getEntity())));
    }

    /** The player's level (1 while the profile is loading). */
    private int levelOf(Player p) {
        return services.profiles().cached(p.getUniqueId()).map(pr -> pr.progression().level()).orElse(1);
    }

    /** Level of whatever hit a player: a SÜLD mob's own level, else (vanilla mobs, the world) the player's. */
    private int attackerLevel(org.bukkit.entity.Entity damager, Player victim) {
        org.bukkit.entity.Entity src = damager;
        if (src instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof org.bukkit.entity.Entity shooter) src = shooter;
        MobDefinition def = mobs.mobId(src).map(SuldContent::mobFor).orElse(null);
        return def != null ? def.level() : levelOf(victim);
    }

    /**
     * SÜLD armour (the ARMOR stat of gear and tree): a hit from any mob loses a / (a + 10 + 2.5 · attacker level), at
     * most 75 % (CombatRules.mitigation). Old armour stops being enough as the mobs get stronger. Vanilla armour points
     * are off for players (SkillTreeService), so this is the only armour there is. HIGH: after the base hit is set.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onMitigate(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        org.bukkit.entity.Entity src = event.getDamager();
        if (src instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Player) return;
        if (src instanceof Player) return;
        mn.suld.plugin.skill.SkillTreeService tree = services.skillTree();
        double armor = tree == null ? 0 : tree.build(victim).stat(mn.suld.api.skill.tree.StatKey.ARMOR);
        if (armor <= 0) return;
        event.setDamage(event.getDamage() * (1 - mn.suld.api.balance.CombatRules.mitigation(armor, attackerLevel(src, victim))));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        long t = mn.suld.plugin.perf.PerfProbe.start();
        try {
            onHit0(event);
        } finally {
            mn.suld.plugin.perf.PerfProbe.stop("combat.hit", t);
        }
    }

    private void onHit0(EntityDamageByEntityEvent event) {
        if (spellDamage || !isTarget(event.getEntity())) {
            return;
        }
        Player player;
        double scale = 1.0;
        if (event.getDamager() instanceof Player p) {
            org.bukkit.event.entity.EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                    && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                return; // thorns and other player-sourced damage keep their vanilla amount, not the SÜLD attack
            }
            player = p;
            scale = meleeScale(p, event);
        } else if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && arrow.getShooter() instanceof Player p) {
            // a fully drawn arrow does ~6 vanilla damage: scale the SÜLD attack by how hard it was drawn
            player = p;
            scale = Math.max(0.25, Math.min(1.4, event.getDamage() / 6.0));
            mn.suld.plugin.skill.SkillTreeService tree = services.skillTree();
            mn.suld.api.skill.tree.SkillBuild skill = tree == null ? mn.suld.api.skill.tree.SkillBuild.EMPTY : tree.build(p);
            boolean single = skill.has(mn.suld.api.skill.tree.KeystoneKind.NEG_SUMNII_KHUVI);
            if (arrow.getScoreboardTags().contains("suld_volley")) {
                scale *= 0.9 * (1 + skill.mod(mn.suld.api.skill.Spell.OLON_SUM, mn.suld.api.skill.tree.ModKey.DAMAGE_PCT) / 100.0)
                        * (tree == null ? 1 : tree.spellDamageMultiplier(p)) * (single ? 0.7 : 1.0);
            } else if (single) {
                scale *= 1.4;
            }
            Integer left = EMPOWERED_ARROWS.get(p.getUniqueId());
            Long until = EMPOWERED_UNTIL.get(p.getUniqueId());
            if (until != null && until < System.currentTimeMillis()) {
                EMPOWERED_ARROWS.remove(p.getUniqueId());
                EMPOWERED_UNTIL.remove(p.getUniqueId());
                left = null;
            }
            if (left != null && left > 0) {
                scale *= 1.5;
                if (left <= 1) EMPOWERED_ARROWS.remove(p.getUniqueId());
                else EMPOWERED_ARROWS.put(p.getUniqueId(), left - 1);
            }
        } else {
            return;
        }
        if (services.profiles().cached(player.getUniqueId()).isEmpty()) {
            return;
        }
        mn.suld.plugin.skill.SkillTreeService skillTree = services.skillTree();
        // level suppression: −4 % per level the mob is above the player (floor 40 %)
        MobDefinition target = mobs.mobId(event.getEntity()).map(SuldContent::mobFor).orElse(null);
        if (target != null) scale *= mn.suld.api.balance.CombatRules.gapDealt(target.level() - levelOf(player));
        DamageResult result = calculator.compute(attackOf(services, player) * scale, critOf(player),
                skillTree == null ? 1.5 : skillTree.critMultiplier(player), 0.0, ThreadLocalRandomRoll());
        event.setDamage(result.finalDamage());
        if (result.critical()) {
            CRIT_HITS.add(event.getEntity().getUniqueId());
            long now = System.currentTimeMillis();
            CRIT_AT.put(event.getEntity().getUniqueId(), now);
            // entries are consumed by the skill tree's crit trigger; anything older than 5 s (no build, mob gone) is dropped
            if (CRIT_AT.size() > 256) CRIT_AT.values().removeIf(at -> now - at > 5000);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
        }
    }

    /** SÜLD hit formula applies to SÜLD mobs (vanilla monsters keep vanilla damage). */
    private boolean isTarget(org.bukkit.entity.Entity e) {
        return mobs.isSuldMob(e);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!mobs.isSuldMob(entity)) {
            vanillaKill(entity);
            return;
        }
        CRIT_AT.remove(entity.getUniqueId());
        CRIT_HITS.remove(entity.getUniqueId());
        // SÜLD mobs use custom loot/EXP, not vanilla drops.
        event.getDrops().clear();
        event.setDroppedExp(0);

        String mobId = mobs.mobId(entity).orElse(null);
        MobDefinition def = mobId == null ? null : SuldContent.mobFor(mobId);
        Player killer = entity.getKiller();
        if (def == null || killer == null) {
            return;
        }
        PlayerProfile profile = services.profiles().cached(killer.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }

        // first_mob_kill once per session (it used to be recorded for every kill); every kill is in the session totals
        if (services.session(killer.getUniqueId()).mobsDefeated++ == 0) {
            services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                    mn.suld.api.analytics.AnalyticsEventType.FIRST_MOB_KILL, killer.getUniqueId()));
        }

        int fromLevel = profile.progression().level();
        mn.suld.plugin.skill.SkillTreeService skillTree = services.skillTree();
        // farming one spot: a soft diminishing return on open-world kills only (ActivityTracker.farmFactor);
        // dungeon, world-event and boss kills, quests and dungeon completions are never reduced
        boolean openWorld = !entity.getScoreboardTags().contains(mn.suld.plugin.dungeon.DungeonService.DUNGEON_TAG)
                && def.tier().ordinal() < mn.suld.api.mob.MobTier.BOSS.ordinal()
                && !services.worldEvents().isEventMob(entity.getUniqueId());
        double farm = openWorld && services.activity != null ? services.activity.farmedKill(killer, mobId, entity.getLocation()) : 1;
        // boss adds (a howl's wolves) are fight mechanics, not a farm: a fifth of the EXP and no loot
        boolean summoned = entity.getScoreboardTags().contains(SUMMON_TAG);
        // dungeon trash drops materials only; the run's gear comes once, from the completion reward
        // (the boss too: its run pays gear once, at completion, to every participant)
        boolean dungeonTrash = entity.getScoreboardTags().contains(mn.suld.plugin.dungeon.DungeonService.DUNGEON_TAG);
        if (summoned) farm = Math.min(farm, 0.2);
        int gap = def.level() - fromLevel;
        // a party shares the kill: every member within 48 blocks gets (1 + 0.15·(n−1)) / n, each with their own gap
        java.util.List<Player> sharers = sharers(killer, entity.getLocation());
        double share = mn.suld.api.balance.ExpRules.partyShare(sharers.size() + 1);
        long[] gain = killExp(killer, profile, def, share, farm);
        long expAmount = gain[0];
        ExpGainResult exp = services.progression().grantExp(profile, expAmount, ExpSource.MOB_KILL);
        long coins = summoned ? 0 : Math.max(0, Math.round(def.coins() * farm));
        if (coins > 0) profile.addCurrency(coins);
        killer.sendMessage(Messages.info("+" + expAmount + " EXP" + (gain[1] > 0 ? " (амарсан +" + gain[1] + ")" : "")
                + (coins > 0 ? " · +" + coins + " ₮" : "") + " (" + def.displayName() + ")"
                + (gap <= -5 ? " · хэт сул мангас: EXP бага" : "")
                + (farm < 1 ? " · нэг газарт хэт олон агнасан ×" + Math.round(farm * 100) / 100.0 + " — өөр газар оч" : "")));
        for (Player m : sharers) {
            PlayerProfile mp = services.profiles().cached(m.getUniqueId()).orElse(null);
            if (mp == null) continue;
            int mFrom = mp.progression().level();
            long[] mg = killExp(m, mp, def, share, summoned ? 0.2 : 1.0);
            ExpGainResult mr = services.progression().grantExp(mp, mg[0], ExpSource.MOB_KILL);
            m.sendActionBar(Messages.info("+" + mg[0] + " EXP · бүлэг (" + def.displayName() + ")"));
            if (mr.leveledUp()) {
                Presentation.levelUp(m, mFrom, mr.after().level());
                services.profiles().save(mp);
            }
            hud.update(m, mp);
        }
        if (services.classArmor != null) services.classArmor.mobKilled(killer, entity, def, farm);
        services.clans().contribute(killer.getUniqueId(), Math.round(SuldContent.CLAN_EXP_PER_MOB_KILL * farm)); // fatigued and summoned kills level the clan less too
        if (exp.leveledUp()) {
            Presentation.levelUp(killer, fromLevel, exp.after().level());
        }

        quests.onMobKilled(killer, profile, mobId);

        mn.suld.plugin.item.ItemService itemService = services.itemService();
        if (itemService != null) {
            mn.suld.api.loot.LootContext ctx = new mn.suld.api.loot.LootContext(def.level(), mn.suld.api.loot.LootTier.of(def.tier()),
                    profile.playerClass().orElse(null), skillTree == null ? 0 : skillTree.lootPct(killer), killer.getUniqueId(), "mob:" + mobId);
            // the loot-chance stat raises rare chances (lootBonus); it used to re-roll the whole table, doubling everything
            java.util.List<mn.suld.api.loot.LootDrop> drops = new java.util.ArrayList<>(itemService.roll(def.lootTableId(), ctx));
            // open-world rarities (Chinggis set pieces, Тэнгэрийн сахиус): a tiny chance on every open-world kill
            if (openWorld) drops.addAll(itemService.roll(WORLD_RARE_TABLE, ctx));
            // farming one spot thins the loot the same way it thins EXP
            if (farm < 1) {
                final double keep = farm;
                drops.removeIf(d -> java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= keep);
            }
            if (summoned) drops.clear();
            // 10+ levels below the player: no gear, materials only (progression v2, ExpRules.allowsGear)
            else if (!mn.suld.api.balance.ExpRules.allowsGear(gap)) drops.removeIf(d -> !itemService.catalog().require(d.item().definitionId()).stackable());
            else if (dungeonTrash) drops.removeIf(d -> !itemService.catalog().require(d.item().definitionId()).stackable());
            drops = itemService.filtered(killer, drops);
            for (mn.suld.api.loot.LootDrop d : drops) {
                ItemInstance inst = d.item();
                // the killer's loot for 45 s (nobody standing on the kill takes it), then anyone's
                entity.getWorld().dropItemNaturally(entity.getLocation(), itemService.stack(inst, killer, d.amount()), it -> {
                    it.setOwner(killer.getUniqueId());
                    org.bukkit.Bukkit.getScheduler().runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(CombatListener.class), () -> {
                        if (it.isValid()) it.setOwner(null);
                    }, 45 * 20L);
                });
                itemService.announce(killer, inst);
                // "first" events once per session (they used to be one row per drop); counts are in the session totals
                mn.suld.api.analytics.SessionTotals st = services.session(killer.getUniqueId());
                if (st.itemsLooted++ == 0) services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                        mn.suld.api.analytics.AnalyticsEventType.FIRST_ITEM, killer.getUniqueId()));
                if (inst.rarity().ordinal() >= ItemRarity.RARE.ordinal()) {
                    if (st.rareItems++ == 0) services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                            mn.suld.api.analytics.AnalyticsEventType.FIRST_RARE_ITEM, killer.getUniqueId()));
                    killer.sendMessage(Messages.accent("Ховор олз: " + mn.suld.api.item.ItemTooltip.name(itemService.catalog(),
                            itemService.catalog().require(inst.definitionId()), inst) + "!"));
                }
            }
        }

        // a save per kill was an async 4-column upsert ~30 times a second in a busy world; at most one per 30 s per
        // player now (quit, death, level-up, rewards and the autosave still save at once)
        long now = System.currentTimeMillis();
        Long last = lastKillSave.get(killer.getUniqueId());
        if (last == null || now - last > 30_000 || exp.leveledUp()) {
            lastKillSave.put(killer.getUniqueId(), now);
            services.profiles().save(profile);
        }
        hud.update(killer, profile);
    }

    private final java.util.Map<java.util.UUID, Long> lastKillSave = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A vanilla hostile (one from a spawner, or any when world.vanilla-hostiles is on) is worth a quarter of a SÜLD
     * mob of the killer's level, so no fight goes unrewarded; it gives no SÜLD loot and no quest progress.
     */
    private void vanillaKill(LivingEntity entity) {
        Player killer = entity.getKiller();
        if (killer == null || !(entity instanceof org.bukkit.entity.Enemy)) return;
        PlayerProfile profile = services.profiles().cached(killer.getUniqueId()).orElse(null);
        if (profile == null || profile.playerClass().isEmpty()) return;
        int from = profile.progression().level();
        long exp = Math.max(1, Math.round(services.boosts().apply(killer.getUniqueId(), Math.round(30 + 10.0 * from + 0.05 * from * from)) * 0.25));
        ExpGainResult r = services.progression().grantExp(profile, exp, ExpSource.MOB_KILL);
        killer.sendActionBar(Messages.info("+" + exp + " EXP"));
        if (r.leveledUp()) {
            Presentation.levelUp(killer, from, r.after().level());
            services.profiles().save(profile);
        }
        hud.update(killer, profile);
    }

    /** Party members (not the killer) alive within 48 blocks of the kill in the same world. */
    private java.util.List<Player> sharers(Player killer, org.bukkit.Location at) {
        java.util.List<Player> out = new java.util.ArrayList<>();
        var party = services.parties().partyOf(killer.getUniqueId()).orElse(null);
        if (party == null) return out;
        double r2 = mn.suld.api.balance.ExpRules.PARTY_RANGE * mn.suld.api.balance.ExpRules.PARTY_RANGE;
        for (java.util.UUID id : party.members()) {
            if (id.equals(killer.getUniqueId())) continue;
            Player m = Bukkit.getPlayer(id);
            if (m == null || m.isDead() || !m.getWorld().equals(at.getWorld()) || m.getLocation().distanceSquared(at) > r2) continue;
            if (services.isSoul.test(id)) continue;
            out.add(m);
        }
        return out;
    }

    /**
     * EXP of one kill for one player (progression v2): the mob's EXP × the level-gap factor × the party share, then
     * the capped bonuses (clan, relic, blessings, gear and tree EXP %: at most +50 %), catch-up, the farming factor,
     * and the rested pool doubling it while it lasts. Returns {total, of which rested}; spends the rested EXP.
     */
    private long[] killExp(Player p, PlayerProfile pr, MobDefinition def, double share, double farm) {
        int level = pr.progression().level();
        double base = def.scaledExp() * mn.suld.api.balance.ExpRules.gapFactor(def.level() - level) * share;
        mn.suld.plugin.skill.SkillTreeService tree = services.skillTree();
        double gearPct = tree == null ? 0 : tree.expMultiplier(p) - 1;
        long amount = services.boosts().apply(p.getUniqueId(), Math.round(base), gearPct);
        amount = Math.round(amount * mn.suld.api.balance.ExpRules.catchUp(level, services.boosts().serverLevel()));
        if (farm < 1) amount = Math.round(amount * farm);
        amount = Math.max(1, amount);
        mn.suld.api.profile.Endgame eg = pr.endgame();
        long rested = level >= services.progression().engine().curve().maxLevel() ? 0
                : mn.suld.api.balance.RestedPool.bonus(eg.restedExp(), amount);
        if (rested > 0) pr.endgame(eg.withRested(eg.restedExp() - rested));
        return new long[]{amount + rested, rested};
    }

    @EventHandler
    public void onQuitForget(org.bukkit.event.player.PlayerQuitEvent e) {
        lastKillSave.remove(e.getPlayer().getUniqueId());
    }

    private static double ThreadLocalRandomRoll() {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
    }
}
