package mn.suld.api.clazz;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The five playable SULD classes.
 *
 * <p>The display names are Mongolian-inspired <em>fantasy</em> archetypes. They
 * are not claims about historical roles; keep historical lore and fantasy lore
 * separate (see {@code GAME_DESIGN.md}).
 *
 * <p>Each constant carries only identity and role metadata here. Concrete
 * balance numbers (base stats, skill trees, scaling) are data-driven and loaded
 * from configuration, never hard-coded into this enum.
 */
public enum PlayerClass {

    /** Баатар — warrior / tank / berserker. High defense, melee bruiser. */
    BAATAR("baatar", "Баатар", ClassRole.TANK, "Хил / Rage", 3, 40.0, 7.0, 100),

    /** Мэргэн — archer / ranged / assassin. High single-target ranged damage. */
    MERGEN("mergen", "Мэргэн", ClassRole.RANGED, "Төвлөрөл / Focus", 4, 26.0, 9.0, 100),

    /** Бөө — spirit mage / support. Area magic and party sustain. */
    BOO("boo", "Бөө", ClassRole.SUPPORT, "Сүнс / Spirit", 5, 24.0, 8.0, 140),

    /** Дархан — forge warrior / weapon crafter. Crafting-driven power. */
    DARKHAN("darkhan", "Дархан", ClassRole.CRAFTER, "Дөл / Heat", 3, 34.0, 7.5, 100),

    /** Хүлэгчин — mounted mobility / charge warrior. Burst engage and kiting. */
    KHULEGCHIN("khulegchin", "Хүлэгчин", ClassRole.MOBILITY, "Хурд / Momentum", 4, 30.0, 8.0, 100);

    private final String id;
    private final String displayName;
    private final ClassRole role;
    private final String resourceName;
    private final int difficulty;      // 1 (easy) .. 5 (hard)
    private final double baseHealth;
    private final double baseAttack;
    private final int resourceMax;

    PlayerClass(String id, String displayName, ClassRole role, String resourceName,
                int difficulty, double baseHealth, double baseAttack, int resourceMax) {
        this.id = id;
        this.displayName = displayName;
        this.role = role;
        this.resourceName = resourceName;
        this.difficulty = difficulty;
        this.baseHealth = baseHealth;
        this.baseAttack = baseAttack;
        this.resourceMax = resourceMax;
    }

    /** Name of this class's unique combat resource (e.g. Rage, Focus, Spirit). */
    public @NotNull String resourceName() {
        return resourceName;
    }

    /** Relative difficulty rating, 1 (easiest) to 5 (hardest). */
    public int difficulty() {
        return difficulty;
    }

    /** Base max health at level 1 (before per-level growth). */
    public double baseHealth() {
        return baseHealth;
    }

    /** Base attack power at level 1 (before per-level growth). */
    public double baseAttack() {
        return baseAttack;
    }

    /** Maximum of the class's unique resource. */
    public int resourceMax() {
        return resourceMax;
    }

    /** Stable lowercase identifier used in config keys and the database. */
    public @NotNull String id() {
        return id;
    }

    /** Mongolian (Cyrillic) display name shown in the UI. */
    public @NotNull String displayName() {
        return displayName;
    }

    public @NotNull ClassRole role() {
        return role;
    }

    /** Resolve a class from its stable {@link #id()}, case-insensitively. */
    public static @NotNull Optional<PlayerClass> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String needle = id.trim().toLowerCase(java.util.Locale.ROOT);
        for (PlayerClass value : values()) {
            if (value.id.equals(needle)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Broad combat role, used for UI grouping and default stat templates. */
    public enum ClassRole {
        TANK,
        RANGED,
        SUPPORT,
        CRAFTER,
        MOBILITY
    }
}
