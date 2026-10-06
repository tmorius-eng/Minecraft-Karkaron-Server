package mn.suld.plugin.persistence;

import mn.suld.api.persistence.RepositoryException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

/**
 * Minimal forward-only schema migrator. Applied versions are tracked in
 * {@code suld_schema_version}; each migration runs once, in order, inside a
 * transaction. Migration SQL lives in {@code db/migration/<dialect>/} on the
 * classpath. The migration list is explicit (no classpath scanning) so the
 * shaded jar behaves identically everywhere.
 */
public final class SchemaMigrator {

    /** An ordered migration step. */
    private record Migration(int version, String description, String fileName) {
    }

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "init", "V1__init.sql"),
            new Migration(2, "profile_quest_currency", "V2__profile_quest_currency.sql")
    );

    private final DataSource dataSource;
    private final SqlDialect dialect;

    public SchemaMigrator(DataSource dataSource, SqlDialect dialect) {
        this.dataSource = dataSource;
        this.dialect = dialect;
    }

    /** Apply all pending migrations. Returns the number applied. */
    public int migrate() {
        try (Connection conn = dataSource.getConnection()) {
            ensureVersionTable(conn);
            int current = currentVersion(conn);
            int applied = 0;
            for (Migration migration : MIGRATIONS) {
                if (migration.version() <= current) {
                    continue;
                }
                applyMigration(conn, migration);
                applied++;
            }
            return applied;
        } catch (SQLException ex) {
            throw new RepositoryException("Schema migration failed", ex);
        }
    }

    private void ensureVersionTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS suld_schema_version ("
                + "version INT PRIMARY KEY, "
                + "description VARCHAR(255) NOT NULL, "
                + "applied_at BIGINT NOT NULL)";
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private int currentVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(version), 0) FROM suld_schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void applyMigration(Connection conn, Migration migration) throws SQLException {
        String resource = "db/migration/" + dialect.migrationFolder() + "/" + migration.fileName();
        String script = readResource(resource);

        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            for (String statement : splitStatements(script)) {
                st.execute(statement);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO suld_schema_version (version, description, applied_at) VALUES (?, ?, ?)")) {
                ps.setInt(1, migration.version());
                ps.setString(2, migration.description());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException ex) {
            conn.rollback();
            throw new RepositoryException("Migration V" + migration.version() + " failed", ex);
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private String readResource(String resource) {
        ClassLoader cl = getClass().getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                throw new RepositoryException("Migration resource not found: " + resource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException ex) {
            throw new RepositoryException("Failed reading migration resource: " + resource, ex);
        }
    }

    /**
     * Split a script into executable statements. Line comments ({@code --} to
     * end of line) are stripped <em>first</em> so semicolons inside comments
     * never split a statement; the remaining text is then split on ';' with
     * blank segments dropped. (Our DDL uses no string literals containing
     * {@code --} or ';', so this simple scanner is sufficient.)
     */
    static List<String> splitStatements(String script) {
        StringBuilder code = new StringBuilder();
        for (String line : script.split("\n")) {
            int comment = line.indexOf("--");
            code.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> out = new ArrayList<>();
        for (String raw : code.toString().split(";")) {
            String trimmed = raw.strip();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
