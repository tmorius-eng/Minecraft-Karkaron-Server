package mn.suld.plugin.command;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.Progression;
import mn.suld.api.progression.ProgressionEngine;
import mn.suld.api.quest.QuestState;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The character commands: {@code /class} (choose or view the class), {@code /profile} (alias {@code /stats}),
 * {@code /exp} (level and EXP bar) and {@code /quest} (the active quest). They read the same profile the HUD
 * shows; nothing here changes progression except the first class choice, which goes through the class GUI.
 */
public final class ProgressCommands {

    private final SuldServices services;

    public ProgressCommands(SuldServices services) {
        this.services = services;
    }

    private PlayerProfile profileOf(CommandSender s, String[] a) {
        Player who = a.length > 0 && s.hasPermission("suld.admin") ? Bukkit.getPlayerExact(a[0])
                : s instanceof Player p ? p : null;
        if (who == null) {
            s.sendMessage(Messages.error(s instanceof Player ? "Тоглогч олдсонгүй." : "Тоглогчийн нэр өгнө үү."));
            return null;
        }
        PlayerProfile profile = services.profiles().cached(who.getUniqueId()).orElse(null);
        if (profile == null) s.sendMessage(Messages.error("Профайл ачаалагдаагүй байна. Түр хүлээнэ үү."));
        return profile;
    }

    private static Component header(String title) {
        return Component.text("━━━━ ", NamedTextColor.GRAY, TextDecoration.BOLD)
                .append(Component.text("ᠰ SÜLD ", Messages.BRAND, TextDecoration.BOLD))
                .append(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    private static Component row(String key, String value, TextColor color) {
        return Component.text(key + ": ", NamedTextColor.WHITE, TextDecoration.BOLD).append(Component.text(value, color, TextDecoration.BOLD));
    }

    /** A 20-segment bar: ▰ filled, ▱ empty. */
    static Component bar(double fraction, TextColor color) {
        double f = Math.max(0, Math.min(1, fraction));
        int filled = (int) Math.round(f * 20);
        return Component.text("▰".repeat(filled), color, TextDecoration.BOLD)
                .append(Component.text("▱".repeat(20 - filled), NamedTextColor.GRAY, TextDecoration.BOLD))
                .append(Component.text(" " + Math.round(f * 100) + "%", NamedTextColor.WHITE, TextDecoration.BOLD));
    }

    private static String roleName(PlayerClass c) {
        return switch (c.role()) {
            case TANK -> "Хамгаалагч / Tank";
            case RANGED -> "Холын дайчин / Ranged";
            case SUPPORT -> "Туслагч / Support";
            case CRAFTER -> "Урлагч / Crafter";
            case MOBILITY -> "Хурдан дайчин / Mobility";
        };
    }

    private abstract static class Simple implements TabExecutor {
        @Override
        public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            return a.length == 1 && s.hasPermission("suld.admin") ? null : List.of();
        }
    }

    // ------------------------------------------------------------------ /class

    private final TabExecutor clazz = new Simple() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            PlayerProfile profile = profileOf(s, a);
            if (profile == null) return true;
            PlayerClass pc = profile.playerClass().orElse(null);
            if (pc == null) {
                if (s instanceof Player p && a.length == 0) {
                    services.classSelectionGui().open(p);
                } else {
                    s.sendMessage(Messages.info(profile.name() + " анги сонгоогүй байна."));
                }
                return true;
            }
            s.sendMessage(header("Анги · " + pc.displayName()));
            s.sendMessage(row("Үүрэг", roleName(pc), NamedTextColor.WHITE));
            s.sendMessage(row("Нөөц", pc.resourceName() + " (" + pc.resourceMax() + ")", NamedTextColor.AQUA));
            s.sendMessage(row("Хүндрэл", "★".repeat(pc.difficulty()) + "☆".repeat(Math.max(0, 5 - pc.difficulty())), NamedTextColor.YELLOW));
            s.sendMessage(row("Суурь", "HP " + (int) pc.baseHealth() + " · ATK " + pc.baseAttack(), NamedTextColor.WHITE));
            s.sendMessage(Component.text("Анги нэг удаа сонгогдоно — hardcore сервер.", NamedTextColor.GRAY, TextDecoration.BOLD));
            return true;
        }
    };

    // ------------------------------------------------------------------ /profile, /stats

    private final TabExecutor profile = new Simple() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            PlayerProfile profile = profileOf(s, a);
            if (profile == null) return true;
            ProgressionEngine engine = services.progression().engine();
            Progression prog = profile.progression();
            Player online = Bukkit.getPlayer(profile.playerId());
            s.sendMessage(header("Дүр · " + profile.name()));
            s.sendMessage(row("Анги", profile.playerClass().map(PlayerClass::displayName).orElse("сонгоогүй — /class"), NamedTextColor.WHITE));
            s.sendMessage(row("Түвшин", prog.level() + " / " + services.config().progression().maxLevel(), NamedTextColor.GOLD));
            s.sendMessage(Component.text("EXP ", NamedTextColor.WHITE, TextDecoration.BOLD).append(bar(engine.progressFraction(prog), Messages.BRAND)));
            if (online != null) {
                AttributeInstance max = online.getAttribute(Attribute.MAX_HEALTH);
                double maxHp = max != null ? max.getValue() : 20;
                s.sendMessage(Component.text("HP  ", NamedTextColor.WHITE, TextDecoration.BOLD)
                        .append(bar(online.getHealth() / maxHp, NamedTextColor.RED))
                        .append(Component.text("  " + Math.round(online.getHealth()) + "/" + Math.round(maxHp), NamedTextColor.WHITE, TextDecoration.BOLD)));
                AttributeInstance armor = online.getAttribute(Attribute.ARMOR);
                s.sendMessage(row("Хуяг", armor != null ? String.valueOf(Math.round(armor.getValue())) : "0", NamedTextColor.WHITE));
            }
            s.sendMessage(row("Зоос", profile.currency() + " ₮", NamedTextColor.GOLD));
            s.sendMessage(row("Овог", services.clans().tagOf(profile.playerId()).map(t -> "[" + t + "]").orElse("—"), NamedTextColor.AQUA));
            double bonus = services.boosts().bonus(profile.playerId());
            if (bonus > 0) s.sendMessage(row("EXP нэмэгдэл", "+" + Math.round(bonus * 100) + "%", NamedTextColor.GREEN));
            s.sendMessage(row("Эрэл", questLine(profile.questState()), NamedTextColor.WHITE));
            return true;
        }
    };

    // ------------------------------------------------------------------ /exp

    private final TabExecutor exp = new Simple() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            PlayerProfile profile = profileOf(s, a);
            if (profile == null) return true;
            ProgressionEngine engine = services.progression().engine();
            Progression prog = profile.progression();
            int maxLevel = services.config().progression().maxLevel();
            s.sendMessage(header("Түвшин " + prog.level()));
            s.sendMessage(Component.text("EXP ", NamedTextColor.WHITE, TextDecoration.BOLD).append(bar(engine.progressFraction(prog), Messages.BRAND)));
            if (prog.level() >= maxLevel) {
                s.sendMessage(Messages.success("Дээд түвшинд хүрсэн."));
            } else {
                s.sendMessage(row("Энэ түвшинд", prog.expIntoLevel() + " EXP", NamedTextColor.WHITE));
                s.sendMessage(row("Дараагийн түвшин хүртэл", engine.expToNextLevel(prog) + " EXP", NamedTextColor.GOLD));
            }
            s.sendMessage(Component.text("EXP: мангас ан, эрэл, агуй, дэлхийн үйл явдал. Овог нэмэгдэл өгнө.", NamedTextColor.GRAY, TextDecoration.BOLD));
            return true;
        }
    };

    // ------------------------------------------------------------------ /quest

    private String questLine(QuestState q) {
        if (q.questId().isEmpty()) return "алга";
        String title = SuldContent.FIRST_HUNT.id().equals(q.questId()) ? SuldContent.FIRST_HUNT.title() : q.questId();
        if (q.completed()) return title + " — дууссан";
        int need = SuldContent.FIRST_HUNT.id().equals(q.questId()) ? SuldContent.FIRST_HUNT.requiredCount() : 0;
        return title + " — " + q.progress() + (need > 0 ? "/" + need : "");
    }

    private final TabExecutor quest = new Simple() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            PlayerProfile profile = profileOf(s, a);
            if (profile == null) return true;
            QuestState q = profile.questState();
            s.sendMessage(header("Эрэл · Quests"));
            if (q.questId().isEmpty()) {
                if (profile.playerClass().isEmpty()) {
                    s.sendMessage(Component.text("Эхлээд ангиа сонго — дараа нь «Анхны Ан» эрэл өгөгдөнө. ", NamedTextColor.WHITE, TextDecoration.BOLD)
                            .append(Component.text("[/class]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/class"))));
                } else {
                    s.sendMessage(Messages.info("Идэвхтэй эрэл алга."));
                }
                return true;
            }
            var def = SuldContent.FIRST_HUNT;
            if (!def.id().equals(q.questId())) {
                s.sendMessage(Messages.info(questLine(q)));
                return true;
            }
            s.sendMessage(row(def.title(), q.completed() ? "дууссан ✔" : "идэвхтэй", q.completed() ? NamedTextColor.GREEN : NamedTextColor.GOLD));
            s.sendMessage(Component.text(def.description(), NamedTextColor.WHITE, TextDecoration.BOLD));
            s.sendMessage(Component.text("Явц ", NamedTextColor.WHITE, TextDecoration.BOLD)
                    .append(bar((double) q.progress() / def.requiredCount(), NamedTextColor.GOLD))
                    .append(Component.text("  " + q.progress() + "/" + def.requiredCount(), NamedTextColor.WHITE, TextDecoration.BOLD)));
            s.sendMessage(row("Шагнал", def.expReward() + " EXP, " + def.currencyReward() + " ₮", NamedTextColor.GREEN));
            if (!q.completed()) {
                s.sendMessage(Component.text("Говийн чононууд хотын хэрмийн гадна, тал нутагт тэнүүчилнэ.", NamedTextColor.GRAY, TextDecoration.BOLD));
            }
            return true;
        }
    };

    public TabExecutor clazz() { return clazz; }
    public TabExecutor profile() { return profile; }
    public TabExecutor exp() { return exp; }
    public TabExecutor quest() { return quest; }
}
