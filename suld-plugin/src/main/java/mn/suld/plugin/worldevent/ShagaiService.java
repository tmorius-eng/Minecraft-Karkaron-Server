package mn.suld.plugin.worldevent;

import mn.suld.api.worldevent.Shagai;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Шагай буулгах (docs/world/SHAGAI.md): once a day (UTC) a player casts four ankle bones with {@code /shagai}. The
 * faces are read by {@link Shagai} (pure, tested); horses bring a small EXP blessing for a while (at most +10 % for an
 * hour, on average about 1–2 %). The blessing is its own source in ProgressionBoosts, so it adds to an ovoo's, and it
 * is kept on the player across relogs.
 */
public final class ShagaiService implements TabExecutor, Listener {

    private final SuldServices services;
    private final NamespacedKey dayKey, untilKey, bonusKey;

    public ShagaiService(Plugin plugin, SuldServices services) {
        this.services = services;
        this.dayKey = new NamespacedKey(plugin, "shagai_day");
        this.untilKey = new NamespacedKey(plugin, "shagai_until");
        this.bonusKey = new NamespacedKey(plugin, "shagai_bonus");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        if (services.isSoul.test(p.getUniqueId())) {
            p.sendMessage(Messages.error("Сүнс шагай буулгахгүй — эхлээд амил."));
            return true;
        }
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        Long last = p.getPersistentDataContainer().get(dayKey, PersistentDataType.LONG);
        if (last != null && last == today) {
            p.sendMessage(Messages.info("Өнөөдөр шагайгаа буулгачихсан. Маргааш (UTC) дахин."));
            return true;
        }
        p.getPersistentDataContainer().set(dayKey, PersistentDataType.LONG, today);
        Shagai.Face[] bones = Shagai.cast(ThreadLocalRandom.current());
        Shagai.Fortune f = Shagai.read(bones);
        StringJoiner faces = new StringJoiner(" · ");
        for (Shagai.Face b : bones) faces.add(b.label());
        p.playSound(p.getLocation(), Sound.BLOCK_BONE_BLOCK_PLACE, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(p.getLocation().getDirection().setY(0).normalize()).add(0, 0.2, 0), 12, 0.3, 0.05, 0.3, 0.05);
        p.showTitle(Title.title(Component.text(faces.toString(), NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(f.name(), NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2800), Duration.ofMillis(500))));
        if (f.minutes() > 0) {
            long until = System.currentTimeMillis() + f.minutes() * 60_000L;
            services.boosts().bless(p.getUniqueId(), "shagai", until, f.bonus());
            p.getPersistentDataContainer().set(untilKey, PersistentDataType.LONG, until);
            p.getPersistentDataContainer().set(bonusKey, PersistentDataType.DOUBLE, f.bonus());
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            p.sendMessage(Messages.success("Шагай: " + faces + " — " + f.name() + " (+" + Math.round(f.bonus() * 100) + "% EXP " + f.minutes() + " мин)"));
        } else {
            p.sendMessage(Messages.info("Шагай: " + faces + " — " + f.name()));
        }
        return true;
    }

    /** A blessing still running when the player left comes back on join. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Long until = p.getPersistentDataContainer().get(untilKey, PersistentDataType.LONG);
        Double bonus = p.getPersistentDataContainer().get(bonusKey, PersistentDataType.DOUBLE);
        if (until != null && bonus != null && until > System.currentTimeMillis()) services.boosts().bless(p.getUniqueId(), "shagai", until, bonus);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return List.of();
    }
}
