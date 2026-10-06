package mn.suld.api.worldbuild.kharkhorum;

import mn.suld.api.json.Json;
import mn.suld.api.worldbuild.BlockKinds;
import mn.suld.api.worldbuild.BlockPos;
import mn.suld.api.worldbuild.Cell;
import mn.suld.api.worldbuild.CityCompiler;
import mn.suld.api.worldbuild.CitySpec;
import mn.suld.api.worldbuild.CityValidator;
import mn.suld.api.worldbuild.CompiledCity;
import mn.suld.api.worldbuild.Footprint;
import mn.suld.api.worldbuild.Module;
import mn.suld.api.worldbuild.ModuleCanvas;
import mn.suld.api.worldbuild.ModuleContext;
import mn.suld.api.worldbuild.Palette;
import mn.suld.api.worldbuild.SliceLoader;
import mn.suld.api.worldbuild.ValidationReport;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Offline tooling (no server): dump a module or a whole slice to the voxel text format used by
 * tools/blender/render_blueprint.py, and validate a slice.
 * <pre>
 *   module &lt;id&gt; &lt;out.txt&gt; ['{"param": ...}']
 *   slice  &lt;slice.json&gt; &lt;points.json&gt; &lt;out.txt&gt;
 * </pre>
 */
public final class KharkhorumTool {

    private KharkhorumTool() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: module <id> <out> [paramsJson] | slice <slice.json> <points.json> <out>");
            System.exit(2);
        }
        switch (args[0]) {
            case "module" -> dumpModule(args[1], Path.of(args[2]), args.length > 3 ? Json.object(Json.parse(args[3])) : Map.of());
            case "slice" -> {
                ValidationReport rep = dumpSlice(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
                System.out.print(rep.summary());
                if (!rep.passed()) System.exit(1);
            }
            case "schem" -> dumpSchematic(Path.of(args[1]), Path.of(args[2]));
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    static void dumpModule(String id, Path out, Map<String, Object> params) throws IOException {
        Module m = Kharkhorum.library().get(id);
        ModuleCanvas c = new ModuleCanvas(Palette.KHARKHORUM, m.layer());
        m.build(c, new ModuleContext(params, Palette.KHARKHORUM, 42));
        Footprint fp = null;
        for (long p : c.cells().keySet()) {
            int x = BlockPos.x(p), y = BlockPos.y(p), z = BlockPos.z(p);
            fp = fp == null ? Footprint.point(x, y, z) : fp.include(x, y, z);
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("# module " + id + " blocks " + c.cells().size() + " bounds " + fp);
            int pad = 4;
            for (int x = fp.minX() - pad; x <= fp.maxX() + pad; x++)
                for (int z = fp.minZ() - pad; z <= fp.maxZ() + pad; z++)
                    if (!c.has(x, 0, z)) w.println(x + " 0 " + z + " minecraft:grass_block");
            for (Map.Entry<Long, Cell> e : c.cells().entrySet()) {
                if (BlockKinds.isAir(e.getValue().block())) continue;
                long p = e.getKey();
                w.println(BlockPos.x(p) + " " + BlockPos.y(p) + " " + BlockPos.z(p) + " " + e.getValue().block());
            }
        }
        System.out.println(id + ": " + c.cells().size() + " blocks, bounds " + fp);
    }

    static void dumpSchematic(Path file, Path out) throws IOException {
        mn.suld.api.worldbuild.schematic.Schematic s;
        try (var in = Files.newInputStream(file)) {
            s = mn.suld.api.worldbuild.schematic.Schematic.read(in);
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("# schematic " + file.getFileName() + " " + s.format() + " " + s.width() + "x" + s.height() + "x" + s.length()
                    + " dataVersion " + s.dataVersion());
            s.blocks().forEach((k, b) -> w.println(mn.suld.api.worldbuild.schematic.Schematic.kx(k) + " "
                    + mn.suld.api.worldbuild.schematic.Schematic.ky(k) + " " + mn.suld.api.worldbuild.schematic.Schematic.kz(k) + " " + b));
        }
        System.out.println(file.getFileName() + ": " + s.format() + " " + s.width() + "x" + s.height() + "x" + s.length()
                + ", " + s.blocks().size() + " blocks, dataVersion " + s.dataVersion());
    }

    static ValidationReport dumpSlice(Path slice, Path points, Path out) throws IOException {
        CitySpec spec = SliceLoader.load(Files.readString(slice), Files.readString(points), Palette.KHARKHORUM);
        long t0 = System.nanoTime();
        CompiledCity city = CityCompiler.compile(spec, Kharkhorum.library());
        long t1 = System.nanoTime();
        ValidationReport rep = CityValidator.validate(city);
        long t2 = System.nanoTime();
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            w.println("# slice " + spec.slice() + " blocks " + city.blockCount() + " fingerprint " + city.fingerprint());
            for (Map.Entry<Long, Cell> e : city.cells().entrySet()) {
                if (BlockKinds.isAir(e.getValue().block())) continue;
                long p = e.getKey();
                w.println(BlockPos.x(p) + " " + BlockPos.y(p) + " " + BlockPos.z(p) + " " + e.getValue().block());
            }
        }
        rep.stats().put("compile_ms", (t1 - t0) / 1_000_000);
        rep.stats().put("validate_ms", (t2 - t1) / 1_000_000);
        return rep;
    }
}
