package mn.suld.plugin.skill;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No key, click or gesture opens the skill tree: it opens from /skills and the menu only. Sneak + right click with the
 * class weapon used to open it, and players sneak through every fight (Shift + Space dodges, Shift + F casts, Shift + Q
 * cycles the lock), so a combo's R, a bow draw or a right click on a mob while sneaking threw them onto the tree stage
 * mid-fight. Read from the sources, as no server runs in the tests.
 */
class NoAccidentalWindowTest {

    /** The events a key, a click or a movement raises. */
    static final Set<String> INPUT_EVENTS = Set.of("PlayerInteractEvent", "PlayerInteractEntityEvent", "PlayerInteractAtEntityEvent",
            "PlayerAnimationEvent", "PlayerToggleSneakEvent", "PlayerToggleSprintEvent", "PlayerSwapHandItemsEvent", "PlayerDropItemEvent",
            "PlayerInputEvent", "PlayerItemHeldEvent", "PlayerJumpEvent", "PlayerMoveEvent", "EntityDamageByEntityEvent", "ProjectileLaunchEvent",
            "EntityShootBowEvent");
    /** Whatever opens the skill tree's windows. */
    static final Pattern OPENS_TREE = Pattern.compile("skillMap|SkillMapMenu|skillSky|SkillSky|menus\\.skills|performCommand\\(\"skill|chat\\(\"/skill");
    /** The windows themselves: their input handlers act only while their own window is open. */
    static final Set<String> WINDOWS = Set.of("SkillSky.java", "SkillMapMenu.java", "MenuSky.java", "AtlasSky.java", "Menus.java");
    static final Pattern HANDLER = Pattern.compile("@EventHandler[^\\n]*\\n\\s*public void (\\w+)\\(([\\w.]+) \\w+\\)\\s*\\{");

    static Path sources() {
        for (String p : new String[]{"src/main/java", "suld-plugin/src/main/java"}) {
            Path d = Path.of(p, "mn/suld/plugin");
            if (Files.isDirectory(d)) return d;
        }
        throw new IllegalStateException("plugin sources not found from " + Path.of("").toAbsolutePath());
    }

    record Handler(String file, String name, String event, String body) {
    }

    /** Every event handler of a source: its name, its event's simple name and its body (braces matched, strings skipped). */
    static List<Handler> handlers(Path file) throws IOException {
        String src = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
        List<Handler> out = new ArrayList<>();
        Matcher m = HANDLER.matcher(src);
        while (m.find()) {
            String event = m.group(2).substring(m.group(2).lastIndexOf('.') + 1);
            int depth = 1, i = m.end();
            while (i < src.length() && depth > 0) {
                char c = src.charAt(i);
                if (src.startsWith("//", i)) { // skip a comment ("the player's" is not a literal)
                    i = src.indexOf('\n', i);
                    if (i < 0) break;
                } else if (src.startsWith("/*", i)) {
                    i = src.indexOf("*/", i + 2) + 1;
                    if (i <= 0) break;
                } else if (c == '"' || c == '\'') { // skip a literal
                    i++;
                    while (i < src.length() && src.charAt(i) != c) i += src.charAt(i) == '\\' ? 2 : 1;
                } else if (c == '{') depth++;
                else if (c == '}') depth--;
                i++;
            }
            out.add(new Handler(file.getFileName().toString(), m.group(1), event, src.substring(m.end(), i)));
        }
        return out;
    }

    static List<Path> all() throws IOException {
        try (Stream<Path> s = Files.walk(sources())) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void noInputListenerCanReachTheSkillTree() throws IOException {
        List<String> bad = new ArrayList<>();
        int inputs = 0;
        for (Path f : all()) {
            if (WINDOWS.contains(f.getFileName().toString())) continue;
            List<Handler> hs = handlers(f);
            boolean listensToInput = hs.stream().anyMatch(h -> INPUT_EVENTS.contains(h.event()));
            if (!listensToInput) continue;
            inputs++;
            // the whole class, not only the handler: the old shortcut sat in a helper the click handlers called
            Matcher m = OPENS_TREE.matcher(Files.readString(f, StandardCharsets.UTF_8));
            if (m.find()) bad.add(f.getFileName() + " (" + m.group() + ")");
        }
        assertTrue(inputs > 10, "the scan found the input listeners (" + inputs + ")");
        assertEquals(List.of(), bad, "a class that listens to keys, clicks or moves can open the skill tree");
    }

    @Test
    void aClickWhileSneakingIsAPlainClick() throws IOException {
        Path f = sources().resolve("skill/SkillService.java");
        List<Handler> hs = handlers(f);
        for (String name : List.of("onInteract", "onInteractEntity", "onBowUse")) {
            Handler h = hs.stream().filter(x -> x.name().equals(name)).findFirst().orElseThrow(() -> new AssertionError(name + " not found"));
            assertFalse(h.body().contains("isSneaking"), name + " treats a sneaking click differently: " + h.body());
        }
        assertFalse(Files.readString(f, StandardCharsets.UTF_8).contains("mapShortcut"), "the sneak + right click shortcut is gone");
    }

    @Test
    void theTreeStillOpensFromItsCommandAndTheMenu() throws IOException {
        String commands = Files.readString(sources().resolve("command/SkillCommands.java"), StandardCharsets.UTF_8);
        assertTrue(commands.contains("map.open(p)"), "/skills opens the tree");
        String menus = Files.readString(sources().resolve("gui/Menus.java"), StandardCharsets.UTF_8);
        assertTrue(menus.contains("skillMap.open(pl)"), "the menu's «Ур чадвар» card opens it");
    }
}
