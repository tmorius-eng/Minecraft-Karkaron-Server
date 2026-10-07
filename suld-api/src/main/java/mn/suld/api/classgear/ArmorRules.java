package mn.suld.api.classgear;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.progression.ExpSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The class armour progression rules (docs/ARMOR_PROGRESSION.md), pure and server-free: armour XP, armour level,
 * tier gates and enhancement. The simulator used exactly these numbers ({@code ProposedRules.armorNeed}, {@code TIER_*}).
 */
public final class ArmorRules {

    public static final int MAX_ARMOR_LEVEL = 60;
    public static final int MAX_ENHANCE = 5;
    /** Repeat fatigue looks at the last 8 dungeon clears. */
    public static final int RECENT_CLEARS = 8;

    // armour XP per source (desk values; ACTIVE_PLAYTIME_SPEC asks for them to be measured)
    public static final double XP_ACTIVE_MINUTE = 0.6;
    public static final double XP_KILL = 0.2;
    public static final double XP_ELITE = 1;
    public static final double XP_CHAMPION = 3;
    public static final double XP_DUNGEON = 25;
    public static final double XP_CHAPTER = 20;
    public static final double XP_DISCOVERY = 15;

    private static final Pattern ID = Pattern.compile("armor\\.class\\.([a-z]+)\\.([a-z]+)\\.t([1-6])");

    private ArmorRules() {
    }

    /** Armour XP to go from armour level {@code a} to {@code a + 1}; 0 at the cap. */
    public static long need(int a) {
        if (a >= MAX_ARMOR_LEVEL) return 0;
        return Math.round(5.6 * Math.pow(Math.max(1, a), 1.3));
    }

    /** Armour XP for an EXP grant of this source (the kinds the spec lists; everything else gives none). */
    public static double xpFor(ExpSource source) {
        return switch (source) {
            case MOB_KILL -> XP_KILL;
            case ELITE_KILL -> XP_ELITE;
            case BOSS_KILL -> XP_CHAMPION;
            case QUEST -> XP_CHAPTER;
            case DISCOVERY -> XP_DISCOVERY;
            default -> 0; // DUNGEON is credited per clear with its fatigue (dungeonXp); ADMIN, WORLD_EVENT, OTHER: none
        };
    }

    /** Repeat fatigue: −15 % per clear of the same dungeon among the last 8 clears, floor 25 %. */
    public static double fatigue(ClassGear g, String dungeonId) {
        long same = g.recent().stream().filter(dungeonId::equals).count();
        return Math.max(0.25, 1 - 0.15 * same);
    }

    /** Armour XP of a dungeon clear, before the clear is recorded. */
    public static double dungeonXp(ClassGear g, String dungeonId) {
        return XP_DUNGEON * fatigue(g, dungeonId);
    }

    /** The result of adding armour XP. */
    public record Gain(ClassGear after, int levels, double added) {
    }

    /**
     * Adds armour XP. The armour level is capped at the player level (and 60); XP counts double while the armour level
     * is more than 3 below the player level (catch-up). At the cap, at most one level's worth is kept, so a level-up
     * of the player is followed at once.
     */
    public static Gain gain(ClassGear g, double xp, int playerLevel) {
        if (!(xp > 0)) return new Gain(g, 0, 0);
        int cap = Math.max(1, Math.min(MAX_ARMOR_LEVEL, playerLevel));
        double add = g.armorLevel() < playerLevel - 3 ? xp * 2 : xp;
        int al = g.armorLevel();
        double have = g.armorXp() + add;
        int levels = 0;
        while (al < cap && have >= need(al)) {
            have -= need(al);
            al++;
            levels++;
        }
        if (al >= MAX_ARMOR_LEVEL) have = 0;
        else if (al >= cap) have = Math.min(have, need(al));
        return new Gain(g.withProgress(al, have), levels, add);
    }

    /** After the player levels up: levels the armour may now take from XP it already holds. */
    public static Gain settle(ClassGear g, int playerLevel) {
        int cap = Math.max(1, Math.min(MAX_ARMOR_LEVEL, playerLevel));
        int al = g.armorLevel();
        double have = g.armorXp();
        int levels = 0;
        while (al < cap && have >= need(al) && need(al) > 0) {
            have -= need(al);
            al++;
            levels++;
        }
        return new Gain(levels == 0 ? g : g.withProgress(al, have), levels, 0);
    }

    // ------------------------------------------------------------------------------------------------ items

    /** {@code armor.class.baatar.helmet.t1} */
    public static String definitionId(PlayerClass c, ArmorPiece p, ArmorTier t) {
        return "armor.class." + c.id() + "." + p.id() + ".t" + t.number();
    }

    /** What a class armour definition id names. */
    public record Parsed(PlayerClass clazz, ArmorPiece piece, ArmorTier tier) {
    }

    public static Optional<Parsed> parse(String definitionId) {
        if (definitionId == null) return Optional.empty();
        Matcher m = ID.matcher(definitionId);
        if (!m.matches()) return Optional.empty();
        PlayerClass c = PlayerClass.byId(m.group(1)).orElse(null);
        ArmorPiece p = ArmorPiece.byId(m.group(2)).orElse(null);
        if (c == null || p == null) return Optional.empty();
        return Optional.of(new Parsed(c, p, ArmorTier.of(Integer.parseInt(m.group(3)))));
    }

    /** The resource-pack equipment asset of a class tier ({@code suld:baatar_t3}), without the namespace. */
    public static String assetId(PlayerClass c, ArmorTier t) {
        return c.id() + "_t" + t.number();
    }

    /** Stable seed of a piece's rolls inside a tier: base rolls and affixes change only with the tier. */
    public static long seed(java.util.UUID owner, ArmorPiece p, ArmorTier t) {
        return owner.getMostSignificantBits() ^ (owner.getLeastSignificantBits() * 31) ^ (p.ordinal() * 7919L + t.number() * 104_729L);
    }

    // ---------------------------------------------------------------------------------------- enhancement

    /** +2 % item power per enhancement step. */
    public static double powerFactor(int enhance) {
        return 1 + 0.02 * Math.max(0, Math.min(MAX_ENHANCE, enhance));
    }

    /** Coins for enhancement step {@code step} (1..5): 40 × armour level × step × tier. */
    public static long enhanceCost(int armorLevel, int step, ArmorTier t) {
        return 40L * armorLevel * step * t.number();
    }

    // ------------------------------------------------------------------------------------------------ tiers

    /** One requirement of a tier upgrade and whether it is met. */
    public record Gate(String label, boolean met) {
    }

    /** What the player has towards the next tier. */
    public record Holdings(long coins, int materials, int ascension) {
    }

    /** The gates of the next tier (empty at T6), ids shown as they are. */
    public static List<Gate> gates(ClassGear g, Holdings h) {
        return gates(g, h, java.util.function.Function.identity());
    }

    /** The gates of the next tier; {@code name} turns a dungeon or material id into its display name. */
    public static List<Gate> gates(ClassGear g, Holdings h, java.util.function.Function<String, String> name) {
        ArmorTier next = g.tier().next().orElse(null);
        List<Gate> out = new ArrayList<>();
        if (next == null) return out;
        out.add(new Gate("Хуягийн түвшин " + next.armorLevel(), g.armorLevel() >= next.armorLevel()));
        if (next.dungeon() != null) out.add(new Gate("Давсан агуй: " + name.apply(next.dungeon()), g.cleared().contains(next.dungeon())));
        if (next.ascension() > 0) out.add(new Gate("Тэнгэрийн Зэрэг " + roman(next.ascension()), h.ascension() >= next.ascension()));
        out.add(new Gate(next.coins() + " зоос", h.coins() >= next.coins()));
        if (next.material() != null) out.add(new Gate(next.materials() + " × " + name.apply(next.material()), h.materials() >= next.materials()));
        return out;
    }

    public static boolean canUpgrade(ClassGear g, Holdings h) {
        List<Gate> gs = gates(g, h);
        return !gs.isEmpty() && gs.stream().allMatch(Gate::met);
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(n);
        };
    }
}
