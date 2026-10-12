package mn.suld.plugin.persistence;

import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.clazz.ClassMasteries;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.skill.tree.SkillState;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real-database test of the class change columns (V22). Opt-in: SULD_TEST_PG_URL / _USER / _PASS. */
class JdbcProfileMasteryIT {

    @Test
    void eachClassesProgressAndTheLastChangeSurviveSaveAndLoad() throws Exception {
        TestDb.Db testDb = TestDb.fresh();
        javax.sql.DataSource ds = testDb.ds();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, testDb.dialect()).migrate());
        JdbcProfileRepository repo = new JdbcProfileRepository(ds, testDb.dialect(), Executors.newSingleThreadExecutor());

        UUID fresh = UUID.randomUUID();
        repo.save(PlayerProfile.createNew(fresh, "Fresh", Instant.now())).join();
        PlayerProfile f = repo.find(fresh).join().orElseThrow();
        assertEquals(ClassMasteries.NONE, f.classMasteries(), "NULL column = never changed class");
        assertEquals(0, f.lastClassChange());

        UUID id = UUID.randomUUID();
        PlayerProfile p = PlayerProfile.createNew(id, "Hybrid", Instant.now());
        p.selectClass(PlayerClass.BAATAR);
        p.skillState(SkillState.NONE.withRanks("b.root:1,b.str:3"));
        ClassGear baatar = ClassGear.NONE.withProgress(17, 12.5).withTier(ArmorTier.T2).withWeapon(UUID.randomUUID());
        p.classGear(baatar);
        p.switchClass(PlayerClass.BOO, 1_700_000_000_000L);
        p.skillState(SkillState.NONE.withRanks("o.root:1"));
        repo.save(p).join();

        PlayerProfile back = repo.find(id).join().orElseThrow();
        assertEquals(PlayerClass.BOO, back.playerClass().orElseThrow());
        assertEquals("o.root:1", back.skillState().ranks());
        assertEquals(1_700_000_000_000L, back.lastClassChange());
        ClassMasteries.Saved saved = back.classMasteries().of(PlayerClass.BAATAR).orElseThrow();
        assertEquals("b.root:1,b.str:3", saved.skills().ranks());
        assertEquals(baatar, saved.gear());

        back.switchClass(PlayerClass.BAATAR, 1_700_000_100_000L);
        repo.save(back).join();
        PlayerProfile again = repo.find(id).join().orElseThrow();
        assertEquals(baatar, again.classGear(), "Баатар's gear progress comes back from the database");
        assertEquals("o.root:1", again.classMasteries().of(PlayerClass.BOO).orElseThrow().skills().ranks());

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE suld_profiles SET class_mastery = '{\"v\":99}' WHERE name = 'Hybrid'");
        }
        CompletionException ex = assertThrows(CompletionException.class, () -> repo.find(id).join());
        assertTrue(ex.getCause().getMessage().contains("Class mastery"), ex.getCause().getMessage());
    }
}
