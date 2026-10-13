package mn.suld.api.map;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A player's own pin on the map (V26, docs/WORLD_MAP.md): a named place they chose, in a colour of their choice. Pins
 * are the player's alone (never shown to others) and live in the overworld.
 *
 * @param id        the pin's id
 * @param owner     the player
 * @param name      1 … {@link #MAX_NAME} characters, already cleaned ({@link #clean})
 * @param x         block x
 * @param y         block y (where it was set; the guide's label floats there)
 * @param z         block z
 * @param colour    one of {@link #COLOURS}
 * @param createdAt epoch ms
 */
public record MapPin(UUID id, UUID owner, String name, int x, int y, int z, String colour, long createdAt) {

    public static final int MAX_NAME = 24;
    /** How many pins a player keeps. */
    public static final int MAX_PINS = 30;
    /** The pin colours (the atlas's pin icons come in these). */
    public static final java.util.List<String> COLOURS = java.util.List.of("green", "red", "blue", "gold", "white");

    public MapPin {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        if (name == null || name.isEmpty() || name.length() > MAX_NAME || !name.equals(clean(name))) throw new IllegalArgumentException("pin name «" + name + "»");
        if (!COLOURS.contains(colour)) throw new IllegalArgumentException("pin colour " + colour);
    }

    /**
     * A name a player typed, made safe to show: no colour codes, no control or formatting characters, single spaces,
     * at most {@link #MAX_NAME} characters; null when nothing is left.
     */
    public static String clean(String raw) {
        if (raw == null) return null;
        StringBuilder b = new StringBuilder();
        boolean space = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§' || cp == '&' && i < raw.length() && "0123456789abcdefklmnorx".indexOf(Character.toLowerCase(raw.charAt(i))) >= 0) {
                if (i < raw.length()) i += Character.charCount(raw.codePointAt(i)); // drop the code and its letter
                continue;
            }
            int type = Character.getType(cp);
            if (Character.isISOControl(cp) || type == Character.FORMAT || type == Character.PRIVATE_USE || type == Character.SURROGATE
                    || type == Character.UNASSIGNED) continue;
            if (Character.isWhitespace(cp)) {
                space = b.length() > 0;
                continue;
            }
            if (space) b.append(' ');
            space = false;
            b.appendCodePoint(cp);
            if (b.codePointCount(0, b.length()) >= MAX_NAME) break;
        }
        String s = b.toString();
        return s.isEmpty() ? null : s;
    }

    /** The next free default name: «Тэмдэг 1», «Тэмдэг 2» … */
    public static String defaultName(java.util.Collection<MapPin> existing) {
        for (int n = 1; ; n++) {
            String name = "Тэмдэг " + n;
            boolean taken = false;
            for (MapPin p : existing) if (p.name().equalsIgnoreCase(name)) taken = true;
            if (!taken) return name;
        }
    }

    /** The pin a player means by {@code what}: its number in the list (1 …) or its name (any case). */
    public static java.util.Optional<MapPin> find(java.util.List<MapPin> pins, String what) {
        if (what == null) return java.util.Optional.empty();
        String w = what.strip();
        try {
            int n = Integer.parseInt(w);
            if (n >= 1 && n <= pins.size()) return java.util.Optional.of(pins.get(n - 1));
        } catch (NumberFormatException ignored) {
            // a name
        }
        for (MapPin p : pins) if (p.name().toLowerCase(Locale.ROOT).equals(w.toLowerCase(Locale.ROOT))) return java.util.Optional.of(p);
        return java.util.Optional.empty();
    }
}
