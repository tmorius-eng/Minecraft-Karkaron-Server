package mn.suld.plugin.auth;

import mn.suld.api.identity.AuthMode;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Server owners ({@code owners:} in config.yml) are always operators: OP is (re-)granted on every join.
 * <p>
 * Identity, not names: OP is granted only when the login is verified ({@link AuthMode#verified()}: online mode, HMAC
 * Velocity forwarding, or behind AuthMe once AuthMe has logged the player in, {@link SuldJoinEvent}). On an owner's first verified join
 * the account UUID is pinned in {@code plugins/SULD/owners.yml}; after that only that UUID is an owner, so
 * whoever later takes the same name (after a rename) never gets OP. An entry may also be a UUID directly.
 * On an unverified (offline) server nothing is granted here: names can be spoofed there.
 * <p>
 * A pin counts only while its name is still configured (SEC-001): removing a name from {@code owners:} drops the
 * pin from {@code owners.yml} on the next start, and the former owner is no longer re-opped on join. The OP they
 * already hold is revoked by the admin ({@code /deop}, {@code op.sh --deop}); this class never takes OP away.
 */
public final class OwnerService implements Listener {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final Plugin plugin;
    private final AuthMode mode;
    private final AuthenticationService auth;
    private final File pinFile;
    private final Set<String> names = new HashSet<>();
    private final Set<UUID> uuids = new HashSet<>();
    /** lower-case name → pinned account UUID. */
    private final Map<String, UUID> pinned = new HashMap<>();

    public OwnerService(Plugin plugin, AuthenticationService auth, List<String> owners) {
        this.plugin = plugin;
        this.auth = auth;
        this.mode = auth.policy().mode();
        this.pinFile = new File(plugin.getDataFolder(), "owners.yml");
        for (String raw : owners) {
            String o = raw == null ? "" : raw.trim();
            try {
                uuids.add(UUID.fromString(o));
                continue;
            } catch (IllegalArgumentException notUuid) {
                // a name
            }
            if (NAME.matcher(o).matches()) names.add(o.toLowerCase(Locale.ROOT));
            else if (!o.isEmpty()) plugin.getLogger().warning("owners: ignoring invalid entry '" + o + "'");
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(pinFile);
        boolean pruned = false;
        for (String key : yml.getKeys(false)) {
            String name = key.toLowerCase(Locale.ROOT);
            UUID id;
            try {
                id = UUID.fromString(yml.getString(key, ""));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("owners.yml: bad UUID for " + key + "; dropped");
                pruned = true;
                continue;
            }
            if (!names.contains(name)) {
                // SEC-001: the name was removed from owners: -> the pin no longer grants anything
                plugin.getLogger().warning("owners: " + key + " (" + id + ") is no longer in owners:, pin dropped. "
                        + "If the account still has OP, revoke it: /deop " + key);
                pruned = true;
                continue;
            }
            pinned.put(name, id);
        }
        if (pruned) savePins();
    }

    /** Grant OP to owners already online (plugin reload). */
    public void start() {
        if (names.isEmpty() && uuids.isEmpty()) return;
        if (!mode.verified()) {
            plugin.getLogger().warning("owners: logins are not verified (" + mode + "), so owners are NOT auto-opped "
                    + "(they only get the cosmetic owner badge).");
            return;
        }
        if (mode == AuthMode.HYBRID_AUTHME) {
            // behind AuthMe the name is the account: whoever registers an owner's name first holds it
            for (String n : names) {
                if (!pinned.containsKey(n) && !auth.bridge().isRegistered(n)) {
                    plugin.getLogger().severe("owners: " + n + " has no AuthMe account yet. Register it before anyone else "
                            + "can (console: authme register " + n + " <password>), or whoever registers it is opped.");
                }
            }
        }
        Bukkit.getOnlinePlayers().stream().filter(auth::admitted).forEach(this::apply);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(SuldJoinEvent e) {
        Player p = e.getPlayer();
        if (mode.verified()) {
            apply(p);
        } else if (names.contains(p.getName().toLowerCase(Locale.ROOT)) || uuids.contains(p.getUniqueId())) {
            // Unverified (local dev) server: names can be spoofed, so no OP here — only the cosmetic owner badge.
            p.addAttachment(plugin, "suld.badge.owner", true);
        }
    }

    /** Whether this (verified) player is an owner; pins the UUID on the first match by name. */
    synchronized boolean isOwner(Player p) {
        UUID id = p.getUniqueId();
        if (uuids.contains(id)) return true;
        String name = p.getName().toLowerCase(Locale.ROOT);
        if (names.contains(name) && id.equals(pinned.get(name))) return true;
        // renamed owner account: the pin under their old name still identifies them while that name is configured
        if (pinned.containsValue(id)) return true;
        if (!names.contains(name)) return false;
        UUID pin = pinned.get(name);
        if (pin != null) {
            plugin.getLogger().warning("owners: " + p.getName() + " (" + id + ") is not the pinned owner account " + pin
                    + "; no OP. If the owner changed accounts, edit plugins/SULD/owners.yml.");
            return false;
        }
        pinned.put(name, id);
        savePins();
        plugin.getLogger().info("owners: pinned " + p.getName() + " to account " + id);
        return true;
    }

    private void apply(Player p) {
        if (!isOwner(p)) return;
        p.addAttachment(plugin, "suld.badge.owner", true);
        if (!p.isOp()) {
            p.setOp(true);
            plugin.getLogger().info("owners: " + p.getName() + " is an owner, granted OP");
        }
    }

    private void savePins() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.options().setHeader(List.of("Owner accounts pinned by SULD on their first verified join (name -> UUID).",
                "Only these UUIDs get automatic OP. Delete a line to re-pin that name on its next verified join."));
        pinned.forEach((n, u) -> yml.set(n, u.toString()));
        try {
            yml.save(pinFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("owners: cannot save owners.yml: " + ex.getMessage());
        }
    }
}
