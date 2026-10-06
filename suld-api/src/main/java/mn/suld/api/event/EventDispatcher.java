package mn.suld.api.event;

/**
 * Sink for {@link SuldEvent}s emitted by domain services.
 *
 * <p>The domain layer depends only on this interface. The Paper layer provides
 * an implementation that forwards to the Bukkit event bus; tests provide a
 * recording fake. A {@link #NOOP} is available where events are irrelevant.
 */
@FunctionalInterface
public interface EventDispatcher {

    void dispatch(SuldEvent event);

    EventDispatcher NOOP = event -> { };
}
