package mn.suld.plugin.command;

import mn.suld.api.audit.AuditEvent;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.item.ClassWeapons;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /classgear recover} — get a lost class weapon back (docs/CLASS_GEAR_SYSTEM.md). Idempotent: nothing happens
 * when the player still has it; the rebuilt weapon keeps its old identity. Players: once per 10 minutes (anti-spam).
 * Staff: {@code /classgear recover <player>} ({@code suld.admin.classgear}). Every restore is audited.
 */
public final class ClassGearCommand implements TabExecutor {

    private static final long COOLDOWN_MS = 10 * 60_000L;

    private final SuldServices services;
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public ClassGearCommand(SuldServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("recover")) {
            sender.sendMessage(Messages.info("/classgear recover — алдсан ангийн зэвсгээ сэргээх"));
            return true;
        }
        Player target;
        boolean staff = args.length > 1;
        if (staff) {
            if (!sender.hasPermission("suld.admin.classgear")) {
                sender.sendMessage(Messages.error("Танд энэ үйлдэл хийх эрх алга."));
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Messages.error("Тоглогч онлайн биш: " + args[1]));
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
            Long t = last.get(p.getUniqueId());
            if (t != null && System.currentTimeMillis() - t < COOLDOWN_MS) {
                long left = (COOLDOWN_MS - (System.currentTimeMillis() - t) + 59_999) / 60_000;
                sender.sendMessage(Messages.error("Дахин " + left + " минутын дараа оролдоно уу."));
                return true;
            }
        } else {
            sender.sendMessage(Messages.info("/classgear recover <тоглогч>"));
            return true;
        }
        if (services.isSoul.test(target.getUniqueId())) {
            sender.sendMessage(Messages.error("Сүнс байхдаа сэргээх боломжгүй."));
            return true;
        }
        ClassWeapons.Recovery r = services.classWeapons().recover(target);
        if (!staff && r == ClassWeapons.Recovery.RESTORED) last.put(target.getUniqueId(), System.currentTimeMillis()); // a no-op costs nothing
        switch (r) {
            case HAS_IT -> sender.sendMessage(Messages.info(target.getName() + " ангийн зэвсгээ авч яваа байна — сэргээх шаардлагагүй."));
            case NO_CLASS -> sender.sendMessage(Messages.error("Эхлээд ангиа сонгоно уу (/class)."));
            case NO_ROOM -> sender.sendMessage(Messages.error("Цүнхэнд нэг сул зай гаргаад дахин оролдоно уу."));
            case RESTORED -> {
                sender.sendMessage(Messages.success("Ангийн зэвсэг сэргээгдлээ" + (staff ? ": " + target.getName() : ".")));
                if (staff) target.sendMessage(Messages.success("Таны ангийн зэвсгийг сэргээлээ."));
                services.equipment().dirty(target);
                services.audit().record(AuditEvent.of(sender instanceof Player p ? p.getUniqueId().toString() : "console",
                        "classgear.recover", target.getUniqueId().toString(), staff ? "staff" : "self"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return List.of("recover");
        if (args.length == 2 && sender.hasPermission("suld.admin.classgear")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }
}
