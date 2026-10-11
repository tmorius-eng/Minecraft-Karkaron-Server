package mn.suld.api.mob;

/**
 * Data-driven definition of a custom SÜLD mob. The plugin's mob service spawns a
 * backing Bukkit entity, tags it with {@link #id()}, and applies these scaled
 * stats. All combat/reward numbers derive from here — never hard-coded in
 * listeners.
 *
 * @param id            stable mob id (e.g. "mob.goviin_chono")
 * @param displayName   Mongolian display name shown above the entity
 * @param backingEntity Bukkit entity type id to spawn (e.g. "WOLF")
 * @param tier          power tier
 * @param level         mob level
 * @param baseHealth    base max health (before tier scaling)
 * @param baseAttack    base attack power (before tier scaling)
 * @param baseExp       base EXP reward (before tier scaling)
 * @param lootTableId   id of the loot table rolled on death
 *
 * <p>Progression v2: {@link #designed} takes health, attack and EXP from {@link mn.suld.api.balance.MobScaling}, and the
 * tier multiplies health and attack separately ({@link MobTier#healthMultiplier()}, {@link MobTier#damageMultiplier()}).
 */
public record MobDefinition(
        String id,
        String displayName,
        String backingEntity,
        MobTier tier,
        int level,
        double baseHealth,
        double baseAttack,
        long baseExp,
        String lootTableId) {

    /** A mob whose numbers all follow from its level and tier (progression v2). */
    public static MobDefinition designed(String id, String displayName, String backingEntity, MobTier tier, int level, String lootTableId) {
        return new MobDefinition(id, displayName, backingEntity, tier, level, mn.suld.api.balance.MobScaling.baseHealth(level),
                mn.suld.api.balance.MobScaling.baseDamage(level), mn.suld.api.balance.MobScaling.baseExp(level), lootTableId);
    }

    public double scaledHealth() {
        return baseHealth * tier.healthMultiplier();
    }

    public double scaledAttack() {
        return baseAttack * tier.damageMultiplier();
    }

    /** Coins dropped on a kill. */
    public long coins() {
        return mn.suld.api.balance.MobScaling.coins(level, tier);
    }

    public long scaledExp() {
        return Math.round(baseExp * tier.rewardMultiplier());
    }
}
