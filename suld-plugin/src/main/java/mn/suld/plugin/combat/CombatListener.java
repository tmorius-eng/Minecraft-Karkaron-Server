package mn.suld.plugin.combat;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.combat.CombatCalculator;
import mn.suld.api.combat.DamageResult;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
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
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Random;

/**
 * Applies the SÜLD combat engine to real Bukkit combat: player hits on SÜLD
 * mobs use {@link CombatCalculator}; SÜLD mob deaths grant EXP, advance quests,
 * and drop custom loot. All numbers come from class/mob/item data — never
 * hard-coded in the listener.
 */
public final class CombatListener implements Listener {

    private final SuldServices services;
    private final MobService mobs;
    private final QuestService quests;
    private final HudService hud;
    private final ItemFactory items;
    private final CombatCalculator calculator = new CombatCalculator(50.0);
    private final mn.suld.api.loot.LootRoller lootRoller = new mn.suld.api.loot.LootRoller(new Random());

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

    /** The SÜLD attack of a player: class base + level growth + weapon ATK (the shared hit/spell/arrow formula). */
    public static double attackOf(SuldServices services, Player player) {
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) return 1;
        PlayerClass clazz = profile.playerClass().orElse(PlayerClass.BAATAR);
        double attack = clazz.baseAttack() + (profile.progression().level() - 1) * 0.75;
        ItemInstance weapon = services.items().read(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon != null) attack += weapon.stat(ItemStat.ATTACK);
        return attack;
    }

    private double critOf(Player player) {
        ItemInstance weapon = items.read(player.getInventory().getItemInMainHand()).orElse(null);
        return 0.05 + (weapon == null ? 0 : weapon.stat(ItemStat.CRIT_CHANCE));
    }

    /** Victims of a critical SÜLD hit this tick (read and cleared by the damage-number display). */
    public static final java.util.Set<java.util.UUID> CRIT_HITS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Arrow damage multiplier set by spells (Чонын Нүд) — consumed per arrow. */
    public static final java.util.Map<java.util.UUID, Integer> EMPOWERED_ARROWS = new java.util.concurrent.ConcurrentHashMap<>();
    /** When the empowered arrows run out even if unused. */
    public static final java.util.Map<java.util.UUID, Long> EMPOWERED_UNTIL = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (spellDamage || !isTarget(event.getEntity())) {
            return;
        }
        Player player;
        double scale = 1.0;
        if (event.getDamager() instanceof Player p) {
            player = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.AbstractArrow arrow && arrow.getShooter() instanceof Player p) {
            // a fully drawn arrow does ~6 vanilla damage: scale the SÜLD attack by how hard it was drawn
            player = p;
            scale = Math.max(0.25, Math.min(1.4, event.getDamage() / 6.0));
            if (arrow.getScoreboardTags().contains("suld_volley")) scale *= 0.9;
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
        DamageResult result = calculator.compute(attackOf(services, player) * scale, critOf(player), 1.5, 0.0, ThreadLocalRandomRoll());
        event.setDamage(result.finalDamage());
        if (result.critical()) {
            CRIT_HITS.add(event.getEntity().getUniqueId());
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
            return;
        }
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

        services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                mn.suld.api.analytics.AnalyticsEventType.FIRST_MOB_KILL, killer.getUniqueId()));

        int fromLevel = profile.progression().level();
        long expAmount = services.boosts().apply(killer.getUniqueId(), def.scaledExp());
        ExpGainResult exp = services.progression().grantExp(profile, expAmount, ExpSource.MOB_KILL);
        killer.sendMessage(Messages.info("+" + expAmount + " EXP (" + def.displayName() + ")"));
        services.clans().contribute(killer.getUniqueId(), SuldContent.CLAN_EXP_PER_MOB_KILL);
        if (exp.leveledUp()) {
            Presentation.levelUp(killer, fromLevel, exp.after().level());
        }

        quests.onMobKilled(killer, profile, mobId);

        for (ItemInstance inst : lootRoller.roll(SuldContent.lootTableFor(def.lootTableId()), "mob:" + mobId)) {
            ItemDefinition idef = SuldContent.definitionFor(inst.definitionId());
            if (idef == null) {
                continue;
            }
            entity.getWorld().dropItemNaturally(entity.getLocation(), items.create(inst, idef));
            services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                    mn.suld.api.analytics.AnalyticsEventType.FIRST_ITEM, killer.getUniqueId()));
            if (inst.rarity().ordinal() >= ItemRarity.RARE.ordinal()) {
                services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of(
                        mn.suld.api.analytics.AnalyticsEventType.FIRST_RARE_ITEM, killer.getUniqueId()));
                killer.sendMessage(Messages.accent("Ховор олз: " + idef.displayName() + "!"));
            }
        }

        services.profiles().save(profile);
        hud.update(killer, profile);
    }

    private static double ThreadLocalRandomRoll() {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble();
    }
}
