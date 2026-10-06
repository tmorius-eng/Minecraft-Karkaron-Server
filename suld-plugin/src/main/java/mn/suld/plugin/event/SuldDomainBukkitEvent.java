package mn.suld.plugin.event;

import mn.suld.api.event.SuldEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit event wrapper that carries a SULD domain {@link SuldEvent} onto the
 * Bukkit event bus, so other plugins/modules can observe SULD gameplay events
 * through the standard listener mechanism without depending on SULD internals.
 *
 * <p>Phase 1 ships this single generic wrapper; later phases may add strongly
 * typed Bukkit events for the most-consumed domain events.
 */
public class SuldDomainBukkitEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final SuldEvent payload;

    public SuldDomainBukkitEvent(SuldEvent payload) {
        this.payload = payload;
    }

    public SuldEvent payload() {
        return payload;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
