package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.json.Json;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.Pass;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Trees, groves and flower meadows (Pass 8: landscaping). */
final class Landscape {

    private Landscape() {
    }

    /**
     * landscape.grove — scatters trees over a rectangle on the planned terrain. Params: rect
     * [x1, z1, x2, z2] (local), count, species [...], spacing (min trunk distance), avoid [[x1,z1,x2,z2],
     * ...] (no trunk within 3 blocks), flowers (meadow flowers per tree).
     */
    static final Module GROVE = Kit.module("landscape.grove", Layer.PROP, (c, ctx) -> {
        SplittableRandom r = ctx.random();
        List<Object> rect = ctx.list("rect");
        int x1 = Json.integer(rect.get(0)), z1 = Json.integer(rect.get(1)), x2 = Json.integer(rect.get(2)), z2 = Json.integer(rect.get(3));
        int count = ctx.integer("count", 6), spacing = ctx.integer("spacing", 7), flowers = ctx.integer("flowers", 3);
        List<String> species = new ArrayList<>();
        for (Object o : ctx.list("species")) species.add((String) o);
        if (species.isEmpty()) species.add("elm");
        List<int[]> avoid = new ArrayList<>();
        for (Object o : ctx.list("avoid")) {
            List<Object> a = Json.array(o);
            avoid.add(new int[]{Json.integer(a.get(0)), Json.integer(a.get(1)), Json.integer(a.get(2)), Json.integer(a.get(3))});
        }
        String[] meadow = {"minecraft:short_grass", "minecraft:short_grass", "minecraft:poppy", "minecraft:cornflower",
                "minecraft:dandelion", "minecraft:oxeye_daisy", "minecraft:azure_bluet"};
        List<int[]> trunks = new ArrayList<>();
        int tries = 0;
        while (trunks.size() < count && tries++ < count * 60) {
            int x = x1 + r.nextInt(x2 - x1 + 1), z = z1 + r.nextInt(z2 - z1 + 1);
            boolean ok = true;
            for (int[] a : avoid) if (x >= a[0] - 3 && x <= a[2] + 3 && z >= a[1] - 3 && z <= a[3] + 3) ok = false;
            for (int[] t : trunks) if ((t[0] - x) * (t[0] - x) + (t[1] - z) * (t[1] - z) < spacing * spacing) ok = false;
            if (!ok) continue;
            trunks.add(new int[]{x, z});
            Kit.tree(c, x, ctx.ground(x, z), z, species.get(r.nextInt(species.size())), r);
        }
        c.pass(Pass.LANDSCAPING);
        for (int[] t : trunks)
            for (int i = 0; i < flowers; i++) {
                int x = t[0] + r.nextInt(9) - 4, z = t[1] + r.nextInt(9) - 4;
                boolean ok = x >= x1 && x <= x2 && z >= z1 && z <= z2;
                for (int[] a : avoid) if (x >= a[0] - 1 && x <= a[2] + 1 && z >= a[1] - 1 && z <= a[3] + 1) ok = false;
                int y = ctx.ground(x, z) + 1;
                if (ok && !c.has(x, y, z)) c.set(x, y, z, meadow[r.nextInt(meadow.length)]);
            }
    });
}
