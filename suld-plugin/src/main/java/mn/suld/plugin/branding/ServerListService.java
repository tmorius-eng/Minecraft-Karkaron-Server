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
 * is on). {@code {live}} in a line is what is happening now ({@link LiveMotd}: a standing world boss, an open Их
 * Хуралдай vote, a boss rising soon, else the static counts), read at most every {@link #LIVE_CACHE_MS} per ping.
 */
public final class ServerListService implements Listener {

    static final String STATIC_LINE = "<white><bold>Хархорум</bold></white> <#FFD24A>·</#FFD24A> <white><bold>5 анги</bold></white> <#FFD24A>·</#FFD24A> <white><bold>{chapters} бүлэг эрэл</bold></white> <#FFD24A>·</#FFD24A> <white><bold>{dungeons} агуй</bold></white>";
    private static final List<String> DEFAULT_MOTD = List.of(
            "<bold><gradient:#FFD24A:#FF9E2C>ᠰ SÜLD</gradient></bold> <white><bold>· Монгол Hardcore MMORPG</bold></white>",
            "{live}");
    private static final long LIVE_CACHE_MS = 10_000;

    private final Plugin plugin;
    private final java.util.function.Supplier<java.util.Optional<String>> live;
    private volatile List<String> lines = List.of();
    private volatile Component motd; // the ping arrives on a network thread: the cache is read and refreshed there
    private volatile long motdAt;
    private CachedServerIcon icon;

    /** @param live what is happening now (plain text), empty when nothing is on; the static line is shown then */
    public ServerListService(Plugin plugin, java.util.function.Supplier<java.util.Optional<String>> live) {
        this.plugin = plugin;
        this.live = live;
    }

    public void start() {
        lines = plugin.getConfig().getStringList("branding.motd");
        if (lines.isEmpty()) lines = DEFAULT_MOTD;
        motd = render();
        motdAt = System.currentTimeMillis();
        boolean ownIcon = new java.io.File(Bukkit.getWorldContainer(), "server-icon.png").isFile();
        if (plugin.getConfig().getBoolean("branding.server-icon", true) && !ownIcon) {
            try (InputStream in = plugin.getResource("server-icon.png")) {
                if (in != null) icon = Bukkit.loadServerIcon(ImageIO.read(in));
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Could not load the bundled server icon", ex);
            }
        }
    }

    private Component render() {
        MiniMessage mm = MiniMessage.miniMessage();
        String now = null;
        Component c = Component.empty();
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            if (i > 0) c = c.append(Component.newline());
            String line = lines.get(i);
            if (line.contains("{live}")) {
                if (now == null) {
                    java.util.Optional<String> l;
                    try {
                        l = live.get();
                    } catch (RuntimeException ex) {
                        l = java.util.Optional.empty();
                    }
                    now = l.map(t -> "<white><bold>" + mm.escapeTags(t) + "</bold></white>").orElse(STATIC_LINE);
                }
                line = line.replace("{live}", now);
            }
            try {
                c = c.append(mm.deserialize(ContentCounts.fill(line)));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("branding.motd line " + (i + 1) + " is not valid MiniMessage: " + ex.getMessage());
            }
        }
        return c;
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        long t = System.currentTimeMillis();
        if (motd == null || t - motdAt > LIVE_CACHE_MS) {
            motd = render();
            motdAt = t;
        }
        e.motd(motd);
        if (icon != null) {
            try {
                e.setServerIcon(icon);
            } catch (UnsupportedOperationException ignored) {
                // some ping kinds (legacy) cannot carry an icon
            }
        }
    }
}
