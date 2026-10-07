package mn.suld.plugin.persistence;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The database a JDBC integration test runs against: a real PostgreSQL when SULD_TEST_PG_URL / _USER / _PASS are set
 * (throwaway database, wiped), otherwise a fresh in-memory H2 in MySQL mode with the same options as the embedded
 * {@code database.type: h2} storage, so every repository is exercised on every build.
 */
final class TestDb {

    record Db(DataSource ds, SqlDialect dialect) {
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    private TestDb() {
    }

    static boolean postgres() {
        String url = System.getenv("SULD_TEST_PG_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    static Db fresh() throws SQLException {
        if (postgres()) {
            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(System.getenv("SULD_TEST_PG_URL"));
            ds.setUser(System.getenv("SULD_TEST_PG_USER"));
            ds.setPassword(System.getenv("SULD_TEST_PG_PASS"));
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("DROP SCHEMA public CASCADE");
                st.execute("CREATE SCHEMA public");
            }
            return new Db(ds, SqlDialect.POSTGRESQL);
        }
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:suld_it_" + SEQ.incrementAndGet() + ";DB_CLOSE_DELAY=-1;" + DataSourceFactory.H2_OPTIONS);
        ds.setUser("sa");
        ds.setPassword("");
        return new Db(ds, SqlDialect.MYSQL);
    }
}
