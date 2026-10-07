package mn.suld.plugin.command;

import mn.suld.api.audit.AuditEvent;
import mn.suld.api.classgear.ArmorRules;
import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.item.ClassArmor;
import mn.suld.plugin.item.ClassWeapons;
import mn.suld.plugin.ui.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /classgear} — the class gear (docs/CLASS_GEAR_SYSTEM.md, docs/ARMOR_PROGRESSION.md):
 * <pre>
 *   /classgear                    armour level, XP, tier, enhancement, next tier's gates, active minutes
 *   /classgear recover            get lost class gear back (idempotent, same identities; once per 10 min)
 *   /classgear upgrade            buy the next armour tier when every gate is met
 *   /classgear enhance            enhancement +1 (coins)
 *   /classgear recover &lt;player&gt;                       staff (suld.admin.classgear)
 *   /classgear set &lt;player&gt; level|tier|enhance &lt;n&gt;   staff QA (suld.admin.classgear), audited
 * </pre>
 */
public final class ClassGearCommand implements TabExecutor {

    private static final long COOLDOWN_MS = 10 * 60_000L;

    private final SuldServices services;
    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    public ClassGearCommand(SuldServices services) {
        this.services = services;
    }

    private ClassArmor armor() {
        return services.classArmor;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "recover" -> recover(sender, args);
            case "set" -> set(sender, args);
            case "info", "upgrade", "enhance" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Messages.info("/classgear recover <тоглогч> · /classgear set <тоглогч> level|tier|enhance <n>"));
                    return true;
                }
                if (sub.equals("info")) info(p);
                else if (sub.equals("upgrade")) upgrade(p);
                else enhance(p);
            }
            default -> sender.sendMessage(Messages.info("/classgear [recover|upgrade|enhance]"));
        }
        return true;
    }

    private void info(Player p) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || armor() == null || !armor().supports(pr.playerClass().orElse(null))) {
            p.sendMessage(Messages.info("Таны ангийн хуяг удахгүй нэмэгдэнэ (одоогоор Баатар)."));
            return;
        }
        ClassGear g = pr.classGear();
        StringBuilder sb = new StringBuilder("§6⚔ Ангийн хуяг §7— §f" + g.tier().name() + " " + g.tier().displayName()
                + (g.enhance() > 0 ? " +" + g.enhance() : "") + "§7 · түвшин §f" + g.armorLevel());
        long need = ArmorRules.need(g.armorLevel());
        if (need > 0) sb.append("§7 (").append(Math.round(g.armorXp())).append('/').append(need).append(" XP)");
        if (g.armorLevel() >= pr.progression().level() && g.armorLevel() < ArmorRules.MAX_ARMOR_LEVEL) sb.append("§e · таны түвшин хүртэл");
        if (g.armorLevel() < pr.progression().level() - 3) sb.append("§a · ×2 XP (гүйцэх)");
        ArmorTier next = g.tier().next().orElse(null);
        if (next != null) {
            sb.append("\n§7Дараагийн зэрэг §f").append(next.name()).append(' ').append(next.displayName()).append("§7:");
            for (ArmorRules.Gate gate : ArmorRules.gates(g, armor().holdings(p, pr), this::name)) {
                sb.append("\n  ").append(gate.met() ? "§a✔ " : "§c✘ ").append("§f").append(gate.label());
            }
        }
        if (g.enhance() < ArmorRules.MAX_ENHANCE) {
            sb.append("\n§7Сайжруулалт +").append(g.enhance() + 1).append(": §f")
                    .append(ArmorRules.enhanceCost(g.armorLevel(), g.enhance() + 1, g.tier())).append(" зоос §7(/classgear enhance)");
        }
        sb.append("\n§7Идэвхтэй тоглолт: §f").append(pr.activeMinutes().total()).append(" мин");
        if (services.activity != null && services.activity.fatigued(p.getUniqueId())) sb.append("§c · энэ газар ядарсан: хуягийн XP алга, өөр газар оч");
        p.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(sb.toString()));
    }

    /** Display name of a material (catalog) or dungeon (the ones in the game); unknown dungeons say so. */
    private String name(String id) {
        if (id.startsWith("dungeon.")) {
            var d = mn.suld.plugin.content.SuldContent.dungeonFor(id);
            return d != null ? d.displayName() : id.substring("dungeon.".length()) + " (тоглоомд хараахан нэмэгдээгүй)";
        }
        return services.itemService().catalog().item(id).map(mn.suld.api.item.ItemDefinition::displayName).orElse(id);
    }

    private void upgrade(Player p) {
        switch (armor() == null ? ClassArmor.Upgrade.NO_CLASS : armor().upgrade(p)) {
            case DONE -> p.sendMessage(Messages.success("Ангийн хуяг шинэ зэрэгт хүрлээ!"));
            case MAX -> p.sendMessage(Messages.info("Хамгийн дээд зэрэг."));
            case GATES -> {
                p.sendMessage(Messages.error("Шаардлага хангагдаагүй:"));
                info(p);
            }
            case SOUL -> p.sendMessage(Messages.error("Сүнс байхдаа боломжгүй."));
            case NO_CLASS -> p.sendMessage(Messages.info("Таны ангийн хуяг удахгүй нэмэгдэнэ (одоогоор Баатар)."));
        }
    }

    private void enhance(Player p) {
        switch (armor() == null ? ClassArmor.Enhance.NO_CLASS : armor().enhance(p)) {
            case DONE -> p.sendMessage(Messages.success("Ангийн хуяг сайжирлаа (+2 % хүч)."));
            case MAX -> p.sendMessage(Messages.info("Энэ зэрэгт хамгийн их сайжруулалт (+" + ArmorRules.MAX_ENHANCE + ")."));
            case COINS -> p.sendMessage(Messages.error("Зоос хүрэлцэхгүй."));
            case SOUL -> p.sendMessage(Messages.error("Сүнс байхдаа боломжгүй."));
            case NO_CLASS -> p.sendMessage(Messages.info("Таны ангийн хуяг удахгүй нэмэгдэнэ (одоогоор Баатар)."));
        }
    }

    private void recover(CommandSender sender, String[] args) {
        Player target;
        boolean staff = args.length > 1;
        if (staff) {
            if (!sender.hasPermission("suld.admin.classgear")) {
                sender.sendMessage(Messages.error("Танд энэ үйлдэл хийх эрх алга."));
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Messages.error("Тоглогч онлайн биш: " + args[1]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
            Long t = last.get(p.getUniqueId());
            if (t != null && System.currentTimeMillis() - t < COOLDOWN_MS) {
                long left = (COOLDOWN_MS - (System.currentTimeMillis() - t) + 59_999) / 60_000;
                sender.sendMessage(Messages.error("Дахин " + left + " минутын дараа оролдоно уу."));
                return;
            }
        } else {
            sender.sendMessage(Messages.info("/classgear recover <тоглогч>"));
            return;
        }
        if (services.isSoul.test(target.getUniqueId())) {
            sender.sendMessage(Messages.error("Сүнс байхдаа сэргээх боломжгүй."));
            return;
        }
        ClassWeapons.Recovery w = services.classWeapons().recover(target);
        ClassArmor.Recovered a = armor() == null ? new ClassArmor.Recovered(0, 0, false) : armor().recover(target);
        boolean restored = w == ClassWeapons.Recovery.RESTORED || a.restored() > 0 || a.duplicatesRemoved() > 0;
        if (!staff && restored) last.put(target.getUniqueId(), System.currentTimeMillis()); // a no-op costs nothing
        if (w == ClassWeapons.Recovery.NO_CLASS) {
            sender.sendMessage(Messages.error("Эхлээд ангиа сонгоно уу (/class)."));
            return;
        }
        if (restored) {
            String what = (w == ClassWeapons.Recovery.RESTORED ? "зэвсэг" : "") + (a.restored() > 0 ? (w == ClassWeapons.Recovery.RESTORED ? " ба " : "")
                    + a.restored() + " хуяг" : "");
            sender.sendMessage(Messages.success("Ангийн эд сэргээгдлээ" + (what.isBlank() ? "" : ": " + what) + (staff ? " (" + target.getName() + ")" : ".")));
            if (staff) target.sendMessage(Messages.success("Таны ангийн эдийг сэргээлээ."));
            services.equipment().dirty(target);
            services.audit().record(AuditEvent.of(sender instanceof Player p ? p.getUniqueId().toString() : "console",
                    "classgear.recover", target.getUniqueId().toString(), (staff ? "staff" : "self") + " weapon=" + w + " armor=" + a.restored()
                            + " duplicates=" + a.duplicatesRemoved()));
        } else {
            sender.sendMessage(Messages.info(target.getName() + " ангийн эдээ бүгдийг авч яваа — сэргээх шаардлагагүй."));
        }
        if (w == ClassWeapons.Recovery.NO_ROOM || a.noRoom()) sender.sendMessage(Messages.error("Цүнхэнд зай гаргаад дахин оролдоно уу."));
    }

    private void set(CommandSender sender, String[] args) {
        if (!sender.hasPermission("suld.admin.classgear")) {
            sender.sendMessage(Messages.error("Танд энэ үйлдэл хийх эрх алга."));
            return;
        }
        if (args.length < 4 || armor() == null) {
            sender.sendMessage(Messages.info("/classgear set <тоглогч> level|tier|enhance <n>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        String what = args[2].toLowerCase(Locale.ROOT);
        int n;
        try {
            n = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            n = -1;
        }
        if (target == null || n < 0 || !List.of("level", "tier", "enhance").contains(what)) {
            sender.sendMessage(Messages.error("/classgear set <онлайн тоглогч> level|tier|enhance <n>"));
            return;
        }
        armor().set(target, what, n);
        services.audit().record(AuditEvent.of(sender instanceof Player p ? p.getUniqueId().toString() : "console",
                "classgear.set", target.getUniqueId().toString(), what + "=" + n));
        sender.sendMessage(Messages.success(target.getName() + ": " + what + " = " + n));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean staff = sender.hasPermission("suld.admin.classgear");
        if (args.length == 1) return staff ? List.of("recover", "upgrade", "enhance", "set") : List.of("recover", "upgrade", "enhance");
        if (args.length == 2 && staff && List.of("recover", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 3 && staff && args[0].equalsIgnoreCase("set")) return List.of("level", "tier", "enhance");
        return List.of();
    }
}
