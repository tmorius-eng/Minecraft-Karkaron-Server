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
    BAATAR("baatar", "Баатар", ClassRole.TANK),

    /** Мэргэн — archer / ranged / assassin. High single-target ranged damage. */
    MERGEN("mergen", "Мэргэн", ClassRole.RANGED),

    /** Бөө — spirit mage / support. Area magic and party sustain. */
    BOO("boo", "Бөө", ClassRole.SUPPORT),

    /** Дархан — forge warrior / weapon crafter. Crafting-driven power. */
    DARKHAN("darkhan", "Дархан", ClassRole.CRAFTER),

    /** Хүлэгчин — mounted mobility / charge warrior. Burst engage and kiting. */
    KHULEGCHIN("khulegchin", "Хүлэгчин", ClassRole.MOBILITY);

    private final String id;
    private final String displayName;
    private final ClassRole role;

    PlayerClass(String id, String displayName, ClassRole role) {
        this.id = id;
        this.displayName = displayName;
        this.role = role;
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
