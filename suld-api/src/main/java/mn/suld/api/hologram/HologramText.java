package mn.suld.api.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills a hologram's template lines (MiniMessage, from content/holograms.json) with live values. Pure: the plugin turns
 * the result into a Component.
 */
public final class HologramText {

    private static final Pattern KEY = Pattern.compile("\\{([a-z_]+)}");

    private HologramText() {
    }

    /**
     * The lines with every {@code {key}} of {@code values} filled in, each value passed through {@code escape} (a
     * player's name never becomes a tag). A line that is only {@code {list}} for a key of {@code lists} becomes the
     * list's header lines and one line per entry ({@code items} gives the entry's template, its own keys filled the
     * same way); an empty list leaves nothing, header included. Unknown keys stay as they are.
     */
    public static List<String> render(List<String> lines, Map<String, String> values, Map<String, List<Map<String, String>>> lists,
                                      Map<String, String> items, Map<String, List<String>> headers, UnaryOperator<String> escape) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String key = listKey(line);
            if (key != null && lists.containsKey(key)) {
                List<Map<String, String>> entries = lists.get(key);
                if (entries.isEmpty()) continue;
                for (String h : headers.getOrDefault(key, List.of())) out.add(fill(h, values, escape));
                String item = items.getOrDefault(key, "{" + key + "}");
                for (Map<String, String> e : entries) out.add(fill(item, e, escape));
                continue;
            }
            out.add(fill(line, values, escape));
        }
        return out;
    }

    /** The list key of a line that is only {@code {key}}, else null. */
    static String listKey(String line) {
        Matcher m = KEY.matcher(line.strip());
        return m.matches() ? m.group(1) : null;
    }

    /** {@code {key}} → the escaped value, for every key {@code values} has. */
    public static String fill(String line, Map<String, String> values, UnaryOperator<String> escape) {
        if (line.indexOf('{') < 0 || values.isEmpty()) return line;
        Matcher m = KEY.matcher(line);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(b, Matcher.quoteReplacement(v == null ? m.group() : escape.apply(v)));
        }
        m.appendTail(b);
        return b.toString();
    }

    /**
     * About how tall a text display showing {@code plainLines} is, in blocks: a line is 10 pixels and a pixel 1/40
     * block at scale 1, each line wraps at the style's line width ({@code width}: a line's pixels), and the background
     * adds a pixel above and below.
     */
    public static double height(List<String> plainLines, HologramStyle style, ToIntFunction<String> width) {
        int[] px = new int[plainLines.size()];
        for (int i = 0; i < px.length; i++) px[i] = width.applyAsInt(plainLines.get(i));
        return height(px, style);
    }

    /** {@link #height(List, HologramStyle, ToIntFunction)} from each line's width in pixels. */
    public static double height(int[] linePixels, HologramStyle style) {
        int rows = 0;
        for (int w : linePixels) rows += Math.max(1, (int) Math.ceil(w / (double) Math.max(1, style.lineWidth())));
        return (rows * 10 + 2) * 0.025 * style.scale();
    }

    /**
     * A vanilla font line's width in pixels, near enough for wrapping: 6 a letter (5 and the gap), 4 for a space and the
     * narrow marks, 7 for the wide Cyrillic letters; bold adds one a letter.
     */
    public static int defaultFontWidth(String plain, boolean bold) {
        int px = 0;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            px += switch (c) {
                case ' ', '.', ',', ':', ';', '\'', '!', '|', 'i', 'l' -> 3;
                case 'ж', 'ш', 'щ', 'ы', 'ю', 'м', 'Ж', 'Ш', 'Щ', 'Ы', 'Ю', 'М', 'Д', 'Ф', 'ф' -> 7;
                default -> 6;
            } + (bold ? 1 : 0);
        }
        return px;
    }
}
