package mn.suld.api.skill.tree;

/** What a passive spell does when it fires. {@code a} and {@code b} of {@link Effect.Proc} depend on the kind. */
public enum ProcKind {
    /** a = hearts of health. */
    HEAL,
    /** a = absorption hearts, b = seconds. */
    SHIELD,
    /** a = speed level (1 = Speed I), b = seconds. */
    SPEED,
    /** a = strength level, b = seconds. */
    STRENGTH,
    /** a = resistance level, b = seconds. */
    RESIST,
    /** a = class resource gained. */
    RESOURCE,
    /** a = attack multiplier, b = radius: damages enemies around the player. */
    AOE,
    /** a = seconds: sets enemies around (or the target) on fire. */
    IGNITE,
    /** a = seconds, b = radius: slows enemies around the player. */
    SLOW_AREA,
    /** a = attack multiplier: extra damage to the target. */
    BONUS,
    /** a = attack multiplier: a lightning strike on the target. */
    SMITE,
    /** a = attack multiplier: damages the nearest other enemy within 6 blocks. */
    CHAIN,
    /** removes harmful effects. */
    CLEANSE,
    /** a = push strength, b = radius: throws enemies around the player back. */
    SHOVE
}
