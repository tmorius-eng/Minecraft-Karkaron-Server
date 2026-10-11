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
    private final NamespacedKey keyMaxHp;

    private final Plugin plugin;

    public MobService(Plugin plugin) {
        this.plugin = plugin;
        this.keyMobId = new NamespacedKey(plugin, "mob_id");
        this.keyMobLevel = new NamespacedKey(plugin, "mob_level");
        this.keyMaxHp = new NamespacedKey(plugin, "mob_max_hp");
    }

    /** Called for every SÜLD mob right after spawning (the model renderer dresses rigged mobs here). */
    public volatile java.util.function.BiConsumer<LivingEntity, MobDefinition> onSpawn = (e, d) -> { };

    public LivingEntity spawn(MobDefinition def, Location location) {
        return spawn(def, location, def.scaledHealth());
    }

    /** Spawn with a max health other than the definition's (a boss sized for its dungeon's party). */
    public LivingEntity spawn(MobDefinition def, Location location, double health) {
        EntityType type = EntityType.valueOf(def.backingEntity());
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            throw new IllegalArgumentException("Backing entity is not living: " + def.backingEntity());
        }
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(true);

        applyHealth(living, health);
        // Some entities (wolves) reset their max health to the vanilla value right after spawning: apply it again.
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (living.isValid() && maxHealth(living) != health) applyHealth(living, health);
        });

        living.getPersistentDataContainer().set(keyMobId, PersistentDataType.STRING, def.id());
        nameplate(living, def, maxHealth(living), maxHealth(living));
        living.getPersistentDataContainer().set(keyMobLevel, PersistentDataType.INTEGER, def.level());
        living.getPersistentDataContainer().set(keyMaxHp, PersistentDataType.DOUBLE, health);
        onSpawn.accept(living, def);
        return living;
    }

    /**
     * The name tag over every SÜLD mob: «Lv 12 Хангайн Саарал Чоно ❤ 140/180». The name's colour is the tier (white
     * normal, gold elite, orange champion, red boss), the hearts go from green to red with the health left. Updated
     * after every hit and heal.
     */
    public static void nameplate(LivingEntity living, MobDefinition def, double hp, double max) {
        net.kyori.adventure.text.format.TextColor tier = switch (def.tier()) {
            case NORMAL -> NamedTextColor.WHITE;
            case ELITE -> NamedTextColor.GOLD;
            case CHAMPION, MYTHIC -> net.kyori.adventure.text.format.TextColor.fromHexString("#FF8C3A");
            case BOSS, WORLD_BOSS -> NamedTextColor.RED;
        };
        double f = max <= 0 ? 0 : Math.max(0, Math.min(1, hp / max));
        NamedTextColor hc = f > 0.6 ? NamedTextColor.GREEN : f > 0.3 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        living.customName(Component.text("Lv " + def.level() + " ", NamedTextColor.GRAY)
                .append(Component.text(def.displayName(), tier))
                .append(Component.text("  ❤ " + (int) Math.ceil(hp) + "/" + (int) Math.round(max), hc)));
    }

    /** Redraw the name tag a tick after a hit or a heal (the health has changed by then). */
    private void refreshSoon(Entity e) {
        if (!(e instanceof LivingEntity living) || !isSuldMob(e)) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!living.isValid() || living.isDead()) return;
            MobDefinition def = mobId(living).map(mn.suld.plugin.content.SuldContent::mobFor).orElse(null);
            if (def != null) nameplate(living, def, living.getHealth(), maxHealth(living));
        });
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurtPlate(org.bukkit.event.entity.EntityDamageEvent e) {
        refreshSoon(e.getEntity());
    }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onHealPlate(org.bukkit.event.entity.EntityRegainHealthEvent e) {
        refreshSoon(e.getEntity());
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
     * SÜLD mobs roam by day too: the undead hosts (stray, husk, drowned, zombie) must not catch fire in the sun.
     * Fire from blocks or other entities (lava, a fire aspect, a spell) still burns them.
     */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onSunburn(org.bukkit.event.entity.EntityCombustEvent e) {
        if (e instanceof org.bukkit.event.entity.EntityCombustByBlockEvent || e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent) return;
        if (isSuldMob(e.getEntity())) e.setCancelled(true);
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
            double want = living.getPersistentDataContainer().getOrDefault(keyMaxHp, PersistentDataType.DOUBLE, def.scaledHealth());
            if (maxHealth != null && maxHealth.getBaseValue() != want) {
                double hp = living.getHealth();
                maxHealth.setBaseValue(want);
                living.setHealth(Math.min(want, Math.max(1, hp)));
                nameplate(living, def, living.getHealth(), want);
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
