package mn.suld.plugin.progress;

import mn.suld.api.persistence.MarksRepository;
import mn.suld.api.progress.Mark;
import mn.suld.api.progress.MarkKind;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The database mirror of the progress marks the player file holds (dungeon clears, sites seen, ovoo days). The file
 * stays the fast in-game truth (no login race, no behaviour change); every write also goes to V17
 * {@code suld_player_marks}, and on login the file is backfilled into the table once, so marks earned before V17 are
 * queryable too. Writes are fire-and-forget upserts on the I/O pool; a failure is logged, never thrown at gameplay.
 */
public final class PlayerMarks {

    /** The player-file keys other services wrote before V17 (and still write). */
    public static final NamespacedKey DUNGEON_CLEARS = new NamespacedKey("suld", "dungeon_clears");
    public static final String SITES_SEEN = "sites_seen";
    public static final String OVOO_PREFIX = "ovoo_";

    private final Plugin plugin;
    private final MarksRepository repository;
    private final NamespacedKey backfilledKey;

    public PlayerMarks(Plugin plugin, MarksRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.backfilledKey = new NamespacedKey(plugin, "marks_backfilled");
    }

    public MarksRepository repository() {
        return repository;
    }

    /** Mirror one mark to the database (upsert). */
    public void record(UUID player, MarkKind kind, String key, long value) {
        try {
            repository.put(new Mark(player, kind, key, value, System.currentTimeMillis())).whenComplete((v, err) -> {
                if (err != null) plugin.getLogger().log(Level.WARNING, "marks: " + kind + " " + key + " of " + player + " not written: " + root(err));
            });
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "marks: " + ex.getMessage());
        }
    }

    /**
     * Once per player file: copy the marks the file already holds into the table. Idempotent (upserts), so running it
     * again after a failed attempt only repeats the same writes. Values are the backfill moment for clears and sites
     * (the file never stored when), the stored day for ovoos.
     */
    public void backfill(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        if (pdc.has(backfilledKey, PersistentDataType.BYTE)) return;
        long now = System.currentTimeMillis();
        List<Mark> marks = new ArrayList<>();
        String clears = pdc.get(DUNGEON_CLEARS, PersistentDataType.STRING);
        if (clears != null) for (String id : clears.split(",")) if (!id.isBlank() && id.length() <= 64) marks.add(new Mark(p.getUniqueId(), MarkKind.DUNGEON_CLEAR, id, now, now));
        String seen = pdc.get(new NamespacedKey(plugin, SITES_SEEN), PersistentDataType.STRING);
        if (seen != null) for (String id : seen.split(",")) if (!id.isBlank() && id.length() <= 64) marks.add(new Mark(p.getUniqueId(), MarkKind.SITE_SEEN, id, now, now));
        for (NamespacedKey k : pdc.getKeys()) {
            if (!k.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT)) || !k.getKey().startsWith(OVOO_PREFIX)
                    || k.getKey().equals("ovoo_bless_until")) continue; // the blessing timer is not a visit
            Long day = pdc.get(k, PersistentDataType.LONG);
            String id = k.getKey().substring(OVOO_PREFIX.length());
            if (day != null && !id.isBlank() && id.length() <= 64) marks.add(new Mark(p.getUniqueId(), MarkKind.OVOO_DAY, id, day, now));
        }
        UUID id = p.getUniqueId();
        repository.putAll(marks).whenComplete((v, err) -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "marks: backfill of " + id + " failed (" + root(err) + "); retried on the next login");
                return;
            }
            // mark the file on the main thread: the player may have left meanwhile
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                Player again = org.bukkit.Bukkit.getPlayer(id);
                if (again != null) again.getPersistentDataContainer().set(backfilledKey, PersistentDataType.BYTE, (byte) 1);
            });
        });
        if (!marks.isEmpty()) plugin.getLogger().fine("marks: backfilling " + marks.size() + " marks of " + p.getName());
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }
}
