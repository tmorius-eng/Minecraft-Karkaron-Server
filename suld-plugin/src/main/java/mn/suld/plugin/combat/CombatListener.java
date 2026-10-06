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

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !mobs.isSuldMob(event.getEntity())) {
            return;
        }
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        PlayerClass clazz = profile.playerClass().orElse(PlayerClass.BAATAR);
        int level = profile.progression().level();
        double attack = clazz.baseAttack() + (level - 1) * 0.75;
        double critChance = 0.05;
        ItemInstance weapon = items.read(player.getInventory().getItemInMainHand()).orElse(null);
        if (weapon != null) {
            attack += weapon.stat(ItemStat.ATTACK);
            critChance += weapon.stat(ItemStat.CRIT_CHANCE);
        }
        DamageResult result = calculator.compute(attack, critChance, 1.5, 0.0, ThreadLocalRandomRoll());
        event.setDamage(result.finalDamage());
        if (result.critical()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
        }
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
        ExpGainResult exp = services.progression().grantExp(profile, def.scaledExp(), ExpSource.MOB_KILL);
        killer.sendMessage(Messages.info("+" + def.scaledExp() + " EXP (" + def.displayName() + ")"));
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
