package mn.suld.plugin.persistence;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.tree.SkillEngine;
import mn.suld.api.skill.tree.SkillState;
import mn.suld.api.skill.tree.SkillTree;
import mn.suld.api.skill.tree.SkillTreeLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-database test of the skill-tree columns. Opt-in: SULD_TEST_PG_URL / _USER / _PASS (throwaway database). */
@EnabledIfEnvironmentVariable(named = "SULD_TEST_PG_URL", matches = "jdbc:postgresql:.+")
class JdbcSkillStateIT {

    @Test
    void skillStateSurvivesSaveAndLoad_andBadDataStopsTheLoad() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(System.getenv("SULD_TEST_PG_URL"));
        ds.setUser(System.getenv("SULD_TEST_PG_USER"));
        ds.setPassword(System.getenv("SULD_TEST_PG_PASS"));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
        }
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, SqlDialect.POSTGRESQL).migrate());
        JdbcProfileRepository repo = new JdbcProfileRepository(ds, SqlDialect.POSTGRESQL, Executors.newSingleThreadExecutor());
        SkillTree tree = SkillTreeLoader.loadAll(SkillTreeLoader.classpath()).trees().get(PlayerClass.BAATAR);

        // a player with nothing learned loads the empty state (also what every pre-V10 row looks like)
        UUID fresh = UUID.randomUUID();
        PlayerProfile p0 = PlayerProfile.createNew(fresh, "Fresh", Instant.now());
        p0.selectClass(PlayerClass.BAATAR);
        repo.save(p0).join();
        assertEquals(SkillState.NONE, repo.find(fresh).join().orElseThrow().skillState());

        UUID id = UUID.randomUUID();
        PlayerProfile p = PlayerProfile.createNew(id, "Skilled", Instant.now());
        p.selectClass(PlayerClass.BAATAR);
        SkillEngine.grant(p, 12);
        SkillEngine.Context ctx = new SkillEngine.Context(10, 0, 0);
        assertTrue(SkillEngine.unlock(p, tree, tree.node("l1"), ctx).ok());
        assertTrue(SkillEngine.unlock(p, tree, tree.node("l1"), ctx).ok());
        assertTrue(SkillEngine.saveBuild(p, tree, "tank", 3).ok());
        assertTrue(SkillEngine.resetAll(p, tree, 1_700_000_000_000L, 0).ok());
        assertTrue(SkillEngine.unlock(p, tree, tree.node("r1"), ctx).ok());
        repo.save(p).join();

        PlayerProfile back = repo.find(id).join().orElseThrow();
        assertEquals(p.skillState(), back.skillState());
        assertEquals("r1=1", back.skillState().ranks());
        assertEquals(12, back.skillState().granted());
        assertEquals("l1=2", back.skillState().builds().get("tank"));
        assertEquals(1, back.skillState().respecs().size());
        assertEquals(1, SkillEngine.spent(back, tree));

        // a row written by a newer server (or corrupted) must not load: saving would wipe it
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE suld_profiles SET skill_data = '{\"v\":99}' WHERE name = 'Skilled'");
        }
        CompletionException ex = assertThrows(CompletionException.class, () -> repo.find(id).join());
        assertTrue(ex.getCause().getMessage().contains("Skill data"), ex.getCause().getMessage());
    }
}
