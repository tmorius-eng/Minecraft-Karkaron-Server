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
public final class MobService {

    private final NamespacedKey keyMobId;
    private final NamespacedKey keyMobLevel;

    public MobService(Plugin plugin) {
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
                .append(Component.text(" [Lvl " + def.level() + "]", NamedTextColor.GRAY)));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(true);

        AttributeInstance maxHealth = living.getAttribute(maxHealthAttribute());
        if (maxHealth != null) {
            maxHealth.setBaseValue(def.scaledHealth());
            living.setHealth(def.scaledHealth());
        }

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

    /** Resolve the max-health attribute via the registry (stable across 1.21.x renames). */
    private static Attribute maxHealthAttribute() {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"));
        if (attr == null) {
            attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.max_health"));
        }
        return attr;
    }
}
