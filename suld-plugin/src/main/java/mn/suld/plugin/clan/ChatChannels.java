package mn.suld.plugin.clan;

import io.papermc.paper.event.player.AsyncChatEvent;
import mn.suld.api.chat.ChatChannel;
import mn.suld.api.chat.ChatHistory;
import mn.suld.plugin.party.PartyService;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SÜLD chat channels (docs/CHAT.md): Нийт (global), Ойр (local, {@link ChatChannel#LOCAL_RADIUS} blocks), Бүлэг (party),
 * Овог (clan) and Худалдаа (trade). Each player has a sticky channel ({@code /ch}, the chat screen's tabs; kept in the
 * player's data); {@code !text} always goes to Нийт, and {@code /g /l /pc /tr /cc text} send one line elsewhere.
 * <p>
 * Chat arrives on async threads, so routing never touches Bukkit state there: once a second the main thread writes an
 * immutable snapshot (world, position, party and clan of every online player) that the async handler reads. The
 * handler narrows {@code event.viewers()} to the channel's audience, the renderer (ChatListener) puts the channel tag
 * and the speaker's head in front, and every line is kept in a bounded {@link ChatHistory} with exactly the players
 * who heard it, for the chat screen.
 */
public final class ChatChannels implements Listener {

    /** Where a player was at the last snapshot. */
    record Where(String world, double x, double z, UUID party, UUID clan) {
    }

    private final Plugin plugin;
    private final PartyService parties;
    private final ClanService clans;
    private final NamespacedKey key;
    private final ChatHistory history = new ChatHistory(80);
    private final Map<UUID, ChatChannel> sticky = new ConcurrentHashMap<>();
    private final Map<UUID, ChatChannel> oneShot = new ConcurrentHashMap<>();
    private final Map<AsyncChatEvent, ChatChannel> routed = Collections.synchronizedMap(new WeakHashMap<>());
    private final boolean heads;
    private volatile Map<UUID, Where> snapshot = Map.of();

    public ChatChannels(Plugin plugin, PartyService parties, ClanService clans) {
        this.plugin = plugin;
        this.parties = parties;
        this.clans = clans;
        this.key = new NamespacedKey(plugin, "chat_channel");
        this.heads = plugin.getConfig().getBoolean("chat.heads", true);
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::snap, 1L, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) load(p);
    }

    private void snap() {
        Map<UUID, Where> m = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location l = p.getLocation();
            UUID party = parties.partyOf(p.getUniqueId()).map(x -> x.id()).orElse(null);
            UUID clan = clans.clanOf(p.getUniqueId()).map(x -> x.id()).orElse(null);
            m.put(p.getUniqueId(), new Where(l.getWorld().getName(), l.getX(), l.getZ(), party, clan));
        }
        snapshot = Map.copyOf(m);
    }

    public ChatHistory history() {
        return history;
    }

    public ChatChannel channel(UUID player) {
        return sticky.getOrDefault(player, ChatChannel.GLOBAL);
    }

    /** True when {@code p} can speak in {@code c} now (party/clan need membership). Main thread. */
    public boolean canUse(Player p, ChatChannel c) {
        return switch (c) {
            case PARTY -> parties.partyOf(p.getUniqueId()).isPresent();
            case CLAN -> clans.clanOf(p.getUniqueId()).isPresent();
            default -> c.speakable();
        };
    }

    /** Sets the sticky channel (main thread: it is stored in the player's data); false when the player cannot use it. */
    public boolean setChannel(Player p, ChatChannel c) {
        if (!c.speakable() || !canUse(p, c)) return false;
        sticky.put(p.getUniqueId(), c);
        p.getPersistentDataContainer().set(key, PersistentDataType.STRING, c.name());
        return true;
    }

    /** Sends one line to {@code c} without changing the sticky channel (main thread). */
    public void say(Player p, ChatChannel c, String text) {
        if (text == null || text.isBlank()) return;
        // Player#chat runs a leading "/" as a command: a chat line is always a chat line
        if (text.startsWith("/")) text = " " + text;
        snap(); // a synchronous send sees the party/clan state of this very moment
        oneShot.put(p.getUniqueId(), c);
        try {
            p.chat(text);
        } finally {
            oneShot.remove(p.getUniqueId());
        }
    }

    /** The channel an event was routed to (for the renderer), GLOBAL when unknown. */
    public ChatChannel routedChannel(AsyncChatEvent e) {
        return routed.getOrDefault(e, ChatChannel.GLOBAL);
    }

    /** "[Н] " in the channel's colour, with the speaker's head in front when enabled. */
    public Component prefix(ChatChannel c, Player speaker) {
        Component tag = Component.text("[" + c.tag() + "] ", TextColor.color(c.rgb()));
        if (!heads) return tag;
        return Component.object(ObjectContents.playerHead(speaker.getUniqueId())).append(Component.text(" ")).append(tag);
    }

    private void load(Player p) {
        String s = p.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s == null) return;
        try {
            ChatChannel c = ChatChannel.valueOf(s);
            if (c.speakable()) sticky.put(p.getUniqueId(), c);
        } catch (IllegalArgumentException ignored) {
            // an old value: back to Нийт
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer());
        snap(); // a newcomer hears local/party/clan lines from the first one on
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sticky.remove(e.getPlayer().getUniqueId());
        oneShot.remove(e.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, this::snap);
    }

    /** Recipients of {@code c} from {@code sender}'s point of view; null = everyone. */
    Set<UUID> audience(UUID sender, ChatChannel c) {
        Map<UUID, Where> snap = snapshot;
        Where me = snap.get(sender);
        if (c == ChatChannel.GLOBAL || c == ChatChannel.TRADE) return null;
        Set<UUID> out = new HashSet<>();
        out.add(sender);
        if (me == null) return out;
        double r2 = (double) ChatChannel.LOCAL_RADIUS * ChatChannel.LOCAL_RADIUS;
        for (Map.Entry<UUID, Where> e : snap.entrySet()) {
            Where w = e.getValue();
            boolean in = switch (c) {
                case LOCAL -> w.world().equals(me.world()) && (w.x() - me.x()) * (w.x() - me.x()) + (w.z() - me.z()) * (w.z() - me.z()) <= r2;
                case PARTY -> me.party() != null && me.party().equals(w.party());
                case CLAN -> me.clan() != null && me.clan().equals(w.clan());
                default -> true;
            };
            if (in) out.add(e.getKey());
        }
        return out;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        ChatChannel c = oneShot.getOrDefault(id, channel(id));
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        if (plain.startsWith("!") && plain.length() > 1) { // "!text": one line to everyone
            c = ChatChannel.GLOBAL;
            e.message(Component.text(plain.substring(1).stripLeading()));
        }
        Where me = snapshot.get(id);
        boolean noParty = c == ChatChannel.PARTY && (me == null || me.party() == null);
        boolean noClan = c == ChatChannel.CLAN && (me == null || me.clan() == null);
        if (noParty || noClan) {
            if (oneShot.containsKey(id)) { // an explicit /pc or /cc: refuse, nothing is sent
                e.setCancelled(true);
                p.sendMessage(Messages.error(noParty ? "Та бүлэгт байхгүй. /party invite <нэр>" : "Та овоггүй. /clan"));
                return;
            }
            // a sticky channel the player has left (party disbanded, clan gone): the line goes to Нийт
            c = ChatChannel.GLOBAL;
            sticky.remove(id);
            p.sendMessage(Messages.info((noParty ? "Бүлэг" : "Овог") + " алга тул Нийт суваг руу бичлээ (/ch-ээр солино)."));
            Bukkit.getScheduler().runTask(plugin, () -> p.getPersistentDataContainer().remove(key));
        }
        Set<UUID> who = audience(id, c);
        if (who != null) {
            e.viewers().removeIf(a -> a instanceof Player v && !who.contains(v.getUniqueId()));
            if (c == ChatChannel.LOCAL && who.size() <= 1) {
                p.sendMessage(Messages.info("Ойр хавьд (" + ChatChannel.LOCAL_RADIUS + " блок) хэн ч алга — !текст гэвэл бүгдэд хүрнэ."));
            }
        }
        routed.put(e, c);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatDone(AsyncChatEvent e) {
        ChatChannel c = routed.remove(e);
        if (e.isCancelled() || c == null) return;
        Set<UUID> heard = null;
        if (c != ChatChannel.GLOBAL && c != ChatChannel.TRADE) {
            heard = new HashSet<>();
            for (Audience a : e.viewers()) if (a instanceof Player v) heard.add(v.getUniqueId());
        }
        history.add(new ChatHistory.Line(System.currentTimeMillis(), e.getPlayer().getUniqueId(), e.getPlayer().getName(), c,
                PlainTextComponentSerializer.plainText().serialize(e.message()), heard));
    }
}
