package mn.suld.plugin.model;

import mn.suld.api.model.Clip;
import mn.suld.api.model.RigLoader;
import mn.suld.plugin.perf.PerfProbe;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * SÜLD's Display-Entity model renderer (docs/MODEL_RENDERER.md): the one manager of every creature rig. It loads
 * rigs from the plugin resources ({@code models/index.json} → {@code models/<id>/rig.json} + {@code clips.json},
 * validated by {@link RigLoader}), attaches them to host entities, and drives them all from a single 1-tick timer:
 * the root follows its host every tick it moved; bone transforms are re-sampled at 10 Hz near players, 5 Hz further
 * out and not at all beyond 48 blocks, and only changed bones are sent. Bosses, elites and future creatures use the
 * same path ({@link #attach}); nothing here is specific to one creature.
 */
public final class ModelService implements Listener {

    public static final String TAG = "suld_model";
    /** Carried by hosts while a rig renders them. */
    public static final String HOST_TAG = "suld_model_host";

    private final Plugin plugin;
    private final Map<String, RigLoader.Model> models = new HashMap<>();
    private final Map<UUID, ModelInstance> byHost = new HashMap<>();
    private final List<ModelInstance> dying = new ArrayList<>();
    private long tick;
    /** Transform updates sent (benchmarks: the network-load proxy). */
    private long transformsSent;

    public ModelService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Load every rig named in {@code models/index.json}; problems are logged and the rig is skipped. */
    public void load() {
        models.clear();
        String index = resource("models/index.json");
        if (index == null) return;
        java.util.Map<String, Object> idx = mn.suld.api.json.Json.object(mn.suld.api.json.Json.parse(index));
        mobRigs.clear();
        Object mobs = idx.get("mobs");
        if (mobs != null) mn.suld.api.json.Json.object(mobs).forEach((mob, rig) -> mobRigs.put(mob, String.valueOf(rig)));
        for (Object o : mn.suld.api.json.Json.array(idx.get("models"))) {
            String id = String.valueOf(o);
            String rig = resource("models/" + id + "/rig.json"), clips = resource("models/" + id + "/clips.json");
            if (rig == null || clips == null) {
                plugin.getLogger().warning("model " + id + ": rig.json or clips.json missing");
                continue;
            }
            RigLoader.Result r = RigLoader.load(rig, clips);
            if (!r.ok()) {
                plugin.getLogger().warning("model " + id + " not loaded: " + r.issues());
                continue;
            }
            models.put(id, r.model());
            plugin.getLogger().info("model " + id + ": " + r.model().rig().bones().size() + " bones, " + r.model().rig().displayCount()
                    + " displays, " + r.model().clips().size() + " clips");
        }
    }

    private String resource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private final Map<String, String> mobRigs = new java.util.HashMap<>();

    /** The rig a SÜLD mob is dressed in (models/index.json "mobs"), if it is loaded. */
    public Optional<String> rigForMob(String mobId) {
        String r = mobRigs.get(mobId);
        return r != null && models.containsKey(r) ? Optional.of(r) : Optional.empty();
    }

    public boolean has(String modelId) {
        return models.containsKey(modelId);
    }

    public Set<String> modelIds() {
        return Set.copyOf(models.keySet());
    }

    /** Attach a rig to a host; the host becomes invisible and silent (its AI, hitbox and health stay). */
    public Optional<ModelInstance> attach(LivingEntity host, String modelId) {
        RigLoader.Model m = models.get(modelId);
        if (m == null || byHost.containsKey(host.getUniqueId())) return Optional.ofNullable(byHost.get(host.getUniqueId()));
        long t = PerfProbe.start();
        ModelInstance inst = ModelInstance.spawn(plugin, host, m);
        PerfProbe.stop("model.spawn", t);
        byHost.put(host.getUniqueId(), inst);
        return Optional.of(inst);
    }

    public Optional<ModelInstance> of(Entity host) {
        return Optional.ofNullable(byHost.get(host.getUniqueId()));
    }

    /** True for a host rendered by a rig (HUD targeting, name bars). */
    public boolean isHost(Entity e) {
        return byHost.containsKey(e.getUniqueId());
    }

    /** Play a one-shot clip; {@code onEvent} gets its events (e.g. "bite_hit") at their ticks. */
    public boolean play(Entity host, String clip, BiConsumer<ModelInstance, Clip.Event> onEvent) {
        ModelInstance i = byHost.get(host.getUniqueId());
        return i != null && i.play(clip, onEvent);
    }

    public int instances() {
        return byHost.size();
    }

    public int displays() {
        int n = 0;
        for (ModelInstance i : byHost.values()) n += i.displayCount();
        for (ModelInstance i : dying) n += i.displayCount();
        return n;
    }

    public long transformsSent() {
        return transformsSent;
    }

    private void tick() {
        tick++;
        long t = PerfProbe.start();
        for (java.util.Iterator<Map.Entry<UUID, ModelInstance>> it = byHost.entrySet().iterator(); it.hasNext(); ) {
            ModelInstance i = it.next().getValue();
            if (!i.hostValid()) {
                // removed without dying (despawned, chunk unloaded, killed by /kill without a death event)
                long d = PerfProbe.start();
                i.despawn();
                PerfProbe.stop("model.despawn", d);
                it.remove();
                continue;
            }
            transformsSent += i.tick(tick);
        }
        for (java.util.Iterator<ModelInstance> it = dying.iterator(); it.hasNext(); ) {
            ModelInstance i = it.next();
            transformsSent += i.tick(tick);
            if (i.deathDone()) {
                long d = PerfProbe.start();
                i.despawn();
                PerfProbe.stop("model.despawn", d);
                it.remove();
            }
        }
        PerfProbe.stop("model.tick", t);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        ModelInstance i = byHost.get(e.getEntity().getUniqueId());
        if (i != null && e.getFinalDamage() > 0) i.flash();
    }

    /** A rigged mob that lands a hit plays its attack clip (bosses with a brain play their own clips instead). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        ModelInstance i = byHost.get(e.getDamager().getUniqueId());
        if (i != null && i.model().clip("attack") != null) i.play("attack", null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        ModelInstance i = byHost.remove(e.getEntity().getUniqueId());
        if (i == null) return;
        i.die();
        dying.add(i);
    }

    /** Leftover rig displays from a crash or an unload are removed when their chunk loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) {
            if (en instanceof ItemDisplay && en.getScoreboardTags().contains(TAG) && !owned(en.getUniqueId())) en.remove();
        }
    }

    private boolean owned(UUID display) {
        for (ModelInstance i : byHost.values()) if (i.owns(display)) return true;
        for (ModelInstance i : dying) if (i.owns(display)) return true;
        return false;
    }

    /** Plugin disable: every rig goes (hosts stay; they are re-dressed on the next spawn path). */
    public void shutdown() {
        for (ModelInstance i : byHost.values()) i.despawn();
        for (ModelInstance i : dying) i.despawn();
        byHost.clear();
        dying.clear();
    }

    /** Spawn location helper for benchmarks. */
    public static Location ground(Location l) {
        return l.getWorld().getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
    }
}
