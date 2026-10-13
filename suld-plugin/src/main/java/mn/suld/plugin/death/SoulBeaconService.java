package mn.suld.plugin.death;

import mn.suld.api.config.DeathSettings;
import mn.suld.api.death.SoulBeacon;
import mn.suld.api.persistence.DeathRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * «Сүнсний Гэрэлт» (docs/DEATH_AND_RECOVERY.md, reform of 2026-10-13): a pale blue beam where a player fell, seen by
 * that player only, for {@code death.beacon.minutes}. Walking back to it as a soul gives back
 * {@code death.beacon.refund} of the EXP the death took, once: the claim is a compare-and-set in storage first
 * ({@code suld_soul_beacons}), the EXP is paid only after it (PERS-002 order). It does not end the soul state:
 * «Тэнгэрийн сорил» or the ovoo ritual do. The beam and its label are vanilla display entities (no custom model),
 * not saved with the chunk: they are put up again whenever the owner comes near, so a chunk unload, a reconnect or a
 * restart (the beacon is re-read on join) changes nothing. Beacons stand only in the main overworld, where souls walk.
 */
public final class SoulBeaconService implements Listener {

    private static final TextColor SOUL = TextColor.fromHexString("#9FD8FF");
    /** The beam shows within this distance of its owner (blocks). */
    private static final double SHOW_RANGE = 160;
    private static final int BEAM_HEIGHT = 40;

    private final Plugin plugin;
    private final SuldServices services;
    private final DeathRepository repo;
    /** The owner's standing beacon (one per player: a new death replaces the old one). */
    private final Map<UUID, SoulBeacon> open = new ConcurrentHashMap<>();
    /** The owner's beam and label entities, while shown. */
    private final Map<UUID, UUID[]> shown = new HashMap<>();
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();

    public SoulBeaconService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.repo = services.deathRepository();
    }

    private DeathSettings settings() {
        return services.config().death();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        for (Player p : Bukkit.getOnlinePlayers()) load(p);
    }

    /** Plugin disable: the beams go (they are stored; the next start reads them again). */
    public void shutdown() {
        for (UUID owner : java.util.List.copyOf(shown.keySet())) hide(owner);
    }

    /** The owner's standing beacon, if any (the soul's boss bar shows it). */
    public SoulBeacon of(UUID owner) {
        SoulBeacon b = open.get(owner);
        return b != null && b.open(System.currentTimeMillis()) ? b : null;
    }

    /** One line for the soul's bar: «Гэрэлт 230 м З · 7:41 · +700 EXP», or null without a standing beacon. */
    public String status(Player p) {
        SoulBeacon b = of(p.getUniqueId());
        if (b == null) return null;
        long left = b.remaining(System.currentTimeMillis()) / 1000;
        String where;
        if (!b.world().equals(p.getWorld().getName())) {
            where = "өөр ертөнцөд";
        } else {
            double dx = b.x() + 0.5 - p.getLocation().getX(), dz = b.z() + 0.5 - p.getLocation().getZ();
            where = Math.round(Math.hypot(dx, dz)) + " м " + Navigation.compass(Navigation.bearing(dx, dz));
        }
        return "Гэрэлт " + where + " · " + left / 60 + ":" + String.format("%02d", left % 60) + " · +" + b.refund(settings()) + " EXP";
    }

    // ---- placing -----------------------------------------------------------------------------------------------

    /**
     * A new beacon for a death at {@code at} that took {@code expLost} (DeathService, right after the lock); null when
     * none stands: off for {@code death.beacon.minutes: 0}, or a death outside the overworld or below its floor (the
     * halls send the soul to the gate, the void cannot be walked back to).
     */
    SoulBeacon place(Player p, UUID deathId, Location at, long expLost) {
        World main = Bukkit.getWorlds().get(0);
        if (at.getWorld() == null || !at.getWorld().equals(main) || at.getY() < main.getMinHeight()) return null; // souls walk only the overworld
        DeathSettings s = settings();
        if (s.beaconMinutes() <= 0) return null;
        // on the ground under the body: a player shot in mid-air or mid-fall must still be able to stand at it (a cave
        // death keeps its own height, the surface is above it)
        int ground = main.getHighestBlockYAt(at.getBlockX(), at.getBlockZ(), org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        SoulBeacon b = SoulBeacon.of(deathId, p.getUniqueId(), at.getWorld().getName(), at.getBlockX(), Math.min(at.getBlockY(), ground),
                at.getBlockZ(), expLost, System.currentTimeMillis(), s);
        hide(p.getUniqueId());
        open.put(p.getUniqueId(), b);
        repo.saveBeacon(b).whenComplete((v, ex) -> {
            if (ex != null) plugin.getLogger().log(Level.WARNING, "[death] beacon of " + p.getName() + " not stored (it stands until a restart)", ex);
        });
        return b;
    }

    private void load(Player p) {
        UUID id = p.getUniqueId();
        repo.openBeacon(id, System.currentTimeMillis()).whenComplete((b, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "[death] beacon of " + id + " not read", ex);
                return;
            }
            if (b.isPresent() && p.isOnline()) open.putIfAbsent(id, b.get());
        }));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hide(e.getPlayer().getUniqueId());
        open.remove(e.getPlayer().getUniqueId()); // stored: read again on the next join
    }

    // ---- the beam and the claim ----------------------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        DeathSettings s = settings();
        for (Map.Entry<UUID, SoulBeacon> en : open.entrySet()) {
            UUID owner = en.getKey();
            SoulBeacon b = en.getValue();
            Player p = Bukkit.getPlayer(owner);
            if (p == null) continue;
            if (!b.open(now)) {
                open.remove(owner, b);
                hide(owner);
                if (services.isSoul.test(owner)) p.sendMessage(Messages.info("Сүнсний Гэрэлт унтарлаа."));
                continue;
            }
            if (!services.isSoul.test(owner)) { // alive again (trial, ritual, staff): the beacon has done its part
                open.remove(owner, b);
                hide(owner);
                continue;
            }
            show(p, b, now, s);
            if (!p.isDead() && b.reached(p.getWorld().getName(), p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ())) {
                claim(p, b, s);
            }
        }
    }

    private void claim(Player p, SoulBeacon b, DeathSettings s) {
        UUID owner = p.getUniqueId();
        if (!claiming.add(owner)) return;
        long now = System.currentTimeMillis();
        repo.claimBeacon(b.deathId(), now).whenComplete((ok, ex) -> Bukkit.getScheduler().runTask(plugin, () -> {
            claiming.remove(owner);
            if (ex != null) {
                plugin.getLogger().log(Level.WARNING, "[death] beacon claim of " + p.getName() + " failed (tried again)", ex);
                return;
            }
            open.remove(owner, b);
            hide(owner);
            if (!ok) return; // claimed already (another server tick, a rejoin) or faded: nothing more is owed
            long refund = b.refund(s);
            PlayerProfile pr = services.profiles().cached(owner).orElse(null);
            if (refund > 0 && pr != null) {
                int from = pr.progression().level();
                ExpGainResult gain = services.progression().grantExp(pr, refund, ExpSource.OTHER);
                services.profiles().save(pr);
                if (gain.leveledUp() && p.isOnline()) Presentation.levelUp(p, from, gain.after().level());
                if (p.isOnline()) services.hud().update(p, pr);
            }
            services.audit().record(mn.suld.api.audit.AuditEvent.of(owner.toString(), "death.beacon_claim", owner.toString(),
                    "death " + b.deathId() + " refund=" + refund + " of " + b.expLost()));
            services.analytics().record(mn.suld.api.analytics.AnalyticsEvent.of("soul_beacon_claim", owner,
                    Map.<String, Object>of("refund", refund, "exp_lost", b.expLost(), "seconds", (now - b.createdAt()) / 1000)));
            if (!p.isOnline()) return;
            p.sendMessage(Component.text("✦ Сүнсний Гэрэлт: ", SOUL, TextDecoration.BOLD)
                    .append(Component.text("алдсан EXP-ийн " + Math.round(s.beaconRefund() * 100) + " % буцлаа (+" + refund + " EXP). ",
                            NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("Одоо /trial эсвэл ойрын овоонд цацал өргөж амил.", NamedTextColor.GRAY)));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 40, 0.4, 1.0, 0.4, 0.02);
        }));
    }

    /** Puts the owner's beam up if it is near and missing, and keeps its label's countdown current. */
    private void show(Player p, SoulBeacon b, long now, DeathSettings s) {
        UUID owner = p.getUniqueId();
        World w = Bukkit.getWorld(b.world());
        if (w == null || !w.equals(p.getWorld())) {
            hide(owner);
            return;
        }
        Location base = new Location(w, b.x() + 0.5, b.y(), b.z() + 0.5);
        if (base.distanceSquared(p.getLocation()) > SHOW_RANGE * SHOW_RANGE || !w.isChunkLoaded(b.x() >> 4, b.z() >> 4)) {
            hide(owner);
            return;
        }
        UUID[] ids = shown.get(owner);
        Entity beam = ids == null ? null : Bukkit.getEntity(ids[0]);
        Entity label = ids == null ? null : Bukkit.getEntity(ids[1]);
        if (beam == null || !beam.isValid() || label == null || !label.isValid()) {
            hide(owner);
            BlockDisplay bd = w.spawn(base, BlockDisplay.class, d -> {
                d.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());
                d.setTransformation(new Transformation(new Vector3f(-0.15f, 0, -0.15f), new AxisAngle4f(),
                        new Vector3f(0.3f, BEAM_HEIGHT, 0.3f), new AxisAngle4f()));
                d.setGlowing(true);
                d.setGlowColorOverride(Color.fromRGB(0x9F, 0xD8, 0xFF));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(3f);
                d.setPersistent(false);
                d.setVisibleByDefault(false);
            });
            TextDisplay td = w.spawn(base.clone().add(0, 2.6, 0), TextDisplay.class, t -> {
                t.setBillboard(Display.Billboard.CENTER);
                t.setShadowed(true);
                t.setBackgroundColor(Color.fromARGB(0x60, 0, 0, 0));
                t.setPersistent(false);
                t.setVisibleByDefault(false);
                t.setViewRange(2f);
            });
            p.showEntity(plugin, bd);
            p.showEntity(plugin, td);
            shown.put(owner, new UUID[]{bd.getUniqueId(), td.getUniqueId()});
            label = td;
        }
        long left = b.remaining(now) / 1000;
        ((TextDisplay) label).text(Component.text("Сүнсний Гэрэлт", SOUL, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("+" + b.refund(s) + " EXP · " + left / 60 + ":" + String.format("%02d", left % 60), NamedTextColor.WHITE)));
    }

    private void hide(UUID owner) {
        UUID[] ids = shown.remove(owner);
        if (ids == null) return;
        for (UUID id : ids) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
    }
}
