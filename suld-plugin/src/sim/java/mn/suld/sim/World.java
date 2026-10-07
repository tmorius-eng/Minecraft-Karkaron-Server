package mn.suld.sim;

import mn.suld.api.mob.MobTier;
import mn.suld.api.quest.QuestType;

import java.util.List;

/**
 * The content a simulated player can spend time on: zones with their mobs, the dungeon ladder, the story and the
 * repeatable activities. {@link LiveRules} builds it from the real content classes; {@link ProposedRules} extends it
 * with the proposed bands 27–60 and the endgame.
 */
public final class World {

    /** One kind of enemy, with the numbers the fight model needs. {@code dmg} is per hit, every {@code interval} s. */
    public record Mob(String id, String name, int level, MobTier tier, double hp, double dmg, double interval,
                      boolean ranged, long exp, String lootTable, long coins) {
        public boolean elite() {
            return tier.ordinal() >= MobTier.ELITE.ordinal();
        }
    }

    /**
     * A wild area. {@code weights} is the spawn share of each mob. {@code landmarks} are optional exploration points
     * (proposed rules only; 0 in the live game).
     */
    public record Zone(String id, String name, int min, int max, List<Mob> mobs, double[] weights, long discoveryExp,
                       int landmarks, int index) {
    }

    /**
     * A dungeon. Gates and rewards are the rules' business; this is the encounter. {@code chapterGate} is the story
     * chapter index that must be finished first (-1 = none), {@code previous} the dungeon that must be cleared once
     * (-1 = none), {@code gpMin} the minimum gear power (0 = none). {@code heroic} marks the level-60 versions.
     */
    public record Dungeon(String id, String name, int min, int max, int index, List<List<Mob>> waves, Mob boss,
                          long completionExp, long coins, String rewardTable, double enrageSeconds, int chapterGate,
                          int previous, double gpMin, boolean heroic, int mythicTier) {
        public int recommended() {
            return min + 3;
        }
    }

    /** A story chapter. Proposed chapters of the new kinds take a fixed time ({@code minutes}) on top of combat. */
    public record Chapter(String id, String title, QuestType type, String target, int count, long exp, long coins,
                          int zone, double minutes, int act) {
    }

    /** A recurring world event: every {@code everyMinutes}, lasting {@code minutes}, scaled to the player's band. */
    public record Event(String id, double everyMinutes, double minutes, long baseExp, long baseCoins, boolean scaled) {
    }

    private final List<Zone> zones;
    private final List<Dungeon> dungeons;
    private final List<Chapter> story;
    private final List<Event> events;

    public World(List<Zone> zones, List<Dungeon> dungeons, List<Chapter> story, List<Event> events) {
        this.zones = List.copyOf(zones);
        this.dungeons = List.copyOf(dungeons);
        this.story = List.copyOf(story);
        this.events = List.copyOf(events);
    }

    public List<Zone> zones() {
        return zones;
    }

    public List<Dungeon> dungeons() {
        return dungeons;
    }

    public List<Chapter> story() {
        return story;
    }

    public List<Event> events() {
        return events;
    }

    public int maxContentLevel() {
        int m = 0;
        for (Zone z : zones) for (Mob mob : z.mobs()) m = Math.max(m, mob.level());
        for (Dungeon d : dungeons) m = Math.max(m, d.boss().level());
        return m;
    }

    public Zone zone(String id) {
        for (Zone z : zones) if (z.id().equals(id)) return z;
        return null;
    }

    public Dungeon dungeon(String id) {
        for (Dungeon d : dungeons) if (d.id().equals(id)) return d;
        return null;
    }

    public Mob mob(String id) {
        for (Zone z : zones) for (Mob m : z.mobs()) if (m.id().equals(id)) return m;
        for (Dungeon d : dungeons) {
            if (d.boss().id().equals(id)) return d.boss();
            for (List<Mob> w : d.waves()) for (Mob m : w) if (m.id().equals(id)) return m;
        }
        return null;
    }

    /** The zone a mob lives in (first match), or -1 when it only appears in dungeons. */
    public int zoneOf(String mobId) {
        for (Zone z : zones) for (Mob m : z.mobs()) if (m.id().equals(mobId)) return z.index();
        return -1;
    }
}
