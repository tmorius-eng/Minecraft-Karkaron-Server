package mn.suld.plugin.auth;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A joined player enters SÜLD's game: every SÜLD join action (screens, rewards, items, OP, the HUD) listens to this,
 * not to {@link org.bukkit.event.player.PlayerJoinEvent}. {@link AuthenticationService} fires it on the server thread
 * once per connection, after the session is bound: at the end of the join when identities are verified there, and
 * behind AuthMe only once AuthMe has logged the player in (a joined player may not be logged in yet).
 */
public final class SuldJoinEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public SuldJoinEvent(@NotNull Player who) {
        super(who);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
