package mn.suld.api.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player-versus-player policy (owner decision 2026-10-11: SÜLD is PvE; players fight each other only by consent).
 *
 * @param wildEnabled        true restores open PvP outside Kharkhorum (the pre-policy behaviour); false by default
 * @param duelEnabled        whether {@code /duel} challenges are offered
 * @param duelRequestSeconds how long a challenge waits for an answer
 * @param duelMinutes        how long an accepted duel stays open before it lapses on its own
 * @param arenas             rectangles where everyone may fight everyone («Сүлдний тулаан» grounds); empty by default
 */
public record PvpSettings(boolean wildEnabled, boolean duelEnabled, int duelRequestSeconds, int duelMinutes, List<Arena> arenas) {

    /** A fighting ground: a world and a block rectangle (x/z, inclusive). */
    public record Arena(String world, int minX, int minZ, int maxX, int maxZ) {

        public Arena {
            if (world == null || world.isBlank()) throw new IllegalArgumentException("arena needs a world");
            int x1 = Math.min(minX, maxX), x2 = Math.max(minX, maxX), z1 = Math.min(minZ, maxZ), z2 = Math.max(minZ, maxZ);
            minX = x1;
            maxX = x2;
            minZ = z1;
            maxZ = z2;
        }

        public boolean contains(String w, int x, int z) {
            return world.equals(w) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** {@code "world x1 z1 x2 z2"} (separators: spaces, commas or colons); null when malformed. */
        public static Arena parse(String text) {
            if (text == null) return null;
            String[] p = text.trim().split("[\\s,:]+");
            if (p.length != 5) return null;
            try {
                return new Arena(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    public PvpSettings {
        duelRequestSeconds = Math.max(10, Math.min(300, duelRequestSeconds));
        duelMinutes = Math.max(1, Math.min(60, duelMinutes));
        arenas = arenas == null ? List.of() : List.copyOf(arenas);
    }

    public static PvpSettings defaults() {
        return new PvpSettings(false, true, 60, 10, List.of());
    }

    /** Parse the config's {@code pvp.arenas} string list; malformed lines are skipped (the caller may log them). */
    public static List<Arena> parseArenas(List<String> lines) {
        List<Arena> out = new ArrayList<>();
        if (lines != null) for (String l : lines) {
            Arena a = Arena.parse(l);
            if (a != null) out.add(a);
        }
        return out;
    }

    public boolean inArena(String world, int x, int z) {
        for (Arena a : arenas) if (a.contains(world, x, z)) return true;
        return false;
    }

    @Override
    public String toString() {
        return "pvp{wild=" + wildEnabled + ", duel=" + duelEnabled + ", arenas=" + arenas.size() + "}".toLowerCase(Locale.ROOT);
    }
}
