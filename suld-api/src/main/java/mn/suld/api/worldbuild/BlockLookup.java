package mn.suld.api.worldbuild;

/** Reads the block at a city-local position: from a compiled city, or from a real world snapshot. */
@FunctionalInterface
public interface BlockLookup {
    String blockAt(int x, int y, int z);
}
