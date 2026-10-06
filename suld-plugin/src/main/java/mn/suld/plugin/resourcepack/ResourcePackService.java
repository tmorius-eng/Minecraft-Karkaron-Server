package mn.suld.plugin.resourcepack;

import mn.suld.api.config.ResourcePackSettings;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends the SÜLD resource pack on join and tracks per-player load status.
 * Pure custom code over the Paper resource-pack API — no external pack plugin.
 */
public final class ResourcePackService implements Listener {

    private final Plugin plugin;
    private volatile ResourcePackSettings settings;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> status = new ConcurrentHashMap<>();

    public ResourcePackService(Plugin plugin, ResourcePackSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** Re-read settings after a config reload. */
    public void updateSettings(ResourcePackSettings settings) {
        this.settings = settings;
    }

    public boolean enabled() {
        return settings.enabled() && !settings.url().isBlank();
    }

    /** Send the pack to a player (if enabled). Safe to call on the main thread. */
    public void send(Player player) {
        if (!enabled()) {
            return;
        }
        byte[] hash = decodeSha1(settings.sha1());
        Component prompt = Component.text(settings.prompt(), Messages.BRAND);
        try {
            player.setResourcePack(settings.url(), hash, prompt, settings.required());
            player.sendMessage(Messages.info("Дүрс багц илгээж байна…"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to send resource pack to " + player.getName() + ": " + ex.getMessage());
        }
    }

    public PlayerResourcePackStatusEvent.Status statusOf(UUID player) {
        return status.get(player);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        status.put(player.getUniqueId(), event.getStatus());
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> player.sendMessage(Messages.success("Дүрс багц амжилттай ачаалагдлаа."));
            case DECLINED -> player.sendMessage(Messages.info("Дүрс багцгүйгээр тоглож байна."));
            case FAILED_DOWNLOAD -> player.sendMessage(Messages.error("Дүрс багц татаж чадсангүй. /suldpack дахин оролдоно уу."));
            default -> {
                // ACCEPTED / DOWNLOADED / other intermediate states: no message.
            }
        }
    }

    private static byte[] decodeSha1(String hex) {
        if (hex == null || hex.isBlank()) {
            return null;
        }
        String clean = hex.trim();
        if (clean.length() % 2 != 0) {
            return null;
        }
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
