package mn.suld.plugin.persistence;

import mn.suld.api.activity.ActiveMinutes;
import mn.suld.api.activity.ActivityCategory;
import mn.suld.api.classgear.ArmorPiece;
import mn.suld.api.classgear.ArmorTier;
import mn.suld.api.classgear.ClassGear;
import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.profile.PlayerProfile;
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

/** Real-database test of the class gear + active minutes columns (V13). Opt-in: SULD_TEST_PG_URL / _USER / _PASS. */
class JdbcClassGearIT {

    @Test
    void classGearAndActiveMinutesSurviveSaveAndLoad_andBadDataStopsTheLoad() throws Exception {
        TestDb.Db testDb = TestDb.fresh();
        javax.sql.DataSource ds = testDb.ds();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(ds, testDb.dialect()).migrate());
        JdbcProfileRepository repo = new JdbcProfileRepository(ds, testDb.dialect(), Executors.newSingleThreadExecutor());

        UUID fresh = UUID.randomUUID();
        repo.save(PlayerProfile.createNew(fresh, "Fresh", Instant.now())).join();
        PlayerProfile f = repo.find(fresh).join().orElseThrow();
        assertEquals(ClassGear.NONE, f.classGear(), "NULL column = no record (every pre-V13 row)");
        assertEquals(ActiveMinutes.NONE, f.activeMinutes());

        UUID id = UUID.randomUUID();
        PlayerProfile p = PlayerProfile.createNew(id, "Armoured", Instant.now());
        p.selectClass(PlayerClass.BAATAR);
        ClassGear g = ClassGear.NONE.withProgress(23, 101.5).withTier(ArmorTier.T2).withEnhance(3)
                .withPiece(ArmorPiece.HELMET, UUID.randomUUID()).withPiece(ArmorPiece.CHESTPLATE, UUID.randomUUID())
                .withPiece(ArmorPiece.LEGGINGS, UUID.randomUUID()).withPiece(ArmorPiece.BOOTS, UUID.randomUUID())
                .withWeapon(UUID.randomUUID()).withCleared("dungeon.khasar_den").withCleared("dungeon.khasar_den");
        p.classGear(g);
        p.activeMinutes(ActiveMinutes.NONE.plus(ActivityCategory.COMBAT, 40).plus(ActivityCategory.EXPLORATION, 7));
        repo.save(p).join();
        PlayerProfile back = repo.find(id).join().orElseThrow();
        assertEquals(g, back.classGear());
        assertEquals(2, back.classGear().recent().size());
        assertEquals(47, back.activeMinutes().total());

        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE suld_profiles SET class_gear = '{\"v\":99}' WHERE name = 'Armoured'");
        }
        CompletionException ex = assertThrows(CompletionException.class, () -> repo.find(id).join());
        assertTrue(ex.getCause().getMessage().contains("Class gear"), ex.getCause().getMessage());
    }
}
