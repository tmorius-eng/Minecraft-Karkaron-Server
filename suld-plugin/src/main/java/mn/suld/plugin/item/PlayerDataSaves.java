package mn.suld.plugin.item;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The order in which an item-for-coins change reaches disk and the database, so that a crash can never duplicate an
 * item. Items live in the player file (written by {@link Player#saveData()}); coins live in SQL. <b>The side a crash
 * could duplicate is written first.</b>
 * <ul>
 *   <li>ITEMS → COINS (sell, trade): the item is removed, the player file is written synchronously on the main thread
 *   in the same tick, then the coins are saved. A crash in between costs the coins, which the audit log can
 *   restore; the item is never both sold and kept.</li>
 *   <li>COINS → ITEMS (shop): the coins are charged and saved; only when that row is committed is the item given
 *   and the player file written. A crash in between costs the coins, never creates an item.</li>
 * </ul>
 * A coin save that fails is logged, told to the player, and leaves the profile dirty: the autosave and the save on
 * quit retry it. Nothing is ever marked persisted on a failed write ({@code JdbcProfileRepository.writeRow}).
 */
public final class PlayerDataSaves {

    private PlayerDataSaves() {
    }

    /**
     * The player file, written now (main thread, same tick). Never skipped or deferred: the item side must reach disk
     * before the coins (see above), even when one tick writes it twice. Timed as {@code player.save_data} (external
     * audit FIND-01) so its real cost shows in {@code /suldperf}.
     */
    public static void now(Player p) {
        if (!p.isOnline()) return;
        long t = mn.suld.plugin.perf.PerfProbe.start();
        p.saveData();
        mn.suld.plugin.perf.PerfProbe.stop("player.save_data", t);
    }

    /** Player files waiting for {@link #soon}, written one a tick. */
    private static final SaveQueue QUEUE = new SaveQueue();
    private static org.bukkit.scheduler.BukkitTask drain;

    /**
     * The player file within the next ticks, one player a tick, for a payout that pays many players in the same tick
     * (a world boss, a world event). Calling {@link #now} for each of them held that tick for about 5 ms a player
     * (FIND-01): a quarter of a second for fifty winners. Only for a reward, a gain with nothing traded for it, which a
     * crash cannot duplicate (at worst it loses the reward, as a crash before the autosave always could). An
     * ITEMS ⇄ COINS change keeps {@link #now} and its order. Main thread.
     */
    public static void soon(Plugin plugin, Player p) {
        if (!plugin.isEnabled()) { // shutting down: no task would run
            now(p);
            return;
        }
        if (!QUEUE.add(p.getUniqueId()) || drain != null && !drain.isCancelled()) return;
        drain = Bukkit.getScheduler().runTaskTimer(plugin, PlayerDataSaves::writeNext, 1L, 1L);
    }

    private static void writeNext() {
        java.util.UUID id = QUEUE.next();
        if (id == null) {
            if (drain != null) drain.cancel();
            drain = null;
            return;
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) now(p); // gone: leaving wrote the file
    }

    /**
     * ITEMS → COINS. The caller has removed the item and added the coins to {@code pr}: this writes the player file
     * now and then saves the coins, reporting a failed save.
     */
    public static CompletableFuture<PlayerProfile> itemsForCoins(Plugin plugin, SuldServices services, Player p, PlayerProfile pr, String what) {
        now(p);
        return saveCoins(plugin, services, p, pr, what);
    }

    /**
     * COINS → ITEMS. The caller has charged {@code price} from {@code pr} (which also blocks a second click): this
     * saves the coins, and once the row is committed runs {@code give} on the main thread and writes the player
     * file. If the save fails, or the player left meanwhile, the coins come back in memory (and are saved again).
     */
    public static void coinsForItem(Plugin plugin, SuldServices services, Player p, PlayerProfile pr, long price, String what, Consumer<Player> give) {
        services.profiles().save(pr).whenComplete((saved, err) -> onMain(plugin, () -> {
            if (err != null) {
                pr.addCurrency(price);
                plugin.getLogger().severe("[coins] " + what + " for " + p.getName() + ": the charge was not saved, refunded in memory: " + err);
                if (p.isOnline()) p.sendMessage(Messages.error("Зоос хадгалагдсангүй — худалдан авалт цуцлагдлаа."));
                return;
            }
            if (!p.isOnline()) {
                pr.addCurrency(price);
                services.profiles().save(pr);
                plugin.getLogger().warning("[coins] " + what + " for " + p.getName() + ": left before the item was given, refunded");
                return;
            }
            give.accept(p);
            now(p);
        }));
    }

    private static CompletableFuture<PlayerProfile> saveCoins(Plugin plugin, SuldServices services, Player p, PlayerProfile pr, String what) {
        return services.profiles().save(pr).whenComplete((saved, err) -> {
            if (err == null) return;
            plugin.getLogger().severe("[coins] " + what + " for " + p.getName() + " not saved, the profile stays dirty for the next save: " + err);
            onMain(plugin, () -> {
                if (p.isOnline()) p.sendMessage(Messages.error("Зоос хадгалагдсангүй — дараагийн хадгалалтаар дахин оролдоно."));
            });
        });
    }

    /** On the main thread; inline when the plugin is disabling (the scheduler refuses new tasks then). */
    static void onMain(Plugin plugin, Runnable r) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, r);
        else r.run();
    }
}
