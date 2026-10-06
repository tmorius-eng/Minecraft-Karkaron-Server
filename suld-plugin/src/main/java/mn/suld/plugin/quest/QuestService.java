package mn.suld.plugin.quest;

import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestEngine;
import mn.suld.api.quest.QuestState;
import mn.suld.api.service.ProgressionService;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Drives quest progression for Vertical Slice 1 using the pure {@link QuestEngine},
 * then applies rewards (EXP + currency) and player feedback. Slice 1 ships the
 * single tutorial quest {@link SuldContent#FIRST_HUNT}; the lookup is structured
 * so a full data-driven quest registry drops in later.
 */
public final class QuestService {

    private final QuestEngine engine = new QuestEngine();
    private final ProgressionService progression;

    public QuestService(ProgressionService progression) {
        this.progression = progression;
    }

    /** Assign the first tutorial quest if the player has none and hasn't done it. */
    public boolean startFirstQuestIfNeeded(PlayerProfile profile) {
        QuestState s = profile.questState();
        if (s.questId().isEmpty() && !s.completed()) {
            profile.questState(SuldContent.FIRST_HUNT.initialState());
            return true;
        }
        return false;
    }

    private QuestDefinition definition(String questId) {
        return SuldContent.FIRST_HUNT.id().equals(questId) ? SuldContent.FIRST_HUNT : null;
    }

    /**
     * Advance the player's active quest on a mob kill. Returns true if quest
     * state changed (so the caller can refresh the HUD).
     */
    public boolean onMobKilled(Player player, PlayerProfile profile, String mobId) {
        QuestState before = profile.questState();
        QuestDefinition def = definition(before.questId());
        if (def == null || !before.active()) {
            return false;
        }
        QuestState after = engine.onMobKilled(before, def, mobId);
        if (after.equals(before)) {
            return false;
        }
        profile.questState(after);

        if (engine.justCompleted(before, after)) {
            progression.grantExp(profile, def.expReward(), ExpSource.QUEST);
            profile.addCurrency(def.currencyReward());
            player.showTitle(Title.title(
                    Component.text("Эрэл Дууслаа", Messages.BRAND),
                    Component.text(def.title(), NamedTextColor.WHITE),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
            player.sendMessage(Messages.success("Эрэл дууслаа: " + def.title()
                    + "  (+" + def.expReward() + " EXP, +" + def.currencyReward() + " зоос)"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } else {
            player.sendMessage(Messages.info("Эрэл: " + def.title() + " "
                    + after.progress() + "/" + def.requiredCount()));
        }
        return true;
    }
}
