package mn.suld.plugin.command;

import mn.suld.api.mastery.GatherPlan;
import mn.suld.api.mastery.Profession;
import mn.suld.api.mastery.ProfessionMaterials;
import mn.suld.api.region.Navigation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.mastery.ProfessionService;
import mn.suld.plugin.ui.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /mergejil} (docs/MASTERY_AND_PROFESSIONS.md): the crafts' page; {@code /mergejil near} lists the nearest place
 * of each craft in the chat; {@code /mergejil refine <id> [all|n]} refines (ore into the smith's iron and gold, wool
 * into felt) without the page.
 */
public final class ProfessionCommand implements TabExecutor {

    private final SuldServices services;

    public ProfessionCommand(SuldServices services) {
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.error("Зөвхөн тоглогч."));
            return true;
        }
        ProfessionService ps = services.professions;
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> ps.show(p);
            case "near", "oir" -> {
                for (Profession c : Profession.values()) {
                    GatherPlan.Node n = ps.nearest(p, c);
                    if (n == null) {
                        p.sendMessage(Messages.info(c.displayName() + ": ойрхон нээлттэй газар алга."));
                        continue;
                    }
                    double dx = n.x() - p.getLocation().getX(), dz = n.z() - p.getLocation().getZ();
                    p.sendMessage(Messages.info(c.displayName() + " " + ps.level(p.getUniqueId(), c) + ": " + ProfessionService.nodeName(c, n.tier())
                            + " — " + Math.round(Math.hypot(dx, dz)) + " м, " + Navigation.compass(Navigation.bearing(dx, dz))));
                }
            }
            case "refine", "bolovsruulah" -> {
                ProfessionMaterials.Refine r = args.length > 1 ? ProfessionMaterials.refine(args[1].toLowerCase(Locale.ROOT)).orElse(null) : null;
                if (r == null) {
                    List<String> ids = new ArrayList<>();
                    for (ProfessionMaterials.Refine x : ProfessionMaterials.REFINES) ids.add(x.id());
                    p.sendMessage(Messages.error("/mergejil refine <" + String.join("|", ids) + "> [all|тоо]"));
                    return true;
                }
                int times = 1;
                if (args.length > 2) {
                    if (args[2].equalsIgnoreCase("all") || args[2].equalsIgnoreCase("bugd")) times = 0;
                    else {
                        try {
                            times = Math.max(1, Math.min(640, Integer.parseInt(args[2])));
                        } catch (NumberFormatException e) {
                            p.sendMessage(Messages.error("Тоо эсвэл all."));
                            return true;
                        }
                    }
                }
                ps.refine(p, r, times);
            }
            default -> p.sendMessage(Messages.error("/mergejil · /mergejil near · /mergejil refine <id> [all|тоо]"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("near", "refine").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("refine")) {
            List<String> out = new ArrayList<>();
            for (ProfessionMaterials.Refine r : ProfessionMaterials.REFINES) if (r.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(r.id());
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("refine")) return List.of("1", "all");
        return List.of();
    }
}
