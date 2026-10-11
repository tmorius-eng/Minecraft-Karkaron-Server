package mn.suld.plugin.content;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.skill.tree.SkillTreeLoader.Issue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The content in use (docs/CONTENT_DATA.md). The plugin installs the server's files at load, before any content is
 * read; the simulator and the unit tests read the bundled files. Content is read once: an edited file takes effect
 * on the next restart ({@code /suld content validate} checks the files first).
 */
public final class Content {

    private static volatile ContentPack pack;
    private static volatile boolean read;

    private Content() {
    }

    /** The content in use (the bundled files unless the plugin installed the server's). */
    public static ContentPack pack() {
        ContentPack p = pack;
        if (p == null) {
            synchronized (Content.class) {
                if (pack == null) pack = bundled();
                p = pack;
            }
        }
        read = true;
        return p;
    }

    /** The bundled content; it is shipped with the jar and must always be valid (ContentDataTest). */
    public static ContentPack bundled() {
        ContentLoader.Result r = ContentLoader.load(ContentLoader.classpath());
        if (!r.ok()) throw new IllegalStateException("the bundled content files are broken: " + r.issues());
        return r.pack();
    }

    /**
     * Plugin load: use the server's content files ({@code plugins/SULD/content/}) where it has them. A set with any
     * problem is not used: the problems are logged and the bundled content runs instead.
     */
    public static List<Issue> install(Path dir, Logger log) {
        List<String> overridden = new ArrayList<>();
        ContentLoader.Result r = ContentLoader.load(ContentLoader.serverOrBundled(dir, overridden), null, null, overridden);
        synchronized (Content.class) {
            if (read) log.warning("[content] content was read before the plugin loaded it: the server's files apply after a restart");
            if (r.ok()) {
                pack = r.pack();
                if (!overridden.isEmpty()) log.info("[content] using the server's " + String.join(", ", overridden) + " (plugins/SULD/content)");
            } else {
                for (Issue i : r.issues()) log.warning("[content] " + i);
                log.warning("[content] the content files have " + r.issues().size() + " problem(s): using the bundled content");
                pack = bundled();
            }
        }
        ContentPack p = pack;
        log.info("[content] " + p.mobs().size() + " mobs, " + p.regions().size() + " regions, " + p.areas().size() + " areas, "
                + p.dungeons().size() + " dungeons, " + p.chapters().size() + " story chapters");
        return r.issues();
    }

    /** A mob the code names directly ({@link ContentLoader#REQUIRED} guarantees it exists). */
    static MobDefinition mob(String id) {
        MobDefinition m = pack().mob(id);
        if (m == null) throw new IllegalStateException("content: required mob " + id + " is missing");
        return m;
    }
}
