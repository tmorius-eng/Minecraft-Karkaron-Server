package mn.suld.api.relic;

/** Outcome of a compare-and-set: on failure {@code current} is the fresh stored record. */
public record CasResult(boolean success, RelicRecord current) {
}
