package mn.suld.plugin.persistence;

import mn.suld.api.config.StorageType;

/**
 * SQL dialect differences SULD cares about: JDBC URL/driver, migration folder,
 * UUID binding, and upsert syntax. Keeping these in one enum means the rest of
 * the persistence code is dialect-agnostic.
 */
public enum SqlDialect {

    MYSQL("com.mysql.cj.jdbc.Driver", "mysql"),
    POSTGRESQL("org.postgresql.Driver", "postgresql");

    private final String driverClass;
    private final String migrationFolder;

    SqlDialect(String driverClass, String migrationFolder) {
        this.driverClass = driverClass;
        this.migrationFolder = migrationFolder;
    }

    public String driverClass() {
        return driverClass;
    }

    public String migrationFolder() {
        return migrationFolder;
    }

    public static SqlDialect forStorage(StorageType type) {
        return switch (type) {
            case MYSQL, H2 -> MYSQL; // H2 runs in MySQL compatibility mode with the MySQL migrations and upserts
            case POSTGRESQL -> POSTGRESQL;
            case MEMORY -> throw new IllegalArgumentException("MEMORY storage has no SQL dialect");
        };
    }

    /** Upsert statement for the profiles table, keyed on player_uuid. */
    public String profileUpsert() {
        return switch (this) {
            // a stale snapshot (lower version than the row) never overwrites a newer one; version is assigned last,
            // because MySQL evaluates the assignments in order
            case MYSQL -> """
                    INSERT INTO suld_profiles
                        (player_uuid, name, class_id, level, exp_into_level, created_at, last_seen_at, version,
                         currency, active_quest_id, quest_progress, quest_completed, skill_data, equipment_data, class_gear, active_minutes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        name = CASE WHEN VALUES(version) >= version THEN VALUES(name) ELSE name END,
                        class_id = CASE WHEN VALUES(version) >= version THEN VALUES(class_id) ELSE class_id END,
                        level = CASE WHEN VALUES(version) >= version THEN VALUES(level) ELSE level END,
                        exp_into_level = CASE WHEN VALUES(version) >= version THEN VALUES(exp_into_level) ELSE exp_into_level END,
                        last_seen_at = CASE WHEN VALUES(version) >= version THEN VALUES(last_seen_at) ELSE last_seen_at END,
                        currency = CASE WHEN VALUES(version) >= version THEN VALUES(currency) ELSE currency END,
                        active_quest_id = CASE WHEN VALUES(version) >= version THEN VALUES(active_quest_id) ELSE active_quest_id END,
                        quest_progress = CASE WHEN VALUES(version) >= version THEN VALUES(quest_progress) ELSE quest_progress END,
                        quest_completed = CASE WHEN VALUES(version) >= version THEN VALUES(quest_completed) ELSE quest_completed END,
                        skill_data = CASE WHEN VALUES(version) >= version THEN VALUES(skill_data) ELSE skill_data END,
                        equipment_data = CASE WHEN VALUES(version) >= version THEN VALUES(equipment_data) ELSE equipment_data END,
                        class_gear = CASE WHEN VALUES(version) >= version THEN VALUES(class_gear) ELSE class_gear END,
                        active_minutes = CASE WHEN VALUES(version) >= version THEN VALUES(active_minutes) ELSE active_minutes END,
                        version = CASE WHEN VALUES(version) >= version THEN VALUES(version) ELSE version END
                    """;
            case POSTGRESQL -> """
                    INSERT INTO suld_profiles
                        (player_uuid, name, class_id, level, exp_into_level, created_at, last_seen_at, version,
                         currency, active_quest_id, quest_progress, quest_completed, skill_data, equipment_data, class_gear, active_minutes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (player_uuid) DO UPDATE SET
                        name = EXCLUDED.name,
                        class_id = EXCLUDED.class_id,
                        level = EXCLUDED.level,
                        exp_into_level = EXCLUDED.exp_into_level,
                        last_seen_at = EXCLUDED.last_seen_at,
                        version = EXCLUDED.version,
                        currency = EXCLUDED.currency,
                        active_quest_id = EXCLUDED.active_quest_id,
                        quest_progress = EXCLUDED.quest_progress,
                        quest_completed = EXCLUDED.quest_completed,
                        skill_data = EXCLUDED.skill_data,
                        equipment_data = EXCLUDED.equipment_data,
                        class_gear = EXCLUDED.class_gear,
                        active_minutes = EXCLUDED.active_minutes
                    WHERE suld_profiles.version <= EXCLUDED.version
                    """;
        };
    }

    /** Upsert for a clan row, keyed on clan_id. Params: id, name, name_key, tag, created_at, exp, version. */
    public String clanUpsert() {
        return switch (this) {
            case MYSQL -> """
                    INSERT INTO suld_clans (clan_id, name, name_key, tag, created_at, exp, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        name = VALUES(name), name_key = VALUES(name_key), tag = VALUES(tag),
                        exp = VALUES(exp), version = VALUES(version)
                    """;
            case POSTGRESQL -> """
                    INSERT INTO suld_clans (clan_id, name, name_key, tag, created_at, exp, version)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (clan_id) DO UPDATE SET
                        name = EXCLUDED.name, name_key = EXCLUDED.name_key, tag = EXCLUDED.tag,
                        exp = EXCLUDED.exp, version = EXCLUDED.version
                    """;
        };
    }
}
