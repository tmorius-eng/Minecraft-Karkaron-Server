package mn.suld.plugin.command;

import mn.suld.api.relic.RelicDefinition;
import mn.suld.api.relic.RelicRecord;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.relic.RelicService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * {@code /relic [list|info <key>|hint|history <key>]} for everyone;
 * {@code setshrine|return|give|recover|tp} for admins ({@code suld.admin.relic}).
 */
public final class RelicCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("list", "info", "hint", "history");
    private static final List<String> ADMIN_SUBS = List.of("setshrine", "return", "give", "recover", "tp");

    private final RelicService relics;

    public RelicCommand(RelicService relics) {
        this.relics = relics;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        String key = args.length > 1 ? args[1] : null;
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission("suld.admin.relic")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return true;
        }
        switch (sub) {
            case "list" -> list(sender);
            case "info" -> info(sender, key);
            case "hint" -> {
                if (sender instanceof Player p) relics.hint(p); else sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            }
            case "history" -> {
                if (key == null) sender.sendMessage(Messages.error("/relic history <түлхүүр>")); else relics.history(sender, key);
            }
            case "setshrine" -> {
                if (!(sender instanceof Player p) || key == null) sender.sendMessage(Messages.error("/relic setshrine <түлхүүр> (тоглогчоор)"));
                else relics.adminSetShrine(p, key);
            }
            case "return" -> {
                if (key == null) sender.sendMessage(Messages.error("/relic return <түлхүүр>")); else relics.adminReturn(sender, key);
            }
            case "give" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (key == null || target == null) sender.sendMessage(Messages.error("/relic give <түлхүүр> <онлайн тоглогч>"));
                else relics.adminGive(sender, key, target);
            }
            case "recover" -> {
                if (key == null) sender.sendMessage(Messages.error("/relic recover <түлхүүр>")); else relics.adminRecover(sender, key);
            }
            case "tp" -> {
                RelicRecord r = key == null ? null : relics.record(key).orElse(null);
                Location loc = r == null ? null : relics.shrineLocation(r);
                if (!(sender instanceof Player p) || loc == null) sender.sendMessage(Messages.error("Сүм олдсонгүй."));
                else p.teleport(loc.clone().add(0.5, 0, 2.5));
            }
            default -> sender.sendMessage(Messages.info("/relic <" + String.join("|", PLAYER_SUBS) + ">"));
        }
        return true;
    }

    private void list(CommandSender sender) {
        sender.sendMessage(Messages.accent("Цор ганц сүлднүүд — серверт тус бүр ганцхан"));
        for (RelicDefinition def : SuldContent.RELICS) {
            RelicRecord r = relics.record(def.key()).orElse(null);
            if (r == null) continue;
            String state = r.isAvailable()
                    ? (r.shrine() == null ? "сүм хараахан босоогүй" : "сүмдээ эзнээ хүлээж байна")
                    : "эзэн: " + r.ownerName() + since(r.acquiredAt());
            sender.sendMessage(Messages.info(def.displayName() + " (" + def.key().substring(6) + ") — " + state));
        }
        sender.sendMessage(Messages.info("/relic info <түлхүүр>, /relic hint"));
    }

    private void info(CommandSender sender, String key) {
        RelicDefinition def = key == null ? null : SuldContent.relicFor(key);
        RelicRecord r = def == null ? null : relics.record(def.key()).orElse(null);
        if (r == null) {
            sender.sendMessage(Messages.error("Ийм сүлд алга. /relic list"));
            return;
        }
        sender.sendMessage(Messages.accent(def.displayName()));
        sender.sendMessage(Messages.info(def.lore()));
        sender.sendMessage(Messages.info("Шаардлага: түвшин " + def.minLevel() + " · эзэмшигчид +" + Math.round(def.expBonus() * 100)
                + "% EXP · эзэмшигч гэрэлтэнэ"));
        sender.sendMessage(Messages.info(r.isAvailable() ? "Одоо: сүмдээ" : "Одоо: " + r.ownerName() + since(r.acquiredAt())));
        if (sender.hasPermission("suld.admin.relic")) {
            sender.sendMessage(Messages.info("item_uuid=" + r.itemUuid() + " үе=" + r.version()
                    + (r.shrine() == null ? " сүм=—" : " сүм=" + r.shrine().world() + " " + r.shrine().x() + " " + r.shrine().y() + " " + r.shrine().z())));
        }
    }

    private static String since(Instant at) {
        if (at == null) return "";
        Duration d = Duration.between(at, Instant.now());
        return d.toHours() >= 1 ? " (" + d.toHours() + " цаг)" : " (" + Math.max(1, d.toMinutes()) + " мин)";
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> subs = sender.hasPermission("suld.admin.relic")
                ? java.util.stream.Stream.concat(PLAYER_SUBS.stream(), ADMIN_SUBS.stream()).toList() : PLAYER_SUBS;
        if (args.length == 1) {
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("hint") && !args[0].equalsIgnoreCase("list")) {
            return SuldContent.RELICS.stream().map(d -> d.key().substring(6)).filter(k -> k.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
