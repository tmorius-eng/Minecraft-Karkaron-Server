package mn.suld.api.worldbuild;

import mn.suld.api.json.Json;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class WorldBuildCoreTest {

    // ---- helpers ---------------------------------------------------------------------------------

    static Module module(String id, Layer layer, BiConsumer<ModuleCanvas, ModuleContext> body) {
        return new Module() {
            public String id() { return id; }
            public Layer layer() { return layer; }
            public void build(ModuleCanvas c, ModuleContext ctx) { body.accept(c, ctx); }
        };
    }

    static CitySpec spec(List<Placement> placements, List<WorldPoint> points, List<TerrainPlan.Zone> zones) {
        TerrainPlan terrain = new TerrainPlan(-20, -20, 20, 20, 4, 8, "minecraft:grass_block", zones);
        return new CitySpec("test", "slice.t", 7, new Footprint(-20, -64, -20, 20, 319, 20), Palette.KHARKHORUM,
                Map.of(), terrain, placements, points);
    }

    static Placement at(String id, String module, int x, int y, int z, int rot, boolean mirror) {
        return new Placement(id, module, x, y, z, Transform.of(rot, mirror), Map.of(), Map.of(), null, false, false, 1);
    }

    // ---- positions, transforms, block states ------------------------------------------------------

    @Test
    void blockPosRoundTripsNegativeCoordinates() {
        for (int[] p : new int[][]{{0, 0, 0}, {-1, -64, -1}, {175, 300, -160}, {-1048576, -2048, 1048575}}) {
            long k = BlockPos.pack(p[0], p[1], p[2]);
            assertArrayEquals(p, new int[]{BlockPos.x(k), BlockPos.y(k), BlockPos.z(k)});
        }
    }

    @Test
    void rotationsTurnClockwiseSeenFromAbove() {
        // east (1,0) → south (0,1) → west (-1,0) → north (0,-1)
        assertArrayEquals(new int[]{0, 1}, Transform.of(90, false).apply(1, 0));
        assertArrayEquals(new int[]{-1, 0}, Transform.of(180, false).apply(1, 0));
        assertArrayEquals(new int[]{0, -1}, Transform.of(270, false).apply(1, 0));
        assertEquals(Facing.EAST, Transform.of(90, false).apply(Facing.NORTH));
        assertEquals(Facing.WEST, Transform.of(0, true).apply(Facing.EAST));
        assertArrayEquals(new int[]{-3, 2}, Transform.of(0, true).apply(3, 2));
        assertEquals(Rotation.CW_270, Rotation.ofDegrees(-90));
    }

    @Test
    void blockStatesFollowTheTransform() {
        Transform r90 = Transform.of(90, false), mirror = Transform.of(0, true);
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]",
                BlockStates.transform("minecraft:oak_stairs[facing=north,half=bottom,shape=straight]", r90));
        assertEquals("minecraft:oak_stairs[facing=west,half=top,shape=outer_right]",
                BlockStates.transform("minecraft:oak_stairs[facing=east,half=top,shape=outer_left]", mirror));
        assertEquals("minecraft:spruce_door[facing=south,half=lower,hinge=right]",
                BlockStates.transform("minecraft:spruce_door[facing=south,half=lower,hinge=left]", mirror));
        assertEquals("minecraft:oak_log[axis=z]", BlockStates.transform("minecraft:oak_log[axis=x]", r90));
        assertEquals("minecraft:oak_log[axis=y]", BlockStates.transform("minecraft:oak_log[axis=y]", r90));
        // banner rotation 0 = south; CW 90 → west (4); mirror keeps south, swaps east(12)/west(4)
        assertEquals("minecraft:red_banner[rotation=4]", BlockStates.transform("minecraft:red_banner[rotation=0]", r90));
        assertEquals("minecraft:red_banner[rotation=12]", BlockStates.transform("minecraft:red_banner[rotation=4]", mirror));
        assertEquals("minecraft:vine[east=true]", BlockStates.transform("minecraft:vine[north=true]", r90));
        assertEquals("minecraft:stone", BlockStates.transform("minecraft:stone", r90));
    }

    @Test
    void fourQuarterTurnsAreIdentity() {
        String b = "minecraft:oak_stairs[facing=west,half=bottom,shape=inner_left]";
        String r = b;
        for (int i = 0; i < 4; i++) r = BlockStates.transform(r, Transform.of(90, false));
        assertEquals(b, r);
    }

    @Test
    void paletteResolvesRolesAndRejectsUnknownOnes() {
        assertEquals("minecraft:deepslate_tile_stairs[facing=north]", Palette.KHARKHORUM.resolve("@roof_stairs[facing=north]"));
        assertEquals("minecraft:stone", Palette.KHARKHORUM.resolve("minecraft:stone"));
        Palette p = Palette.KHARKHORUM.with(Map.of("roof", "minecraft:red_nether_bricks"));
        assertEquals("minecraft:red_nether_bricks", p.resolve("@roof"));
        assertThrows(IllegalArgumentException.class, () -> Palette.KHARKHORUM.resolve("@nonexistent"));
    }

    @Test
    void jsonParsesNestedValues() {
        Map<String, Object> o = Json.object(Json.parse("{\"a\": [1, 2.5, \"x\\u00fc\"], \"b\": {\"c\": true, \"d\": null}}"));
        List<Object> a = Json.array(o.get("a"));
        assertEquals(1L, a.get(0));
        assertEquals(2.5, a.get(1));
        assertEquals("xü", a.get(2));
        assertEquals(true, Json.object(o.get("b")).get("c"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\": }"));
    }

    // ---- compiler -------------------------------------------------------------------------------

    @Test
    void placementsAreRotatedAndOffset() {
        ModuleLibrary lib = new ModuleLibrary().register(module("bar", Layer.STRUCTURE,
                (c, x) -> c.set(1, 1, 0, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight]")));
        CompiledCity city = CityCompiler.compile(spec(List.of(at("p", "bar", 5, 0, 5, 90, false)), List.of(), List.of()), lib);
        assertFalse(city.hasErrors(), city.issues().toString());
        // local (1, 0) → CW90 → (0, 1) → +(5,5)
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight]", city.blockAt(5, 1, 6));
    }

    @Test
    void sameLayerOverlapIsAnErrorAndHigherLayersWin() {
        ModuleLibrary lib = new ModuleLibrary()
                .register(module("a", Layer.STRUCTURE, (c, x) -> c.fill(0, 1, 0, 2, 1, 2, "minecraft:stone")))
                .register(module("b", Layer.STRUCTURE, (c, x) -> c.fill(0, 1, 0, 2, 1, 2, "minecraft:oak_planks")))
                .register(module("road", Layer.INFRA, (c, x) -> c.fill(-3, 1, 0, 3, 1, 0, "minecraft:andesite")));
        CompiledCity bad = CityCompiler.compile(spec(List.of(at("a1", "a", 0, 0, 0, 0, false),
                at("b1", "b", 1, 0, 1, 0, false)), List.of(), List.of()), lib);
        assertTrue(bad.issues().stream().anyMatch(i -> i.code().equals("OVERLAP") && i.message().contains("4 block")), bad.issues().toString());

        CompiledCity ok = CityCompiler.compile(spec(List.of(at("a1", "a", 0, 0, 0, 0, false),
                at("r", "road", 0, 0, 0, 0, false)), List.of(), List.of()), lib);
        assertFalse(ok.issues().stream().anyMatch(i -> i.code().equals("OVERLAP")));
        assertEquals("minecraft:stone", ok.blockAt(1, 1, 0));      // structure kept over road
        assertEquals("minecraft:andesite", ok.blockAt(-2, 1, 0));  // road elsewhere
    }

    @Test
    void missingModulesDuplicateLandmarksAndIdsAreReported() {
        ModuleLibrary lib = new ModuleLibrary().register(module("m", Layer.LANDMARK, (c, x) -> c.set(0, 1, 0, "minecraft:stone")));
        Placement l1 = new Placement("l1", "m", 0, 0, 0, Transform.IDENTITY, Map.of(), Map.of(), null, true, false, 1);
        Placement l2 = new Placement("l2", "m", 9, 0, 9, Transform.IDENTITY, Map.of(), Map.of(), null, true, false, 1);
        CompiledCity city = CityCompiler.compile(spec(List.of(l1, l2, at("x", "ghost", 0, 0, 0, 0, false),
                at("x", "ghost", 1, 0, 0, 0, false)), List.of(), List.of()), lib);
        List<String> codes = city.issues().stream().map(Issue::code).toList();
        assertTrue(codes.contains("DUPLICATE_LANDMARK"));
        assertTrue(codes.contains("MISSING_MODULE"));
        assertTrue(codes.contains("DUPLICATE_ID"));
    }

    @Test
    void fencesConnectAcrossPlacementsAfterRotation() {
        ModuleLibrary lib = new ModuleLibrary().register(module("post", Layer.PROP,
                (c, x) -> c.set(0, 1, 0, "minecraft:spruce_fence")));
        CompiledCity city = CityCompiler.compile(spec(List.of(at("a", "post", 0, 0, 0, 0, false),
                at("b", "post", 1, 0, 0, 270, false)), List.of(), List.of()), lib);
        assertEquals("minecraft:spruce_fence[north=false,east=true,south=false,west=false]", city.blockAt(0, 1, 0));
        assertEquals("minecraft:spruce_fence[north=false,east=false,south=false,west=true]", city.blockAt(1, 1, 0));
    }

    @Test
    void compileIsDeterministic() {
        ModuleLibrary lib = new ModuleLibrary().register(module("rnd", Layer.PROP,
                (c, ctx) -> { for (int i = 0; i < 20; i++) c.set(ctx.random().nextInt(10), 1, ctx.random().nextInt(10), "minecraft:stone"); }));
        CitySpec s = spec(List.of(at("r", "rnd", 0, 0, 0, 0, false)), List.of(), List.of());
        assertEquals(CityCompiler.compile(s, lib).fingerprint(), CityCompiler.compile(s, lib).fingerprint());
    }

    // ---- terrain --------------------------------------------------------------------------------

    @Test
    void terrainZonesFallOffAndFeatherIntoNaturalGround() {
        TerrainPlan t = new TerrainPlan(-20, -20, 20, 20, 6, 8, "minecraft:grass_block", List.of(
                new TerrainPlan.Zone("hill", new TerrainPlan.Circle(0, 0, 3), 4, 4, null),
                new TerrainPlan.Zone("terrace", new TerrainPlan.Rect(10, -20, 20, -10), 8, 0, "minecraft:stone_bricks")));
        assertEquals(4, t.elevation(0, 0));
        assertEquals(2, t.elevation(5, 0));      // halfway down the slope
        assertEquals(0, t.elevation(8, 0));
        assertEquals(8, t.elevation(15, -15));
        assertEquals(0, t.elevation(15, -9));    // crisp edge
        assertEquals("minecraft:stone_bricks", t.surface(15, -15));
        // natural ground at +6 outside: the feather band ramps monotonically from 0 to 6
        List<TerrainPlan.Column> cols = t.columns((x, z) -> 6);
        int prev = -1;
        for (int x = 20; x <= 26; x++) {
            int fx = x;
            int top = cols.stream().filter(c -> c.x() == fx && c.z() == 0).mapToInt(TerrainPlan.Column::top).findFirst().orElse(6);
            assertTrue(top >= prev, "ramp must not dip at x=" + x);
            prev = top;
        }
        assertEquals(6, prev);
    }

    // ---- validator ------------------------------------------------------------------------------

    @Test
    void floatingBlocksAreFound() {
        ModuleLibrary lib = new ModuleLibrary().register(module("f", Layer.STRUCTURE, (c, x) -> {
            c.fill(0, 1, 0, 0, 3, 0, "minecraft:stone");   // pillar on the ground
            c.set(1, 3, 0, "minecraft:lantern");            // attached to the pillar
            c.set(5, 6, 5, "minecraft:stone");              // floating
        }));
        ValidationReport r = CityValidator.validate(CityCompiler.compile(spec(List.of(at("f", "f", 0, 0, 0, 0, false)),
                List.of(new WorldPoint("spawn", "spawn", null, -10, 1, -10, 0, true, "slice.t")), List.of()), lib));
        assertEquals(1, r.stats().get("floating_blocks"));
    }

    @Test
    void pointsMustBeReachableOnFootAndCanalsNeedBridges() {
        // a 2-wide water moat ring around the shop at (10, 0)
        Module canal = module("canal", Layer.INFRA, (c, x) -> {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) < 3) continue;
                c.set(dx, 0, dz, "minecraft:water");
                c.set(dx, -1, dz, "minecraft:water");
                c.set(dx, -2, dz, "minecraft:stone");
            }
        });
        Module bridge = module("bridge", Layer.STRUCTURE, (c, x) -> {
            for (int dx = -5; dx <= -2; dx++) c.set(dx, 1, 0, "minecraft:spruce_slab[type=bottom]");
        });
        ModuleLibrary lib = new ModuleLibrary().register(canal).register(bridge);
        List<WorldPoint> pts = List.of(new WorldPoint("spawn", "spawn", null, -10, 1, 0, 0, true, "slice.t"),
                new WorldPoint("shop", "merchant", null, 10, 1, 0, 0, true, "slice.t"));
        ValidationReport without = CityValidator.validate(CityCompiler.compile(spec(List.of(at("c", "canal", 10, 0, 0, 0, false)), pts, List.of()), lib));
        assertTrue(without.issues().stream().anyMatch(i -> i.code().equals("POINT_UNREACHABLE")), without.summary());
        ValidationReport with = CityValidator.validate(CityCompiler.compile(spec(List.of(at("c", "canal", 10, 0, 0, 0, false),
                at("b", "bridge", 10, 0, 0, 0, false)), pts, List.of()), lib));
        assertTrue(with.passed(), with.summary());
        assertEquals("2/2", with.stats().get("points_ok"));
    }

    @Test
    void blockedPointsAreReported() {
        ModuleLibrary lib = new ModuleLibrary().register(module("box", Layer.STRUCTURE, (c, x) -> c.fill(0, 1, 0, 2, 3, 2, "minecraft:stone")));
        List<WorldPoint> pts = new ArrayList<>(List.of(new WorldPoint("spawn", "spawn", null, -10, 1, 0, 0, true, "slice.t"),
                new WorldPoint("inside", "npc", null, 1, 1, 1, 0, true, "slice.t")));
        ValidationReport r = CityValidator.validate(CityCompiler.compile(spec(List.of(at("b", "box", 0, 0, 0, 0, false)), pts, List.of()), lib));
        assertTrue(r.issues().stream().anyMatch(i -> i.code().equals("POINT_BLOCKED")), r.summary());
    }
}
