package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import javax.sql.DataSource;

class PostgresAssignmentMigrationTest {
    Flyway migrate(DataSource source, String version) {
        var flyway =
                Flyway.configure()
                        .dataSource(source)
                        .locations("classpath:db/migration")
                        .target(version)
                        .load();
        flyway.migrate();
        flyway.validate();
        return flyway;
    }

    @Test
    void upgradesV4WithoutGrantingExistingRecordsAndEnforcesAssignmentConstraints() {
        var source = TestPostgres.createDatabase("migration_upgrade");
        migrate(source, "4");
        var jdbc = new JdbcTemplate(source);
        var user = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO auth_user (id,username,password_hash,role,enabled) VALUES (?,"
                    + " 'fixture', 'test-only', 'USER', true)",
                user);
        Long transport =
                jdbc.queryForObject(
                        "INSERT INTO transport"
                            + " (reference,status,created_at,updated_at,created_by,updated_by)"
                            + " VALUES ('existing','CREATED',now(),now(),'fixture','fixture')"
                            + " RETURNING id",
                        Long.class);
        migrate(source, "5");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transport_assignment", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transport", Long.class)).isEqualTo(1);
        jdbc.update("INSERT INTO transport_assignment VALUES (?, ?)", transport, user);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO transport_assignment VALUES (?, ?)",
                                        transport,
                                        user))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO transport_assignment VALUES (?, ?)",
                                        transport,
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO transport_assignment VALUES (?, ?)",
                                        -1L,
                                        user))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM pg_indexes WHERE"
                                    + " tablename='transport_assignment' AND indexdef LIKE"
                                    + " '%(user_id)%'",
                                Long.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForList(
                                "SELECT tablename FROM pg_publication_tables WHERE"
                                    + " pubname='transport_cdc_publication'",
                                String.class))
                .containsExactlyInAnyOrder("transport", "job", "container");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT"
                                    + " has_table_privilege('debezium','transport_assignment','SELECT')",
                                Boolean.class))
                .isFalse();
        jdbc.update("DELETE FROM auth_user WHERE id=?", user);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transport_assignment", Long.class))
                .isZero();
    }

    @Test
    void freshDatabaseMigratesThroughV5() {
        var source = TestPostgres.createDatabase("migration_fresh");
        var flyway = migrate(source, "5");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
    }
}
