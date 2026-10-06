package mn.suld.plugin.clan;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * SÜLD chat format: {@code [TAG] Name » message}. Runs on Paper's async chat thread, so it
 * only reads the thread-safe clan tag cache. Message content is never modified.
 */
public final class ChatListener implements Listener {

    private final ClanService clans;

    public ChatListener(ClanService clans) {
        this.clans = clans;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String tag = clans.tagOf(event.getPlayer().getUniqueId()).orElse(null);
        Component prefix = tag == null ? Component.empty() : Component.text("[" + tag + "] ", NamedTextColor.GOLD);
        event.renderer((source, displayName, message, viewer) -> prefix
                .append(displayName.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" » ", NamedTextColor.GRAY))
                .append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }
}
