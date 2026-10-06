package mn.suld.api.relic;

/**
 * Where a relic is. There is deliberately no "dropped in the world" state: a relic is either
 * waiting at its shrine or carried by exactly one bearer. It never exists as an item entity.
 * (Stored as the V1 {@code state} column; {@code UNCLAIMED}/{@code OWNED} match its values.)
 */
public enum RelicState {
    UNCLAIMED,
    OWNED
}
