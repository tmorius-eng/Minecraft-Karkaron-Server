package mn.suld.plugin.branding;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.CachedServerIcon;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;

/**
 * The server-list entry: a two-line MiniMessage MOTD ({@code branding.motd}) and the SÜLD icon (the bundled
 * {@code server-icon.png}, used only when the server folder has no icon of its own and {@code branding.server-icon}
 * is on).
 */
public final class ServerListService implements Listener {

    private static final List<String> DEFAULT_MOTD = List.of(
            "<bold><gradient:#FFD24A:#FF9E2C>ᠰ SÜLD</gradient></bold> <white><bold>· Монгол Hardcore MMORPG</bold></white>",
            "<white><bold>Хархорум</bold></white> <#FFD24A>·</#FFD24A> <white><bold>5 анги</bold></white> <#FFD24A>·</#FFD24A> <white><bold>18 бүлэг эрэл</bold></white> <#FFD24A>·</#FFD24A> <white><bold>4 агуй</bold></white>");

    private final Plugin plugin;
    private Component motd;
    private CachedServerIcon icon;

    public ServerListService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        List<String> lines = plugin.getConfig().getStringList("branding.motd");
        if (lines.isEmpty()) lines = DEFAULT_MOTD;
        MiniMessage mm = MiniMessage.miniMessage();
        Component c = Component.empty();
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            if (i > 0) c = c.append(Component.newline());
            try {
                c = c.append(mm.deserialize(lines.get(i)));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("branding.motd line " + (i + 1) + " is not valid MiniMessage: " + ex.getMessage());
            }
        }
        motd = c;
        boolean ownIcon = new java.io.File(Bukkit.getWorldContainer(), "server-icon.png").isFile();
        if (plugin.getConfig().getBoolean("branding.server-icon", true) && !ownIcon) {
            try (InputStream in = plugin.getResource("server-icon.png")) {
                if (in != null) icon = Bukkit.loadServerIcon(ImageIO.read(in));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not load the bundled server icon", ex);
            }
        }
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (motd != null) e.motd(motd);
        if (icon != null) {
            try {
                e.setServerIcon(icon);
            } catch (UnsupportedOperationException ignored) {
                // some ping kinds (legacy) cannot carry an icon
            }
        }
    }
}
