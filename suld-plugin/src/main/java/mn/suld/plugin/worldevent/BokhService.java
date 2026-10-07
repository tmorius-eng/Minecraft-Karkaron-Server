package mn.suld.plugin.worldevent;

import mn.suld.api.worldevent.WrestlingTitle;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Наадам · Бөх барилдаан (docs/world/NAADAM.md): the wrestling of the Three Manly Games (VERIFIED tradition; the
 * winner performs the eagle dance, дэвэх). Two players agree to a bout with {@code /barildaan <player>}; a ring of
 * 4.5 blocks is drawn in particles where the challenger stands (the world is never changed). Nobody is hurt: a hit
 * pushes, harder the more charged it is, and whoever leaves the ring or falls from it loses. Wins climb the
 * traditional titles (Начин … Аварга, {@link WrestlingTitle}); there are no coins, so two accounts gain nothing by
 * trading wins but a title on themselves.
 */
public final class BokhService implements TabExecutor, Listener {

    private static final double RADIUS = 4.5, DROP = 1.5, REACH = 10;
    private static final int BOUT_S = 90, COUNTDOWN_TICKS = 60, REQUEST_S = 30;

    private enum Phase { SETUP, LIVE }

    private static final class Bout {
        final UUID a, b;
        final Location centre;
        Phase phase = Phase.SETUP;
        long liveAt, endsAt;
        int task = -1, ticks;

        Bout(UUID a, UUID b, Location centre) {
            this.a = a;
            this.b = b;
            this.centre = centre;
        }

        UUID other(UUID id) {
            return id.equals(a) ? b : a;
        }
    }

    private record Request(UUID challenger, long expires) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final NamespacedKey winsKey, lossesKey;
    private final Map<UUID, Bout> bouts = new HashMap<>();
    private final Map<UUID, Request> requests = new HashMap<>(); // target -> challenge

    public BokhService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.winsKey = new NamespacedKey(plugin, "bokh_wins");
        this.lossesKey = new NamespacedKey(plugin, "bokh_losses");
    }

    public boolean inBout(UUID id) {
        return bouts.containsKey(id);
    }

    // ------------------------------------------------------------------ challenge

    /** Why {@code p} cannot wrestle now, or null. */
    private String problem(Player p) {
        if (bouts.containsKey(p.getUniqueId())) return p.getName() + " барилдаж байна.";
        if (services.isSoul.test(p.getUniqueId())) return p.getName() + " сүнс байна.";
        if (services.dungeons() != null && services.dungeons().isInAnyRun(p.getUniqueId())) return p.getName() + " агуйд байна.";
        if (p.isInsideVehicle() || p.isFlying() || p.isGliding()) return p.getName() + " газар дээр зогсох ёстой.";
        return null;
    }

    private void challenge(Player p, Player o) {
        if (o == null || o.equals(p)) {
            p.sendMessage(Messages.error("Хэнтэй барилдах вэ? /barildaan <тоглогч>"));
            return;
        }
        String why = problem(p);
        if (why == null) why = problem(o);
        if (why == null && (!p.getWorld().equals(o.getWorld()) || p.getLocation().distance(o.getLocation()) > REACH)) {
            why = o.getName() + " " + (int) REACH + " блок дотор байх ёстой.";
        }
        if (why == null && !ringClear(p.getLocation())) why = "Дэвжээнд зай хүрэлцэхгүй — 9×9 задгай тэгш газар сонго.";
        if (why != null) {
            p.sendMessage(Messages.error(why));
            return;
        }
        requests.put(o.getUniqueId(), new Request(p.getUniqueId(), System.currentTimeMillis() + REQUEST_S * 1000L));
        p.sendMessage(Messages.info(o.getName() + "-г барилдаанд урилаа (" + REQUEST_S + " с)."));
        o.sendMessage(Messages.accent(p.getName() + " тантай барилдахыг хүсэж байна — /barildaan accept (хохиролгүй, дэвжээнээс түлхэх)"));
    }

    private void accept(Player o) {
        Request r = requests.remove(o.getUniqueId());
        Player p = r == null ? null : Bukkit.getPlayer(r.challenger());
        if (r == null || r.expires() < System.currentTimeMillis() || p == null) {
            o.sendMessage(Messages.error("Хүлээгдэж буй барилдааны урилга алга."));
            return;
        }
        String why = problem(p);
        if (why == null) why = problem(o);
        if (why == null && (!p.getWorld().equals(o.getWorld()) || p.getLocation().distance(o.getLocation()) > REACH)) why = "Хэт хол байна.";
        if (why == null && !ringClear(p.getLocation())) why = "Дэвжээнд зай хүрэлцэхгүй.";
        if (why != null) {
            o.sendMessage(Messages.error(why));
            p.sendMessage(Messages.error(why));
            return;
        }
        begin(p, o);
    }

    /** Open, level ground: feet and head room free over the ring, solid floor under nearly all of it. */
    private static boolean ringClear(Location at) {
        int bx = at.getBlockX(), by = at.getBlockY(), bz = at.getBlockZ(), floor = 0, cells = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dz * dz > 20) continue;
                cells++;
                Block feet = at.getWorld().getBlockAt(bx + dx, by, bz + dz);
                if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable() || feet.isLiquid()) return false;
                if (!feet.getRelative(0, -1, 0).isPassable()) floor++;
            }
        }
        return floor >= cells * 0.85;
    }

    // ------------------------------------------------------------------ the bout

    private void begin(Player a, Player b) {
        Location c = a.getLocation().toBlockLocation().add(0.5, 0, 0.5);
        c.setYaw(0);
        c.setPitch(0);
        Bout bout = new Bout(a.getUniqueId(), b.getUniqueId(), c);
        bouts.put(a.getUniqueId(), bout);
        bouts.put(b.getUniqueId(), bout);
        Location sa = c.clone().add(-2.5, 0, 0), sb = c.clone().add(2.5, 0, 0);
        sa.setYaw(-90);
        sb.setYaw(90);
        a.teleportAsync(sa);
        b.teleportAsync(sb);
        for (Player p : List.of(a, b)) {
            p.showTitle(Title.title(Component.text("Бөх барилдаан", NamedTextColor.GOLD, TextDecoration.BOLD),
                    Component.text("Дэвжээнээс түлхэж гарга · 3 секунд", NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2600), Duration.ofMillis(300))));
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.5f);
        }
        for (Player near : c.getWorld().getPlayers()) {
            if (near.getLocation().distanceSquared(c) < 48 * 48 && !bouts.containsKey(near.getUniqueId())) {
                near.sendMessage(Messages.accent("Бөх барилдаан: " + a.getName() + " ба " + b.getName() + "!"));
            }
        }
        bout.liveAt = System.currentTimeMillis() + COUNTDOWN_TICKS * 50L;
        bout.endsAt = bout.liveAt + BOUT_S * 1000L;
        bout.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(bout), 2L, 2L).getTaskId();
    }

    private void tick(Bout bout) {
        bout.ticks += 2;
        long now = System.currentTimeMillis();
        if (bout.ticks % 10 == 0) ring(bout.centre);
        Player a = Bukkit.getPlayer(bout.a), b = Bukkit.getPlayer(bout.b);
        if (a == null || b == null) {
            end(bout, a == null ? bout.b : bout.a, "өрсөлдөгч гарсан");
            return;
        }
        if (bout.phase == Phase.SETUP) {
            if (now >= bout.liveAt) {
                bout.phase = Phase.LIVE;
                for (Player p : List.of(a, b)) {
                    p.showTitle(Title.title(Component.text("Барь!", NamedTextColor.RED, TextDecoration.BOLD), Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(200))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.6f);
                }
            }
            return;
        }
        for (Player p : List.of(a, b)) {
            Location l = p.getLocation();
            if (!l.getWorld().equals(bout.centre.getWorld())
                    || WrestlingTitle.outOfRing(l.getX() - bout.centre.getX(), l.getY() - bout.centre.getY(), l.getZ() - bout.centre.getZ(), RADIUS, DROP)) {
                end(bout, bout.other(p.getUniqueId()), p.getName() + " дэвжээнээс гарлаа");
                return;
            }
        }
        long left = Math.max(0, (bout.endsAt - now) / 1000);
        if (bout.ticks % 20 == 0) {
            Component bar = Component.text("Бөх · " + left / 60 + ":" + String.format(Locale.ROOT, "%02d", left % 60), NamedTextColor.GOLD);
            a.sendActionBar(bar);
            b.sendActionBar(bar);
        }
        if (now >= bout.endsAt) end(bout, null, "цаг дууссан");
    }

    private static void ring(Location c) {
        Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(0x2F6FD6), 1.3f);
        for (int k = 0; k < 28; k++) {
            double t = k * Math.PI * 2 / 28;
            c.getWorld().spawnParticle(Particle.DUST, c.getX() + Math.cos(t) * RADIUS, c.getY() + 0.15, c.getZ() + Math.sin(t) * RADIUS,
                    1, 0, 0, 0, 0, blue);
        }
    }

    /** Ends the bout: {@code winner} null is a draw. */
    private void end(Bout bout, UUID winner, String why) {
        if (bouts.get(bout.a) != bout) return;
        bouts.remove(bout.a);
        bouts.remove(bout.b);
        if (bout.task != -1) Bukkit.getScheduler().cancelTask(bout.task);
        Player a = Bukkit.getPlayer(bout.a), b = Bukkit.getPlayer(bout.b);
        if (winner == null) {
            for (Player p : new Player[]{a, b}) if (p != null) p.sendMessage(Messages.info("Барилдаан тэнцлээ (" + why + ")."));
            return;
        }
        Player w = Bukkit.getPlayer(winner), l = Bukkit.getPlayer(bout.other(winner));
        if (w == null) return;
        int wins = add(w, winsKey);
        if (l != null) add(l, lossesKey);
        WrestlingTitle before = WrestlingTitle.forWins(wins - 1), now = WrestlingTitle.forWins(wins);
        String loser = l != null ? l.getName() : "өрсөлдөгч";
        for (Player near : bout.centre.getWorld().getPlayers()) {
            if (near.getLocation().distanceSquared(bout.centre) < 48 * 48) {
                near.sendMessage(Messages.success(w.getName() + " " + loser + "-г давлаа (" + why + ") — бүргэд дэвэв!"));
            }
        }
        if (now != before) {
            Bukkit.broadcast(Messages.accent("Бөхийн цол: " + w.getName() + " «" + now.label() + "» цолтой боллоо!"));
        }
        if (l != null) l.sendMessage(Messages.info("Энэ удаа " + w.getName() + " давлаа. Бөх ахин барилдана."));
        eagleDance(w);
    }

    private static int add(Player p, NamespacedKey key) {
        int n = p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0) + 1;
        p.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, n);
        return n;
    }

    /** The eagle dance (дэвэх): wings of cloud rise and beat around the winner for two seconds. */
    private void eagleDance(Player w) {
        w.playSound(w.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 0.8f);
        int[] step = {0};
        int[] id = {-1};
        id[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!w.isOnline() || step[0]++ >= 10) {
                Bukkit.getScheduler().cancelTask(id[0]);
                return;
            }
            Location l = w.getLocation().add(0, 1.4, 0);
            Vector side = l.getDirection().setY(0).normalize().crossProduct(new Vector(0, 1, 0));
            double lift = Math.sin(step[0] * 0.9) * 0.5;
            for (int k = 1; k <= 6; k++) {
                double r = k * 0.28;
                for (int s = -1; s <= 1; s += 2) {
                    Location f = l.clone().add(side.clone().multiply(s * r)).add(0, lift * k / 6.0 - k * 0.04, 0);
                    w.getWorld().spawnParticle(Particle.CLOUD, f, 1, 0, 0, 0, 0);
                }
            }
            if (step[0] % 3 == 0) w.getWorld().playSound(l, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.2f);
        }, 0L, 4L).getTaskId();
    }

    // ------------------------------------------------------------------ rules during a bout

    /** A hit between the two wrestlers pushes instead of hurting; nobody else takes part. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        Bout bout = bouts.get(p.getUniqueId());
        Bout target = e.getAttacked() instanceof Player t ? bouts.get(t.getUniqueId()) : null;
        if (bout == null && target == null) return;
        e.setCancelled(true);
        if (bout == null || bout != target || bout.phase != Phase.LIVE) return;
        Player o = (Player) e.getAttacked();
        float charge = p.getAttackCooldown();
        p.resetCooldown(); // the cancelled swing still costs its charge: no spam pushing
        Vector dir = o.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() < 1.0e-4) dir = p.getLocation().getDirection().setY(0);
        dir.normalize().multiply(0.25 + 0.85 * charge * charge).setY(0.16 + 0.14 * charge);
        o.setVelocity(o.getVelocity().add(dir));
        o.getWorld().playSound(o.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 0.9f + charge * 0.3f);
        o.getWorld().spawnParticle(Particle.SWEEP_ATTACK, o.getLocation().add(0, 1, 0), 1);
    }

    /** Wrestlers take no damage of any kind during a bout (a fall from the ring loses, it does not hurt). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && bouts.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Bout bout = bouts.get(e.getPlayer().getUniqueId());
        if (bout != null && bout.phase == Phase.LIVE) end(bout, bout.other(e.getPlayer().getUniqueId()), e.getPlayer().getName() + " зөөгдсөн");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        requests.remove(id);
        Bout bout = bouts.get(id);
        if (bout != null) end(bout, bout.other(id), e.getPlayer().getName() + " гарсан");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Bout bout = bouts.get(e.getEntity().getUniqueId());
        if (bout != null) end(bout, bout.other(e.getEntity().getUniqueId()), e.getEntity().getName() + " унасан");
    }

    public void shutdown() {
        for (Bout b : new ArrayList<>(bouts.values())) end(b, null, "сервер унтарч байна");
    }

    // ------------------------------------------------------------------ /barildaan

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("stats")) {
            int wins = p.getPersistentDataContainer().getOrDefault(winsKey, PersistentDataType.INTEGER, 0);
            int losses = p.getPersistentDataContainer().getOrDefault(lossesKey, PersistentDataType.INTEGER, 0);
            WrestlingTitle t = WrestlingTitle.forWins(wins), n = t.next();
            p.sendMessage(Messages.accent("Бөх барилдаан — " + wins + " ялалт, " + losses + " ялагдал"
                    + (t == WrestlingTitle.NONE ? "" : " · цол «" + t.label() + "»")));
            if (n != null) p.sendMessage(Messages.info("Дараагийн цол «" + n.label() + "»: дахиад " + (n.wins() - wins) + " ялалт."));
            p.sendMessage(Messages.info("/barildaan <тоглогч> — урих · /barildaan accept · /barildaan deny"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "accept" -> accept(p);
            case "deny" -> {
                Request r = requests.remove(p.getUniqueId());
                Player c = r == null ? null : Bukkit.getPlayer(r.challenger());
                p.sendMessage(Messages.info(r == null ? "Урилга алга." : "Татгалзлаа."));
                if (c != null) c.sendMessage(Messages.info(p.getName() + " барилдаанаас татгалзлаа."));
            }
            default -> challenge(p, Bukkit.getPlayerExact(args[0]));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        List<String> out = new ArrayList<>(List.of("accept", "deny", "stats"));
        for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(sender)) out.add(o.getName());
        String pre = args[0].toLowerCase(Locale.ROOT);
        return out.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(pre)).toList();
    }
}
