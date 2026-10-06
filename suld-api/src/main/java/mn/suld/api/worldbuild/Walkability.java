package mn.suld.api.worldbuild;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Player movement model shared by the compile-time and the in-world validators. A position is the
 * block a player's feet occupy. Walkable: feet and head passable and dry, floor standable (or the
 * feet are on a ladder). Moves: four directions, up 1 (step/jump), down up to 3; ladders climb.
 */
public final class Walkability {

    private Walkability() {
    }

    private static boolean ladder(String b) {
        return BlockStates.id(b).equals("minecraft:ladder") || BlockStates.id(b).equals("minecraft:vine");
    }

    private static boolean open(String b) {
        return BlockKinds.isPassable(b) && !BlockKinds.isLiquid(b);
    }

    public static boolean walkable(BlockLookup w, int x, int y, int z) {
        String feet = w.blockAt(x, y, z);
        if (!open(feet) || !open(w.blockAt(x, y + 1, z))) return false;
        return ladder(feet) || BlockKinds.isStandable(w.blockAt(x, y - 1, z));
    }

    /**
     * Flood-fills reachable feet positions from a start, inside an area, up to maxNodes.
     * Returns packed positions ({@link BlockPos}).
     */
    public static Set<Long> flood(BlockLookup w, int sx, int sy, int sz, Footprint area, int maxNodes) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        if (!walkable(w, sx, sy, sz)) return seen;
        seen.add(BlockPos.pack(sx, sy, sz));
        queue.add(new long[]{sx, sy, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && seen.size() < maxNodes) {
            long[] n = queue.poll();
            int x = (int) n[0], y = (int) n[1], z = (int) n[2];
            boolean headroom = open(w.blockAt(x, y + 2, z));
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                if (!area.contains(nx, y, nz)) continue;
                for (int dy = 1; dy >= -3; dy--) {
                    int ny = y + dy;
                    if (dy == 1 && !headroom) continue;
                    if (!walkable(w, nx, ny, nz)) continue;
                    boolean clear = true;
                    for (int yy = ny + 2; yy <= y + 1 && clear; yy++) clear = open(w.blockAt(nx, yy, nz));
                    if (!clear) continue;
                    if (seen.add(BlockPos.pack(nx, ny, nz))) queue.add(new long[]{nx, ny, nz});
                    break; // take the highest landing
                }
            }
            // ladders: climb up and down
            for (int dy : new int[]{1, -1}) {
                int ny = y + dy;
                if ((ladder(w.blockAt(x, y, z)) || ladder(w.blockAt(x, ny, z))) && walkable(w, x, ny, z)
                        && seen.add(BlockPos.pack(x, ny, z))) {
                    queue.add(new long[]{x, ny, z});
                }
            }
        }
        return seen;
    }
}
