package mn.suld.api.skill.tree;

/**
 * The ability-point economy. Points come from real progression: levels (the bulk), story chapters finished,
 * regions discovered, plus points an administrator grants. Every part is derived from persisted state, so a
 * point can never be lost or counted twice.
 */
public final class SkillPoints {

    private SkillPoints() {
    }

    // progression v2 (mn.suld.api.balance.Economy.skillPoints): 59 from levels at 60, up to 14 from the story, 4 from
    // the regions; every part only grows, so no build learned under the old formula loses a point.

    /** One point per level. Level 1 has none. */
    public static int forLevel(int level) {
        return Math.max(0, level - 1);
    }

    /** One point for every three finished story chapters, at most 14. */
    public static int forChapters(int finishedChapters) {
        return Math.max(0, Math.min(14, finishedChapters / 3));
    }

    /** One point for every two discovered regions, at most four. */
    public static int forDiscovery(int discoveredRegions) {
        return Math.max(0, Math.min(4, discoveredRegions / 2));
    }

    public static int total(int level, int finishedChapters, int discoveredRegions, int granted) {
        return forLevel(level) + forChapters(finishedChapters) + forDiscovery(discoveredRegions) + Math.max(0, granted);
    }

    /** Respeccing for free is allowed while the build is still tiny (new players may experiment). */
    public static final int FREE_RESET_LEVEL = 10;
}
