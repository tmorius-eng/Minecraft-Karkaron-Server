package mn.suld.plugin.clan;

import io.papermc.paper.event.player.AsyncChatEvent;
import mn.suld.api.style.Cosmetic;
import mn.suld.api.style.PlayerStyle;
import mn.suld.plugin.resourcepack.ResourcePackService;
import mn.suld.plugin.style.StyleService;
import mn.suld.plugin.ui.StyleFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * SÜLD chat: {@code [STAFF] [RANK] [OVOG] Name ✦Tag » message}. Rendered per viewer: viewers with the resource pack
 * see pixel badges and emoji icons, others the same as coloured text. The message text itself is never altered
 * (its colour and emoji icons are presentation only). Also the styled join/leave lines.
 */
public final class ChatListener implements Listener {

    private final ClanService clans;
    private final StyleService styles;
    private final ResourcePackService packs;
    private final ChatChannels channels;

    public ChatListener(ClanService clans, StyleService styles, ResourcePackService packs, ChatChannels channels) {
        this.clans = clans;
        this.styles = styles;
        this.packs = packs;
        this.channels = channels;
    }

    private boolean hasPack(Object viewer) {
        return viewer instanceof Player v && packs.statusOf(v.getUniqueId()) == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        PlayerStyle s = styles.of(p.getUniqueId());
        String tag = clans.tagOf(p.getUniqueId()).orElse(null);
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Component clan = tag == null ? Component.empty()
                : Component.text("[" + tag + "] ", NamedTextColor.AQUA, TextDecoration.BOLD);
        Component name = StyleFormat.name(p, s);
        Component title = StyleFormat.tag(s).map(t -> Component.text(" ").append(t)).orElse(Component.empty());
        Component channel = channels == null ? Component.empty() : channels.prefix(channels.routedChannel(event), p);
        event.renderer((source, displayName, message, viewer) -> {
            boolean pack = hasPack(viewer);
            return channel.append(StyleFormat.badges(p, s, pack)).append(clan).append(name).append(title)
                    .append(Component.text(" » ", NamedTextColor.GRAY))
                    .append(StyleFormat.message(s, plain, pack));
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerStyle s = styles.of(p.getUniqueId());
        e.joinMessage(s.equipped(Cosmetic.Category.JOIN_MESSAGE).map(c -> StyleFormat.joinMessage(c, p.getName()))
                .orElse(Component.text("+ ", NamedTextColor.GREEN, TextDecoration.BOLD).append(StyleFormat.name(p, s))
                        .append(Component.text(" ирлээ", NamedTextColor.GRAY))));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        e.quitMessage(Component.text("- ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(StyleFormat.name(p, styles.of(p.getUniqueId()))).append(Component.text(" гарлаа", NamedTextColor.GRAY)));
    }
}
