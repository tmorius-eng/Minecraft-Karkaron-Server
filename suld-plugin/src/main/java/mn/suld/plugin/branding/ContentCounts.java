package mn.suld.plugin.branding;

import mn.suld.plugin.content.Content;
import mn.suld.plugin.content.ContentPack;

/**
 * In-game text never types a content count by hand (audit DOC-001: the MOTD, tips and guide boards said 18 chapters
 * and 4 dungeons long after the story had 42 and the ladder 10). Lines carry placeholders that are filled from the
 * loaded content pack: {@code {chapters} {dungeons} {regions} {areas} {sites} {relics} {campaigns} {shops} {npcs} {sideQuests}}.
 */
public final class ContentCounts {

    private ContentCounts() {
    }

    public static String fill(String text) {
        if (text == null || text.indexOf('{') < 0) return text;
        return fill(text, Content.pack());
    }

    static String fill(String text, ContentPack p) {
        return text.replace("{chapters}", Integer.toString(p.chapters().size()))
                .replace("{dungeons}", Integer.toString(p.dungeons().size()))
                .replace("{regions}", Integer.toString(p.regions().size()))
                .replace("{areas}", Integer.toString(p.areas().size()))
                .replace("{sites}", Integer.toString(p.historicSites().size()))
                .replace("{relics}", Integer.toString(p.relics().size()))
                .replace("{campaigns}", Integer.toString(p.campaigns().size()))
                .replace("{shops}", Integer.toString(p.shops().size()))
                .replace("{npcs}", Integer.toString(p.npcs().size()))
                .replace("{sideQuests}", Integer.toString(p.sideQuests().size()));
    }
}
