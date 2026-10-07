package mn.suld.api.dungeon.hall;

import java.util.ArrayList;
import java.util.List;

/**
 * The gate of a dungeon in the open world (docs/world/DUNGEON_HALLS.md): a stone platform, two tall pillars with a
 * carved lintel and the theme's accent, a dark doorway, two lamps and a ring of standing stones, so it reads from far
 * away as a place. Relative to the site: (0, 0) is the doorway, y = 0 is the platform (players stand at y = 1), the
 * gate faces south (+z, the player walks north into it). The foundation goes down {@link #FOUNDATION} blocks so it
 * sits on uneven ground; the space above the platform is cleared. Pure and deterministic.
 */
public final class EntranceBlueprint {

    public static final int HALF = 6, FOUNDATION = 8, CLEAR = 9;
    public static final int VERSION = 1;

    private EntranceBlueprint() {
    }

    /** Where a player stands to use the gate, relative to the site. */
    public static final HallBlueprint.Anchor STAND = new HallBlueprint.Anchor(0.5, 1, 2.5, 180);

    public static List<HallBlueprint.Placement> build(HallTheme t) {
        List<HallBlueprint.Placement> out = new ArrayList<>();
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                double r = Math.hypot(x, z);
                if (r > HALF + 0.4) continue;
                for (int y = -FOUNDATION; y < 0; y++) out.add(new HallBlueprint.Placement(x, y, z, t.base()));
                out.add(new HallBlueprint.Placement(x, 0, z, r > HALF - 1.2 ? t.accent() : HallBlueprint.pick(t.floor(), x, 0, z)));
                for (int y = 1; y <= CLEAR; y++) out.add(new HallBlueprint.Placement(x, y, z, "minecraft:air"));
            }
        }
        // the gate: two 2-wide pillars, 6 high, a lintel across, the accent on top, a dark doorway between
        for (int y = 1; y <= 6; y++) {
            for (int x : new int[]{-3, -2, 2, 3}) out.add(new HallBlueprint.Placement(x, y, -1, t.pillar()));
        }
        for (int x = -3; x <= 3; x++) {
            out.add(new HallBlueprint.Placement(x, 7, -1, t.accent()));
            if (Math.abs(x) <= 1) out.add(new HallBlueprint.Placement(x, 8, -1, t.dais()));
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 6; y++) out.add(new HallBlueprint.Placement(x, y, -1, "minecraft:black_concrete"));
            for (int y = 1; y <= 6; y++) out.add(new HallBlueprint.Placement(x, y, -2, t.pillar()));
        }
        out.add(new HallBlueprint.Placement(-4, 1, 1, t.lamp()));
        out.add(new HallBlueprint.Placement(4, 1, 1, t.lamp()));
        // standing stones round the platform's rim (not in front of the gate)
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(30 + 60 * k);
            int sx = (int) Math.round(Math.sin(a) * (HALF - 0.5)), sz = (int) Math.round(-Math.cos(a) * (HALF - 0.5));
            if (sz > 2) continue; // keep the approach open
            for (int y = 1; y <= 2 + (k % 2); y++) out.add(new HallBlueprint.Placement(sx, y, sz, t.pillar()));
        }
        return List.copyOf(out);
    }
}
