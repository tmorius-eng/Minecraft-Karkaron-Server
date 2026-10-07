package mn.suld.plugin.command;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.DungeonRun;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /dungeon list|enter [id]|status|leave|abort}. */
public final class DungeonCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("list", "enter", "status", "leave", "abort");

    private final SuldServices services;

    public DungeonCommand(SuldServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("grant")) { // /dungeon grant <player> <dungeon|all>: support
            if (!sender.hasPermission("suld.admin.world")) {
                sender.sendMessage(Messages.error("Эрх алга."));
                return true;
            }
            Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Messages.error("Тоглогч онлайн биш."));
                return true;
            }
            int n = 0;
            for (DungeonDefinition d : mn.suld.plugin.content.DungeonContent.ALL) {
                if (args[2].equalsIgnoreCase("all") || shortId(d).equalsIgnoreCase(args[2]) || d.id().equalsIgnoreCase(args[2])) {
                    services.dungeons().grantClear(target, d.id());
                    n++;
                }
            }
            sender.sendMessage(n == 0 ? Messages.error("Ийм агуй алга.") : Messages.success(target.getName() + ": " + n + " агуй давсанд тооцлоо."));
            services.audit().record(mn.suld.api.audit.AuditEvent.of(sender.getName(), "dungeon.grant", target.getName(), args[2]));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч ашиглана."));
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> list(player);
            case "enter" -> enter(player, args.length > 1 ? args[1] : "khasar_den");
            case "status" -> status(player);
            case "leave" -> services.parties().leave(player, true);
            case "abort" -> {
                boolean admin = player.hasPermission("suld.admin");
                if (!services.dungeons().abort(player.getUniqueId(), admin)) {
                    player.sendMessage(Messages.error("Зогсоох агуй алга (эсвэл та ахлагч биш)."));
                }
            }
            default -> player.sendMessage(Messages.info("/dungeon <list|enter|status|leave|abort>"));
        }
        return true;
    }

    private void list(Player player) {
        player.sendMessage(Messages.accent("Агуйнууд — хаалга дээр нь очиж орно"));
        int level = services.profiles().cached(player.getUniqueId()).map(p -> p.progression().level()).orElse(1);
        var halls = services.dungeons().halls().orElse(null);
        for (DungeonDefinition d : mn.suld.plugin.content.DungeonContent.ALL) {
            DungeonDefinition prev = mn.suld.plugin.content.DungeonContent.previous(d.id());
            boolean done = services.dungeons().cleared(player, d.id());
            boolean open = level >= d.minLevel() && (prev == null || services.dungeons().cleared(player, prev.id()));
            String mark = done ? "✔ " : open ? "▶ " : "🔒 ";
            String gate = "";
            if (halls != null) {
                var site = halls.site(d.id()).orElse(null);
                if (site != null) {
                    int[] xz = halls.gateXZ(site);
                    double b = mn.suld.api.region.Navigation.bearing(xz[0] - player.getLocation().getX(), xz[1] - player.getLocation().getZ());
                    int dist = (int) Math.hypot(xz[0] - player.getLocation().getX(), xz[1] - player.getLocation().getZ());
                    gate = " · хаалга " + xz[0] + ", " + xz[1] + " (" + mn.suld.api.region.Navigation.compass(b) + ", " + dist + " блок)";
                }
            }
            String lock = open || done ? "" : level < d.minLevel() ? " · түвшин " + d.minLevel() + " хэрэгтэй" : " · эхлээд «" + prev.displayName() + "»";
            player.sendMessage(Messages.info(mark + shortId(d) + " — " + d.displayName() + " · " + mn.suld.plugin.content.DungeonContent.where(d.id())
                    + " (түвшин " + d.minLevel() + "+, " + d.minPartySize() + "–" + d.maxPartySize() + " тоглогч, "
                    + d.totalWaves() + " давалгаа + босс)" + gate + lock));
        }
        player.sendMessage(Messages.info("Хаалгыг дарах эсвэл хаалган дээр /dungeon enter <нэр>"));
    }

    private void enter(Player player, String rawId) {
        String id = rawId.startsWith("dungeon.") ? rawId : "dungeon." + rawId;
        DungeonDefinition def = SuldContent.dungeonFor(id);
        if (def == null) {
            player.sendMessage(Messages.error("Ийм агуй олдсонгүй. /dungeon list"));
            return;
        }
        Component error = services.dungeons().start(player, def);
        if (error != null) {
            player.sendMessage(error);
        }
    }

    private static String shortId(DungeonDefinition d) {
        return d.id().startsWith("dungeon.") ? d.id().substring("dungeon.".length()) : d.id();
    }

    private void status(Player player) {
        Optional<DungeonRun> run = services.dungeons().runFor(player.getUniqueId());
        if (run.isEmpty()) {
            player.sendMessage(Messages.info("Та агуйд байхгүй байна."));
            return;
        }
        DungeonRun r = run.get();
        player.sendMessage(Messages.accent("Агуй: " + r.dungeonId()));
        player.sendMessage(Messages.info("Төлөв: " + services.dungeons().statusLine(player.getUniqueId()).orElse("—")
                + " · " + r.elapsedSeconds() + "с"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("enter")) {
            return mn.suld.plugin.content.DungeonContent.ALL.stream().map(DungeonCommand::shortId)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
