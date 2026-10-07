package mn.suld.plugin.command;

import mn.suld.api.config.DeathSettings;
import mn.suld.api.death.DeathLock;
import mn.suld.api.death.DeathRecord;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.death.DeathService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Admin tools of the death system (docs/DEATH_AND_RECOVERY.md), all audited by the DeathService:
 * <pre>
 *   /deathstatus &lt;player&gt;            lock, time left, wound, last 5 deaths            suld.admin.death
 *   /deathinfo &lt;player&gt; [death#]      one death in full                                 suld.admin.death
 *   /deathrevive &lt;player&gt;            end the lock now (wound still applies)            suld.admin.death.revive
 *   /deathreset &lt;player&gt;             end the lock and clear the wound (staff errors)   suld.admin.death.reset
 * </pre>
 * {@code /revive} is the same as {@code /deathrevive}. Offline players are resolved from the server's player cache.
 */
public final class DeathCommands implements TabExecutor {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Ulaanbaatar"));

    private final Plugin plugin;
    private final SuldServices services;
    private final DeathService deaths;

    public DeathCommands(Plugin plugin, SuldServices services, DeathService deaths) {
        this.plugin = plugin;
        this.services = services;
        this.deaths = deaths;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("revive")) cmd = "deathrevive";
        String perm = switch (cmd) {
            case "deathrevive" -> "suld.admin.death.revive";
            case "deathreset" -> "suld.admin.death.reset";
            default -> "suld.admin.death";
        };
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(Messages.error("Танд энэ үйлдэл хийх эрх алга."));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Messages.info("Хэрэглээ: /" + cmd + " <тоглогч>" + (cmd.equals("deathinfo") ? " [үхлийн #]" : "")));
            return true;
        }
        UUID id = resolve(args[0]);
        if (id == null) {
            sender.sendMessage(Messages.error("Тоглогч олдсонгүй: " + args[0]));
            return true;
        }
        String name = args[0];
        String actor = sender instanceof Player p ? p.getUniqueId().toString() : "console";
        switch (cmd) {
            case "deathstatus" -> status(sender, id, name);
            case "deathinfo" -> info(sender, id, name, args.length > 1 ? parse(args[1]) : -1);
            case "deathrevive" -> deaths.recover(id, DeathRecord.State.ADMIN_REVIVED, actor).thenAccept(had -> reply(sender,
                    had ? Messages.success(name + ": түгжээ дууслаа (шарх хэвээр).") : Messages.info(name + " түгжээгүй байна.")));
            case "deathreset" -> deaths.recover(id, DeathRecord.State.RESET, actor).thenAccept(had -> reply(sender,
                    Messages.success(name + ": " + (had ? "түгжээ ба " : "") + "шарх цэвэрлэгдлээ.")));
            default -> sender.sendMessage(Messages.error("?"));
        }
        return true;
    }

    private void status(CommandSender sender, UUID id, String name) {
        DeathSettings s = services.config().death();
        long left = deaths.lockRemaining(id);
        deaths.storedWound(id).thenCombine(deaths.history(id, 5), (w, list) -> {
            StringBuilder sb = new StringBuilder("§b☠ " + name + "§7 — ");
            DeathRecord open = list.stream().filter(DeathRecord::locked).findFirst().orElse(null);
            if (open != null) {
                long l = Math.max(left, open.remaining(System.currentTimeMillis()));
                sb.append("§cТҮГЖЭЭТЭЙ§7 ").append(DeathLock.format(l)).append(" (").append(WHEN.format(Instant.ofEpochMilli(open.lockedUntil()))).append(")");
            } else {
                sb.append("§aтүгжээгүй");
            }
            sb.append("§7 · шарх ").append(w.stacks()).append(" (−").append(w.percent(s)).append(" %)");
            if (w.stacks() > 0) sb.append(", эдгэрэх ").append(Math.round(s.woundHealMinutes() - w.healMinutes())).append(" мин");
            for (DeathRecord r : list) {
                sb.append("\n§7 #").append(r.seq()).append(' ').append(WHEN.format(Instant.ofEpochMilli(r.diedAt())))
                        .append(" L").append(r.level()).append(' ').append(r.cause()).append(" → §f").append(r.state());
            }
            return sb.toString();
        }).whenComplete((text, ex) -> reply(sender, ex != null ? Messages.error("Уншиж чадсангүй: " + ex.getMessage()) : net.kyori.adventure.text.Component.text(text)));
    }

    private void info(CommandSender sender, UUID id, String name, int seq) {
        deaths.history(id, 200).whenComplete((list, ex) -> {
            if (ex != null || list.isEmpty()) {
                reply(sender, Messages.info(name + ": бүртгэгдсэн үхэл алга."));
                return;
            }
            DeathRecord r = seq < 0 ? list.get(0) : list.stream().filter(x -> x.seq() == seq).findFirst().orElse(null);
            if (r == null) {
                reply(sender, Messages.error("#" + seq + " олдсонгүй."));
                return;
            }
            reply(sender, net.kyori.adventure.text.Component.text("§b☠ " + name + " #" + r.seq() + "§7 (" + r.deathId() + ")"
                    + "\n§7 үхсэн: §f" + WHEN.format(Instant.ofEpochMilli(r.diedAt())) + "§7 · түвшин §f" + r.level()
                    + "§7 · шалтгаан §f" + r.cause()
                    + "\n§7 газар: §f" + r.world() + " " + r.x() + " " + r.y() + " " + r.z()
                    + "\n§7 түгжээ: §f" + DeathLock.format(r.lockedUntil() - r.diedAt()) + "§7 → " + WHEN.format(Instant.ofEpochMilli(r.lockedUntil()))
                    + "\n§7 төлөв: §f" + r.state() + (r.recoveredAt() == null ? "" : "§7 (" + WHEN.format(Instant.ofEpochMilli(r.recoveredAt())) + ")")
                    + "§7 · шарх дараа нь §f" + r.woundAfter()));
        });
    }

    private void reply(CommandSender sender, net.kyori.adventure.text.Component c) {
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(c));
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replace("#", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null ? null : cached.getUniqueId();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String p = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
