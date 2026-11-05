package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

class EmailVerificationMigrationTest {
    @Test
    void v5UpgradePreservesAccountsAndAssignmentsWithoutAutomaticallyVerifyingAnyone() {
        var source = TestPostgres.createDatabase("email_upgrade");
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration")
                .target("5")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(source);
        var user = UUID.randomUUID();
        jdbc.update(
                "insert into auth_user(id,username,password_hash,role,enabled) values"
                    + " (?,'legacy','hash-before-upgrade','USER',true)",
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
                Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(jdbc.queryForObject("select email from auth_user", String.class)).isNull();
        assertThat(jdbc.queryForObject("select email_verified from auth_user", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject("select password_hash from auth_user", String.class))
                .isEqualTo("hash-before-upgrade");
        assertThat(jdbc.queryForObject("select count(*) from transport_assignment", Long.class))
                .isEqualTo(1);
        jdbc.update("update auth_user set email='legacy@test.invalid' where id=?", user);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update auth_user set email='UPPER@test.invalid' where"
                                            + " id=?",
                                        user))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into"
                                            + " auth_user(id,username,password_hash,role,enabled,email)"
                                            + " values"
                                            + " (?,'duplicate','hash','USER',true,'legacy@test.invalid')",
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into"
                                            + " auth_user(id,username,password_hash,role,enabled,email_verified)"
                                            + " values (?,'invalid','hash','USER',true,true)",
                                        UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update(
                "insert into email_verification_token values (?,?,now(),now()+interval '24 hours')",
                user,
                "a".repeat(64));
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "insert into email_verification_token values"
                                            + " (?,?,now(),now()+interval '24 hours')",
                                        user,
                                        "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "update email_verification_token set expires_at=issued_at"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(
                        jdbc.queryForList(
                                "select tablename from pg_publication_tables where"
                                    + " pubname='transport_cdc_publication'",
                                String.class))
                .containsExactlyInAnyOrder("transport", "job", "container");
        assertThat(
                        jdbc.queryForObject(
                                "select"
                                    + " has_table_privilege('debezium','email_verification_token','SELECT')",
                                Boolean.class))
                .isFalse();
        jdbc.update("delete from auth_user where id=?", user);
        assertThat(jdbc.queryForObject("select count(*) from email_verification_token", Long.class))
                .isZero();
    }
}
