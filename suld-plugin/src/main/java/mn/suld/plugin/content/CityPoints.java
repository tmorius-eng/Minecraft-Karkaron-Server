package mn.suld.plugin.content;

import mn.suld.api.json.Json;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ids of Kharkhorum's gameplay points ({@code assets/world/kharkhorum/points.json}, packed into the jar as
 * {@code /world/kharkhorum/points.json}), read once: the content loader checks every NPC of content/npcs.json
 * against them before the world builder exists. Outside the jar (a unit test, the simulator) the repository file is
 * read; when neither can be read the set is empty and the loader is told not to check.
 */
public final class CityPoints {

    private static volatile Set<String> ids;

    private CityPoints() {
    }

    public static Set<String> ids() {
        Set<String> out = ids;
        if (out == null) {
            synchronized (CityPoints.class) {
                if (ids == null) ids = read();
                out = ids;
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> read() {
        String text = null;
        try (InputStream in = CityPoints.class.getResourceAsStream("/world/kharkhorum/points.json")) {
            if (in != null) text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            text = null;
        }
        if (text == null) {
            for (Path p : List.of(Path.of("assets/world/kharkhorum/points.json"), Path.of("../assets/world/kharkhorum/points.json"))) {
                try {
                    if (Files.isRegularFile(p)) {
                        text = Files.readString(p, StandardCharsets.UTF_8);
                        break;
                    }
                } catch (Exception ignored) {
                    text = null;
                }
            }
        }
        if (text == null) return Set.of();
        try {
            Map<String, Object> root = (Map<String, Object>) Json.parse(text);
            Set<String> out = new LinkedHashSet<>();
            for (Object o : (List<Object>) root.get("points")) out.add((String) ((Map<String, Object>) o).get("id"));
            return Collections.unmodifiableSet(out);
        } catch (Exception e) {
            return Set.of();
        }
    }
}
