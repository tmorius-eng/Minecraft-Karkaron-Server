package mn.suld.plugin.command;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.worldbuild.WorldBuildService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player-facing basics SÜLD owns: {@code /help} (the in-game guide), {@code /rules}, {@code /spawn}
 * (to Kharkhorum, with a warm-up) and {@code /balance} / {@code /pay} (SÜLD coins — the server's only
 * currency). A command router makes sure these labels reach SÜLD even when Bukkit or EssentialsX also
 * define them.
 */
public final class CityCommands implements Listener {

    private static final Set<String> OWNED = Set.of("help", "?", "tuslamj", "zaavar", "rules", "juram",
            "spawn", "hot", "balance", "bal", "money", "zoos", "pay", "tuluh",
            "class", "angi", "profile", "stats", "dur", "exp", "level", "lvl", "tuvshin", "quest", "quests", "erel");
    private static final int SPAWN_WARMUP_SECONDS = 3;

    private final Plugin plugin;
    private final SuldServices services;
    private final WorldBuildService city;
    private final Map<UUID, BukkitTask> warmups = new ConcurrentHashMap<>();

    public CityCommands(Plugin plugin, SuldServices services, WorldBuildService city) {
        this.plugin = plugin;
        this.services = services;
        this.city = city;
    }

    /** Route the labels SÜLD owns to SÜLD, whatever order the plugins registered them in. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String msg = e.getMessage();
        int sp = msg.indexOf(' ');
        String label = (sp < 0 ? msg.substring(1) : msg.substring(1, sp)).toLowerCase(Locale.ROOT);
        if (OWNED.contains(label)) e.setMessage("/suld:" + label + (sp < 0 ? "" : msg.substring(sp)));
    }

    // ------------------------------------------------------------------ /help

    private record Page(String key, String title, List<String[]> lines) {
    }

    /** [text, command or null] — commands become clickable. */
    private static String[] l(String text, String cmd) {
        return new String[]{text, cmd};
    }

    private static final List<Page> PAGES = List.of(
            new Page("start", "Эхлэх · Getting started", List.of(
                    l("1. Хархорумын төв талбайд ирлээ — энд аюулгүй.", null),
                    l("2. Талбайн баруун талын Бөөгийн чулуунуудад ангиа сонго (эсвэл /class).", "/class"),
                    l("3. Анчдын ахлагчаас «Анхны Ан» эрлийг ав — талбайн зүүн тал.", "/quest"),
                    l("4. Хотын хаалгаар гараад Говийн чонуудыг ан — тал нутаг аюултай.", null),
                    l("5. Олз (чонын арьс)-оо захын худалдаачинд зарж зоос ол.", null),
                    l("6. Дархнаас зэвсгээ засуул, Өртөөгөөр хаалга хооронд хурдан яв.", null),
                    l("Хот руу буцах: /spawn", "/spawn"))),
            new Page("class", "Анги · Classes", List.of(
                    l("Баатар — тэсвэр, ойрын тулаан (Хил / Rage)", null),
                    l("Мэргэн — нум сум, холын тулаан (Төвлөрөл / Focus)", null),
                    l("Бөө — сүнсний ид шид, бүлгээ хамгаална (Сүнс / Spirit)", null),
                    l("Дархан — дархны дайчин (Дөл / Heat)", null),
                    l("Хүлэгчин — хурд, довтолгоо (Хурд / Momentum)", null),
                    l("Анги сонгох / харах: /class   Дүр: /profile", "/class"),
                    l("EXP цуглуулж түвшин ахиулна: /exp", "/exp"))),
            new Page("city", "Хархорум · The city", List.of(
                    l("Хот бол аюулгүй бүс: барих/нураах, PvP, мангас байхгүй.", null),
                    l("  (реликс эзэмшигч хаана ч аюулд байдаг)", null),
                    l("Хөтөч — талбайн урд: зааварчилгаа.", null),
                    l("Худалдаачид — зүүн захын гудамж: хоол, хувцас, зэвсэг; олз худалдаж авна.", null),
                    l("Дархан — гар урлалын хороо: зэвсэг, хуяг засна.", null),
                    l("Өртөө — талбай болон хаалга бүрт: хурдан аялал.", null),
                    l("Тэнгэрийн тахилга — баруун хойд толгод: адислал.", null),
                    l("Их Ордон — хойд дэнж: хааны танхим.", null))),
            new Page("dungeon", "Агуй ба бүлэг · Dungeons and parties", List.of(
                    l("Бүлэг байгуулах: /party invite <нэр>   хүлээн авах: /party accept", "/party info"),
                    l("Агуйн аян: /dungeon list → тал нутагт /dungeon enter", "/dungeon list"),
                    l("Агуй хотын дотор эхлэхгүй — хаалгаар гар.", null),
                    l("Төлөв: /dungeon status   гарах: /dungeon leave", "/dungeon status"))),
            new Page("clan", "Овог · Clans", List.of(
                    l("Овог байгуулах: /clan create <нэр> <таг>", "/clan top"),
                    l("Урих / нэгдэх: /clan invite <нэр>, /clan accept", null),
                    l("Овгийн чат: /cc <мессеж>", null),
                    l("Овог EXP-ийн нэмэгдэл өгнө: /clan info", "/clan info"))),
            new Page("relic", "Реликс · World-unique relics", List.of(
                    l("Дэлхийд ганц эд зүйлс: Хөх Сүлд, Алтан Гэрэгэ…", "/relic list"),
                    l("Сүмийг олох зөвлөгөө: /relic hint", "/relic hint"),
                    l("Эзэмшигч гэрэлтэж, хаана ч халдлагад өртөнө.", null),
                    l("Түүх: /relic history <key>", null))),
            new Page("commands", "Командууд · Commands", List.of(
                    l("/help [хуудас] — энэ гарын авлага   /rules — дүрэм", "/rules"),
                    l("/spawn — Хархорум руу   /balance — зоос   /pay <нэр> <тоо>", "/balance"),
                    l("/class /profile /exp /quest — дүр ба ахиц", "/profile"),
                    l("/party /dungeon /clan /cc /relic — тоглоом", null),
                    l("/home /sethome /delhome /tpa /tpaccept /tpdeny /msg /r /back /warp — серверийн", null),
                    l("/suldpack — дүрс багцыг дахин ачаалах", "/suldpack"))),
            new Page("rules", "Дүрэм · Rules", List.<String[]>of(
                    l("Дэлгэрэнгүй: /rules", "/rules"))));

    private static final List<String> RULES = List.of(
            "1. Хууран мэхлэх програм, макро, бөөгнөрөл (exploit) хориотой.",
            "2. Бусдыг доромжлох, үзэн ядалт, спам хориотой. Хүндэтгэлтэй бай.",
            "3. Алдааг (bug) ашиглахгүй — /help-ээр биш, ажилтанд мэдэгд.",
            "4. Бодит мөнгөөр эд зүйл, данс худалдахгүй.",
            "5. Нэг хүн — нэг данс. Хуурамч данс хориотой.",
            "6. Хархорум бол аюулгүй бүс; тал нутагт PvP нээлттэй.",
            "7. Ажилтны шийдвэрийг хүндэл. Гомдлыг Discord-оор илгээнэ үү.");

    private final TabExecutor help = new TabExecutor() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            int index = 0;
            if (a.length > 0) {
                String want = a[0].toLowerCase(Locale.ROOT);
                for (int i = 0; i < PAGES.size(); i++) if (PAGES.get(i).key().equals(want)) index = i;
                try {
                    index = Math.max(0, Math.min(PAGES.size() - 1, Integer.parseInt(want) - 1));
                } catch (NumberFormatException ignored) {
                    // a page key
                }
            }
            Page page = PAGES.get(index);
            s.sendMessage(Component.text("━━━━ ", NamedTextColor.GRAY, TextDecoration.BOLD)
                    .append(Component.text("ᠰ SÜLD ", Messages.BRAND, TextDecoration.BOLD))
                    .append(Component.text(page.title(), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("  " + (index + 1) + "/" + PAGES.size(), NamedTextColor.WHITE, TextDecoration.BOLD)));
            for (String[] line : page.lines()) {
                Component t = Component.text(line[0], NamedTextColor.WHITE, TextDecoration.BOLD);
                if (line[1] != null) {
                    t = t.clickEvent(ClickEvent.runCommand(line[1]))
                            .hoverEvent(HoverEvent.showText(Component.text("Дарж ажиллуулах: " + line[1], NamedTextColor.WHITE, TextDecoration.BOLD)));
                }
                s.sendMessage(t);
            }
            Component nav = Component.empty();
            for (int i = 0; i < PAGES.size(); i++) {
                Page p = PAGES.get(i);
                nav = nav.append(Component.text("[" + (i + 1) + "]", i == index ? NamedTextColor.GOLD : NamedTextColor.AQUA, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/help " + p.key()))
                                .hoverEvent(HoverEvent.showText(Component.text(p.title()))))
                        .append(Component.text(" "));
            }
            s.sendMessage(Component.text("Хуудас: ", NamedTextColor.WHITE, TextDecoration.BOLD).append(nav));
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            return a.length == 1 ? PAGES.stream().map(Page::key).filter(k -> k.startsWith(a[0].toLowerCase(Locale.ROOT))).toList() : List.of();
        }
    };

    private final TabExecutor rules = new TabExecutor() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            s.sendMessage(Component.text("━━━━ ", NamedTextColor.GRAY, TextDecoration.BOLD).append(Component.text("Дүрэм · Rules", NamedTextColor.GOLD, TextDecoration.BOLD)));
            RULES.forEach(r -> s.sendMessage(Component.text(r, NamedTextColor.WHITE, TextDecoration.BOLD)));
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            return List.of();
        }
    };

    // ------------------------------------------------------------------ /spawn

    private final TabExecutor spawn = new TabExecutor() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            if (!(s instanceof Player p)) {
                s.sendMessage(Messages.error("Зөвхөн тоглогч."));
                return true;
            }
            Location target = city.pointLocation("spawn");
            if (target == null) target = p.getWorld().getSpawnLocation();
            if (services.dungeons().isInAnyRun(p.getUniqueId())) {
                p.sendMessage(Messages.error("Агуйн аяны үеэр хот руу буцахгүй. /dungeon leave"));
                return true;
            }
            if (p.hasPermission("suld.admin") || services.inCity(p.getLocation())) {
                p.teleport(target);
                return true;
            }
            BukkitTask old = warmups.remove(p.getUniqueId());
            if (old != null) old.cancel();
            Location start = p.getLocation().clone();
            Location dest = target;
            p.sendMessage(Messages.info("Хархорум руу " + SPAWN_WARMUP_SECONDS + " секундын дараа… Хөдлөхгүй байна уу."));
            int[] left = {SPAWN_WARMUP_SECONDS};
            warmups.put(p.getUniqueId(), Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!p.isOnline() || p.getLocation().getWorld() != start.getWorld() || p.getLocation().distanceSquared(start) > 0.5) {
                    cancel(p, "хөдөлсөн тул цуцлагдлаа");
                    return;
                }
                if (--left[0] <= 0) {
                    BukkitTask t = warmups.remove(p.getUniqueId());
                    if (t != null) t.cancel();
                    p.teleport(dest);
                    p.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f);
                }
            }, 20L, 20L));
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            return List.of();
        }
    };

    private void cancel(Player p, String why) {
        BukkitTask t = warmups.remove(p.getUniqueId());
        if (t != null) {
            t.cancel();
            p.sendMessage(Messages.error("Хот руу буцах " + why + "."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && warmups.containsKey(p.getUniqueId())) cancel(p, "гэмтэл авсан тул цуцлагдлаа");
    }

    // ------------------------------------------------------------------ /balance, /pay

    private final TabExecutor balance = new TabExecutor() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            Player who = a.length > 0 && s.hasPermission("suld.admin") ? Bukkit.getPlayerExact(a[0]) : s instanceof Player p ? p : null;
            if (who == null) {
                s.sendMessage(Messages.error("Тоглогч олдсонгүй."));
                return true;
            }
            PlayerProfile profile = services.profiles().cached(who.getUniqueId()).orElse(null);
            if (profile == null) {
                s.sendMessage(Messages.error("Профайл ачаалагдаагүй байна."));
                return true;
            }
            s.sendMessage(Messages.info((who == s ? "Таны" : who.getName() + "-ийн") + " зоос: ")
                    .append(Component.text(profile.currency() + " ₮", NamedTextColor.GOLD, TextDecoration.BOLD)));
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            return a.length == 1 && s.hasPermission("suld.admin") ? null : List.of();
        }
    };

    private final TabExecutor pay = new TabExecutor() {
        @Override
        public boolean onCommand(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            if (!(s instanceof Player from)) {
                s.sendMessage(Messages.error("Зөвхөн тоглогч."));
                return true;
            }
            if (a.length < 2) {
                s.sendMessage(Messages.info("/pay <тоглогч> <тоо>"));
                return true;
            }
            Player to = Bukkit.getPlayerExact(a[0]);
            long amount;
            try {
                amount = Long.parseLong(a[1]);
            } catch (NumberFormatException e) {
                amount = -1;
            }
            if (to == null || to.equals(from)) {
                s.sendMessage(Messages.error("Онлайн өөр тоглогч сонгоно уу."));
                return true;
            }
            if (amount <= 0 || amount > 1_000_000) {
                s.sendMessage(Messages.error("Тоо 1–1000000 байна."));
                return true;
            }
            PlayerProfile a1 = services.profiles().cached(from.getUniqueId()).orElse(null);
            PlayerProfile b1 = services.profiles().cached(to.getUniqueId()).orElse(null);
            if (a1 == null || b1 == null) {
                s.sendMessage(Messages.error("Профайл ачаалагдаагүй байна."));
                return true;
            }
            synchronized (CityCommands.class) { // one transfer at a time: no double spend
                if (a1.currency() < amount) {
                    s.sendMessage(Messages.error("Зоос хүрэлцэхгүй (" + a1.currency() + ")."));
                    return true;
                }
                a1.addCurrency(-amount);
                b1.addCurrency(amount);
            }
            from.sendMessage(Messages.success(to.getName() + "-д " + amount + " ₮ шилжүүллээ."));
            to.sendMessage(Messages.success(from.getName() + " танд " + amount + " ₮ илгээлээ."));
            return true;
        }

        @Override
        public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, @NotNull String[] a) {
            return a.length == 1 ? null : List.of();
        }
    };

    public TabExecutor help() { return help; }
    public TabExecutor rules() { return rules; }
    public TabExecutor spawn() { return spawn; }
    public TabExecutor balance() { return balance; }
    public TabExecutor pay() { return pay; }
}
