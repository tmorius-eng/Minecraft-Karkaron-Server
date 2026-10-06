package mn.suld.plugin.auth;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.audit.AuditLog;
import mn.suld.api.config.AuthSettings;
import mn.suld.api.identity.AuthDecision;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The secure join pipeline. SÜLD never authenticates anyone itself — no /register, no /login,
 * no passwords. Minecraft/Microsoft authentication has already happened when Paper fires
 * {@link AsyncPlayerPreLoginEvent}; this service only:
 * <ol>
 *   <li>refuses identities that were not verified ({@link AuthPolicy}, fail closed),</li>
 *   <li>opens a session and acquires the profile <i>by UUID</i> before the player exists in
 *       the world (a load failure refuses the login — never a blank profile),</li>
 *   <li>binds the session on join and releases the profile only when the <i>last</i> session
 *       for that UUID ends (duplicate logins and reconnects are safe),</li>
 *   <li>audits every step.</li>
 * </ol>
 */
public final class AuthenticationService implements Listener {

    private final Plugin plugin;
    private final SuldServices services;
    private final AuthPolicy policy;
    private final AuthSettings settings;
    private final AuditLog audit;
    private final SessionRegistry sessions = new SessionRegistry();
    /** Session id per joined connection (identity map: each Player object is one connection). */
    private final Map<Player, Long> joined = new IdentityHashMap<>();

    public AuthenticationService(Plugin plugin, SuldServices services, AuthPolicy policy,
                                 AuthSettings settings, AuditLog audit) {
        this.plugin = plugin;
        this.services = services;
        this.policy = policy;
        this.settings = settings;
        this.audit = audit;
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
            if (velocity && proxyOnline && secret != null && !secret.isBlank()) {
                return AuthMode.VELOCITY_FORWARDED; // HMAC-verified forwarding from an online-mode proxy
            }
        }
        return AuthMode.INSECURE; // offline mode or legacy (spoofable) BungeeCord forwarding
    }

    public void announceMode() {
        AuthMode mode = policy.mode();
        audit.record(AuditEvent.of("server", AuditActions.AUTH_MODE, mode.name(),
                "acceptsLogins=" + policy.acceptsLogins()));
        if (mode.verified()) {
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
    }

    // ------------------------------------------------------------- pipeline

    /** Runs on an async login thread after Mojang verification; HIGHEST so bans/whitelist decide first. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        AuthDecision decision = policy.evaluate(uuid, name);
        if (!decision.allowed()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(decision.reason(), NamedTextColor.RED));
            audit.record(AuditEvent.of(String.valueOf(uuid), AuditActions.DENIED, safeName(name), decision.code().name()));
            return;
        }
        if (decision.code() == AuthDecision.Code.ALLOWED_INSECURE_DEV) {
            audit.record(AuditEvent.of(uuid.toString(), AuditActions.INSECURE_DEV_LOGIN, name, "unverified identity"));
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
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(
                    "SÜLD профайл түр ачааллагдсангүй. Хэдэн секундын дараа дахин орно уу.\n"
                            + "Your SÜLD profile could not be loaded right now — please try again shortly.",
                    NamedTextColor.RED));
            return;
        }
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

    /** MONITOR: after every other quit handler has finished mutating the profile. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Long sid = joined.remove(player);
        if (sid == null) {
            return; // was kicked by onJoin; nothing bound
        }
        UUID uuid = player.getUniqueId();
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
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            PlayerProfile profile = services.profiles().cached(id).orElse(null);
            out.add(" - " + p.getName() + " uuid=" + id + " v" + id.version()
                    + " sessions=" + sessions.liveSessions(id)
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
