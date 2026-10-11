package mn.suld.plugin.command;

import mn.suld.plugin.combat.DuelService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** {@code /duel <нэр> | accept | decline | end | status}: a consented fight outside Kharkhorum (docs/GAME_DESIGN.md «PvP»). */
public final class DuelCommand implements TabExecutor {

    private final DuelService duels;

    public DuelCommand(DuelService duels) {
        this.duels = duels;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "accept" -> duels.accept(p);
            case "decline" -> duels.decline(p);
            case "end", "stop" -> duels.end(p);
            case "status" -> p.sendMessage(duels.duelOf(p.getUniqueId())
                    .map(d -> Messages.accent("Тулаанд: " + name(d.other(p.getUniqueId())) + " · дуустал "
                            + Math.max(0, (d.endsAt().toEpochMilli() - System.currentTimeMillis()) / 1000) + " с"))
                    .orElse(Messages.info("SÜLD бол PvE: тоглогчтой зөвхөн зөвшөөрөлтэй тулалдана. /duel <нэр> — урих.")));
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    p.sendMessage(Messages.error("Ийм тоглогч онлайн биш: " + args[0]));
                    return true;
                }
                duels.challenge(p, target);
            }
        }
        return true;
    }

    private static String name(java.util.UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p == null ? "?" : p.getName();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        String a = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new java.util.ArrayList<>(List.of("accept", "decline", "end", "status").stream().filter(s -> s.startsWith(a)).toList());
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(a) && !p.equals(sender)) out.add(p.getName());
        return out;
    }
}
