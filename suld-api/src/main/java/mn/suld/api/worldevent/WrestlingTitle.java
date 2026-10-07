package mn.suld.api.worldevent;

/**
 * Mongolian wrestling titles (бөхийн цол; the names and their order are VERIFIED naadam tradition: Начин the falcon,
 * Харцага the hawk, Заан the elephant, Гарьд the garuda, Арслан the lion, Аварга the titan). Real titles are won by
 * rounds at the national naadam; the win counts here are SÜLD game fiction (docs/world/NAADAM.md). Pure and tested.
 */
public enum WrestlingTitle {
    NONE("", 0),
    NACHIN("Начин", 3),
    KHARTSAGA("Харцага", 6),
    ZAAN("Заан", 10),
    GARID("Гарьд", 15),
    ARSLAN("Арслан", 25),
    AVARGA("Аварга", 40);

    private final String label;
    private final int wins;

    WrestlingTitle(String label, int wins) {
        this.label = label;
        this.wins = wins;
    }

    public String label() {
        return label;
    }

    /** Bouts won to earn the title. */
    public int wins() {
        return wins;
    }

    public static WrestlingTitle forWins(int wins) {
        WrestlingTitle best = NONE;
        for (WrestlingTitle t : values()) if (wins >= t.wins) best = t;
        return best;
    }

    /** The next title and how many more wins it needs, or null at Аварга. */
    public WrestlingTitle next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    /**
     * Ring rules: a wrestler loses when pushed out of the ring ({@code radius}, measured on the ground plane) or
     * down off it ({@code drop} blocks under its floor).
     */
    public static boolean outOfRing(double dx, double dy, double dz, double radius, double drop) {
        return dx * dx + dz * dz > radius * radius || dy < -drop;
    }
}
