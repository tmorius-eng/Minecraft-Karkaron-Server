package mn.suld.plugin.map;

import mn.suld.api.map.MapPin;
import mn.suld.api.persistence.MapPinRepository;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import mn.suld.plugin.auth.SuldJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * «Миний тэмдэг», the players' own map pins (docs/WORLD_MAP.md): read from the database when a player joins and kept
 * for the atlas and the minimap while they are online. One change at a time per player (the next waits for the last
 * to be stored), so the limit of {@link MapPin#MAX_PINS} holds exactly. Main thread.
 */
public final class PinService implements Listener {

    private final Plugin plugin;
    private final MapPinRepository repo;
    private final Map<UUID, List<MapPin>> pins = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    /** Counts each player's pin changes (read, added, removed), so the minimap redraws on any change, not just a new count. */
    private final Map<UUID, Integer> versions = new HashMap<>();

    public PinService(Plugin plugin, MapPinRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public void start() {
        for (Player p : Bukkit.getOnlinePlayers()) load(p.getUniqueId());
    }

    /** The player's pins, oldest first (empty until they are read). */
    public List<MapPin> pins(UUID player) {
        return pins.getOrDefault(player, List.of());
    }

    /** A number that changes whenever the player's pins change. */
    public int version(UUID player) {
        return versions.getOrDefault(player, 0);
    }

    private void load(UUID id) {
        repo.list(id).whenComplete((list, err) -> main(() -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[map] pins of " + id + " not read", err);
                return;
            }
            if (Bukkit.getPlayer(id) == null) return;
            pins.put(id, new ArrayList<>(list));
            versions.merge(id, 1, Integer::sum);
        }));
    }

    /** Sets a pin named {@code rawName} (a default name when blank) at block (x, y, z); {@code done} gets it when stored. */
    public void add(Player p, String rawName, int x, int y, int z, Consumer<MapPin> done) {
        UUID id = p.getUniqueId();
        List<MapPin> mine = pins(id);
        if (busy.contains(id)) {
            p.sendMessage(Messages.error("Өмнөх тэмдгийг хадгалж байна, түр хүлээнэ үү."));
            return;
        }
        if (mine.size() >= MapPin.MAX_PINS) {
            p.sendMessage(Messages.error("Тэмдэг " + MapPin.MAX_PINS + " хүрсэн: хуучнаа устгана уу (/map unpin <нэр>)."));
            return;
        }
        String name = MapPin.clean(rawName);
        if (name == null) name = MapPin.defaultName(mine);
        String n = name;
        if (mine.stream().anyMatch(q -> q.name().equalsIgnoreCase(n))) {
            p.sendMessage(Messages.error("«" + name + "» нэртэй тэмдэг аль хэдийн бий."));
            return;
        }
        MapPin pin = new MapPin(UUID.randomUUID(), id, name, x, y, z, MapPin.COLOURS.get(mine.size() % MapPin.COLOURS.size()), System.currentTimeMillis());
        busy.add(id);
        repo.add(pin, MapPin.MAX_PINS).whenComplete((ok, err) -> main(() -> {
            busy.remove(id);
            if (err != null || !Boolean.TRUE.equals(ok)) {
                if (err != null) plugin.getLogger().log(Level.WARNING, "[map] pin of " + p.getName() + " not stored", err);
                if (p.isOnline()) p.sendMessage(Messages.error("Тэмдэг хадгалагдсангүй."));
                return;
            }
            if (Bukkit.getPlayer(id) == null) return; // left meanwhile: the next join reads the stored pins
            List<MapPin> list = pins.computeIfAbsent(id, k -> new ArrayList<>());
            // back already: that join's read may hold this pin
            if (list.stream().noneMatch(q -> q.id().equals(pin.id()))) list.add(pin);
            versions.merge(id, 1, Integer::sum);
            if (p.isOnline()) p.sendMessage(Messages.success("◆ «" + pin.name() + "» тэмдэг тавигдлаа (x " + x + ", z " + z + ")."));
            if (done != null) done.accept(pin);
        }));
    }

    public void remove(Player p, MapPin pin, Runnable done) {
        UUID id = p.getUniqueId();
        if (busy.contains(id)) {
            p.sendMessage(Messages.error("Өмнөх өөрчлөлтийг хадгалж байна, түр хүлээнэ үү."));
            return;
        }
        busy.add(id);
        repo.remove(id, pin.id()).whenComplete((ok, err) -> main(() -> {
            busy.remove(id);
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[map] pin of " + p.getName() + " not removed", err);
                if (p.isOnline()) p.sendMessage(Messages.error("Тэмдэг устсангүй."));
                return;
            }
            if (Bukkit.getPlayer(id) == null) return; // left meanwhile: nothing of theirs is kept
            List<MapPin> list = pins.get(id);
            if (list != null) list.removeIf(q -> q.id().equals(pin.id()));
            versions.merge(id, 1, Integer::sum);
            if (p.isOnline()) p.sendMessage(Messages.success("«" + pin.name() + "» тэмдэг устлаа."));
            if (done != null) done.run();
        }));
    }

    private void main(Runnable r) {
        if (!plugin.isEnabled()) return;
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(SuldJoinEvent e) {
        load(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        pins.remove(e.getPlayer().getUniqueId());
        busy.remove(e.getPlayer().getUniqueId());
        versions.remove(e.getPlayer().getUniqueId());
    }
}
