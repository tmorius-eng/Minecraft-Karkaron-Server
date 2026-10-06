package mn.suld.plugin.mob;

import mn.suld.api.mob.MobDefinition;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Spawns and identifies custom SÜLD mobs. A real Bukkit entity backs each mob;
 * SÜLD stats/identity are applied and stored in the entity's PDC. Hand-written —
 * no MythicMobs or any mob plugin.
 */
public final class MobService implements org.bukkit.event.Listener {

    private final NamespacedKey keyMobId;
    private final NamespacedKey keyMobLevel;

    private final Plugin plugin;

    public MobService(Plugin plugin) {
        this.plugin = plugin;
        this.keyMobId = new NamespacedKey(plugin, "mob_id");
        this.keyMobLevel = new NamespacedKey(plugin, "mob_level");
    }

    public LivingEntity spawn(MobDefinition def, Location location) {
        EntityType type = EntityType.valueOf(def.backingEntity());
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            throw new IllegalArgumentException("Backing entity is not living: " + def.backingEntity());
        }
        living.customName(Component.text(def.displayName(), Messages.BRAND)
                .append(Component.text(" [Lvl " + def.level() + "]", NamedTextColor.WHITE)));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(true);

        applyHealth(living, def.scaledHealth());
        // Some entities (wolves) reset their max health to the vanilla value right after spawning: apply it again.
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (living.isValid() && maxHealth(living) != def.scaledHealth()) applyHealth(living, def.scaledHealth());
        });

        living.getPersistentDataContainer().set(keyMobId, PersistentDataType.STRING, def.id());
        living.getPersistentDataContainer().set(keyMobLevel, PersistentDataType.INTEGER, def.level());
        return living;
    }

    public Optional<String> mobId(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                entity.getPersistentDataContainer().get(keyMobId, PersistentDataType.STRING));
    }

    public boolean isSuldMob(Entity entity) {
        return mobId(entity).isPresent();
    }

    /**
     * Keep a SÜLD mob aggressive: vanilla wolves are neutral and calm down after a while,
     * which would make wolf waves/raids trivial. Re-targets the nearest valid player within
     * {@code radius} when the mob has no live target.
     */
    @SuppressWarnings("deprecation") // Wolf#setAngry is the simplest stable anger switch across 1.21.x
    public static void keepHostile(LivingEntity entity, java.util.Collection<? extends org.bukkit.entity.Player> candidates,
                                   double radius) {
        if (!(entity instanceof org.bukkit.entity.Mob mob) || !mob.isValid()) {
            return;
        }
        LivingEntity target = mob.getTarget();
        boolean targetOk = target instanceof org.bukkit.entity.Player p && p.isValid() && !p.isDead()
                && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && p.getGameMode() != org.bukkit.GameMode.CREATIVE
                && p.getWorld().equals(mob.getWorld());
        if (!targetOk) {
            org.bukkit.entity.Player best = null;
            double bestDist = radius * radius;
            for (org.bukkit.entity.Player p : candidates) {
                if (!p.isValid() || p.isDead() || !p.getWorld().equals(mob.getWorld())
                        || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    continue;
                }
                double d = p.getLocation().distanceSquared(mob.getLocation());
                if (d <= bestDist) {
                    bestDist = d;
                    best = p;
                }
            }
            if (best != null) {
                mob.setTarget(best);
            }
        }
        if (mob instanceof org.bukkit.entity.Wolf wolf && mob.getTarget() != null) {
            wolf.setAngry(true);
        }
    }

    /**
     * Saved SÜLD mobs come back from disk with vanilla stats (a wolf's load logic resets its max health): restore the
     * SÜLD max health, keeping the current health.
     */
    @org.bukkit.event.EventHandler
    public void onEntitiesLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            String id = living.getPersistentDataContainer().get(keyMobId, PersistentDataType.STRING);
            MobDefinition def = id == null ? null : mn.suld.plugin.content.SuldContent.mobFor(id);
            if (def == null) continue;
            AttributeInstance maxHealth = living.getAttribute(maxHealthAttribute());
            if (maxHealth != null && maxHealth.getBaseValue() != def.scaledHealth()) {
                double hp = living.getHealth();
                maxHealth.setBaseValue(def.scaledHealth());
                living.setHealth(Math.min(def.scaledHealth(), Math.max(1, hp)));
            }
        }
    }

    private static void applyHealth(LivingEntity living, double health) {
        AttributeInstance maxHealth = living.getAttribute(maxHealthAttribute());
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
            living.setHealth(health);
        }
    }

    /** Current max health of a living entity (0 if the attribute is unavailable). */
    public static double maxHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(maxHealthAttribute());
        return attr == null ? 0.0 : attr.getValue();
    }

    /** Resolve the max-health attribute via the registry (stable across 1.21.x renames). */
    private static Attribute maxHealthAttribute() {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (attr == null) {
            attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
        }
        return attr;
    }
}
