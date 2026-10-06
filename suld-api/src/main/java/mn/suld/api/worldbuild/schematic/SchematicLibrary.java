package mn.suld.api.worldbuild.schematic;

import mn.suld.api.json.Json;
import mn.suld.api.worldbuild.Facing;
import mn.suld.api.worldbuild.Layer;
import mn.suld.api.worldbuild.ModuleLibrary;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Loads authored schematics listed in {@code assets/world/schematics/schematics.json} (file, front,
 * sink, layer, and the author/source/licence credit that must travel with every third-party build)
 * and registers them as {@code schem:<id>} modules.
 */
public final class SchematicLibrary {

    /** One catalogued schematic and its credit. */
    public record Entry(String id, String file, Facing front, int sink, Layer layer, String title,
                        String author, String source, String licence) {
    }

    private SchematicLibrary() {
    }

    public static List<Entry> catalogue(String json) {
        List<Entry> out = new ArrayList<>();
        for (Object o : Json.array(Json.object(Json.parse(json)).get("schematics"))) {
            Map<String, Object> e = Json.object(o);
            out.add(new Entry((String) e.get("id"), (String) e.get("file"),
                    Facing.valueOf(((String) e.getOrDefault("front", "south")).toUpperCase(Locale.ROOT)),
                    Json.integer(e.getOrDefault("sink", 0L)),
                    Layer.valueOf(((String) e.getOrDefault("layer", "structure")).toUpperCase(Locale.ROOT)),
                    (String) e.get("title"), (String) e.get("author"), (String) e.get("source"), (String) e.get("licence")));
        }
        return out;
    }

    /** Registers every catalogued schematic; {@code open} resolves a file name to its bytes. */
    public static void register(ModuleLibrary library, List<Entry> entries, Function<String, InputStream> open) throws IOException {
        for (Entry e : entries) {
            try (InputStream in = open.apply(e.file())) {
                if (in == null) throw new IOException("schematic file missing: " + e.file());
                library.register(new SchematicModule("schem:" + e.id(), Schematic.read(in), e.front(), e.sink(), e.layer()));
            }
        }
    }
}
