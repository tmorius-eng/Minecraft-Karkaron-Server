package mn.suld.plugin.hud;

import mn.suld.api.clazz.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws the SÜLD HUD from a {@link HudState} with the {@code suld:hud} glyphs (pure: no Bukkit).
 *
 * <pre>
 *            [buff][buff][buff]                         [I][II][III][IV][✦]     ← spell slots / status icons
 *   ╔═══════ HP 184/200 ════════╗ (LV) ╔═════ resource 75/140 ═════╗           ← row A
 *   ╚═══ food / mount / air ════╝ (12) ╚═════ EXP 57% ══════════════╝          ← row B
 *   ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔ hotbar ▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔
 * </pre>
 * The text line (status chips, a notice or the combo in progress) sits above, at the vanilla action-bar height.
 */
public final class HudComposer {

    /** The panel canvas: x in [-128, 128] around the screen centre. */
    public static final int HALF = 128;
    /** The target frame canvas (boss bar). */
    public static final int TARGET_HALF = 96;
    public static final int MAX_BUFFS = 6;
    static final int TARGET_INNER = HudGlyphs.TARGET_W - 2;

    static final TextColor WHITE = TextColor.color(0xFFFFFF);
    static final TextColor GOLD = TextColor.color(0xFFD24A);
    static final TextColor GREY = TextColor.color(0x9A9A9A);
    static final TextColor RED = TextColor.color(0xFF6B6B);

    private HudComposer() {
    }

    /** Fill width in px of {@code value / max} over {@code inner} px (never negative, never past the end). */
    public static int px(double value, double max, int inner) {
        if (max <= 0 || value <= 0) return 0;
        return (int) Math.min(inner, Math.round(value / max * inner));
    }

    static String n(double v) {
        long r = Math.round(Math.max(0, v));
        if (r >= 100_000) return (r / 1000) + "k";
        if (r >= 10_000) return String.format(java.util.Locale.ROOT, "%.1fk", r / 1000.0);
        return Long.toString(r);
    }

    private static HudGlyphs.G[] resourcePieces(@Nullable PlayerClass c) {
        if (c == null) return HudGlyphs.FILL_RES_KHULEGCHIN;
        return switch (c) {
            case BAATAR -> HudGlyphs.FILL_RES_BAATAR;
            case MERGEN -> HudGlyphs.FILL_RES_MERGEN;
            case BOO -> HudGlyphs.FILL_RES_BOO;
            case DARKHAN -> HudGlyphs.FILL_RES_DARKHAN;
            case KHULEGCHIN -> HudGlyphs.FILL_RES_KHULEGCHIN;
        };
    }

    /** The colour of a class's resource (bar and numerals). */
    public static TextColor resourceColor(@Nullable PlayerClass c) {
        if (c == null) return WHITE;
        return switch (c) {
            case BAATAR -> TextColor.color(0xFF6A30);
            case MERGEN -> TextColor.color(0x7CE07C);
            case BOO -> TextColor.color(0xB06BFF);
            case DARKHAN -> TextColor.color(0xFF9A3C);
            case KHULEGCHIN -> TextColor.color(0x5AAFFF);
        };
    }

    /** Bars run from |x| = SIDE_X (hidden under the diamond) outwards; the diamond's edges cut their inner ends. */
    static final int LEFT = -HudGlyphs.SIDE_X - HudGlyphs.BAR_W;
    static final int RIGHT = HudGlyphs.SIDE_X;
    /** Centre of the visible part of a bar (the diamond hides about 8 px of its inner end). */
    static final int LABEL_X = (HudGlyphs.BAR_W + HudGlyphs.SIDE_X + 8) / 2;

    /** The whole bottom panel for the action bar. {@code pulse} alternates every refresh (low-health warning). */
    public static Component panel(HudState s, boolean pulse) {
        HudCanvas c = new HudCanvas(HALF);
        int inner = HudGlyphs.BAR_W - 2;

        // row A left: health (+ absorption, + the pale chip of recent damage); fills grow from the outer end
        boolean low = s.maxHp() > 0 && s.hp() / s.maxHp() < 0.25;
        c.glyphAt(LEFT, low && pulse ? HudGlyphs.FRAME_A_L_LOW : HudGlyphs.FRAME_A_L);
        int hpW = px(s.hp(), s.maxHp(), inner);
        int chipW = px(Math.max(s.recentHp(), s.hp()), s.maxHp(), inner);
        int absW = px(s.absorption(), s.maxHp(), inner);
        c.at(LEFT + 1).fill(HudGlyphs.FILL_HP, hpW);
        if (chipW > hpW) c.at(LEFT + 1 + hpW).fill(HudGlyphs.FILL_HP_CHIP, chipW - hpW);
        if (absW > 0) c.at(LEFT + 1 + Math.min(hpW, inner - absW)).fill(HudGlyphs.FILL_ABSORB, absW);
        label(c, HudGlyphs.ROW_A, HudGlyphs.BI_A_HEART, -LABEL_X,
                n(s.hp()) + (s.absorption() >= 0.5 ? "+" + n(s.absorption()) : "") + "/" + n(s.maxHp()));

        // row A right: class resource (fills from the outer end, mirrored)
        c.glyphAt(RIGHT, HudGlyphs.FRAME_A_R);
        if (s.clazz() != null) {
            int w = px(s.resource(), s.resourceMax(), inner);
            c.at(RIGHT + 1 + inner - w).fill(resourcePieces(s.clazz()), w);
            label(c, HudGlyphs.ROW_A, HudGlyphs.BI_A_RES, LABEL_X, s.resource() + "/" + s.resourceMax());
        }

        // row B left: food / mount / breath
        c.glyphAt(LEFT, HudGlyphs.FRAME_B_L);
        HudState.LowBar lb = s.lowBar();
        HudGlyphs.G[] lowPieces = switch (lb.kind()) {
            case FOOD -> HudGlyphs.FILL_FOOD;
            case MOUNT -> HudGlyphs.FILL_MOUNT;
            case AIR -> HudGlyphs.FILL_AIR;
        };
        HudGlyphs.G lowIcon = switch (lb.kind()) {
            case FOOD -> HudGlyphs.BI_B_FOOD;
            case MOUNT -> HudGlyphs.BI_B_HORSE;
            case AIR -> HudGlyphs.BI_B_AIR;
        };
        c.at(LEFT + 1).fill(lowPieces, px(lb.value(), lb.max(), inner));
        label(c, HudGlyphs.ROW_B, lowIcon, -LABEL_X, n(lb.value()) + "/" + n(lb.max()));

        // row B right: EXP into the level
        c.glyphAt(RIGHT, HudGlyphs.FRAME_B_R);
        int ew = s.maxLevel() ? inner : px(s.expFraction(), 1, inner);
        c.at(RIGHT + 1 + inner - ew).fill(HudGlyphs.FILL_EXP, ew);
        label(c, HudGlyphs.ROW_B, HudGlyphs.BI_B_EXP, LABEL_X, s.maxLevel() ? "MAX" : (int) Math.floor(s.expFraction() * 100 + 1e-9) + "%");

        // the cartouche and the level diamond over the middle (together they hide the vanilla level number)
        c.glyphAt(-HudGlyphs.PLAQUE_W / 2, HudGlyphs.PLAQUE);
        c.glyphAt(-HudGlyphs.DIAMOND_W / 2, HudGlyphs.DIAMOND);
        c.centred(HudGlyphs.LEVEL, 0, Integer.toString(s.level()), GOLD);

        slots(c, s.slots(), RIGHT + HudGlyphs.BAR_W);
        buffs(c, s.buffs(), LEFT);
        line(c, s.line());
        return c.build();
    }

    /** "♥ 184/200": icon then number, the pair centred on x. */
    static void label(HudCanvas c, HudGlyphs.CharSet set, HudGlyphs.G icon, int x, String text) {
        int w = icon.advance() + 1 + HudCanvas.width(set, text);
        c.at(x - w / 2).glyph(icon);
        c.at(c.pen() + 1).text(set, text, WHITE);
    }

    static void slots(HudCanvas c, List<HudState.Slot> slots, int endX) {
        int n = slots.size();
        int x = endX - n * HudGlyphs.SLOT_W - (n - 1) * 2;
        for (HudState.Slot sl : slots) {
            HudGlyphs.G base = switch (sl.state()) {
                case READY -> sl.numeral() == 0 ? HudGlyphs.SLOT_ULT : HudGlyphs.SLOT_READY;
                case COOLDOWN -> HudGlyphs.SLOT_CD;
                case LOCKED -> HudGlyphs.SLOT_LOCKED;
                case NO_RESOURCE -> HudGlyphs.SLOT_NORES;
            };
            c.glyphAt(x, base);
            int mid = x + HudGlyphs.SLOT_W / 2;
            switch (sl.state()) {
                case COOLDOWN -> c.centred(HudGlyphs.SLOT, mid, seconds(sl.seconds()), WHITE);
                // the level that unlocks it (10, 20, 35) when known, else a padlock
                case LOCKED -> {
                    if (sl.seconds() >= 1) c.centred(HudGlyphs.SLOT, mid, Integer.toString((int) sl.seconds()), GREY);
                    else centredGlyph(c, mid, HudGlyphs.NUM_LOCK, GREY);
                }
                case NO_RESOURCE -> centredGlyph(c, mid, numeral(sl.numeral()), RED);
                case READY -> centredGlyph(c, mid, numeral(sl.numeral()), sl.numeral() == 0 ? GOLD : sl.color());
            }
            x += HudGlyphs.SLOT_W + 2;
        }
    }

    static String seconds(double s) {
        if (s >= 60) return (int) Math.ceil(s / 60) + "M";
        if (s >= 1) return Integer.toString((int) Math.ceil(s));
        int tenths = Math.max(1, (int) Math.ceil(s * 10));
        return tenths >= 10 ? "1" : "." + tenths;
    }

    private static HudGlyphs.G numeral(int k) {
        return switch (k) {
            case 1 -> HudGlyphs.NUM_I;
            case 2 -> HudGlyphs.NUM_II;
            case 3 -> HudGlyphs.NUM_III;
            case 4 -> HudGlyphs.NUM_IV;
            default -> HudGlyphs.NUM_ULT;
        };
    }

    private static void centredGlyph(HudCanvas c, int mid, HudGlyphs.G g, TextColor tint) {
        c.at(mid - (g.advance() - 1) / 2).glyph(g, tint);
    }

    static void buffs(HudCanvas c, List<HudState.Buff> buffs, int startX) {
        int x = startX;
        int shown = 0;
        for (HudState.Buff b : buffs) {
            if (shown++ >= MAX_BUFFS) break;
            HudGlyphs.G g = icon(b.icon());
            if (g == null) continue;
            c.glyphAt(x, g);
            x += 10;
            if (b.label() != null && !b.label().isEmpty()) {
                c.at(x).text(HudGlyphs.BUFF, b.label(), WHITE);
                x = c.pen();
            }
            x += 3;
        }
    }

    /** The icon glyph called {@code ICON_<name>}, or null. */
    static HudGlyphs.G icon(String name) {
        try {
            return (HudGlyphs.G) HudGlyphs.class.getField("ICON_" + name).get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Only the characters the pixel font can draw (the rest, e.g. emoji, are dropped), upper-cased. */
    static String drawable(String text) {
        StringBuilder sb = new StringBuilder();
        for (char ch : HudCanvas.fontCase(text).toCharArray()) if (HudGlyphs.LINE.indexOf(ch) >= 0) sb.append(ch);
        return sb.toString();
    }

    /**
     * The text line, centred. Leading/trailing blanks are trimmed; a line wider than the canvas is cut at the last
     * word that fits and ends with "..".
     */
    static void line(HudCanvas c, List<HudState.Run> runs) {
        int max = 2 * HALF - 4;
        List<HudState.Run> clean = new java.util.ArrayList<>();
        for (HudState.Run r : runs) clean.add(new HudState.Run(drawable(r.text()), r.color()));
        // trim the outer blanks of the whole line
        while (!clean.isEmpty() && clean.get(0).text().isBlank()) clean.remove(0);
        if (!clean.isEmpty()) clean.set(0, new HudState.Run(clean.get(0).text().stripLeading(), clean.get(0).color()));
        if (!clean.isEmpty()) {
            HudState.Run last = clean.get(clean.size() - 1);
            clean.set(clean.size() - 1, new HudState.Run(last.text().stripTrailing(), last.color()));
        }
        StringBuilder all = new StringBuilder();
        for (HudState.Run r : clean) all.append(r.text());
        String full = all.toString();
        String shown = full;
        if (HudCanvas.width(HudGlyphs.LINE, full) > max) {
            int ell = HudCanvas.width(HudGlyphs.LINE, "..");
            int cut = 0, used = 0, lastSpace = -1;
            while (cut < full.length()) {
                int cw = HudCanvas.width(HudGlyphs.LINE, String.valueOf(full.charAt(cut)));
                if (used + cw + ell > max) break;
                if (full.charAt(cut) == ' ') lastSpace = cut;
                used += cw;
                cut++;
            }
            int end = lastSpace > 0 ? lastSpace : cut;
            shown = full.substring(0, end).stripTrailing() + "..";
        }
        c.at(-HudCanvas.width(HudGlyphs.LINE, shown) / 2);
        int pos = 0;
        for (HudState.Run r : clean) {
            if (pos >= shown.length()) break;
            int take = Math.min(r.text().length(), Math.max(0, Math.min(shown.length(), full.length()) - pos));
            String part = shown.substring(pos, Math.min(shown.length(), pos + take));
            c.text(HudGlyphs.LINE, part, r.color());
            pos += part.length();
        }
        if (pos < shown.length()) c.text(HudGlyphs.LINE, shown.substring(pos), clean.isEmpty() ? WHITE : clean.get(clean.size() - 1).color());
    }

    // ------------------------------------------------------------------ target frame

    public enum Tier { NORMAL, ELITE, BOSS }

    /** What the target frame shows. */
    public record Target(String name, int level, double hp, double maxHp, double recentHp, Tier tier, boolean hostile) {
    }

    /** The target frame as a boss-bar title: name line, then the frame with the health fill and numbers. */
    public static Component target(Target t) {
        HudCanvas c = new HudCanvas(TARGET_HALF);
        int x0 = -HudGlyphs.TARGET_W / 2;
        TextColor nameColor = t.tier() == Tier.BOSS ? GOLD : t.tier() == Tier.ELITE ? TextColor.color(0xC4CCD6)
                : t.hostile() ? TextColor.color(0xFFB0A0) : WHITE;
        String lv = t.level() > 0 ? "LV " + t.level() + "  " : "";
        line(c, List.of(new HudState.Run(lv, GOLD), new HudState.Run(t.name(), nameColor)));
        HudGlyphs.G frame = switch (t.tier()) {
            case BOSS -> HudGlyphs.TGT_FRAME_BOSS;
            case ELITE -> HudGlyphs.TGT_FRAME_ELITE;
            case NORMAL -> HudGlyphs.TGT_FRAME;
        };
        c.glyphAt(x0, frame);
        int hpW = px(t.hp(), t.maxHp(), TARGET_INNER);
        int chipW = px(Math.max(t.recentHp(), t.hp()), t.maxHp(), TARGET_INNER);
        c.at(x0 + 1).fill(HudGlyphs.FILL_TGT, hpW);
        if (chipW > hpW) c.at(x0 + 1 + hpW).fill(HudGlyphs.FILL_TGT_CHIP, chipW - hpW);
        c.centred(HudGlyphs.TARGET, 0, n(t.hp()) + "/" + n(t.maxHp()), WHITE);
        return c.build();
    }
}
