package mn.suld.plugin.auth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * AuthMe 6 ({@code docs/qa/2026-10-18-authme-spike.md}), read by reflection so SÜLD neither compiles nor ships against
 * it: {@code fr.xephi.authme.api.v3.AuthMeApi} answers whether a player is logged in, and its {@code LoginEvent} and
 * {@code LogoutEvent} are listened to by class name. Any failure reads as "not logged in": the gate stays shut.
 */
final class AuthMeBridge implements AuthBridge {

    static final String PLUGIN = "AuthMe";
    private static final String API = "fr.xephi.authme.api.v3.AuthMeApi";
    private static final String LOGIN_EVENT = "fr.xephi.authme.events.LoginEvent";
    private static final String LOGOUT_EVENT = "fr.xephi.authme.events.LogoutEvent";

    private final Plugin suld;
    private final Object api;
    private final Method isAuthenticated;
    private final Method isRegistered;
    private final String version;
    private volatile boolean failed;

    private AuthMeBridge(Plugin suld, Object api, Method isAuthenticated, Method isRegistered, String version) {
        this.suld = suld;
        this.api = api;
        this.isAuthenticated = isAuthenticated;
        this.isRegistered = isRegistered;
        this.version = version;
    }

    /** The bridge to the enabled AuthMe, its events wired to {@code events}; {@link AuthBridge#NONE} when it is not there. */
    static AuthBridge connect(Plugin suld, Events events) {
        Plugin authme = suld.getServer().getPluginManager().getPlugin(PLUGIN);
        if (authme == null || !authme.isEnabled()) {
            suld.getLogger().severe("AuthMe is not enabled: nobody can be logged in, every login is refused.");
            return NONE;
        }
        try {
            ClassLoader cl = authme.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(API, true, cl);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) throw new IllegalStateException("AuthMeApi.getInstance() is null");
            AuthMeBridge bridge = new AuthMeBridge(suld, api, apiClass.getMethod("isAuthenticated", Player.class),
                    apiClass.getMethod("isRegistered", String.class), authme.getPluginMeta().getVersion());
            bridge.listen(cl.loadClass(LOGIN_EVENT).asSubclass(Event.class), events::loggedIn);
            bridge.listen(cl.loadClass(LOGOUT_EVENT).asSubclass(Event.class), events::loggedOut);
            return bridge;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            suld.getLogger().log(Level.SEVERE, "AuthMe " + authme.getPluginMeta().getVersion()
                    + " does not offer the API SÜLD reads; every login is refused", e);
            return NONE;
        }
    }

    private void listen(Class<? extends Event> type, java.util.function.Consumer<Player> to) throws NoSuchMethodException {
        Method getPlayer = type.getMethod("getPlayer");
        Listener owner = new Listener() { };
        suld.getServer().getPluginManager().registerEvent(type, owner, EventPriority.MONITOR, (l, event) -> {
            if (!type.isInstance(event)) return;
            Player p;
            try {
                p = (Player) getPlayer.invoke(event);
            } catch (ReflectiveOperationException | ClassCastException e) {
                fail(e);
                return;
            }
            if (p == null) return;
            // AuthMe logs players in on its own threads: the gate is moved on the server thread only
            if (Bukkit.isPrimaryThread()) to.accept(p);
            else Bukkit.getScheduler().runTask(suld, () -> to.accept(p));
        }, suld, false);
    }

    private void fail(Throwable e) {
        if (!failed) suld.getLogger().log(Level.SEVERE, "AuthMe bridge failed; logins wait (fail closed)", e);
        failed = true;
    }

    @Override
    public boolean available() {
        return !failed;
    }

    @Override
    public boolean isAuthenticated(Player p) {
        if (failed || p == null) return false;
        try {
            return Boolean.TRUE.equals(isAuthenticated.invoke(api, p));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    @Override
    public boolean isRegistered(String name) {
        if (failed || name == null) return false;
        try {
            return Boolean.TRUE.equals(isRegistered.invoke(api, name));
        } catch (ReflectiveOperationException | RuntimeException e) {
            fail(e);
            return false;
        }
    }

    @Override
    public String describe() {
        return "AuthMe " + version + (failed ? " (FAILED: logins refused)" : "");
    }
}
