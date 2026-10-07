package mn.suld.api.dungeon.hall;

import java.util.Objects;

/**
 * Where a dungeon's gate stands in the open world and what its hall looks like: a compass bearing (0 = north,
 * 90 = east) and a distance from the world spawn, inside the dungeon's region. Positions are offsets, so they follow
 * the spawn like every region ring.
 */
public record DungeonSite(String dungeonId, HallTheme theme, double bearingDeg, double radius) {

    public DungeonSite {
        Objects.requireNonNull(dungeonId, "dungeonId");
        Objects.requireNonNull(theme, "theme");
        if (radius < 64 || radius > 4900) throw new IllegalArgumentException("radius out of range: " + radius);
    }

    /** Offset {dx, dz} of the gate from the spawn. */
    public int[] offset() {
        double a = Math.toRadians(bearingDeg);
        return new int[]{(int) Math.round(Math.sin(a) * radius), (int) Math.round(-Math.cos(a) * radius)};
    }

    /** The short id used in commands: "dungeon.govi_bulsh" → "govi_bulsh". */
    public String shortId() {
        return dungeonId.startsWith("dungeon.") ? dungeonId.substring("dungeon.".length()) : dungeonId;
    }
}
