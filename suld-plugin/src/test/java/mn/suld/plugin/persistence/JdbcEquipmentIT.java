package mn.suld.plugin.persistence;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.EquipSlot;
import mn.suld.api.item.EquipmentState;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.item.ItemGenerator;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.loot.Rng;
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

/** Real-database test of the equipment column (V11). Opt-in: SULD_TEST_PG_URL / _USER / _PASS (throwaway database). */
@EnabledIfEnvironmentVariable(named = "SULD_TEST_PG_URL", matches = "jdbc:postgresql:.+")
class JdbcEquipmentIT {

    @Test
    void accessoriesSurviveSaveAndLoad_andBadDataStopsTheLoad() throws Exception {
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
        ItemCatalogLoader.Result loaded = ItemCatalogLoader.load(ItemCatalogLoader.classpath());
        assertTrue(loaded.issues().isEmpty(), loaded.issues().toString());
        ItemCatalog catalog = loaded.catalog();
        ItemGenerator gen = new ItemGenerator(catalog);

        // nothing worn: stored as NULL, loads as NONE (also what every pre-V11 row looks like)
        UUID fresh = UUID.randomUUID();
        PlayerProfile p0 = PlayerProfile.createNew(fresh, "Bare", Instant.now());
        repo.save(p0).join();
        assertEquals(EquipmentState.NONE, repo.find(fresh).join().orElseThrow().equipment());

        UUID id = UUID.randomUUID();
        PlayerProfile p = PlayerProfile.createNew(id, "Ringed", Instant.now());
        p.selectClass(PlayerClass.BAATAR);
        ItemInstance ring = gen.generate(catalog.require("jewel.altan_bugj"), ItemRarity.EPIC, 20, Rng.seeded(7), "test", null).boundTo(id);
        ItemInstance amulet = gen.generate(catalog.require("jewel.tengeriin_sakhius"), catalog.require("jewel.tengeriin_sakhius").rarity(), 30, Rng.seeded(8), "test", null);
        p.equipment(p.equipment().with(EquipSlot.ACCESSORY_1, ring).with(EquipSlot.ACCESSORY_2, amulet));
        repo.save(p).join();

        PlayerProfile back = repo.find(id).join().orElseThrow();
        assertEquals(p.equipment(), back.equipment());
        ItemInstance ringBack = back.equipment().get(EquipSlot.ACCESSORY_1).orElseThrow();
        assertEquals(ring.uuid(), ringBack.uuid());
        assertEquals(ring.affixes(), ringBack.affixes());
        assertEquals(ring.stats(), ringBack.stats());
        assertEquals(id, ringBack.boundTo());

        // taking one off persists too
        back.equipment(back.equipment().with(EquipSlot.ACCESSORY_2, null));
        repo.save(back).join();
        assertTrue(repo.find(id).join().orElseThrow().equipment().get(EquipSlot.ACCESSORY_2).isEmpty());

        // a row written by a newer server (or corrupted) must not load: saving would wipe the accessories
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("UPDATE suld_profiles SET equipment_data = '{\"v\":99}' WHERE name = 'Ringed'");
        }
        CompletionException ex = assertThrows(CompletionException.class, () -> repo.find(id).join());
        assertTrue(ex.getCause().getMessage().contains("Equipment data"), ex.getCause().getMessage());
    }
}
