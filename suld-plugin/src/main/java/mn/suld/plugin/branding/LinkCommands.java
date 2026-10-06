package mn.suld.plugin.branding;

import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** {@code /discord}, {@code /website}, {@code /vote}: clickable links from {@code branding:} in config.yml. */
public final class LinkCommands implements TabExecutor {

    private final Plugin plugin;

    public LinkCommands(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        String name = c.getName();
        String url = switch (name) {
            case "discord" -> plugin.getConfig().getString("branding.discord", "");
            case "vote" -> plugin.getConfig().getString("branding.vote-url", "");
            default -> {
                String d = plugin.getConfig().getString("branding.domain", "");
                yield d.isBlank() ? "" : (d.startsWith("http") ? d : "https://" + d);
            }
        };
        if (url == null || url.isBlank()) {
            s.sendMessage(Messages.info("Холбоос хараахан тохируулаагүй байна."));
            return true;
        }
        String label = switch (name) {
            case "discord" -> "Discord";
            case "vote" -> "Санал өгөх";
            default -> "Вэбсайт";
        };
        s.sendMessage(Component.text("✦ " + label + ": ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(url, NamedTextColor.AQUA, TextDecoration.BOLD, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return List.of();
    }
}
