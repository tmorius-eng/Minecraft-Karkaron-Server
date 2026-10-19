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

    /**
     * Appends items for an offline player (an empty list is a no-op). Read-modify-write under the mailbox lock and
     * written at once (PERS-005): two keeps in a row can no longer lose the first batch to a stale read.
     */
    public synchronized void keep(UUID player, List<ItemStack> items, String why) {
        List<ItemStack> all = new ArrayList<>(read(player));
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) all.add(it);
        if (all.isEmpty()) return;
        write(player, all);
        plugin.getLogger().info("[mailbox] " + items.size() + " stack(s) kept for " + player + " (" + why + ")");
    }

    /**
     * Into an online player's bag; what does not fit is kept here for the next join, never dropped on the ground (a
     * dungeon hall is cleared seconds after the win, and a bound reward on the ground is lost). Returns the stacks kept.
     */
    public int giveOrKeep(Player p, List<ItemStack> items, String why) {
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir()) continue;
            if (p.isOnline()) rest.addAll(p.getInventory().addItem(it).values());
            else rest.add(it);
        }
        return keepOverflow(p, rest, why);
    }

    /** Stacks that did not fit an online player's bag: kept, and the player told where they went. Returns the count. */
    public int keepOverflow(Player p, List<ItemStack> rest, String why) {
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack it : rest) if (it != null && !it.getType().isAir()) kept.add(it);
        if (kept.isEmpty()) return 0;
        keep(p.getUniqueId(), kept, why);
        if (p.isOnline()) p.sendMessage(mn.suld.plugin.ui.Messages.info("Цүнх дүүрэн байсан тул " + kept.size()
                + " эд зүйл шуудангийн хайрцагт аюулгүй хадгалагдлаа — дахин нэвтрэхэд ирнэ."));
        return kept.size();
    }

    /** Players whose delivered file could not be emptied: no second delivery from it in this run (FIND-08). */
    private final java.util.Set<UUID> stuck = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Hands the player everything kept for them; what does not fit stays kept. Returns the stacks delivered.
     * PERS-005: the player file is written first, then the mailbox file shrinks, so a crash at any point leaves every
     * stack in at least one place (never in neither).
     */
    public synchronized int deliver(Player p) {
        if (stuck.contains(p.getUniqueId())) return 0; // delivered once already; its file could not be emptied
        List<ItemStack> kept = read(p.getUniqueId());
        if (kept.isEmpty()) return 0;
        List<ItemStack> left = new ArrayList<>();
        int delivered = 0;
        long before = amount(kept);
        for (ItemStack it : kept) {
            var rest = p.getInventory().addItem(it.clone());
            if (rest.isEmpty()) delivered++;
            else left.addAll(rest.values());
        }
        // compare item counts, not stacks: a stack that only partly fitted moved items too and must shrink the file
        if (amount(left) == before) return 0; // nothing fitted: the file stays as it is
        mn.suld.plugin.item.PlayerDataSaves.now(p);
        write(p.getUniqueId(), left);
        return delivered;
    }

    private static long amount(List<ItemStack> stacks) {
        long n = 0;
        for (ItemStack it : stacks) n += it.getAmount();
        return n;
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

    /** One file step that may fail. */
    interface IoStep {
        void run() throws java.io.IOException;
    }

    /**
     * The delivered file goes: removed, else emptied, else the player is marked {@code stuck} so the stacks are never
     * read again this run (external audit FIND-08: the stacks were delivered and the player file saved, a file that
     * cannot go would deliver them twice). Returns what happened; the steps are parameters so a test can make them fail.
     */
    static String clearDelivered(Path f, UUID player, java.util.Set<UUID> stuck, java.util.logging.Logger log, IoStep remove, IoStep empty) {
        try {
            remove.run();
            return "removed";
        } catch (java.io.IOException e) {
            try {
                empty.run();
                log.warning("[mailbox] could not remove " + f.getFileName() + " (" + e.getMessage() + "); emptied it instead");
                return "emptied";
            } catch (java.io.IOException e2) {
                stuck.add(player);
                log.severe("[mailbox] delivered but could neither remove nor empty " + f.getFileName() + ": "
                        + e2.getMessage() + " — not delivered again until restart; remove it by hand before then");
                return "stuck";
            }
        }
    }

    private void write(UUID player, List<ItemStack> items) {
        Path f = file(player);
        if (items.isEmpty()) {
            clearDelivered(f, player, stuck, plugin.getLogger(), () -> java.nio.file.Files.deleteIfExists(f), () -> {
                YamlConfiguration empty = new YamlConfiguration();
                empty.set("items", List.of());
                AtomicYaml.writeNow(f, empty);
            });
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        List<String> enc = new ArrayList<>();
        for (ItemStack it : items) enc.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        y.set("items", enc);
        long t = mn.suld.plugin.perf.PerfProbe.start();
        try {
            AtomicYaml.writeNow(f, y); // a few stacks: synchronous keeps the order with the player file
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("[mailbox] could not write " + f.getFileName() + " (" + items.size() + " stacks): " + e.getMessage());
        } finally {
            mn.suld.plugin.perf.PerfProbe.stop("mailbox.write", t); // /suldperf: the cost of the synchronous write
        }
    }
}
