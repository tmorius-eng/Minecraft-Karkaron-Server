package mn.suld.plugin.command;

import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /commands}: every command the sender may use, grouped, with what it does. Players see the game commands
 * and the extras their group grants (homes, teleport requests, messages…); staff additionally see the tools their
 * permissions unlock. The console sees everything. The permission column mirrors plugin.yml and the setup in
 * resources/permissions/luckperms.txt.
 */
public final class CommandCatalog implements TabExecutor {

    private enum Group {
        GAME("Тоглоом · Game", "#7CE07C"),
        SERVER("Сервер · Server (бүлгээс хамаарна)", "#9FF3FF"),
        STAFF("Ажилтан · Staff", "#FFB454"),
        ADMIN("Админ · Admin", "#FF6B6B");

        final String title;
        final TextColor color;

        Group(String title, String color) {
            this.title = title;
            this.color = TextColor.fromHexString(color);
        }
    }

    private record Entry(Group group, String command, String what, String permission) {
    }

    private static Entry e(Group g, String command, String what, String permission) {
        return new Entry(g, command, what, permission);
    }

    private static final List<Entry> ALL = List.of(
            e(Group.GAME, "/menu", "Үндсэн цэс (мөн 9-р нүдний цаг)", null),
            e(Group.GAME, "/help [хуудас]", "Тоглоомын гарын авлага", null),
            e(Group.GAME, "/tutorial", "Алхам алхмаар заавар", null),
            e(Group.GAME, "/class", "Анги сонгох / харах", null),
            e(Group.GAME, "/profile", "Дүрийн мэдээлэл", null),
            e(Group.GAME, "/exp", "Түвшин, EXP", null),
            e(Group.GAME, "/skills", "Чадварын мод: оноо зарцуулж шид, идэвхгүй чадвар нээх", null),
            e(Group.GAME, "/skill <нод> [unlock|refund]", "Нэг чадварыг үзэх, нээх, буцаах", null),
            e(Group.GAME, "/skills build save|load <нэр>", "Чадварын бүтэц хадгалах, солих", null),
            e(Group.GAME, "/skills reset", "Оноог буцаах (баталгаажуулалттай)", null),
            e(Group.GAME, "/quest [info|track]", "«Сүлдний Зам» эрэл; заагч асаах/унтраах", null),
            e(Group.GAME, "/tasks", "Өдрийн 3 анчны даалгавар", null),
            e(Group.GAME, "/daily", "Өдрийн шагнал (7 хоногийн дараалал)", null),
            e(Group.GAME, "/shop", "Хангамж, олз зарах", null),
            e(Group.GAME, "/rankup", "Цол ахиулах", null),
            e(Group.GAME, "/lvlup", "Түвшний шагнал авах", null),
            e(Group.GAME, "/cosmetics", "Таг, өнгө, мэндчилгээ, эможи", null),
            e(Group.GAME, "/buy", "Кредит дэлгүүр (гоёлд л)", null),
            e(Group.GAME, "/top [level|coins]", "Шилдэгүүдийн жагсаалт", null),
            e(Group.GAME, "/trade <нэр>", "Тоглогчтой аюулгүй арилжаа", null),
            e(Group.GAME, "/mori", "Өөрийн морь (5-р түвшнээс)", null),
            e(Group.GAME, "/party …", "Бүлэг: invite, accept, leave, kick…", null),
            e(Group.GAME, "/dungeon list|enter|status|leave", "Агуйн аян (4 агуй)", null),
            e(Group.GAME, "/clan …", "Овог: create, invite, top, info…", null),
            e(Group.GAME, "/cc <мессеж>", "Овгийн чат", null),
            e(Group.GAME, "/relic list|info|hint|history", "Дэлхийд ганц реликс", null),
            e(Group.GAME, "/spawn", "Хархорум руу буцах", null),
            e(Group.GAME, "/balance  /pay <нэр> <тоо>", "Зоос харах, шилжүүлэх", null),
            e(Group.GAME, "/rules  /discord  /website  /vote", "Дүрэм, холбоосууд", null),
            e(Group.GAME, "/commands", "Энэ жагсаалт", null),

            e(Group.SERVER, "/home  /sethome  /delhome", "Гэрийн цэг", "essentials.home"),
            e(Group.SERVER, "/tpa <нэр>  /tpaccept  /tpdeny", "Найз руугаа очих хүсэлт", "essentials.tpa"),
            e(Group.SERVER, "/msg <нэр> <текст>  /r", "Хувийн мессеж, хариулах", "essentials.msg"),
            e(Group.SERVER, "/mail send|read", "Офлайн тоглогчид захидал", "essentials.mail"),
            e(Group.SERVER, "/ignore <нэр>", "Тоглогчийг чимээгүй болгох", "essentials.ignore"),
            e(Group.SERVER, "/afk  /list", "AFK тэмдэг, онлайн жагсаалт", "essentials.afk"),
            e(Group.SERVER, "/sit  (гадаргуу дээр товш)", "Суух (GSit)", "gsit.sit"),

            e(Group.STAFF, "/kick  /mute <нэр>", "Хөөх, дуугүй болгох", "essentials.kick"),
            e(Group.STAFF, "/tp <нэр>  /tphere", "Тоглогч руу очих / дуудах", "essentials.tp"),
            e(Group.STAFF, "/vanish  /socialspy  /invsee", "Нууц ажиглалт", "essentials.vanish"),
            e(Group.STAFF, "/ban  /tempban", "Түр болон бүрмөсөн хориглох", "essentials.ban"),
            e(Group.STAFF, "/co inspect|lookup|rollback", "CoreProtect: блокийн түүх, буцаалт", "coreprotect.inspect"),
            e(Group.STAFF, "/revive <нэр>", "Сүнсний төлвөөс буцаах", "suld.admin.revive"),

            e(Group.ADMIN, "/suld exp|coins|quest|guide|auth", "EXP, зоос, эрлийн бүлэг, самбар шинэчлэх, нэвтрэлт", "suld.admin"),
            e(Group.ADMIN, "/credits give|take <нэр|uuid> <тоо>", "Дэлгүүрийн кредит", "suld.admin.credits"),
            e(Group.ADMIN, "/skillsadmin inspect|grantpoints|unlock|reset|orb|reload|validate", "Чадварын мод удирдах", "suld.admin.skills"),
            e(Group.ADMIN, "/suldevent  /suldpack", "Дэлхийн үйл явдал, дүрс багц", "suld.admin.event"),
            e(Group.ADMIN, "/worldbuild …", "Хархорумын барилга (status, validate, rollback…)", "suld.admin.world"),
            e(Group.ADMIN, "/relic setshrine|return|give|recover|tp", "Реликсийн удирдлага", "suld.admin.relic"),
            e(Group.ADMIN, "/lp …", "LuckPerms: бүлэг, эрх", "luckperms.editor"),
            e(Group.ADMIN, "/chunky …  //wand  /rg …", "Дэлхий бэлдэх, WorldEdit, WorldGuard", "chunky.command"));

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        boolean console = !(s instanceof org.bukkit.entity.Player);
        s.sendMessage(Component.text("━━━━━ ", NamedTextColor.GOLD).append(Component.text("КОМАНДУУД", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" ━━━━━", NamedTextColor.GOLD)));
        List<Group> shown = new ArrayList<>();
        for (Group g : Group.values()) {
            List<Entry> rows = new ArrayList<>();
            for (Entry en : ALL) {
                if (en.group() != g) continue;
                if (console || en.permission() == null || s.hasPermission(en.permission())) rows.add(en);
            }
            if (rows.isEmpty()) continue;
            shown.add(g);
            s.sendMessage(Component.text("▎" + g.title, g.color, TextDecoration.BOLD));
            for (Entry en : rows) {
                String bare = en.command().split(" ")[0];
                s.sendMessage(Component.text("  " + en.command(), NamedTextColor.AQUA, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.suggestCommand(bare))
                        .append(Component.text(" — " + en.what(), NamedTextColor.WHITE, TextDecoration.BOLD)));
            }
        }
        if (!console && !shown.contains(Group.SERVER)) {
            s.sendMessage(Messages.info("Нэмэлт серверийн командууд (/home, /tpa, /msg…) таны бүлэгт одоогоор нээгдээгүй."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
        return List.of();
    }
}
