package com.example.transport.security;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

// Real PostgreSQL 17, independent of Docker, shared by this test JVM only.
final class TestPostgres {
    static final EmbeddedPostgres INSTANCE = start();

    private static EmbeddedPostgres start() {
        try {
            var postgres =
                    EmbeddedPostgres.builder()
                            .setPort(0)
                            .setDataDirectory(
                                    java.nio.file.Files.createTempDirectory(
                                            java.nio.file.Path.of("target"), "test-postgres-"))
                            .setServerConfig("wal_level", "logical")
                            .start();
            new JdbcTemplate(postgres.getPostgresDatabase())
                    .execute("CREATE ROLE debezium WITH LOGIN REPLICATION");
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try {
                                            postgres.close();
                                        } catch (java.io.IOException exception) {
                                            System.err.println(
                                                    "Could not stop test PostgreSQL: "
                                                            + exception.getMessage());
                                        }
                                    }));
            return postgres;
        } catch (Exception exception) {
            throw new IllegalStateException("Test PostgreSQL failed to start", exception);
        }
    }

    static DataSource createDatabase(String name) {
        if (!name.matches("[a-z_]+"))
            throw new IllegalArgumentException("Invalid test database name");
        new JdbcTemplate(INSTANCE.getPostgresDatabase()).execute("CREATE DATABASE " + name);
        return INSTANCE.getDatabase("postgres", name);
    }
}
