package mn.suld.api.identity;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The authenticated identity of a player. <b>The UUID is the identity</b>; the name is a
 * mutable display attribute. Two identities are equal iff their UUIDs are equal, so a
 * renamed account is the same player and can never become a second character.
 *
 * <p>Instances are only ever built from the server's authenticated login profile, never
 * from anything the client typed (chat, commands, sign text).
 */
public final class PlayerIdentity {

    /** Minecraft Java names: 1–16 of [A-Za-z0-9_] (legacy accounts can be shorter than 3). */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final UUID uuid;
    private final String name;

    private PlayerIdentity(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static PlayerIdentity of(UUID uuid, String name) {
        Objects.requireNonNull(uuid, "uuid");
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid Minecraft name");
        }
        return new PlayerIdentity(uuid, name);
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }

    /**
     * Mojang/Microsoft-authenticated Java accounts always have random (version 4) UUIDs.
     * Offline-mode servers derive version 3 UUIDs from the name — so a v3 UUID on a server
     * that believes it is authenticating is proof of misconfiguration or spoofing.
     */
    public boolean hasAuthenticatedUuidShape() {
        return uuid.version() == 4 && uuid.variant() == 2;
    }

    /** True when the stored display name differs from the authenticated one (account rename). */
    public boolean isRenameOf(String storedName) {
        return storedName != null && !storedName.equals(name);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlayerIdentity other && uuid.equals(other.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return name + "(" + uuid + ")";
    }
}
