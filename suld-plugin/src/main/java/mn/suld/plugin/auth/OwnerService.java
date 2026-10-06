package mn.suld.plugin.auth;

import mn.suld.api.identity.AuthMode;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
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
 * Identity, not names: OP is granted only when the login is verified by Minecraft/Microsoft
 * ({@link AuthMode#verified()}: online mode or HMAC Velocity forwarding). On an owner's first verified join
 * the account UUID is pinned in {@code plugins/SULD/owners.yml}; after that only that UUID is an owner, so
 * whoever later takes the same name (after a rename) never gets OP. An entry may also be a UUID directly.
 * On an unverified (offline) server nothing is granted here: names can be spoofed there.
 */
public final class OwnerService implements Listener {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final Plugin plugin;
    private final AuthMode mode;
    private final File pinFile;
    private final Set<String> names = new HashSet<>();
    private final Set<UUID> uuids = new HashSet<>();
    /** lower-case name → pinned account UUID. */
    private final Map<String, UUID> pinned = new HashMap<>();

    public OwnerService(Plugin plugin, AuthMode mode, List<String> owners) {
        this.plugin = plugin;
        this.mode = mode;
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
        for (String key : yml.getKeys(false)) {
            try {
                pinned.put(key.toLowerCase(Locale.ROOT), UUID.fromString(yml.getString(key, "")));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("owners.yml: bad UUID for " + key);
            }
        }
    }

    /** Grant OP to owners already online (plugin reload). */
    public void start() {
        if (names.isEmpty() && uuids.isEmpty()) return;
        if (!mode.verified()) {
            plugin.getLogger().warning("owners: logins are not verified (" + mode + "), so owners are NOT auto-opped.");
            return;
        }
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (mode.verified()) apply(e.getPlayer());
    }

    /** Whether this (verified) player is an owner; pins the UUID on the first match by name. */
    synchronized boolean isOwner(Player p) {
        UUID id = p.getUniqueId();
        if (uuids.contains(id) || pinned.containsValue(id)) return true;
        String name = p.getName().toLowerCase(Locale.ROOT);
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
