package mn.suld.plugin.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/**
 * A pen that lays glyphs of the {@code suld:hud} font out on a horizontal line, to the pixel. The line spans
 * {@code [-half, +half]} GUI pixels around the screen centre: the pen starts at {@code -half} and {@link #build()}
 * moves it to {@code +half}, so the whole line is always {@code 2 * half} wide and the client (which centres
 * action-bar and boss-bar text) puts canvas x = 0 exactly at the screen centre, whatever was drawn.
 * Vertical placement comes from the glyphs themselves (each set has its own ascent in the font).
 */
public final class HudCanvas {

    private final int half;
    private int pen;
    private final TextComponent.Builder out = Component.text();
    private final StringBuilder run = new StringBuilder();
    private TextColor runColor = NamedTextColor.WHITE;

    public HudCanvas(int half) {
        this.half = half;
        this.pen = -half;
    }

    public int pen() {
        return pen;
    }

    /** Move the pen to absolute x. */
    public HudCanvas at(int x) {
        int d = x - pen;
        if (d == 0) return this;
        int left = Math.abs(d);
        for (int k = 7; k >= 0; k--) {
            int n = 1 << k;
            while (left >= n) {
                emit(spaceGlyph(d < 0, n), NamedTextColor.WHITE);
                left -= n;
            }
        }
        pen = x;
        return this;
    }

    private static String spaceGlyph(boolean neg, int n) {
        return switch (n) {
            case 1 -> neg ? HudGlyphs.NEG_1.ch() : HudGlyphs.POS_1.ch();
            case 2 -> neg ? HudGlyphs.NEG_2.ch() : HudGlyphs.POS_2.ch();
            case 4 -> neg ? HudGlyphs.NEG_4.ch() : HudGlyphs.POS_4.ch();
            case 8 -> neg ? HudGlyphs.NEG_8.ch() : HudGlyphs.POS_8.ch();
            case 16 -> neg ? HudGlyphs.NEG_16.ch() : HudGlyphs.POS_16.ch();
            case 32 -> neg ? HudGlyphs.NEG_32.ch() : HudGlyphs.POS_32.ch();
            case 64 -> neg ? HudGlyphs.NEG_64.ch() : HudGlyphs.POS_64.ch();
            default -> neg ? HudGlyphs.NEG_128.ch() : HudGlyphs.POS_128.ch();
        };
    }

    /** Draw a glyph at the pen (untinted); the pen advances by its advance. */
    public HudCanvas glyph(HudGlyphs.G g) {
        return glyph(g, NamedTextColor.WHITE);
    }

    public HudCanvas glyph(HudGlyphs.G g, TextColor tint) {
        emit(g.ch(), tint);
        pen += g.advance();
        return this;
    }

    /** Draw a glyph with its left edge at x, then leave the pen where the glyph ends. */
    public HudCanvas glyphAt(int x, HudGlyphs.G g) {
        return at(x).glyph(g);
    }

    /**
     * A bar fill {@code width} px wide from the pen, from power-of-two pieces (each piece's 1 px advance gap is
     * taken back, so the pieces butt together). {@code pieces[k]} is {@code 2^k} px wide.
     */
    public HudCanvas fill(HudGlyphs.G[] pieces, int width) {
        int w = Math.max(0, width);
        int max = (1 << pieces.length) - 1;
        w = Math.min(w, max);
        for (int k = pieces.length - 1; k >= 0; k--) {
            if ((w & (1 << k)) == 0) continue;
            glyph(pieces[k]);
            at(pen - 1);
        }
        return this;
    }

    /** Width in px of {@code text} in a char set (unknown characters are skipped). */
    public static int width(HudGlyphs.CharSet set, String text) {
        int w = 0;
        for (char c : text.toCharArray()) {
            int i = set.indexOf(c);
            if (i >= 0) w += set.advances()[i];
        }
        return w;
    }

    /** Text in a char set from the pen, tinted. */
    public HudCanvas text(HudGlyphs.CharSet set, String text, TextColor color) {
        for (char c : text.toCharArray()) {
            int i = set.indexOf(c);
            if (i < 0) continue;
            emit(String.valueOf(set.chars().charAt(i)), color);
            pen += set.advances()[i];
        }
        return this;
    }

    /** Text centred on x. */
    public HudCanvas centred(HudGlyphs.CharSet set, int x, String text, TextColor color) {
        return at(x - width(set, text) / 2).text(set, text, color);
    }

    /** Text right-aligned so it ends at x. */
    public HudCanvas rightAligned(HudGlyphs.CharSet set, int x, String text, TextColor color) {
        return at(x - width(set, text)).text(set, text, color);
    }

    /** Upper-case text for the pixel font (it has capitals only); characters it cannot draw are dropped. */
    public static String fontCase(String s) {
        return s.toUpperCase(Locale.ROOT).replace('Ё', 'Е');
    }

    private void emit(String s, TextColor color) {
        if (!color.equals(runColor) && !run.isEmpty()) flush();
        runColor = color;
        run.append(s);
    }

    private void flush() {
        if (run.isEmpty()) return;
        out.append(Component.text(run.toString()).font(HudGlyphs.FONT).color(runColor).shadowColor(ShadowColor.none()));
        run.setLength(0);
    }

    /** End the line at +half and return it. */
    public Component build() {
        at(half);
        flush();
        return out.build();
    }
}
