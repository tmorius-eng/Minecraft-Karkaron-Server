package mn.suld.plugin.ui;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.CosmeticCatalog;
import mn.suld.api.style.PlayerStyle;
import mn.suld.api.style.Rank;
import mn.suld.api.style.StaffBadge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * How a player looks in chat, the TAB list and above the head: staff badge, rank badge, coloured name, tag.
 * Badges are pixel glyphs from the resource pack ({@link Glyphs}); a viewer without the pack gets the same
 * information as coloured text, so nothing turns into empty boxes.
 */
public final class StyleFormat {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private StyleFormat() {
    }

    /**
     * Parts side by side. Always build glyph + text lines with this (or an empty root): text appended <em>to</em> a
     * glyph component is its child and inherits the pixel font, which has no letters or digits (shown as hex boxes).
     */
    public static Component join(Component... parts) {
        Component out = Component.empty();
        for (Component part : parts) out = out.append(part);
        return out;
    }

    /** A pack glyph, untinted and without the text shadow. */
    public static Component glyph(String glyph) {
        return Component.text(glyph).font(Glyphs.FONT).color(NamedTextColor.WHITE).shadowColor(ShadowColor.none());
    }

    /** Horizontal shift by {@code px} (negative = left) using the pack's space glyphs. */
    public static Component shift(int px) {
        StringBuilder sb = new StringBuilder();
        int left = Math.abs(px);
        for (int n = 128; n >= 1; n /= 2) {
            while (left >= n) {
                sb.append(glyphFor(px < 0, n));
                left -= n;
            }
        }
        return Component.text(sb.toString()).font(Glyphs.FONT);
    }

    private static String glyphFor(boolean neg, int n) {
        return switch (n) {
            case 128 -> neg ? Glyphs.NEG_128 : Glyphs.POS_128;
            case 64 -> neg ? Glyphs.NEG_64 : Glyphs.POS_64;
            case 32 -> neg ? Glyphs.NEG_32 : Glyphs.POS_32;
            case 16 -> neg ? Glyphs.NEG_16 : Glyphs.POS_16;
            case 8 -> neg ? Glyphs.NEG_8 : Glyphs.POS_8;
            case 4 -> neg ? Glyphs.NEG_4 : Glyphs.POS_4;
            case 2 -> neg ? Glyphs.NEG_2 : Glyphs.POS_2;
            default -> neg ? Glyphs.NEG_1 : Glyphs.POS_1;
        };
    }

    public static TextColor hex(String hex) {
        TextColor c = TextColor.fromHexString(hex);
        return c == null ? NamedTextColor.WHITE : c;
    }

    /**
     * The highest staff/supporter badge the player has: permission {@code suld.badge.<id>} (default false, so
     * operators do not inherit every badge); an operator without one shows ADMIN.
     */
    public static Optional<StaffBadge> staffOf(Player p) {
        for (StaffBadge b : StaffBadge.values()) if (p.hasPermission(b.permission())) return Optional.of(b);
        return p.isOp() ? Optional.of(StaffBadge.ADMIN) : Optional.empty();
    }

    private static final Map<Rank, String> RANK_GLYPH = Map.of(
            Rank.ARD, Glyphs.BADGE_RANK_ARD, Rank.TSEREG, Glyphs.BADGE_RANK_TSEREG, Rank.ARAVT, Glyphs.BADGE_RANK_ARAVT,
            Rank.ZUUT, Glyphs.BADGE_RANK_ZUUT, Rank.MYANGAT, Glyphs.BADGE_RANK_MYANGAT, Rank.TUMEN, Glyphs.BADGE_RANK_TUMEN,
            Rank.NOYON, Glyphs.BADGE_RANK_NOYON, Rank.KHAAN, Glyphs.BADGE_RANK_KHAAN);

    private static final Map<StaffBadge, String> STAFF_GLYPH = Map.of(
            StaffBadge.OWNER, Glyphs.BADGE_STAFF_OWNER, StaffBadge.ADMIN, Glyphs.BADGE_STAFF_ADMIN,
            StaffBadge.DEVELOPER, Glyphs.BADGE_STAFF_DEVELOPER, StaffBadge.MOD, Glyphs.BADGE_STAFF_MOD,
            StaffBadge.HELPER, Glyphs.BADGE_STAFF_HELPER, StaffBadge.STREAMER, Glyphs.BADGE_STAFF_STREAMER,
            StaffBadge.SPONSOR, Glyphs.BADGE_STAFF_SPONSOR);

    private static final Map<PlayerClass, String> CLASS_GLYPH = Map.of(
            PlayerClass.BAATAR, Glyphs.BADGE_CLASS_BAATAR, PlayerClass.MERGEN, Glyphs.BADGE_CLASS_MERGEN,
            PlayerClass.BOO, Glyphs.BADGE_CLASS_BOO, PlayerClass.DARKHAN, Glyphs.BADGE_CLASS_DARKHAN,
            PlayerClass.KHULEGCHIN, Glyphs.BADGE_CLASS_KHULEGCHIN);

    public static Component rankBadge(Rank r, boolean pack) {
        return pack ? glyph(RANK_GLYPH.get(r))
                : Component.text(r.badgeLabel(), hex(r.color()), TextDecoration.BOLD);
    }

    public static Component staffBadge(StaffBadge b, boolean pack) {
        return pack ? glyph(STAFF_GLYPH.get(b))
                : Component.text(b.badgeLabel(), hex(b.color()), TextDecoration.BOLD);
    }

    public static Component classBadge(PlayerClass c, boolean pack) {
        return pack ? glyph(CLASS_GLYPH.get(c)) : Component.text(c.displayName().toUpperCase(Locale.ROOT), NamedTextColor.GRAY);
    }

    /** {@code [staff] [rank] } — always ends with a space. */
    public static Component badges(Player p, PlayerStyle s, boolean pack) {
        Component out = Component.empty();
        Optional<StaffBadge> staff = staffOf(p);
        if (staff.isPresent()) out = out.append(staffBadge(staff.get(), pack)).append(Component.text(" "));
        return out.append(rankBadge(s.rank(), pack)).append(Component.text(" "));
    }

    /** The player name in their equipped name colour (staff default to their badge colour, others white). */
    public static Component name(Player p, PlayerStyle s) {
        Optional<Cosmetic> nc = s.equipped(Cosmetic.Category.NAME_COLOR);
        if (nc.isPresent()) return MM.deserialize(nc.get().style().replace("{}", "<n>"), Placeholder.unparsed("n", p.getName()));
        TextColor color = staffOf(p).filter(StaffBadge::staff).map(b -> hex(b.color())).orElse(NamedTextColor.WHITE);
        return Component.text(p.getName(), color);
    }

    public static Optional<Component> tag(PlayerStyle s) {
        return s.equipped(Cosmetic.Category.TAG).map(c -> MM.deserialize(c.style()));
    }

    /** A chat message in the player's chat colour, with their owned emojis turned into icons. */
    public static Component message(PlayerStyle s, String plain, boolean viewerHasPack) {
        Optional<Cosmetic> cc = s.equipped(Cosmetic.Category.CHAT_COLOR);
        Component msg = cc.isPresent()
                ? MM.deserialize(cc.get().style().replace("{}", "<m>"), Placeholder.unparsed("m", plain))
                : Component.text(plain, NamedTextColor.WHITE);
        if (!viewerHasPack) return msg;
        for (Cosmetic e : CosmeticCatalog.of(Cosmetic.Category.EMOJI)) {
            if (!s.owns(e.id()) || !plain.contains(e.style())) continue;
            String icon = switch (e.id()) {
                case "emoji.zurkh" -> Glyphs.ICON_HEART;
                case "emoji.zoos" -> Glyphs.ICON_COIN;
                case "emoji.od" -> Glyphs.ICON_STAR;
                case "emoji.ild" -> Glyphs.ICON_SWORD;
                case "emoji.ulzii" -> Glyphs.ICON_ULZII;
                case "emoji.guul" -> Glyphs.ICON_SKULL;
                default -> null;
            };
            if (icon == null) continue;
            msg = msg.replaceText(TextReplacementConfig.builder().match(Pattern.quote(e.style())).replacement(glyph(icon)).build());
        }
        return msg;
    }

    public static Component mini(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static Component joinMessage(Cosmetic c, String playerName) {
        return MM.deserialize(c.style().replace("{name}", "<n>"), Placeholder.unparsed("n", playerName));
    }
}
