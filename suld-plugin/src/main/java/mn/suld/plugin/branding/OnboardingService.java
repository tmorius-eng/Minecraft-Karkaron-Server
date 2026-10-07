package mn.suld.plugin.branding;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.style.Rank;
import mn.suld.plugin.SuldServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes a new player understand the server at once: a title that points at the guide boards, a clickable "five steps"
 * card in chat, and — for the first twenty minutes of a session at low level — a hint on the action bar every
 * 40 seconds that depends on where the player is in the game (no class, first hunt, loot to sell, coins to spend…).
 */
public final class OnboardingService implements Listener {

    private static final TextColor GOLD = TextColor.fromHexString("#FFD24A");
    private static final long HINT_WINDOW_MS = 20 * 60_000L;
    private static final int HINT_LEVEL_LIMIT = 15;

    private final Plugin plugin;
    private final SuldServices services;
    private final Map<UUID, Long> joined = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hintIndex = new ConcurrentHashMap<>();

    private GuideBoards guide;

    public OnboardingService(Plugin plugin, SuldServices services) {
        this.plugin = plugin;
        this.services = services;
    }

    /** The boards are re-checked for every arriving player. */
    public void guide(GuideBoards guide) {
        this.guide = guide;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::hints, 800L, 800L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        joined.put(p.getUniqueId(), System.currentTimeMillis());
        Bukkit.getScheduler().runTaskLater(plugin, () -> intro(p), 50L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        joined.remove(e.getPlayer().getUniqueId());
        hintIndex.remove(e.getPlayer().getUniqueId());
    }

    private PlayerProfile profile(Player p) {
        return services.profiles().cached(p.getUniqueId()).orElse(null);
    }

    private void intro(Player p) {
        if (!p.isOnline()) return;
        if (guide != null) guide.refresh();
        PlayerProfile pr = profile(p);
        if (pr == null) return;
        boolean fresh = !pr.hasSelectedClass() || pr.progression().level() < 5;
        if (fresh) {
            p.showTitle(Title.title(
                    Component.text("ТАВТАЙ МОРИЛ", GOLD, TextDecoration.BOLD),
                    Component.text("Урд байгаа хөвөгч самбаруудыг уншаарай ➜", NamedTextColor.WHITE, TextDecoration.BOLD),
                    Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(5), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            card(p);
        }
    }

    private static Component step(String n, String text, String command) {
        Component c = Component.text(" " + n + " ", GOLD, TextDecoration.BOLD)
                .append(Component.text(text + " ", NamedTextColor.WHITE, TextDecoration.BOLD));
        if (command != null) {
            c = c.append(Component.text("[" + command + "]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand(command)));
        }
        return c;
    }

    private void card(Player p) {
        p.sendMessage(Component.text("━━━━━ ", GOLD).append(Component.text("ТОГЛОХ ЗААВАР", GOLD, TextDecoration.BOLD)).append(Component.text(" ━━━━━", GOLD)));
        p.sendMessage(step("1.", "Ангиа сонго", "/class"));
        p.sendMessage(step("2.", "Эрлээ ав, дага (дэлгэцийн дээд талд заагч)", "/quest"));
        p.sendMessage(step("3.", "Хотын хаалгаар гараад мангас ан — олз, EXP, зоос унана", null));
        p.sendMessage(step("4.", "Олзоо зарж зоос ол", "/shop"));
        p.sendMessage(step("5.", "Өдөр бүр шагнал, даалгавар ав", "/daily"));
        p.sendMessage(Component.text(" Бүх заавар: ", NamedTextColor.WHITE, TextDecoration.BOLD)
                .append(Component.text("[/help]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/help")))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Component.text("[/tutorial]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/tutorial")))
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(Component.text("[/commands]", NamedTextColor.AQUA, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/commands"))));
    }

    /** The hint that fits the player's situation right now, or null when there is nothing useful to say. */
    String hintFor(Player p, PlayerProfile pr, int rotate) {
        if (!pr.hasSelectedClass()) return "Ангиа сонго: /class";
        int level = pr.progression().level();
        var q = pr.questState();
        java.util.List<String> hints = new java.util.ArrayList<>();
        if (q.active()) {
            var def = services.quests().definition(q.questId()).orElse(null);
            if (def != null) hints.add("Эрэл: " + def.title() + " " + q.progress() + "/" + def.requiredCount() + " — заагч дээд талд");
        }
        if (level < 5) hints.add("Хотын хаалгаар гарч мангас ан — зүүн зүгт Хэрлэн (олз, EXP)");
        if (hasLoot(p)) hints.add("Олзоо /shop-д зарж зоос ол");
        Rank next = services.styles().of(p.getUniqueId()).rank().next().orElse(null);
        if (next != null && level >= next.requiredLevel() && pr.currency() >= next.cost()) hints.add("Цол ахиулах боломжтой: /rankup");
        if (level >= 5) hints.add("Өөрийн морьтой болсон: /mori");
        hints.add("Өдрийн шагнал /daily, даалгавар /tasks");
        hints.add("Бүх команд: /commands · Заавар: /help");
        return hints.get(rotate % hints.size());
    }

    private boolean hasLoot(Player p) {
        for (var it : p.getInventory().getStorageContents()) {
            if (it == null) continue;
            var inst = services.items().read(it).orElse(null);
            if (inst != null && !inst.definitionId().startsWith("weapon.class.") && !inst.soulbound()) return true;
        }
        return false;
    }

    private void hints() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Long at = joined.get(p.getUniqueId());
            PlayerProfile pr = profile(p);
            if (at == null || pr == null || now - at > HINT_WINDOW_MS || pr.progression().level() >= HINT_LEVEL_LIMIT) continue;
            if (services.dungeons().isInAnyRun(p.getUniqueId())) continue;
            int i = hintIndex.merge(p.getUniqueId(), 1, Integer::sum);
            String hint = hintFor(p, pr, i);
            if (hint != null) p.sendActionBar(Component.text("➜ " + hint, GOLD, TextDecoration.BOLD));
        }
    }
}
