package mn.suld.plugin.command;

import mn.suld.api.worldevent.WorldEventDefinition;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.worldevent.WorldEventService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Admin: {@code /suldevent start [wolf_raid] | stop | boss [id] | boss stop | status}. Status is open to everyone and
 * names the next world boss too.
 */
public final class SuldEventCommand implements CommandExecutor, TabCompleter {

    private final WorldEventService events;
    private final mn.suld.plugin.SuldServices services;

    public SuldEventCommand(WorldEventService events, mn.suld.plugin.SuldServices services) {
        this.events = events;
        this.services = services;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("status") && !sender.hasPermission("suld.admin.event")) {
            sender.sendMessage(Messages.error("Эрх алга."));
            return true;
        }
        switch (sub) {
            case "start" -> {
                WorldEventDefinition def = SuldContent.worldEventFor(args.length > 1 ? args[1] : "wolf_raid");
                if (def == null) {
                    sender.sendMessage(Messages.error("Ийм үйл явдал алга. (wolf_raid)"));
                    return true;
                }
                Component error = events.start(def);
                sender.sendMessage(error != null ? error : Messages.success(def.displayName() + " эхэллээ."));
            }
            case "boss" -> {
                var bosses = services.worldBosses;
                if (bosses == null) {
                    sender.sendMessage(Messages.error("Дэлхийн босс ачаалагдаагүй байна."));
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("stop")) {
                    sender.sendMessage(bosses.stop() ? Messages.info("Дэлхийн боссыг шагналгүй буцаалаа.") : Messages.error("Босс алга."));
                    return true;
                }
                var all = bosses.bosses();
                var def = args.length > 1
                        ? all.stream().filter(b -> b.id().equals(args[1]) || b.id().equals("worldboss." + args[1])).findFirst().orElse(null)
                        : all.isEmpty() ? null : all.get(0);
                if (def == null) {
                    sender.sendMessage(Messages.error("Ийм босс алга. (" + String.join(", ", all.stream().map(b -> b.id().substring(10)).toList()) + ")"));
                    return true;
                }
                Component error = bosses.spawn(def, sender.getName());
                sender.sendMessage(error != null ? error : Messages.success(mn.suld.plugin.worldboss.WorldBossService.name(def) + " бослоо."));
            }
            case "stop" -> sender.sendMessage(events.stop()
                    ? Messages.info("Үйл явдлыг зогсоолоо.") : Messages.error("Идэвхтэй үйл явдал алга."));
            default -> {
                List<Component> lines = new java.util.ArrayList<>();
                lines.add(events.active()
                    .map(r -> Messages.accent(r.definition().displayName() + ": " + r.kills() + "/"
                            + r.definition().targetKills() + ", " + r.remainingSeconds(Instant.now()) + "с үлдсэн, "
                            + r.contributions().size() + " оролцогч"))
                    .orElse(Messages.info("Одоогоор үйл явдал алга.")));
                var bosses = services.worldBosses;
                if (bosses != null) {
                    bosses.active().ifPresentOrElse(f -> lines.add(Messages.accent("⚔ " + mn.suld.plugin.worldboss.WorldBossService.name(f.definition())
                                    + " босоод байна: " + f.ranking().size() + " баатар, " + f.elapsedSeconds(Instant.now()) + " с")),
                            () -> bosses.nextText().ifPresent(t -> lines.add(Messages.info("Дараагийн дэлхийн босс: " + t))));
                }
                if (sender instanceof org.bukkit.entity.Player p) {
                    List<Component> hd = new java.util.ArrayList<>();
                    for (Component l : lines) hd.add(mn.suld.plugin.ui.StyleFormat.hd(l));
                    mn.suld.plugin.ui.Pages.text(p, "ДЭЛХИЙН ҮЙЛ ЯВДАЛ", hd, List.of(mn.suld.plugin.ui.Pages.command("chronicle boss", "Боссын хроник")), pl -> pl.performCommand("menu"));
                } else {
                    lines.forEach(sender::sendMessage);
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop", "status", "boss").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("boss") && services.worldBosses != null) {
            List<String> out = new java.util.ArrayList<>(List.of("stop"));
            for (var b : services.worldBosses.bosses()) out.add(b.id().substring("worldboss.".length()));
            return out.stream().filter(x -> x.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return args.length == 2 && args[0].equalsIgnoreCase("start") ? List.of("wolf_raid") : List.of();
    }
}
