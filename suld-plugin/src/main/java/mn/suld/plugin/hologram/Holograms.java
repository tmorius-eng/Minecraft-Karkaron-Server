package mn.suld.plugin.hologram;

import mn.suld.api.hologram.HologramLabel;
import mn.suld.api.hologram.HologramLoader;
import mn.suld.api.hologram.HologramSet;
import mn.suld.api.hologram.HologramStyle;
import mn.suld.api.hologram.HologramText;
import mn.suld.plugin.branding.ContentCounts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The holograms of content/holograms.json (docs/CONTENT_DATA.md): read once at start and again with
 * {@code /suld holograms reload}; the server's copy in plugins/SULD/content/ wins over the bundled one, and a copy with
 * any problem is listed and not used. Every text display the game puts in the world takes its words, its look and its
 * height from here: the boards ({@link HologramService}) and the labels of gates, sites, the training yard, profession
 * nodes and soul beacons.
 */
public final class Holograms {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final List<String> COUNTS = List.of("{chapters}", "{dungeons}", "{regions}", "{areas}", "{sites}", "{relics}", "{campaigns}");
    private static volatile HologramSet set, bundled;

    private Holograms() {
    }

    /** The bundled file's text (the jar's content/holograms.json). */
    public static String bundledText() throws IOException {
        try (InputStream in = Holograms.class.getResourceAsStream("/content/" + HologramLoader.FILE)) {
            if (in == null) throw new IOException("content/" + HologramLoader.FILE + " is missing from the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static HologramSet bundledSet() {
        HologramSet b = bundled;
        if (b == null) {
            try {
                HologramLoader.Result r = HologramLoader.load(bundledText());
                if (!r.ok()) throw new IllegalStateException("the bundled holograms have problems: " + r.issues());
                b = r.set();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            bundled = b;
        }
        return b;
    }

    /** The server's file, else the bundled one, read and checked (nothing installed). */
    public static HologramLoader.Result read(Path contentDir) throws IOException {
        Path f = contentDir.resolve(HologramLoader.FILE);
        return HologramLoader.load(Files.isRegularFile(f) ? Files.readString(f, StandardCharsets.UTF_8) : bundledText());
    }

    /**
     * Uses the server's plugins/SULD/content/holograms.json when it has one and it is right, else the bundled file.
     * Returns the problems of the server's file (empty when it is right or there is none).
     */
    public static List<HologramLoader.Issue> install(Plugin plugin) {
        Path dir = plugin.getDataFolder().toPath().resolve("content");
        if (!Files.isRegularFile(dir.resolve(HologramLoader.FILE))) {
            set = bundledSet();
            return List.of();
        }
        try {
            HologramLoader.Result r = read(dir);
            if (r.ok()) {
                set = r.set();
                plugin.getLogger().info("[holograms] using the server's content/" + HologramLoader.FILE + " (" + r.set().boards().size() + " boards)");
                return List.of();
            }
            r.issues().forEach(i -> plugin.getLogger().warning("[holograms] " + i));
            plugin.getLogger().warning("[holograms] the server's file has " + r.issues().size() + " problem(s): the bundled holograms are shown");
            set = bundledSet();
            return r.issues();
        } catch (IOException e) {
            plugin.getLogger().warning("[holograms] content/" + HologramLoader.FILE + " not read (" + e.getMessage() + "): the bundled holograms are shown");
            set = bundledSet();
            return List.of(new HologramLoader.Issue("", "not read: " + e.getMessage()));
        }
    }

    /** The holograms in use (the bundled ones until {@link #install}). */
    public static HologramSet set() {
        HologramSet s = set;
        return s != null ? s : bundledSet();
    }

    /** A label the game uses ({@link HologramLoader#REQUIRED_LABELS}: always there). */
    public static HologramLabel label(String id) {
        return set().label(id).or(() -> bundledSet().label(id))
                .orElseThrow(() -> new IllegalArgumentException("no hologram label " + id));
    }

    /**
     * MiniMessage lines with the content counts ({@code {chapters}} …) and {@code values} filled in (values escaped),
     * list placeholders expanded, joined by new lines.
     */
    public static String mini(List<String> lines, Map<String, String> values, Map<String, List<Map<String, String>>> lists,
                              Map<String, String> items, Map<String, List<String>> headers) {
        List<String> filled = new ArrayList<>(lines.size());
        for (String l : lines) filled.add(counts(l));
        return String.join("\n", HologramText.render(filled, values, lists, items, headers, s -> MM.escapeTags(s)));
    }

    private static String counts(String line) {
        for (String k : COUNTS) if (line.contains(k)) return ContentCounts.fill(line);
        return line;
    }

    public static Component component(String mini) {
        return MM.deserialize(mini);
    }

    /** A label's text, its {@code variant} (null: the fixed text) with {@code values} filled in. */
    public static Component text(String labelId, String variant, Map<String, String> values) {
        return component(mini(label(labelId).lines(variant), values, Map.of(), Map.of(), Map.of()));
    }

    /** About how tall {@code mini} stands in blocks when shown in {@code style} (a board on top of it starts there). */
    public static double height(String mini, HologramStyle style) {
        String[] lines = mini.split("\n", -1);
        int[] px = new int[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String plain = PlainTextComponentSerializer.plainText().serialize(MM.deserialize(lines[i]));
            px[i] = HologramText.defaultFontWidth(plain, lines[i].contains("<bold>") || lines[i].contains("<b>"));
        }
        return HologramText.height(px, style);
    }

    /** Gives a text display the style's look. */
    public static void style(TextDisplay t, HologramStyle s) {
        t.setBillboard(switch (s.billboard()) {
            case CENTER -> Display.Billboard.CENTER;
            case VERTICAL -> Display.Billboard.VERTICAL;
            case HORIZONTAL -> Display.Billboard.HORIZONTAL;
            case FIXED -> Display.Billboard.FIXED;
        });
        t.setAlignment(switch (s.align()) {
            case CENTER -> TextDisplay.TextAlignment.CENTER;
            case LEFT -> TextDisplay.TextAlignment.LEFT;
            case RIGHT -> TextDisplay.TextAlignment.RIGHT;
        });
        t.setShadowed(s.shadow());
        t.setBackgroundColor(Color.fromARGB(s.background()));
        t.setLineWidth(s.lineWidth());
        t.setViewRange(s.viewRange());
        if (s.scale() != 1f) {
            t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s.scale(), s.scale(), s.scale()), new AxisAngle4f()));
        }
    }

    /**
     * A label's text display, its {@code dy} above {@code base}, styled and filled in; {@code setup} adds the caller's own
     * tags, persistence and visibility before it appears.
     */
    public static TextDisplay spawnLabel(Location base, String labelId, String variant, Map<String, String> values, Consumer<TextDisplay> setup) {
        HologramLabel l = label(labelId);
        Component text = component(mini(l.lines(variant), values, Map.of(), Map.of(), Map.of()));
        return base.getWorld().spawn(base.clone().add(0, l.dy(), 0), TextDisplay.class, t -> {
            style(t, l.style());
            t.text(text);
            setup.accept(t);
        });
    }
}
