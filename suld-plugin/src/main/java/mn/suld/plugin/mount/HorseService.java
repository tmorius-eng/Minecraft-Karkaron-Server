package mn.suld.plugin.mount;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.style.Rank;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /mori}: a personal steppe horse (Монгол морь). From level {@value #MIN_LEVEL}; it is faster and changes
 * coat with the player's rank, cannot be hurt, ridden by others, bred or looted, and disappears as soon as its rider
 * gets off (or leaves, teleports or enters a dungeon). Not in dungeons; a short cooldown stops spam.
 */
public final class HorseService implements Listener, TabExecutor {

    public static final int MIN_LEVEL = 5;
    private static final long COOLDOWN_MS = 5_000;
    private static final Horse.Color[] COATS = {Horse.Color.BROWN, Horse.Color.CHESTNUT, Horse.Color.DARK_BROWN, Horse.Color.BLACK,
            Horse.Color.GRAY, Horse.Color.CREAMY, Horse.Color.WHITE, Horse.Color.WHITE};

    private final Plugin plugin;
    private final SuldServices services;
    private final NamespacedKey key;
    private final Map<UUID, UUID> horses = new ConcurrentHashMap<>(); // rider → horse
    private final Map<UUID, Long> lastCall = new ConcurrentHashMap<>();

    public HorseService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
        this.key = new NamespacedKey(plugin, "steppe_horse");
    }

    /** Movement speed by rank: vanilla horses are 0.1125–0.3375; Ард starts at 0.24, Хаан reaches 0.33. */
    static double speed(Rank rank) {
        return 0.24 + rank.ordinal() * 0.013;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        UUID current = horses.get(p.getUniqueId());
        Entity alive = current == null ? null : Bukkit.getEntity(current);
        if (alive != null && alive.isValid()) {
            dismiss(p.getUniqueId());
            p.sendMessage(Messages.info("Морио тайвшрууллаа."));
            return true;
        }
        horses.remove(p.getUniqueId()); // the horse vanished some other way: forget it and call a new one
        summon(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return List.of();
    }

    public void summon(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !pr.hasSelectedClass()) {
            p.sendMessage(Messages.error("Эхлээд ангиа сонго: /class"));
            return;
        }
        if (pr.progression().level() < MIN_LEVEL) {
            p.sendMessage(Messages.error("Морь " + MIN_LEVEL + "-р түвшинд нээгдэнэ."));
            return;
        }
        if (services.dungeons().isInAnyRun(p.getUniqueId())) {
            p.sendMessage(Messages.error("Агуйд морь дуудах боломжгүй."));
            return;
        }
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс байхдаа морь дуудах боломжгүй."));
            return;
        }
        if (p.isInsideVehicle() || p.isFlying() || p.isGliding() || !p.isOnGround()) {
            p.sendMessage(Messages.error("Газар дээр зогсож байж морио дууд."));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastCall.get(p.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            p.sendMessage(Messages.info("Морь удахгүй ирнэ…"));
            return;
        }
        lastCall.put(p.getUniqueId(), now);
        Rank rank = services.styles().of(p.getUniqueId()).rank();
        Horse h = p.getWorld().spawn(p.getLocation(), Horse.class, horse -> {
            horse.setTamed(true);
            horse.setOwner(p);
            horse.setAdult();
            horse.setAgeLock(true);
            horse.setBreed(false);
            horse.setColor(COATS[Math.min(COATS.length - 1, rank.ordinal())]);
            horse.setStyle(rank.ordinal() >= 4 ? Horse.Style.WHITE : Horse.Style.NONE);
            horse.setJumpStrength(0.7 + rank.ordinal() * 0.02);
            horse.setInvulnerable(true);
            horse.setPersistent(false);
            horse.setRemoveWhenFarAway(true);
            horse.setSilent(false);
            horse.customName(Component.text(p.getName() + "-ийн морь", TextColor.fromHexString("#FFD24A")));
            horse.setCustomNameVisible(false);
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            AttributeInstance sp = horse.getAttribute(Attribute.MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(speed(rank));
            horse.getPersistentDataContainer().set(key, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        horses.put(p.getUniqueId(), h.getUniqueId());
        h.addPassenger(p);
        p.playSound(p.getLocation(), Sound.ENTITY_HORSE_AMBIENT, 1f, 1f);
        p.getWorld().spawnParticle(Particle.CLOUD, h.getLocation().add(0, 1, 0), 20, 0.5, 0.4, 0.5, 0.02);
        p.sendActionBar(Component.text("Монгол морь · " + rank.displayName() + " · /mori — буух", TextColor.fromHexString("#FFD24A")));
    }

    private boolean isSteppeHorse(Entity e) {
        return e instanceof Horse && e.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    /** Sends the player's horse away (dungeon entry, death…). */
    public void dismissFor(UUID rider) {
        dismiss(rider);
    }

    private void dismiss(UUID rider) {
        UUID hid = horses.remove(rider);
        if (hid == null) return;
        Entity e = Bukkit.getEntity(hid);
        if (e != null) {
            e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, 1, 0), 15, 0.5, 0.4, 0.5, 0.02);
            e.remove();
        }
    }

    public void shutdown() {
        for (UUID rider : List.copyOf(horses.keySet())) dismiss(rider);
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (e.getEntity() instanceof Player p && isSteppeHorse(e.getDismounted())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isInsideVehicle()) dismiss(p.getUniqueId());
            }, 20L);
        }
    }

    /** Nobody else rides, feeds or opens it. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!isSteppeHorse(e.getRightClicked())) return;
        String owner = e.getRightClicked().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (!e.getPlayer().getUniqueId().toString().equals(owner) || e.getPlayer().isSneaking()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (isSteppeHorse(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (isSteppeHorse(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (horses.containsKey(e.getPlayer().getUniqueId()) && e.getCause() != PlayerTeleportEvent.TeleportCause.DISMOUNT) {
            dismiss(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismiss(e.getPlayer().getUniqueId());
        lastCall.remove(e.getPlayer().getUniqueId());
    }
}
