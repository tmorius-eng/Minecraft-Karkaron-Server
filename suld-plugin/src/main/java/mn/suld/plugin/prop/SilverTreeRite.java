package mn.suld.plugin.prop;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.balance.TimedBoosts;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progress.MarkKind;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.auth.SuldJoinEvent;
import mn.suld.plugin.item.PlayerDataSaves;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Pages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * «Мөнгөн Модны Найр» (docs/world/OVOO.md): one drink a day at the Silver Tree of the palace square. Rubruck's
 * account of the tree is history (four silver lions pouring mare's milk, four gilded serpents pouring wine,
 * caracosmos, bal and rice mead, docs/research/history/KHARKHORUM.md §5); drinking there is SÜLD's game. Each drink is
 * a 20-minute timed boost of its own kind (ProgressionBoosts, the strongest source of a kind counts, so the black
 * airag never stacks with an ovoo's blessing).
 *
 * <p>Order (as OvooService.bless): the day, the drink and its end go into the player file, the file is written, then
 * the boost starts; a join puts back a drink still running. The day is the server zone's (daily.timezone).
 */
public final class SilverTreeRite implements Listener {

    public static final String PROP = "silver_tree";
    private static final long DRINK_MS = 20 * 60_000L;
    private static final double REACH = 8.0;
    private static final String SOURCE = "tree";
    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");

    /** The four drinks Rubruck saw poured; the boost each gives is SÜLD's. */
    public enum Drink {
        WINE("wine", "Дарс", TimedBoosts.Kind.DAMAGE, 0.03, "дамаж +3 %"),
        CARACOSMOS("caracosmos", "Хар айраг", TimedBoosts.Kind.EXP, 0.05, "EXP +5 % (овооны адислалтай нэмэгдэхгүй)"),
        BAL("bal", "Бал", TimedBoosts.Kind.MITIGATION, 0.10, "авах цохилт −10 %"),
        RICE_MEAD("rice_mead", "Будааны дарс", TimedBoosts.Kind.LOOT, 5, "олзны аз +5");

        public final String id;
        public final String title;
        public final TimedBoosts.Kind kind;
        public final double bonus;
        public final String effect;

        Drink(String id, String title, TimedBoosts.Kind kind, double bonus, String effect) {
            this.id = id;
            this.title = title;
            this.kind = kind;
            this.bonus = bonus;
            this.effect = effect;
        }

        public static Drink byId(String id) {
            for (Drink d : values()) if (d.id.equalsIgnoreCase(id)) return d;
            return null;
        }
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final PropService props;
    private final NamespacedKey dayKey, drinkKey, untilKey;

    public SilverTreeRite(Plugin plugin, SuldServices services, PropService props) {
        this.plugin = plugin;
        this.services = services;
        this.props = props;
        this.dayKey = new NamespacedKey(plugin, "tree_day");
        this.drinkKey = new NamespacedKey(plugin, "tree_drink");
        this.untilKey = new NamespacedKey(plugin, "tree_until");
    }

    /** The tree's page: what Rubruck saw, what drinking here does, a button for each drink (or today's drink). */
    public void open(Player p) {
        List<Component> lines = new ArrayList<>();
        lines.add(Pages.line("Парисын алтны дархан Гийом Бушье Мөнх хаанд хийсэн мод: үндэст нь дөрвөн мөнгөн арслан гүүний сүү "
                + "асгадаг, дээш өгсөх дөрвөн алтадмал могой дарс, хар айраг (caracosmos), бал, будааны дарс асгадаг, оройд нь "
                + "бүрээ үлээх сахиусан тэнгэр. Рубрук 1254 онд үүнийг харж бичсэн."));
        lines.add(Pages.dim("түүхэн баримт · Рубрукийн аяллын тэмдэглэл"));
        lines.add(Pages.line("Найранд ундаанаас нэгийг хүртэх нь SÜLD-ийн зан үйл: өдөрт нэг удаа, 20 минут."));
        lines.add(Pages.dim("SÜLD-ийн домог"));
        Drink today = drunkToday(p);
        List<Pages.Button> buttons = new ArrayList<>();
        if (today != null) {
            long left = minutesLeft(p);
            lines.add(Pages.line("Өнөөдөр: " + today.title + (left > 0 ? " · " + left + " мин үлдлээ" : " · дууссан") + " — маргааш дахин."));
        } else {
            for (Drink d : Drink.values()) buttons.add(Pages.action(d.title, d.effect + " · 20 мин", pl -> drink(pl, d)));
        }
        Pages.text(p, "Мөнгөн Модны Найр", lines, buttons, null);
    }

    /** One drink: refused for a soul, a player without a class, the training yard's stand-in, away from the tree, or twice a day. */
    public boolean drink(Player p, Drink d) {
        PlayerProfile pr = services.profiles().cached(p.getUniqueId()).orElse(null);
        if (pr == null || !pr.hasSelectedClass() || pr.ephemeral() || services.isSoul.test(p.getUniqueId()) || p.isDead()) {
            p.sendMessage(Messages.error("Найрт ангитай, амьд хүн л ундаа хүртэнэ."));
            return false;
        }
        Location tree = props.anchor(PROP);
        if (tree == null || !p.getWorld().equals(tree.getWorld()) || tree.distanceSquared(p.getLocation()) > REACH * REACH) {
            p.sendMessage(Messages.error("Мөнгөн модны дэргэд очиж хүрт."));
            return false;
        }
        Drink had = drunkToday(p);
        if (had != null) {
            p.sendMessage(Messages.info("Өнөөдөр " + had.title + " хүртсэн — маргааш дахин ир."));
            return false;
        }
        if (untilOf(p) > System.currentTimeMillis()) { // yesterday's drink, still running past midnight: one at a time
            p.sendMessage(Messages.info("Өчигдрийн ундаа дуусаагүй байна — " + minutesLeft(p) + " минутын дараа хүрт."));
            return false;
        }
        long today = today();
        long until = System.currentTimeMillis() + DRINK_MS;
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        pdc.set(dayKey, PersistentDataType.LONG, today);
        pdc.set(drinkKey, PersistentDataType.STRING, d.id);
        pdc.set(untilKey, PersistentDataType.LONG, until);
        // the once-a-day guard is on disk before the boost starts, so a crash cannot give a second drink (as the ovoo)
        PlayerDataSaves.now(p);
        services.marks().record(p.getUniqueId(), MarkKind.TREE_DRINK, d.id, today); // V33 mirror
        services.boosts().bless(p.getUniqueId(), d.kind, SOURCE, until, d.bonus);
        if (services.achievements != null) services.achievements.record(p, mn.suld.api.achievement.Deed.TREE_DRINK, d.id, 1);
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.SILVER_TREE_DRINK, p.getUniqueId(), Map.of("drink", d.id)));
        p.sendMessage(Component.text("Мөнгөн Модны Найр: ", GOLD).append(Component.text(d.title + " — " + d.effect + ", 20 мин.", NamedTextColor.WHITE)));
        p.getWorld().spawnParticle(Particle.END_ROD, tree.clone().add(0, 3, 0), 24, 0.8, 1.6, 0.8, 0.02);
        p.playSound(tree, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        return true;
    }

    /** A drink still running is put back at the join (the boosts are dropped at quit). */
    @EventHandler
    public void onJoin(SuldJoinEvent e) {
        Player p = e.getPlayer();
        Drink d = Drink.byId(p.getPersistentDataContainer().getOrDefault(drinkKey, PersistentDataType.STRING, ""));
        long until = untilOf(p);
        if (d != null && until > System.currentTimeMillis()) services.boosts().bless(p.getUniqueId(), d.kind, SOURCE, until, d.bonus);
    }

    /**
     * Staff: {@code /suld tree <player> drink <id>|status|reset}; a given drink and a reset are audited. A reset clears
     * the player file and the boost; the V33 marks keep the days each drink was taken (history, not the guard).
     */
    public void admin(CommandSender sender, Player target, String action, String arg) {
        switch (action) {
            case "status" -> {
                Drink d = drunkToday(target);
                long left = Math.max(0, untilOf(target) - System.currentTimeMillis()) / 1000;
                sender.sendMessage(Messages.info("tree " + target.getName() + ": " + (d == null ? "-" : d.id + " today, " + left + " s left")));
                return; // a read is not audited
            }
            case "reset" -> {
                PersistentDataContainer pdc = target.getPersistentDataContainer();
                pdc.remove(dayKey);
                pdc.remove(drinkKey);
                pdc.remove(untilKey);
                PlayerDataSaves.now(target);
                for (Drink d : Drink.values()) services.boosts().bless(target.getUniqueId(), d.kind, SOURCE, 0, 0);
                sender.sendMessage(Messages.success(target.getName() + ": Мөнгөн модны өдрийн ундаа арилгалаа."));
            }
            case "drink" -> {
                Drink d = Drink.byId(arg == null ? "" : arg);
                if (d == null) {
                    sender.sendMessage(Messages.error("wine | caracosmos | bal | rice_mead"));
                    return;
                }
                if (!drink(target, d)) {
                    sender.sendMessage(Messages.info(target.getName() + ": татгалзсан (хариу нь тоглогчийн чатад)"));
                    return; // nothing given, nothing audited
                }
                sender.sendMessage(Messages.success(target.getName() + ": " + d.id));
            }
            default -> {
                sender.sendMessage(Messages.error("/suld tree <тоглогч> drink <id> | status | reset"));
                return;
            }
        }
        services.audit().record(AuditEvent.of(sender.getName(), AuditActions.SILVER_TREE_ADMIN, target.getUniqueId().toString(), action + (arg == null ? "" : " " + arg)));
    }

    private Drink drunkToday(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        Long day = pdc.get(dayKey, PersistentDataType.LONG);
        if (day == null || day != today()) return null;
        Drink d = Drink.byId(pdc.getOrDefault(drinkKey, PersistentDataType.STRING, ""));
        return d == null ? Drink.WINE : d; // an unknown id still counts as today's drink
    }

    /** Minutes of the running drink, rounded up (0 only once it has ended). */
    private long minutesLeft(Player p) {
        long ms = untilOf(p) - System.currentTimeMillis();
        return ms <= 0 ? 0 : (ms + 59_999) / 60_000L;
    }

    private long untilOf(Player p) {
        return p.getPersistentDataContainer().getOrDefault(untilKey, PersistentDataType.LONG, 0L);
    }

    private long today() {
        return LocalDate.now(services.zone()).toEpochDay();
    }
}
