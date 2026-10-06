package mn.suld.plugin.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigratorTest {

    @Test
    void splitDropsCommentsAndBlanks() {
        String script = """
                -- a comment
                CREATE TABLE a (id INT);

                -- another
                CREATE TABLE b (id INT);
                """;
        List<String> statements = SchemaMigrator.splitStatements(script);
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).startsWith("CREATE TABLE a"));
        assertTrue(statements.get(1).startsWith("CREATE TABLE b"));
    }

    @Test
    void trailingWhitespaceSegmentIgnored() {
        List<String> statements = SchemaMigrator.splitStatements("CREATE TABLE a (id INT);\n\n");
        assertEquals(1, statements.size());
    }

    @Test
    void bundledMysqlMigrationParses() {
        // Sanity-check the shipped migration splits into executable statements.
        String script = new java.util.Scanner(
                SchemaMigratorTest.class.getClassLoader().getResourceAsStream("db/migration/mysql/V1__init.sql"),
                "UTF-8").useDelimiter("\\A").next();
        List<String> statements = SchemaMigrator.splitStatements(script);
        assertEquals(5, statements.size(), "V1 should define 5 tables");
    }

    @Test
    void bundledV3ClanMigrationsParseForBothDialects() {
        assertEquals(2, SchemaMigrator.splitStatements(resource("db/migration/mysql/V3__clans.sql")).size(),
                "MySQL V3: clans + members (index inline)");
        assertEquals(3, SchemaMigrator.splitStatements(resource("db/migration/postgresql/V3__clans.sql")).size(),
                "PostgreSQL V3: clans + members + index");
    }

    private static String resource(String path) {
        return new java.util.Scanner(SchemaMigratorTest.class.getClassLoader().getResourceAsStream(path), "UTF-8")
                .useDelimiter("\\A").next();
    }
}
