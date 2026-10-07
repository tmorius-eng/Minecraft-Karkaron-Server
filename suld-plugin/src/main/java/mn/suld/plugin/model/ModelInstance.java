package mn.suld.plugin.model;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import mn.suld.api.model.Clip;
import mn.suld.api.model.Rig;
import mn.suld.api.model.RigLoader;
import mn.suld.api.model.Sampler;
import mn.suld.api.model.Xf;
import mn.suld.plugin.perf.PerfProbe;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * One rendered creature: an invisible host, a root {@link ItemDisplay} that follows it, and one bone display per
 * visible bone riding the root (docs/MODEL_RENDERER.md §1). Driven by {@link ModelService}; main thread only.
 */
public final class ModelInstance {

    private static final Color FLASH = Color.fromRGB(255, 90, 90);
    private static final int FLASH_TICKS = 4;
    private static final double WALK = 0.03, RUN = 0.2; // blocks per tick
    private static final int ACTION_FADE = 4;

    private final LivingEntity host;
    private final RigLoader.Model model;
    private final ItemDisplay root;
    private final ItemDisplay[] bones;
    private final ItemStack[] items;
    private final Xf[] sent;
    private final Set<UUID> ids = new HashSet<>();

    private String base = "idle";
    private long baseStart;
    private Clip action;
    private long actionStart;
    private double actionPrev;
    private BiConsumer<ModelInstance, Clip.Event> onEvent;
    private long flashUntil = -1;
    private boolean flashed;
    private boolean dying;
    private long deathStart;
    private Location lastLoc;
    private double speed;
    private double nearest = 0;
    private long now;

    private ModelInstance(LivingEntity host, RigLoader.Model model, ItemDisplay root, ItemDisplay[] bones, ItemStack[] items) {
        this.host = host;
        this.model = model;
        this.root = root;
        this.bones = bones;
        this.items = items;
        this.sent = new Xf[bones.length];
        ids.add(root.getUniqueId());
        for (ItemDisplay d : bones) if (d != null) ids.add(d.getUniqueId());
        this.lastLoc = host.getLocation();
    }

    static ModelInstance spawn(Plugin plugin, LivingEntity host, RigLoader.Model model) {
        Rig rig = model.rig();
        host.setInvisible(true);
        host.setSilent(true);
        host.setCustomNameVisible(false);
        host.addScoreboardTag(ModelService.HOST_TAG);
        Location at = host.getLocation();
        Location flat = at.clone();
        flat.setYaw(0);
        flat.setPitch(0);
        ItemDisplay root = at.getWorld().spawn(flat, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.addScoreboardTag(ModelService.TAG);
            d.setTeleportDuration(2);
        });
        ItemDisplay[] bones = new ItemDisplay[rig.bones().size()];
        ItemStack[] items = new ItemStack[rig.bones().size()];
        for (int i = 0; i < rig.bones().size(); i++) {
            Rig.Bone b = rig.bones().get(i);
            if (!b.model()) continue;
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("suld", "entity/" + rig.id() + "/" + b.id()));
            it.setItemMeta(meta);
            items[i] = it;
            bones[i] = at.getWorld().spawn(flat, ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.addScoreboardTag(ModelService.TAG);
                d.setItemStack(it);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(1.5f); // × 64 blocks
                d.setShadowRadius(0);
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(3);
            });
            root.addPassenger(bones[i]);
        }
        ModelInstance inst = new ModelInstance(host, model, root, bones, items);
        inst.update(0, true);
        return inst;
    }

    boolean hostValid() {
        return host.isValid() && !host.isDead();
    }

    boolean owns(UUID display) {
        return ids.contains(display);
    }

    int displayCount() {
        int n = 1;
        for (ItemDisplay d : bones) if (d != null) n++;
        return n;
    }

    public LivingEntity host() {
        return host;
    }

    public RigLoader.Model model() {
        return model;
    }

    /** Play a one-shot (or looping) action over the locomotion; returns false for an unknown clip. */
    public boolean play(String clip, BiConsumer<ModelInstance, Clip.Event> events) {
        Clip c = model.clip(clip);
        if (c == null || dying) return false;
        action = c;
        actionStart = now;
        actionPrev = -1;
        onEvent = events;
        return true;
    }

    /** The playing action's name, or null. */
    public String action() {
        return action == null ? null : action.name();
    }

    void flash() {
        long t = PerfProbe.start();
        flashUntil = now + FLASH_TICKS;
        if (!flashed) {
            for (int i = 0; i < bones.length; i++) {
                if (bones[i] == null) continue;
                ItemStack red = items[i].clone();
                red.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(FLASH).build());
                bones[i].setItemStack(red);
            }
            flashed = true;
        }
        PerfProbe.stop("model.flash", t);
    }

    void die() {
        dying = true;
        deathStart = now;
        action = model.clip("death");
        actionStart = now;
        actionPrev = -1;
        onEvent = null;
    }

    boolean deathDone() {
        Clip d = model.clip("death");
        return dying && now - deathStart > (d == null ? 0 : d.length()) + 20;
    }

    void despawn() {
        for (ItemDisplay d : bones) if (d != null && d.isValid()) d.remove();
        if (root.isValid()) root.remove();
        host.removeScoreboardTag(ModelService.HOST_TAG);
        if (host.isValid() && !host.isDead()) host.setInvisible(false); // plugin disable: never leave an invisible mob
    }

    /** One server tick; returns the number of bone transformations sent. */
    int tick(long tick) {
        now = tick;
        if (!dying) follow();
        if (flashed && now >= flashUntil) {
            for (int i = 0; i < bones.length; i++) if (bones[i] != null) bones[i].setItemStack(items[i]);
            flashed = false;
        }
        if (tick % 10 == 0) nearest = nearestViewer();
        int every = nearest < 24 ? 2 : nearest < 48 ? 4 : 0;
        if (every == 0 && !dying) return 0; // nobody near: frozen (the root still follows)
        if (every == 0) every = 4;
        if (tick % every != 0) return 0;
        return update(every, false);
    }

    private void follow() {
        Location l = host.getLocation();
        double d = l.getWorld() == lastLoc.getWorld() ? Math.hypot(l.getX() - lastLoc.getX(), l.getZ() - lastLoc.getZ()) : 0;
        speed = speed * 0.7 + d * 0.3;
        if (l.getWorld() != lastLoc.getWorld() || l.distanceSquared(lastLoc) > 1e-4) {
            Location flat = l.clone();
            flat.setYaw(0);
            flat.setPitch(0);
            root.teleport(flat, io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
            lastLoc = l;
        }
        String want = speed > RUN && model.clip("run") != null ? "run" : speed > WALK ? "walk" : "idle";
        if (!want.equals(base)) {
            base = want;
            baseStart = now;
        }
    }

    private double nearestViewer() {
        double best = Double.MAX_VALUE;
        Location l = root.getLocation();
        for (Player p : l.getWorld().getPlayers()) best = Math.min(best, p.getLocation().distanceSquared(l));
        return Math.sqrt(best);
    }

    private int update(int every, boolean force) {
        long t = PerfProbe.start();
        Clip baseClip = model.clip(base);
        Sampler.Layer layerBase = new Sampler.Layer(baseClip, now - baseStart, 1);
        Sampler.Layer layerAction = null;
        if (action != null) {
            double at = now - actionStart;
            if (onEvent != null) for (Clip.Event e : action.eventsBetween(actionPrev, at)) onEvent.accept(this, e);
            actionPrev = at;
            double w = Math.min(1, (at + 1) / ACTION_FADE);
            if (action.finished(at)) {
                double out = 1 - (at - action.length()) / ACTION_FADE;
                if (out <= 0 && !dying) {
                    action = null;
                    onEvent = null;
                } else {
                    w = dying ? 1 : Math.max(0, out);
                }
            }
            if (action != null) layerAction = new Sampler.Layer(action, at, w);
        }
        float yaw = dying ? lastYaw : host.getBodyYaw();
        lastYaw = yaw;
        Xf[] pose = Sampler.display(model.rig(), Sampler.pose(model.rig(), layerBase, layerAction), yaw);
        int n = 0;
        for (int i = 0; i < bones.length; i++) {
            if (bones[i] == null) continue;
            Xf x = pose[i], s = sent[i];
            if (!force && s != null && s.translation().distance(x.translation()) < 0.003 && s.rotation().angleTo(x.rotation()) < 0.3
                    && Math.abs(s.scale() - x.scale()) < 0.001) continue;
            bones[i].setInterpolationDelay(0);
            bones[i].setInterpolationDuration(Math.max(2, every + 1));
            float sc = (float) x.scale();
            bones[i].setTransformation(new Transformation(
                    new Vector3f((float) x.translation().x(), (float) x.translation().y(), (float) x.translation().z()),
                    new Quaternionf((float) x.rotation().x(), (float) x.rotation().y(), (float) x.rotation().z(), (float) x.rotation().w()),
                    new Vector3f(sc, sc, sc), new Quaternionf()));
            sent[i] = x;
            n++;
        }
        PerfProbe.stop("model.anim", t);
        return n;
    }

    private float lastYaw;

    /** The bones' world positions (VFX anchors): host location + the bone's display translation. */
    public Location bone(String id) {
        int i = model.rig().index(id);
        Location l = root.getLocation();
        if (i < 0 || sent[i] == null) return l;
        return l.add(sent[i].translation().x(), sent[i].translation().y(), sent[i].translation().z());
    }

    List<Entity> entities() {
        java.util.ArrayList<Entity> out = new java.util.ArrayList<>();
        out.add(root);
        for (ItemDisplay d : bones) if (d != null) out.add(d);
        return out;
    }
}
