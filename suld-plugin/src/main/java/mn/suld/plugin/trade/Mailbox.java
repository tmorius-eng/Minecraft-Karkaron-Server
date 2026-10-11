package mn.suld.plugin.trade;

import mn.suld.plugin.persistence.AtomicYaml;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Items that could not be handed to a player because they were offline (a trade cancelled by a disconnect, a trade
 * mid-flight at shutdown, a respawn that never came): kept in {@code plugins/SULD/mailbox/<uuid>.yml} as the item's
 * own bytes ({@link ItemStack#serializeAsBytes()}, so every component survives) and given back on the next join.
 * What does not fit the inventory stays in the file for the join after.
 */
public final class Mailbox {

    private final Plugin plugin;

    public Mailbox(Plugin plugin) {
        this.plugin = plugin;
    }

    private Path file(UUID player) {
        return plugin.getDataFolder().toPath().resolve("mailbox").resolve(player + ".yml");
    }

    /** Appends items for an offline player (an empty list is a no-op). Writes the file atomically. */
    public void keep(UUID player, List<ItemStack> items, String why) {
        List<ItemStack> all = new ArrayList<>(read(player));
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) all.add(it);
        if (all.isEmpty()) return;
        write(player, all);
        plugin.getLogger().info("[mailbox] " + items.size() + " stack(s) kept for " + player + " (" + why + ")");
    }

    /** Hands the player everything kept for them; what does not fit stays kept. Returns the stacks delivered. */
    public int deliver(Player p) {
        List<ItemStack> kept = read(p.getUniqueId());
        if (kept.isEmpty()) return 0;
        List<ItemStack> left = new ArrayList<>();
        int delivered = 0;
        for (ItemStack it : kept) {
            var rest = p.getInventory().addItem(it);
            if (rest.isEmpty()) delivered++;
            else left.addAll(rest.values());
        }
        write(p.getUniqueId(), left);
        return delivered;
    }

    List<ItemStack> read(UUID player) {
        YamlConfiguration y = AtomicYaml.read(file(player));
        List<ItemStack> out = new ArrayList<>();
        for (String b64 : y.getStringList("items")) {
            try {
                out.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(b64)));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[mailbox] unreadable stack for " + player + " dropped: " + ex.getMessage());
            }
        }
        return out;
    }

    private void write(UUID player, List<ItemStack> items) {
        Path f = file(player);
        if (items.isEmpty()) {
            try {
                java.nio.file.Files.deleteIfExists(f);
            } catch (java.io.IOException ignored) {
                // a leftover empty file is harmless: read() yields nothing
            }
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        List<String> enc = new ArrayList<>();
        for (ItemStack it : items) enc.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        y.set("items", enc);
        AtomicYaml.write(plugin, f, y);
    }
}
