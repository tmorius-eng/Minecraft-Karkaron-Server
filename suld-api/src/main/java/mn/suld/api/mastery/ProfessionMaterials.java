package mn.suld.api.mastery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the crafts gather and what the gatherings become (docs/MASTERY_AND_PROFESSIONS.md). Each craft has a common
 * material (tier 1–2 nodes) and a fine one (tier 3–4). The ores are smelted into the smith's own materials, and wool is
 * beaten into felt; felt, horsehair and herbs also go into a few recipes of their own beside the old ones (recipes.json,
 * the «…_alt» recipes). Every id here is an item of the catalogue (ProfessionMaterialsCatalogTest). Pure.
 */
public final class ProfessionMaterials {

    public static final String TUMRIIN_KHUDAR = "item.tumriin_khudar";
    public static final String ALTAN_KHUDAR = "item.altan_khudar";
    public static final String GANGA = "item.ganga";
    public static final String ALTAN_GAGNUUR = "item.altan_gagnuur";
    public static final String KHYALGAS = "item.khyalgas";
    public static final String KHULGIIN_DEL = "item.khulgiin_del";
    public static final String KHONINY_NOOS = "item.khoniny_noos";
    public static final String NARIIN_NOOS = "item.nariin_noos";
    /** Felt, beaten from wool (the Эсгийч's refining). */
    public static final String ESGII = "item.esgii";
    /** The smith's own materials the ores become. */
    public static final String IRON_SHARDS = "item.tumriin_kheltarkhii", GOLD_DUST = "item.altan_toos";

    private ProfessionMaterials() {
    }

    /** What one gather of a craft at a node of {@code tier} gives (common at 1–2, fine at 3–4). */
    public static String gathered(Profession p, int tier) {
        boolean fine = tier >= 3;
        return switch (p) {
            case TOMRIIN_DARKHAN -> fine ? ALTAN_KHUDAR : TUMRIIN_KHUDAR;
            case EMIIN_BARIACH -> fine ? ALTAN_GAGNUUR : GANGA;
            case ADUUCHIN -> fine ? KHULGIIN_DEL : KHYALGAS;
            case ESGIICH -> fine ? NARIIN_NOOS : KHONINY_NOOS;
        };
    }

    /** Every material a craft gathers, common first. */
    public static List<String> gatheredBy(Profession p) {
        return List.of(gathered(p, 1), gathered(p, 3));
    }

    /**
     * A refining: {@code in} becomes {@code out}, for a player whose {@code craft} has reached {@code level}.
     *
     * @param name what the page calls it («Төмөр хайлуулах»)
     */
    public record Refine(String id, String name, Profession craft, int level, Map<String, Integer> in, Map<String, Integer> out) {

        public Refine {
            in = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(in));
            out = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(out));
        }

        /** How many times {@code have} (material id → amount) covers the inputs. */
        public int times(Map<String, Integer> have) {
            int n = Integer.MAX_VALUE;
            for (Map.Entry<String, Integer> e : in.entrySet()) n = Math.min(n, have.getOrDefault(e.getKey(), 0) / e.getValue());
            return n == Integer.MAX_VALUE ? 0 : n;
        }
    }

    /**
     * The refinings. Rates keep near the merchant's prices: three iron ores (1 coin each) make two shards (2 each), two
     * gold ores (3 each) one gold dust (5), four wool (2 each) or two fine wool (4 each) one felt (8, a wolf pelt's price).
     */
    public static final List<Refine> REFINES = List.of(
            new Refine("tumur", "Төмөр хайлуулах", Profession.TOMRIIN_DARKHAN, 1,
                    Map.of(TUMRIIN_KHUDAR, 3), Map.of(IRON_SHARDS, 2)),
            new Refine("alt", "Алт хайлуулах", Profession.TOMRIIN_DARKHAN, Profession.tierLevel(3),
                    Map.of(ALTAN_KHUDAR, 2), Map.of(GOLD_DUST, 1)),
            new Refine("esgii", "Эсгий хийх", Profession.ESGIICH, 1,
                    Map.of(KHONINY_NOOS, 4), Map.of(ESGII, 1)),
            new Refine("esgii_nariin", "Нарийн ноосноос эсгий", Profession.ESGIICH, Profession.tierLevel(3),
                    Map.of(NARIIN_NOOS, 2), Map.of(ESGII, 1)));

    public static Optional<Refine> refine(String id) {
        for (Refine r : REFINES) if (r.id().equals(id)) return Optional.of(r);
        return Optional.empty();
    }

    /** Every material id this file names (gathered, felt and the refinings' outputs). */
    public static List<String> all() {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        for (Profession p : Profession.values()) s.addAll(gatheredBy(p));
        s.add(ESGII);
        for (Refine r : REFINES) {
            s.addAll(r.in().keySet());
            s.addAll(r.out().keySet());
        }
        return List.copyOf(s);
    }
}
