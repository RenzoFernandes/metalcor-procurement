package com.metalcor.procurement.copilot;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Wraps the copilot's own read-only JdbcClient in a distinct type so it is never a candidate for
 * the plain {@code JdbcClient} injected (unqualified) by every other repository in the app, which
 * always means the main, read-write metalcor_app connection.
 */
public class ReadonlyJdbcClient implements AutoCloseable {

    private final JdbcClient jdbc;
    private final HikariDataSource dataSource;

    ReadonlyJdbcClient(JdbcClient jdbc, HikariDataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
