package mn.suld.plugin.worldbuild;

import mn.suld.api.worldbuild.BorderSpec;
import mn.suld.plugin.perf.SafeTeleport;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The playable overworld: Minecraft's own world border, {@code world.border.diameter} blocks (10 000) wide and centred
 * on the world spawn, i.e. Kharkhorum's plaza (docs/world/WORLD_BORDER.md). The border lives in level.dat, so it is
 * set once (on start, and again only when the spawn or the config changes it) and never re-sent per tick; the vanilla
 * client draws it, and vanilla collision keeps walkers, riders and boats inside.
 * <p>
 * What vanilla lets through is closed here, event-driven only (no per-tick scans): a teleport whose destination lies
 * outside (ender pearl, chorus fruit, a plugin or command) is cancelled, a respawn point outside is moved to the
 * spawn, and a player who logs in outside (an old save from before the border) is brought back to the city.
 * {@code suld.border.bypass} (op) skips the teleport rule for staff.
 */
public final class WorldBorderService implements Listener {

    private final Plugin plugin;

    public WorldBorderService(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("world.border.enabled", true);
    }

    public static World overworld() {
        return Bukkit.getWorlds().get(0);
    }

    /** The border the config asks for, from the current spawn. */
    public BorderSpec desired() {
        World w = overworld();
        Location s = w.getSpawnLocation();
        double[] c = BorderSpec.center(plugin.getConfig().getString("world.border.center", "spawn"),
                s.getBlockX() + 0.5, s.getBlockZ() + 0.5);
        return new BorderSpec(c[0], c[1], plugin.getConfig().getDouble("world.border.diameter", BorderSpec.DEFAULT_DIAMETER));
    }

    /** Sets the native border when it differs from the config; a no-op otherwise. Main thread. */
    public void apply(String why) {
        if (!enabled()) return;
        World w = overworld();
        BorderSpec want = desired();
        WorldBorder b = w.getWorldBorder();
        int warn = plugin.getConfig().getInt("world.border.warning-distance", 24);
        double buffer = plugin.getConfig().getDouble("world.border.damage-buffer", 4);
        double dmg = plugin.getConfig().getDouble("world.border.damage-per-block", 0.5);
        boolean changed = want.differsFrom(b.getCenter().getX(), b.getCenter().getZ(), b.getSize());
        if (changed) {
            b.setCenter(want.centerX(), want.centerZ());
            b.setSize(want.diameter());
            plugin.getLogger().info("World border (" + why + "): " + (int) want.diameter() + " x " + (int) want.diameter()
                    + " blocks centred on " + (int) Math.floor(want.centerX()) + ", " + (int) Math.floor(want.centerZ())
                    + " (radius " + (int) want.radius() + ").");
        }
        if (b.getWarningDistance() != warn) b.setWarningDistance(warn);
        if (b.getDamageBuffer() != buffer) b.setDamageBuffer(buffer);
        if (b.getDamageAmount() != dmg) b.setDamageAmount(dmg);
    }

    public List<String> status() {
        World w = overworld();
        WorldBorder b = w.getWorldBorder();
        List<String> out = new ArrayList<>();
        out.add("Хил: " + (enabled() ? "идэвхтэй" : "унтраастай (world.border.enabled)") + " · " + w.getName());
        out.add("Төв: " + (int) Math.floor(b.getCenter().getX()) + ", " + (int) Math.floor(b.getCenter().getZ())
                + " · хэмжээ " + (int) b.getSize() + " блок (радиус " + (int) (b.getSize() / 2) + ")");
        Location s = w.getSpawnLocation();
        out.add("Спавн: " + s.getBlockX() + ", " + s.getBlockY() + ", " + s.getBlockZ() + (b.isInside(s) ? " (дотор)" : " (ГАДНА!)"));
        out.add("Анхааруулга " + b.getWarningDistance() + " блок · хамгаалалт " + b.getDamageBuffer() + " · гэмтэл " + b.getDamageAmount() + "/блок");
        if (enabled() && desired().differsFrom(b.getCenter().getX(), b.getCenter().getZ(), b.getSize())) {
            out.add("Тохиргоотой зөрж байна: /suldworld border apply");
        }
        return out;
    }

    private boolean outside(Location to) {
        if (to == null || to.getWorld() == null || !enabled()) return false;
        return to.getWorld().equals(overworld()) && !to.getWorld().getWorldBorder().isInside(to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (!outside(e.getTo())) return;
        if (e.getPlayer().hasPermission("suld.border.bypass")) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(Messages.error("Их Монголын хилээс гадна гарах боломжгүй."));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        if (outside(e.getRespawnLocation())) e.setRespawnLocation(overworld().getSpawnLocation().add(0.5, 0, 0.5));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!outside(p.getLocation())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !outside(p.getLocation())) return;
            SafeTeleport.to(plugin, p, overworld().getSpawnLocation().add(0.5, 0, 0.5),
                    PlayerTeleportEvent.TeleportCause.PLUGIN, ok -> {
                        if (ok) p.sendMessage(Messages.info("Та хилээс гадна байсан тул Хархорум руу буцаагдлаа."));
                    });
        }, 5L);
    }
}
