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

    /**
     * The same mob lifted (or lowered) to {@code newLevel}: health, attack and EXP follow the curves to that level, the
     * id, name, kind, tier and loot table kept. A mob whose content overrides a number keeps its ratio to the curve (one
     * with twice the curve's health at its own level has twice the curve's health at the new level). A heroic hall
     * (docs/HEROIC_DUNGEONS.md) runs every mob at 60.
     */
    public MobDefinition atLevel(int newLevel) {
        if (newLevel == level) return this;
        return new MobDefinition(id, displayName, backingEntity, tier, newLevel,
                follow(baseHealth, mn.suld.api.balance.MobScaling.baseHealth(level), mn.suld.api.balance.MobScaling.baseHealth(newLevel)),
                follow(baseAttack, mn.suld.api.balance.MobScaling.baseDamage(level), mn.suld.api.balance.MobScaling.baseDamage(newLevel)),
                Math.round(follow(baseExp, mn.suld.api.balance.MobScaling.baseExp(level), mn.suld.api.balance.MobScaling.baseExp(newLevel))),
                lootTableId);
    }

    /** {@code value} moved from where the curve stood ({@code from}) to where it stands ({@code to}), its ratio kept. */
    private static double follow(double value, double from, double to) {
        return from > 0 ? to * (value / from) : to;
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
