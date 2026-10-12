package mn.suld.plugin.command;

import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /report <text>}: a player tells the staff about a bug, a cheater or anything wrong, from inside the game.
 * The report goes to the audit log ({@code suld_audit_log}, action {@code player.report}, with the place) and to
 * every online staff member ({@code suld.badge.helper} and up). One report a minute per player, 200 characters.
 */
public final class ReportCommand implements CommandExecutor {

    static final long COOLDOWN_MS = 60_000;
    static final int MAX_LENGTH = 200;
    static final String STAFF_PERMISSION = "suld.badge.helper";

    private final SuldServices services;
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public ReportCommand(SuldServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Тоглогч л мэдэгдэл илгээнэ."));
            return true;
        }
        String text = String.join(" ", args).strip();
        if (text.length() < 5) {
            p.sendMessage(Messages.info("Хэрэглээ: /report <юу болсныг товч бичнэ үү> (жишээ: «агуйд гацлаа», «X хуурч байна»)"));
            return true;
        }
        if (text.length() > MAX_LENGTH) text = text.substring(0, MAX_LENGTH);
        long now = System.currentTimeMillis();
        Long prev = last.get(p.getUniqueId());
        if (prev != null && now - prev < COOLDOWN_MS) {
            p.sendMessage(Messages.error("Дараагийн мэдэгдлийг " + ((COOLDOWN_MS - (now - prev)) / 1000 + 1) + " секундын дараа илгээнэ үү."));
            return true;
        }
        last.put(p.getUniqueId(), now);
        Location l = p.getLocation();
        String where = l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        services.audit().record(AuditEvent.of(p.getName(), AuditActions.PLAYER_REPORT, where, text));
        int staff = 0;
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.equals(p) || !s.hasPermission(STAFF_PERMISSION)) continue;
            s.sendMessage(Messages.accent("⚑ Мэдэгдэл " + p.getName() + " (" + where + "): " + text));
            staff++;
        }
        Bukkit.getLogger().info("[SULD] [report] " + p.getName() + " @ " + where + ": " + text);
        p.sendMessage(Messages.success("Мэдэгдэл хүлээн авлаа. Баярлалаа!" + (staff > 0 ? " Ажилтан " + staff + " хүн харлаа." : " Ажилтан удахгүй харна.")));
        return true;
    }
}
