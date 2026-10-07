package mn.suld.api.skill.tree;

/**
 * The ability-point economy. Points come from real progression: levels (the bulk), story chapters finished,
 * regions discovered, plus points an administrator grants. Every part is derived from persisted state, so a
 * point can never be lost or counted twice.
 */
public final class SkillPoints {

    private SkillPoints() {
    }

    /** One point per level up to 30, then one per two levels. Level 1 has none. */
    public static int forLevel(int level) {
        if (level <= 1) return 0;
        int early = Math.min(level, 30) - 1;
        int late = level > 30 ? (level - 30) / 2 : 0;
        return early + late;
    }

    /** One point for every three finished story chapters (the 15-chapter story pays 5). */
    public static int forChapters(int finishedChapters) {
        return Math.max(0, Math.min(5, finishedChapters / 3));
    }

    /** One point for every five discovered regions, at most four. */
    public static int forDiscovery(int discoveredRegions) {
        return Math.max(0, Math.min(4, discoveredRegions / 5));
    }

    public static int total(int level, int finishedChapters, int discoveredRegions, int granted) {
        return forLevel(level) + forChapters(finishedChapters) + forDiscovery(discoveredRegions) + Math.max(0, granted);
    }

    /** Respeccing for free is allowed while the build is still tiny (new players may experiment). */
    public static final int FREE_RESET_LEVEL = 10;
}
