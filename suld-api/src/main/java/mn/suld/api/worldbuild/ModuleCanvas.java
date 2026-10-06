package mn.suld.api.worldbuild;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where a module writes its blocks, in module-local coordinates. Tokens ({@code @role[...]}) are
 * resolved through the context palette at write time, so the stored blocks are concrete and can be
 * rotated/mirrored exactly. Every write is tagged with the current {@link Pass} and {@link Layer}.
 * Within one canvas a later write to the same position replaces the earlier one, so modules can
 * layer detail over a base shape.
 */
public final class ModuleCanvas {

    private final Palette palette;
    private final Map<Long, Cell> cells = new LinkedHashMap<>();
    private Pass pass = Pass.SHELLS;
    private Layer layer;
    private int nested;

    public ModuleCanvas(Palette palette, Layer defaultLayer) {
        this.palette = palette;
        this.layer = defaultLayer;
    }

    public ModuleCanvas pass(Pass p) {
        this.pass = p;
        return this;
    }

    public Pass pass() { return pass; }

    public ModuleCanvas layer(Layer l) {
        this.layer = l;
        return this;
    }

    public Layer layer() { return layer; }

    public Map<Long, Cell> cells() { return cells; }

    public String get(int x, int y, int z) {
        Cell c = cells.get(BlockPos.pack(x, y, z));
        return c == null ? null : c.block();
    }

    public boolean has(int x, int y, int z) {
        return cells.containsKey(BlockPos.pack(x, y, z));
    }

    public ModuleCanvas set(int x, int y, int z, String token) {
        String block = palette.resolve(token);
        if (!block.startsWith("minecraft:")) throw new IllegalArgumentException("not a block: " + block);
        cells.put(BlockPos.pack(x, y, z), new Cell(block, pass, layer, -1));
        return this;
    }

    /** Write only if nothing has been written there yet in this canvas. */
    public ModuleCanvas setIfEmpty(int x, int y, int z, String token) {
        if (!has(x, y, z)) set(x, y, z, token);
        return this;
    }

    public ModuleCanvas air(int x, int y, int z) {
        return set(x, y, z, "minecraft:air");
    }

    public ModuleCanvas remove(int x, int y, int z) {
        cells.remove(BlockPos.pack(x, y, z));
        return this;
    }

    public ModuleCanvas fill(int x1, int y1, int z1, int x2, int y2, int z2, String token) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, token);
        return this;
    }

    /** The four side walls of a box (no floor or ceiling). */
    public ModuleCanvas walls(int x1, int y1, int z1, int x2, int y2, int z2, String token) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = ax; x <= bx; x++) {
                set(x, y, az, token);
                set(x, y, bz, token);
            }
            for (int z = az; z <= bz; z++) {
                set(ax, y, z, token);
                set(bx, y, z, token);
            }
        }
        return this;
    }

    /** Filled disc at height y (distance from block centres). */
    public ModuleCanvas disc(int cx, int y, int cz, double radius, String token) {
        return ring(cx, y, cz, -1, radius, token);
    }

    /** Ring: inner < d <= outer (inner < 0 = filled). */
    public ModuleCanvas ring(int cx, int y, int cz, double inner, double outer, String token) {
        int r = (int) Math.ceil(outer);
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                double d2 = x * x + z * z;
                if (d2 <= outer * outer + 0.25 && (inner < 0 || d2 > inner * inner + 0.25)) set(cx + x, y, cz + z, token);
            }
        return this;
    }

    /** A straight horizontal run of blocks from (x1,z1) to (x2,z2) at height y (Bresenham). */
    public ModuleCanvas line(int x1, int y, int z1, int x2, int z2, String token) {
        int dx = Math.abs(x2 - x1), dz = Math.abs(z2 - z1), sx = x1 < x2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;
        int err = dx - dz, x = x1, z = z1;
        while (true) {
            set(x, y, z, token);
            if (x == x2 && z == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
        }
        return this;
    }

    /**
     * Places a nested module: builds it into its own canvas with its own default layer and the
     * current pass as its starting pass, then copies its cells here under the given transform and
     * offset. Nested writes replace what this canvas had at those positions.
     */
    public ModuleCanvas place(Module module, int ox, int oy, int oz, Transform t, Map<String, Object> params, ModuleContext parent) {
        ModuleCanvas sub = new ModuleCanvas(palette, module.layer());
        sub.pass(pass);
        module.build(sub, parent.child(params, nested++));
        for (Map.Entry<Long, Cell> e : sub.cells.entrySet()) {
            long p = e.getKey();
            int[] xz = t.apply(BlockPos.x(p), BlockPos.z(p));
            Cell c = e.getValue();
            cells.put(BlockPos.pack(ox + xz[0], oy + BlockPos.y(p), oz + xz[1]),
                    new Cell(BlockStates.transform(c.block(), t), c.pass(), c.layer(), -1));
        }
        return this;
    }

    public ModuleCanvas place(Module module, int ox, int oy, int oz, Transform t, ModuleContext parent) {
        return place(module, ox, oy, oz, t, Map.of(), parent);
    }

    /** Copy (for tests/exports). */
    public Map<Long, Cell> snapshot() {
        return new HashMap<>(cells);
    }
}
