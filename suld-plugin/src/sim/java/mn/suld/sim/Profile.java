package mn.suld.sim;

/**
 * Who is playing and how: the archetype (hours per day, when the session starts), the playstyle (what the player
 * prefers), the policy quality and the calibration multipliers. Immutable; scenarios are built with the withers.
 */
public record Profile(String archetype, double hoursPerDay, double startHour, Style style, boolean optimal, int party,
                      Calibration cal, Exploit exploit) {

    /** §29 of the directive: different players chase different things. */
    public enum Style { GENERALIST, QUEST_FARMER, DUNGEON_FARMER, EXPLORER, BOSS_FARMER, CRAFTER }

    /** Forced behaviours that probe the rules (docs/PROGRESSION_EXPLOIT_AUDIT.md). */
    public enum Exploit {
        NONE,
        /** E1: repeat the fastest dungeon forever (DG-1/DG-2). */
        LOW_DUNGEON_FARM,
        /** E2: grind the highest-EXP zone regardless of its level (XP-1/XP-2). */
        ZONE_RUSH,
        /** E3: carried by a level-60 friend through dungeons from level 10 (DG-5). */
        CARRY,
        /** E4: the most efficient single activity for 70 hours (§5 last paragraph). */
        SINGLE_ACTIVITY,
        /** E5: AFK in one spot most of the session (activity tracker test). */
        AFK
    }

    /**
     * Calibration multipliers ("mid" = desk values, see docs/PROGRESSION_SIMULATION.md §Calibration). {@code xp} and
     * {@code loot} exist for the sensitivity analysis only.
     */
    public record Calibration(String name, double killRate, double dungeonTime, double deathRate, double loot, double build,
                              double xp) {
        public static final Calibration MID = new Calibration("mid", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        public static final Calibration LOW = new Calibration("low", 0.75, 1.3, 1.5, 1.0, 1.0, 1.0);
        public static final Calibration HIGH = new Calibration("high", 1.3, 0.75, 0.6, 1.0, 1.0, 1.0);

        public Calibration with(String n, double kr, double dt, double dr, double lt, double b, double x) {
            return new Calibration(n, kr, dt, dr, lt, b, x);
        }
    }

    public static Profile casual() {
        return new Profile("casual", 2, 19, Style.GENERALIST, false, 1, Calibration.MID, Exploit.NONE);
    }

    public static Profile active() {
        return new Profile("active", 5, 15, Style.GENERALIST, true, 1, Calibration.MID, Exploit.NONE);
    }

    public static Profile hardcore() {
        return new Profile("hardcore", 10, 10, Style.GENERALIST, true, 1, Calibration.MID, Exploit.NONE);
    }

    public Profile style(Style s) {
        return new Profile(archetype, hoursPerDay, startHour, s, optimal, party, cal, exploit);
    }

    public Profile cal(Calibration c) {
        return new Profile(archetype, hoursPerDay, startHour, style, optimal, party, c, exploit);
    }

    public Profile exploit(Exploit e) {
        return new Profile(archetype, hoursPerDay, startHour, style, optimal, party, cal, e);
    }

    public Profile party(int n) {
        return new Profile(archetype, hoursPerDay, startHour, style, optimal, n, cal, exploit);
    }

    public Profile hours(double h) {
        return new Profile(archetype, h, startHour, style, optimal, party, cal, exploit);
    }

    /** Fraction of the theoretical kill rate a player of this policy reaches (inventory, positioning, chat). */
    public double efficiency() {
        return optimal ? 0.85 : 0.70;
    }

    /** Highest danger (damage of one fight / max health) the player willingly accepts in normal play. */
    public double dangerTolerance() {
        return optimal ? 0.45 : 0.30;
    }
}
