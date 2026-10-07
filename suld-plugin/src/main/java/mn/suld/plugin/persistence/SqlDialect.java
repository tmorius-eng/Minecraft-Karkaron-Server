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
            case MYSQL -> """
                    INSERT INTO suld_profiles
                        (player_uuid, name, class_id, level, exp_into_level, created_at, last_seen_at, version,
                         currency, active_quest_id, quest_progress, quest_completed, skill_data, equipment_data, class_gear, active_minutes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        name = VALUES(name),
                        class_id = VALUES(class_id),
                        level = VALUES(level),
                        exp_into_level = VALUES(exp_into_level),
                        last_seen_at = VALUES(last_seen_at),
                        version = VALUES(version),
                        currency = VALUES(currency),
                        active_quest_id = VALUES(active_quest_id),
                        quest_progress = VALUES(quest_progress),
                        quest_completed = VALUES(quest_completed),
                        skill_data = VALUES(skill_data),
                        equipment_data = VALUES(equipment_data),
                        class_gear = VALUES(class_gear),
                        active_minutes = VALUES(active_minutes)
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
