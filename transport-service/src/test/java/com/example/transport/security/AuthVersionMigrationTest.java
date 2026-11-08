package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

class AuthVersionMigrationTest {
    @Test
    void v6UpgradePreservesIdentityAndAssignmentsAndDefaultsVersionsToZero() {
        var source = TestPostgres.createDatabase("auth_version_upgrade");
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration")
                .target("6")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(source);
        var user = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user(id,username,password_hash,role,enabled,email,email_verified)"
                        + " values (?,'legacy','old-hash','USER',true,'legacy@test.invalid',true)",
                user);
        Long transport =
                jdbc.queryForObject(
                        "insert into"
                            + " transport(reference,status,created_at,updated_at,created_by,updated_by)"
                            + " values ('T','CREATED',now(),now(),'fixture','fixture') returning"
                            + " id",
                        Long.class);
        jdbc.update("insert into transport_assignment values (?,?)", transport, user);
        var flyway =
                Flyway.configure()
                        .dataSource(source)
                        .locations("classpath:db/migration")
                        .target("7")
                        .load();
        flyway.migrate();
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("7");
        assertThat(jdbc.queryForObject("select auth_version from auth_user", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select password_hash from auth_user", String.class))
                .isEqualTo("old-hash");
        assertThat(jdbc.queryForObject("select email_verified from auth_user", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from transport_assignment", Long.class))
                .isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("update auth_user set auth_version=-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update auth_user set auth_version=null"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(
                        jdbc.queryForList(
                                "select tablename from pg_publication_tables where"
                                        + " pubname='transport_cdc_publication'",
                                String.class))
                .containsExactlyInAnyOrder("transport", "job", "container");
        assertThat(
                        jdbc.queryForObject(
                                "select has_table_privilege('debezium','auth_user','SELECT')",
                                Boolean.class))
                .isFalse();
    }
}
