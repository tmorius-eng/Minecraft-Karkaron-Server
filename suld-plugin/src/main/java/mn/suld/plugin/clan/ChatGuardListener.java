package mn.suld.plugin.clan;

import io.papermc.paper.event.player.AsyncChatEvent;
import mn.suld.api.chat.ChatGuard;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Applies {@link ChatGuard} before SÜLD formats chat ({@code chat-guard:} in config.yml). Staff with
 * {@code suld.chat.bypass} are not limited. Blocked messages are cancelled with a private notice; shouted ones are
 * lowered.
 */
public final class ChatGuardListener implements Listener {

    private final ChatGuard guard;
    private final boolean enabled;

    public ChatGuardListener(Plugin plugin) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("chat-guard");
        ChatGuard.Settings d = ChatGuard.Settings.defaults();
        if (c == null) {
            enabled = true;
            guard = new ChatGuard(d);
            return;
        }
        enabled = c.getBoolean("enabled", true);
        List<String> domains = c.getStringList("allowed-domains");
        if (domains.isEmpty()) domains = List.of(plugin.getConfig().getString("branding.domain", "suld.mn"));
        guard = new ChatGuard(new ChatGuard.Settings(
                Math.max(1, c.getInt("max-messages", d.maxMessages())),
                Math.max(1, c.getInt("window-seconds", 5)) * 1000L,
                Math.max(0, c.getInt("repeat-seconds", 30)) * 1000L,
                c.getBoolean("block-links", d.blockLinks()),
                domains,
                c.getDouble("caps-limit", d.capsLimit())));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!enabled || p.hasPermission("suld.chat.bypass")) return;
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        ChatGuard.Result r = guard.check(p.getUniqueId(), plain, System.currentTimeMillis());
        switch (r.verdict()) {
            case ALLOW -> {
                if (!r.text().equals(plain.strip())) e.message(Component.text(r.text()));
            }
            case TOO_FAST -> {
                e.setCancelled(true);
                p.sendMessage(Messages.error("Хэт хурдан бичиж байна — түр хүлээгээрэй."));
            }
            case REPEAT -> {
                e.setCancelled(true);
                p.sendMessage(Messages.error("Ижил мессежийг дахин бүү илгээ."));
            }
            case ADVERT -> {
                e.setCancelled(true);
                p.sendMessage(Messages.error("Өөр серверийн хаяг, IP чатад хориотой."));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        guard.forget(e.getPlayer().getUniqueId());
    }
}
