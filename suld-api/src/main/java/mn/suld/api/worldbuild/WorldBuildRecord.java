package mn.suld.api.worldbuild;

import java.time.Instant;

/** A structure that has been built into a world (origin = blueprint (0,0,0), i.e. ground level). */
public record WorldBuildRecord(String buildId, String fingerprint, String world, int originX, int originY, int originZ,
                               int blocks, Instant builtAt) {
}
