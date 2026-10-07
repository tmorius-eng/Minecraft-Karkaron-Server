package mn.suld.api.worldevent;

import java.util.Random;

/**
 * Шагай буулгах: casting four sheep ankle bones (шагай) and reading their faces is a VERIFIED Mongolian custom, for
 * play and for fortune. Each bone lands on one of four sides, named horse, camel, sheep and goat. The side
 * probabilities here are a game approximation of real bones (the narrow horse and camel sides come up rarely). What
 * each cast means in SÜLD, and its blessing, is game fiction (docs/world/SHAGAI.md). Pure and tested.
 */
public final class Shagai {

    public enum Face {
        HORSE("морь", 0.10), CAMEL("тэмээ", 0.12), SHEEP("хонь", 0.39), GOAT("ямаа", 0.39);

        private final String label;
        private final double chance;

        Face(String label, double chance) {
            this.label = label;
            this.chance = chance;
        }

        public String label() {
            return label;
        }

        public double chance() {
            return chance;
        }
    }

    /** A reading: a name, an EXP bonus and how long it lasts (0 = no blessing). */
    public record Fortune(String name, double bonus, int minutes) {
    }

    private Shagai() {
    }

    public static Face face(Random rng) {
        double r = rng.nextDouble(), acc = 0;
        for (Face f : Face.values()) {
            acc += f.chance;
            if (r < acc) return f;
        }
        return Face.GOAT;
    }

    public static Face[] cast(Random rng) {
        return new Face[]{face(rng), face(rng), face(rng), face(rng)};
    }

    /** Reads four bones. More horses are the luckiest; four alike or four different are good casts too. */
    public static Fortune read(Face[] bones) {
        if (bones.length != 4) throw new IllegalArgumentException("four bones");
        int horses = 0;
        int[] n = new int[Face.values().length];
        for (Face f : bones) {
            n[f.ordinal()]++;
            if (f == Face.HORSE) horses++;
        }
        if (horses == 4) return new Fortune("Дөрвөн морь — хурдан морьдын заяа", 0.10, 60);
        if (horses == 3) return new Fortune("Гурван морь — хийморь сэргэлээ", 0.08, 40);
        boolean alike = false, different = true;
        for (int c : n) {
            if (c == 4) alike = true;
            if (c > 1) different = false;
        }
        if (alike) return new Fortune("Дөрвөн ижил — бүтэн буулаа", 0.05, 30);
        if (different) return new Fortune("Дөрвөн өөр — мал сүрэг өсөхийн бэлэг", 0.05, 30);
        if (horses == 2) return new Fortune("Хоёр морь — зам дардан", 0.03, 20);
        if (horses == 1) return new Fortune("Нэг морь — бага зэрэг ивээл", 0.02, 10);
        return new Fortune("Морьгүй — маргааш дахин буулга", 0, 0);
    }
}
