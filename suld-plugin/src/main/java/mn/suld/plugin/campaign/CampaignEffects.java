package mn.suld.plugin.campaign;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.audit.AuditActions;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.campaign.WeeklyCycle;
import mn.suld.api.campaign.WorldEffect;
import mn.suld.api.campaign.WorldFlag;
import mn.suld.api.config.CampaignSettings;
import mn.suld.api.persistence.CampaignRepository;
import mn.suld.plugin.SuldServices;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * What a successful campaign leaves in the world (docs/KHURALDAI.md §Амьд ертөнц). The single source of truth is the
 * {@code suld_world_state} flag: {@link #apply} writes the flag and then makes the change live; {@link #restore}
 * re-reads every flag on start and makes the live ones live again and the lapsed ones go away. Every live change is
 * idempotent, so applying twice is harmless.
 */
public final class CampaignEffects {

    private final Plugin plugin;
    private final SuldServices services;
    private final CampaignRepository repository;
    private final CampaignSettings settings;
    private final WeeklyCycle cycle;
    /** The flags currently live in the world, by key. */
    private final Set<String> live = new HashSet<>();

    public CampaignEffects(Plugin plugin, SuldServices services, CampaignRepository repository, CampaignSettings settings, WeeklyCycle cycle) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
        this.settings = settings;
        this.cycle = cycle;
    }

    /** Record the effect of {@code campaignId} (won in {@code cycleId}) and make it live. Main thread. */
    public void apply(WorldEffect effect, String campaignId, String cycleId) {
        String expires = null;
        if (settings.effectCycles() > 0) {
            expires = cycleId;
            for (int i = 0; i <= settings.effectCycles(); i++) expires = cycle.nextCycleId(expires);
        }
        WorldFlag flag = new WorldFlag(effect.flagKey(campaignId), effect.encode(), cycleId, expires, System.currentTimeMillis());
        repository.setFlag(flag).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[campaign] world flag " + flag.key() + " not saved: " + root(err));
                return;
            }
            makeLive(flag, effect);
            services.audit().record(AuditEvent.of("server", AuditActions.CAMPAIGN_EFFECT_APPLIED, flag.key(), effect.encode()));
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.WORLD_EFFECT_APPLIED, null,
                    Map.of("campaign", campaignId, "type", effect.type().name(), "ref", effect.ref())));
        }));
    }

    /** On start and at every cycle boundary: live flags live, lapsed flags gone. Main thread. */
    public void restore(String cycleId) {
        repository.flags().whenComplete((flags, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "[campaign] world flags not read: " + root(err));
                return;
            }
            int on = 0, off = 0;
            for (WorldFlag f : flags) {
                WorldEffect effect;
                try {
                    effect = WorldEffect.decode(f.value());
                } catch (RuntimeException bad) {
                    plugin.getLogger().warning("[campaign] world flag " + f.key() + " is unreadable (" + f.value() + "); cleared");
                    repository.clearFlag(f.key());
                    continue;
                }
                if (f.liveIn(cycleId)) {
                    makeLive(f, effect);
                    on++;
                } else {
                    undo(f, effect);
                    repository.clearFlag(f.key());
                    off++;
                }
            }
            if (on + off > 0) plugin.getLogger().info("[campaign] world effects: " + on + " live, " + off + " lapsed");
        }));
    }

    public List<String> liveKeys() {
        return List.copyOf(live);
    }

    private void makeLive(WorldFlag flag, WorldEffect effect) {
        if (!live.add(flag.key())) return;
        switch (effect.type()) {
            case UNLOCK_EVENT -> services.worldEvents().unlock(effect.ref());
            case PROP -> {
                if (services.props == null) plugin.getLogger().warning("[campaign] no prop service: " + flag.key() + " waits for the next start");
                else if (!services.props.placeDynamic(flag.key(), effect.ref(), effect.anchor(), effect.dx(), effect.dz(), effect.yaw()))
                    plugin.getLogger().warning("[campaign] prop " + effect.ref() + " unknown: " + flag.key() + " shows nothing");
            }
            case NPC -> {
                if (services.npcs == null) plugin.getLogger().warning("[campaign] no NPC service: " + flag.key() + " waits for the next start");
                else services.npcs.addDynamic(flag.key(), effect.ref(), effect.anchor(), effect.dx(), effect.dz(), effect.yaw());
            }
        }
    }

    private void undo(WorldFlag flag, WorldEffect effect) {
        live.remove(flag.key());
        switch (effect.type()) {
            case UNLOCK_EVENT -> services.worldEvents().lock(effect.ref());
            case PROP -> {
                if (services.props != null) services.props.removeDynamic(flag.key());
            }
            case NPC -> {
                if (services.npcs != null) services.npcs.removeDynamic(flag.key());
            }
        }
    }

    private static String root(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }
}
