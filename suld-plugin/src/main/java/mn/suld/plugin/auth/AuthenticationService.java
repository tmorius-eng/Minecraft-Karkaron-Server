package mn.suld.plugin.auth;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.audit.AuditLog;
import mn.suld.api.config.AuthSettings;
import mn.suld.api.identity.AuthDecision;
import mn.suld.api.identity.AuthGate;
import mn.suld.api.identity.AuthMode;
import mn.suld.api.identity.AuthPolicy;
import mn.suld.api.identity.PlayerIdentity;
import mn.suld.api.identity.SessionRegistry;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.service.ProfileLoad;
import mn.suld.plugin.SuldServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The secure join pipeline. SÜLD never authenticates anyone itself and holds no password: Minecraft/Microsoft
 * authentication has already happened when Paper fires {@link AsyncPlayerPreLoginEvent}, or, in
 * {@link AuthMode#HYBRID_AUTHME}, AuthMe on the proxy and here logs the player in. This service only:
 * <ol>
 *   <li>refuses identities that were not verified ({@link AuthPolicy}, fail closed), and behind AuthMe a name that
 *       another profile holds in another case,</li>
 *   <li>opens a session and acquires the profile <i>by UUID</i> before the player exists in
 *       the world (a load failure refuses the login — never a blank profile),</li>
 *   <li>binds the session on join and releases the profile only when the <i>last</i> session
 *       for that UUID ends (duplicate logins and reconnects are safe),</li>
 *   <li>lets the player into SÜLD's game ({@link SuldJoinEvent}) once they are logged in ({@link AuthGate}); behind
 *       AuthMe a joined player who is not logged in yet can only log in,</li>
 *   <li>audits every step.</li>
 * </ol>
 */
public final class AuthenticationService implements Listener, AuthBridge.Events {

    /** What a player waiting for AuthMe may type (AuthMe's own login commands). */
    private static final Set<String> LOGIN_COMMANDS = Set.of("login", "l", "log", "register", "reg", "email", "captcha",
            "2fa", "totp", "authme:login", "authme:l", "authme:register", "authme:reg", "authme:email", "authme:captcha",
            "authme:2fa", "authme:totp");
    private static final long PREMIUM_CONFIRM_MS = 120_000;

    private final Plugin plugin;
    private final SuldServices services;
    private final AuthPolicy policy;
    private final AuthSettings settings;
    private final AuditLog audit;
    private final SessionRegistry sessions = new SessionRegistry();
    /** Session id per joined connection (identity map: each Player object is one connection). */
    private final Map<Player, Long> joined = new IdentityHashMap<>();
    private final AuthGate gate;
    private AuthBridge bridge = AuthBridge.NONE;
    /** Who asked for {@code /premium} and when: it takes effect only with {@code /premium confirm} soon after. */
    private final Map<UUID, Long> premiumAsked = new ConcurrentHashMap<>();

    public AuthenticationService(Plugin plugin, SuldServices services, AuthPolicy policy,
                                 AuthSettings settings, AuditLog audit) {
        this.plugin = plugin;
        this.services = services;
        this.policy = policy;
        this.settings = settings;
        this.audit = audit;
        this.gate = new AuthGate(policy.mode());
    }

    /** Behind AuthMe: connect to it (it is enabled before SÜLD, plugin.yml softdepend). Called once at enable. */
    public void connectBridge() {
        if (policy.mode() == AuthMode.HYBRID_AUTHME) bridge = AuthMeBridge.connect(plugin, this);
    }

    public AuthBridge bridge() {
        return bridge;
    }

    /** Whether the player is in SÜLD's game (their {@link SuldJoinEvent} has run on this connection). */
    public boolean admitted(Player p) {
        return joined.containsKey(p) && gate.hasEntered(p.getUniqueId());
    }

    /** Decide how this server establishes identity, from the live server + Paper proxy config. */
    public static AuthMode detectMode(Server server, File serverRoot) {
        if (server.getOnlineMode()) {
            return AuthMode.ONLINE;
        }
        File paperGlobal = new File(serverRoot, "config/paper-global.yml");
        if (paperGlobal.isFile()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(paperGlobal);
            boolean velocity = yml.getBoolean("proxies.velocity.enabled", false);
            boolean proxyOnline = yml.getBoolean("proxies.velocity.online-mode", false);
            String secret = yml.getString("proxies.velocity.secret", "");
            boolean signed = velocity && secret != null && !secret.isBlank();
            if (signed && proxyOnline) {
                return AuthMode.VELOCITY_FORWARDED; // HMAC-verified forwarding from an online-mode proxy
            }
            if (signed && server.getPluginManager().getPlugin(AuthMeBridge.PLUGIN) != null) {
                // HMAC-verified forwarding from an offline-mode proxy, AuthMe owning the names (docs/AUTHENTICATION.md)
                return AuthMode.HYBRID_AUTHME;
            }
        }
        return AuthMode.INSECURE; // offline mode or legacy (spoofable) BungeeCord forwarding
    }

    public void announceMode() {
        AuthMode mode = policy.mode();
        audit.record(AuditEvent.of("server", AuditActions.AUTH_MODE, mode.name(),
                "acceptsLogins=" + policy.acceptsLogins()));
        if (mode == AuthMode.HYBRID_AUTHME) {
            plugin.getLogger().info("Authentication: HYBRID_AUTHME — offline Velocity proxy with signed forwarding, "
                    + "logins by AuthMe (" + bridge.describe() + "); players are identified by the offline UUID of "
                    + "their name and enter the game only once AuthMe has logged them in.");
            if (!bridge.available()) {
                plugin.getLogger().severe("AuthMe cannot be reached: SÜLD is REFUSING ALL LOGINS (fail closed).");
            }
        } else if (mode.verified()) {
            plugin.getLogger().info("Authentication: " + mode + " — identities verified by Minecraft/Microsoft; "
                    + "players are identified by UUID. No /register or /login.");
        } else if (policy.acceptsLogins()) {
            plugin.getLogger().severe("==============================================================");
            plugin.getLogger().severe(" INSECURE OFFLINE DEV MODE: accounts are NOT verified.");
            plugin.getLogger().severe(" Anyone can join as anyone. NEVER expose this server publicly.");
            plugin.getLogger().severe("==============================================================");
        } else {
            plugin.getLogger().severe("==============================================================");
            plugin.getLogger().severe(" Accounts are NOT verified (online-mode=false, no Velocity forwarding).");
            plugin.getLogger().severe(" SÜLD is REFUSING ALL LOGINS (fail closed). Set online-mode=true.");
            plugin.getLogger().severe("==============================================================");
        }
    }

    public void startSweeper() {
        long period = 20L * 20; // every 20s
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweepPending, period, period);
        if (gate.deferred()) {
            // AuthMe logs players in on its own threads; its event is the fast path, this once-a-second check the net
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (UUID id : gate.due(this::loggedInNow)) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) enter(p);
                }
            }, 20L, 20L);
        }
    }

    private boolean loggedInNow(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null && joined.containsKey(p) && bridge.isAuthenticated(p);
    }

    // ------------------------------------------------------------- pipeline

    /** Runs on an async login thread after Mojang verification; HIGHEST so bans/whitelist decide first. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            joinAttempt(event.getUniqueId(), "refused_before_suld:" + event.getLoginResult());
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        AuthDecision decision = policy.evaluate(uuid, name);
        if (!decision.allowed()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(decision.reason(), NamedTextColor.RED));
            audit.record(AuditEvent.of(String.valueOf(uuid), AuditActions.DENIED, safeName(name), decision.code().name()));
            joinAttempt(uuid, "denied:" + decision.code().name());
            return;
        }
        if (decision.code() == AuthDecision.Code.ALLOWED_INSECURE_DEV) {
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.INSECURE_DEV_LOGIN, name, "unverified identity"));
        }
        if (policy.mode() == AuthMode.HYBRID_AUTHME) {
            AuthDecision owner = bridge.available() ? nameOwner(uuid, name) : AuthDecision.bridgeUnavailable();
            if (!owner.allowed()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(owner.reason(), NamedTextColor.RED));
                audit.record(AuditEvent.of(uuid.toString(), AuditActions.DENIED, name, owner.code().name()));
                joinAttempt(uuid, "denied:" + owner.code().name());
                return;
            }
        }
        PlayerIdentity identity = PlayerIdentity.of(uuid, name);
        SessionRegistry.Start session = sessions.begin(uuid, Instant.now());
        if (session.duplicate()) {
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.DUPLICATE_SESSION, name,
                    "existing session will be replaced; profile stays loaded"));
        }
        ProfileLoad load;
        try {
            load = services.profiles().acquire(identity).get(settings.profileLoadTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            sessions.end(uuid, session.sessionId());
            String why = ex instanceof TimeoutException ? "timeout" : rootCause(ex).getClass().getSimpleName();
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.PROFILE_LOAD_FAILED, name, why));
            joinAttempt(uuid, "profile_failed:" + why);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(
                    "SÜLD профайл түр ачааллагдсангүй. Хэдэн секундын дараа дахин орно уу.\n"
                            + "Your SÜLD profile could not be loaded right now — please try again shortly.",
                    NamedTextColor.RED));
            return;
        }
        joinAttempt(uuid, load.created() ? "allowed:new" : "allowed");
        if (load.created()) {
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.FIRST_JOIN, name, "profile created"));
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.FIRST_LOGIN, uuid));
        } else {
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.LOGIN, name,
                    "level=" + load.profile().progression().level()));
        }
        if (load.renamed()) {
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.NAME_CHANGE, name,
                    load.previousName() + " -> " + name + " (same UUID, same profile)"));
        }
    }

    /**
     * Behind AuthMe every name has its own offline UUID, so {@code dorj} would be a second account beside {@code Dorj}:
     * the profile that holds the name in any case owns it. AuthMe refuses the other case too ({@code preventOtherCase});
     * this also covers profiles older than AuthMe. A lookup that fails refuses the login (fail closed).
     */
    private AuthDecision nameOwner(UUID uuid, String name) {
        Optional<UUID> owner;
        try {
            owner = services.profiles().ownerOfName(name).get(settings.profileLoadTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            plugin.getLogger().warning("Name check failed for " + name + ": " + rootCause(ex).getClass().getSimpleName());
            return AuthDecision.bridgeUnavailable();
        }
        return AuthDecision.checkNameOwner(uuid, owner);
    }

    /** The first funnel step (docs/ANALYTICS.md): every login attempt with its outcome, allowed or not. */
    private void joinAttempt(UUID uuid, String outcome) {
        services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.JOIN_ATTEMPT, uuid, java.util.Map.of("outcome", outcome)));
    }

    /** LOWEST: bind the session before any gameplay listener touches the player. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long sid = sessions.activateNewest(uuid, Instant.now());
        if (sid < 0 || services.profiles().cached(uuid).isEmpty()) {
            // Never let a player into the world without an authenticated session + profile.
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.JOIN_WITHOUT_SESSION, player.getName(),
                    "sid=" + sid));
            if (sid >= 0) {
                sessions.end(uuid, sid);
            }
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(Component.text(
                    "SÜLD: нэвтрэлт баталгаажсангүй, дахин орно уу. / Login could not be verified, please rejoin.",
                    NamedTextColor.RED)));
            return;
        }
        joined.put(player, sid);
    }

    /** MONITOR: after every plugin's join (AuthMe's limbo included), SÜLD's own join runs, or waits for AuthMe. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoined(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Long sid = joined.get(player);
        if (sid == null || !player.isOnline()) return; // refused by onJoin
        boolean loggedIn = gate.deferred() && bridge.isAuthenticated(player);
        if (gate.joined(player.getUniqueId(), sid, loggedIn) == AuthGate.Step.ENTER) {
            enter(player);
        } else {
            audit.record(AuditEvent.of(player.getUniqueId().toString(), AuditActions.AUTHME_PENDING, player.getName(),
                    "joined, waiting for the AuthMe login"));
        }
    }

    private void enter(Player p) {
        if (!p.isOnline()) return;
        if (gate.deferred()) {
            audit.record(AuditEvent.of(p.getUniqueId().toString(), AuditActions.AUTHME_LOGIN, p.getName(), "enters the game"));
        }
        Bukkit.getPluginManager().callEvent(new SuldJoinEvent(p));
    }

    @Override
    public void loggedIn(Player p) {
        Long sid = joined.get(p);
        if (sid != null && gate.loggedIn(p.getUniqueId(), sid)) enter(p);
    }

    @Override
    public void loggedOut(Player p) {
        Long sid = joined.get(p);
        if (sid == null || !gate.loggedOut(p.getUniqueId(), sid)) return;
        // the game was set up for this connection; it is never set up twice: log in again from the start
        audit.record(AuditEvent.of(p.getUniqueId().toString(), AuditActions.AUTHME_LOGOUT, p.getName(), "kicked to log in again"));
        p.kick(Component.text("Та системээс гарлаа. Дахин орж нэвтэрнэ үү. / You logged out; please rejoin.", NamedTextColor.YELLOW));
    }

    // ------------------------------------------------- before the AuthMe login

    /** A player who is not logged in yet can only log in (AuthMe blocks the rest too; this does not depend on it). */
    private boolean waiting(Player p) {
        return gate.deferred() && !gate.hasEntered(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        String msg = e.getMessage().trim();
        String label = (msg.startsWith("/") ? msg.substring(1) : msg).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (waiting(p)) {
            if (!LOGIN_COMMANDS.contains(label)) {
                e.setCancelled(true);
                p.sendMessage(Component.text("Эхлээд нэвтэрнэ үү: /login <нууц үг> (шинэ бол /register <нууц үг> <нууц үг>)",
                        NamedTextColor.RED));
            } else if (label.equals("l") || label.equals("login")) {
                // /l is also SÜLD's local-chat command (plugin.yml): whichever plugin registered the bare label first
                // owns it, so a player still logging in is sent to AuthMe's login by its full name
                String rest = msg.contains(" ") ? msg.substring(msg.indexOf(' ')) : "";
                e.setMessage("/authme:login" + rest);
            }
            return;
        }
        if (gate.deferred() && (label.equals("premium") || label.equals("authme:premium"))) premium(e, p, msg);
    }

    /**
     * {@code /premium} ties the name to the Microsoft account of that name: whoever does not own that account can never
     * log in with a password again (the proxy then asks Mojang; the spike locked a test name out this way). So it runs
     * only as {@code /premium confirm}, after a warning.
     */
    private void premium(PlayerCommandPreprocessEvent e, Player p, String msg) {
        String[] parts = msg.split("\\s+");
        Long asked = premiumAsked.get(p.getUniqueId());
        if (parts.length == 2 && parts[1].equalsIgnoreCase("confirm") && asked != null
                && System.currentTimeMillis() - asked < PREMIUM_CONFIRM_MS) {
            premiumAsked.remove(p.getUniqueId());
            e.setMessage(parts[0]); // AuthMe's own /premium, as typed
            return;
        }
        e.setCancelled(true);
        premiumAsked.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(Component.text("⚠ /premium энэ нэрийг ижил нэртэй Microsoft (Minecraft Java) данстай холбоно.", NamedTextColor.GOLD));
        p.sendMessage(Component.text("Тэр данс таных биш бол та нууц үгээрээ ч дахин нэвтэрч чадахгүй болно.", NamedTextColor.RED));
        p.sendMessage(Component.text("Өөрийн худалдаж авсан данс мөн бол 2 минутын дотор: /premium confirm", NamedTextColor.YELLOW));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        if (waiting(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(org.bukkit.event.player.PlayerInteractEvent e) {
        if (waiting(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (waiting(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (waiting(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && waiting(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && waiting(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && waiting(p)) e.setCancelled(true);
    }

    /** MONITOR: after every other quit handler has finished mutating the profile. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Long sid = joined.remove(player);
        if (sid == null) {
            return; // was kicked by onJoin; nothing bound
        }
        UUID uuid = player.getUniqueId();
        gate.quit(uuid, sid);
        premiumAsked.remove(uuid);
        SessionRegistry.Ended ended = sessions.end(uuid, sid);
        if (ended.last()) {
            services.profiles().saveAndUnload(uuid).exceptionally(ex -> {
                plugin.getLogger().warning("Profile save on quit failed for " + uuid + " (kept cached for retry): "
                        + rootCause(ex).getClass().getSimpleName());
                return null;
            });
        } else {
            services.profiles().cached(uuid).ifPresent(services.profiles()::save);
        }
        audit.record(AuditEvent.of(uuid.toString(), AuditActions.SESSION_END, player.getName(),
                ended.last() ? "profile released" : "another session still owns the profile"));
    }

    private void sweepPending() {
        for (SessionRegistry.Ended e : sessions.expirePending(Instant.now(),
                Duration.ofSeconds(settings.pendingSessionTtlSeconds()))) {
            audit.record(AuditEvent.of(e.uuid().toString(), AuditActions.PENDING_EXPIRED, "",
                    "client never joined; last=" + e.last()));
            if (e.last() && Bukkit.getPlayer(e.uuid()) == null) {
                services.profiles().saveAndUnload(e.uuid());
            }
        }
    }

    // ----------------------------------------------------------- diagnostics

    public AuthPolicy policy() {
        return policy;
    }

    /** Lines for {@code /suld auth}: mode + per-online-player session state. Never shows secrets. */
    public List<String> statusLines() {
        List<String> out = new ArrayList<>();
        out.add("Auth mode: " + policy.mode() + (policy.mode().verified() ? " (verified)" : " (NOT verified)")
                + ", accepting logins: " + policy.acceptsLogins());
        if (gate.deferred()) out.add("Login bridge: " + bridge.describe() + ", waiting for login: " + gate.waitingCount());
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            PlayerProfile profile = services.profiles().cached(id).orElse(null);
            out.add(" - " + p.getName() + " uuid=" + id + " v" + id.version()
                    + " sessions=" + sessions.liveSessions(id)
                    + (gate.deferred() ? (gate.hasEntered(id) ? " in-game" : " WAITING-LOGIN") : "")
                    + " profile=" + (profile == null ? "MISSING" : "lvl" + profile.progression().level()
                    + " class=" + profile.playerClass().map(Enum::name).orElse("-")));
        }
        return out;
    }

    private static String safeName(String name) {
        return name == null ? "" : name;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c;
    }
}
