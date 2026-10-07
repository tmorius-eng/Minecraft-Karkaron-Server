package mn.suld.api.classgear;

import mn.suld.api.json.Json;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * A character's class gear record (docs/CLASS_GEAR_SYSTEM.md "Profile record"), stored with the profile: the source
 * of truth. The stacks in the inventory are only renderings of it — regenerated from it with the same UUIDs.
 *
 * @param armorLevel armour level (1..60, capped at the player level)
 * @param armorXp    armour XP into the current armour level
 * @param tier       armour tier
 * @param enhance    enhancement inside the tier (0..{@link ArmorRules#MAX_ENHANCE})
 * @param pieces     the identity of each class armour piece (missing = not granted yet)
 * @param weapon     the identity of the class weapon (null = unknown)
 * @param cleared    dungeons cleared at least once (tier gates)
 * @param recent     the last {@link ArmorRules#RECENT_CLEARS} dungeon clears, newest last (repeat fatigue)
 */
public record ClassGear(int armorLevel, double armorXp, ArmorTier tier, int enhance, Map<ArmorPiece, UUID> pieces, UUID weapon,
                        Set<String> cleared, List<String> recent) {

    public static final int VERSION = 1;
    public static final ClassGear NONE = new ClassGear(1, 0, ArmorTier.T1, 0, Map.of(), null, Set.of(), List.of());

    public ClassGear {
        armorLevel = Math.max(1, Math.min(ArmorRules.MAX_ARMOR_LEVEL, armorLevel));
        armorXp = Double.isFinite(armorXp) ? Math.max(0, armorXp) : 0;
        tier = tier == null ? ArmorTier.T1 : tier;
        enhance = Math.max(0, Math.min(ArmorRules.MAX_ENHANCE, enhance));
        Map<ArmorPiece, UUID> p = new EnumMap<>(ArmorPiece.class);
        if (pieces != null) pieces.forEach((k, v) -> {
            if (k != null && v != null) p.put(k, v);
        });
        pieces = Collections.unmodifiableMap(p);
        cleared = cleared == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(cleared));
        recent = recent == null ? List.of() : List.copyOf(recent.subList(Math.max(0, recent.size() - ArmorRules.RECENT_CLEARS), recent.size()));
    }

    public boolean isEmpty() {
        return equals(NONE);
    }

    public Optional<UUID> piece(ArmorPiece p) {
        return Optional.ofNullable(pieces.get(p));
    }

    public ClassGear withPiece(ArmorPiece p, UUID id) {
        Map<ArmorPiece, UUID> m = new EnumMap<>(ArmorPiece.class);
        m.putAll(pieces);
        m.put(p, id);
        return new ClassGear(armorLevel, armorXp, tier, enhance, m, weapon, cleared, recent);
    }

    public ClassGear withWeapon(UUID id) {
        return new ClassGear(armorLevel, armorXp, tier, enhance, pieces, id, cleared, recent);
    }

    public ClassGear withProgress(int level, double xp) {
        return new ClassGear(level, xp, tier, enhance, pieces, weapon, cleared, recent);
    }

    public ClassGear withTier(ArmorTier t) {
        return new ClassGear(armorLevel, armorXp, t, 0, pieces, weapon, cleared, recent); // a new tier resets enhancement
    }

    public ClassGear withEnhance(int e) {
        return new ClassGear(armorLevel, armorXp, tier, e, pieces, weapon, cleared, recent);
    }

    /** A dungeon clear: the tier gate set and the repeat-fatigue list. */
    public ClassGear withCleared(String dungeonId) {
        Set<String> s = new TreeSet<>(cleared);
        s.add(dungeonId);
        List<String> r = new java.util.ArrayList<>(recent);
        r.add(dungeonId);
        return new ClassGear(armorLevel, armorXp, tier, enhance, pieces, weapon, s, r);
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", VERSION);
        m.put("al", armorLevel);
        m.put("xp", Math.round(armorXp * 100) / 100.0);
        m.put("t", tier.number());
        m.put("e", enhance);
        Map<String, Object> p = new LinkedHashMap<>();
        pieces.forEach((k, v) -> p.put(k.id(), v.toString()));
        m.put("p", p);
        if (weapon != null) m.put("w", weapon.toString());
        m.put("c", List.copyOf(cleared));
        m.put("r", recent);
        return Json.write(m);
    }

    /** null / blank = no record yet. Throws for unreadable or newer data (a save must never erase it). */
    public static ClassGear fromJson(String json) {
        if (json == null || json.isBlank()) return NONE;
        Map<String, Object> m;
        try {
            m = Json.object(Json.parse(json));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("broken class gear data: " + e.getMessage(), e);
        }
        int v = m.get("v") instanceof Number n ? n.intValue() : -1;
        if (v != VERSION) throw new IllegalArgumentException("class gear data version " + v + " is not supported (expected " + VERSION + ")");
        try {
            Map<ArmorPiece, UUID> pieces = new EnumMap<>(ArmorPiece.class);
            for (Map.Entry<String, Object> e : Json.object(m.getOrDefault("p", Map.of())).entrySet()) {
                ArmorPiece piece = ArmorPiece.byId(e.getKey()).orElseThrow(() -> new IllegalArgumentException("unknown piece " + e.getKey()));
                pieces.put(piece, UUID.fromString(String.valueOf(e.getValue())));
            }
            Set<String> cleared = new TreeSet<>();
            for (Object o : Json.array(m.getOrDefault("c", List.of()))) cleared.add(String.valueOf(o));
            List<String> recent = new java.util.ArrayList<>();
            for (Object o : Json.array(m.getOrDefault("r", List.of()))) recent.add(String.valueOf(o));
            Object w = m.get("w");
            return new ClassGear(Json.integer(m.getOrDefault("al", 1)), m.get("xp") instanceof Number n ? n.doubleValue() : 0,
                    ArmorTier.of(Json.integer(m.getOrDefault("t", 1))), Json.integer(m.getOrDefault("e", 0)), pieces,
                    w == null ? null : UUID.fromString(String.valueOf(w)), cleared, recent);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("broken class gear data: " + e.getMessage(), e);
        }
    }
}
