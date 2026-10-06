package mn.suld.plugin.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Centralised Adventure message/branding helpers. Keeping colours and the chat
 * prefix in one place keeps the UI consistent and resource-pack friendly.
 */
public final class Messages {

    public static final TextColor BRAND = TextColor.fromHexString("#00ffd0");
    public static final Component PREFIX = Component.text("ᠰ SULD ", BRAND, TextDecoration.BOLD)
            .append(Component.text("» ", NamedTextColor.GRAY));

    private Messages() {
    }

    public static Component info(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.WHITE, TextDecoration.BOLD));
    }

    public static Component accent(String text) {
        return PREFIX.append(Component.text(text, BRAND, TextDecoration.BOLD));
    }

    public static Component success(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    public static Component error(String text) {
        return PREFIX.append(Component.text(text, NamedTextColor.RED, TextDecoration.BOLD));
    }
}
