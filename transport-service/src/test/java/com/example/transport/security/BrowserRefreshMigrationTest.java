package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

class BrowserRefreshMigrationTest {
    @Test
    void v7UpgradePreservesAccountsEnforcesTokenConstraintsAndExcludesCredentialsFromCdc() {
        var source = TestPostgres.createDatabase("browser_refresh_upgrade");
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/migration")
                .target("7")
                .load()
                .migrate();
        var jdbc = new JdbcTemplate(source);
        var id = UUID.randomUUID();
        jdbc.update(
                "insert into"
                    + " auth_user(id,username,password_hash,role,enabled,email,email_verified,auth_version)"
                    + " values (?,'legacy','unchanged','USER',true,'legacy@test.invalid',true,9)",
                id);
        var flyway =
                Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        flyway.migrate();
        flyway.validate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(jdbc.queryForObject("select auth_version from auth_user", Long.class))
                .isEqualTo(9);
        assertThat(jdbc.queryForObject("select password_hash from auth_user", String.class))
                .isEqualTo("unchanged");
        assertThat(jdbc.queryForObject("select count(*) from browser_refresh_token", Long.class))
                .isZero();
        String insert =
                "insert into"
                    + " browser_refresh_token(token_hash,user_id,family_id,auth_version,role,issued_at,expires_at)"
                    + " values (?,?,?,9,'USER',now(),now()+interval '7 days')";
        var family = UUID.randomUUID();
        jdbc.update(insert, "a".repeat(64), id, family);
        assertThatThrownBy(() -> jdbc.update(insert, "a".repeat(64), id, family))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "b".repeat(64), UUID.randomUUID(), family))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert, "plaintext-token", id, family))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String invalid :
                new String[] {
                    "auth_version=-1",
                    "role='ROOT'",
                    "expires_at=issued_at",
                    "consumed_at=issued_at-interval '1 second'"
                })
            assertThatThrownBy(() -> jdbc.update("update browser_refresh_token set " + invalid))
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
                                    + " has_table_privilege('debezium','browser_refresh_token','SELECT')",
                                Boolean.class))
                .isFalse();
        assertThat(
                        jdbc.queryForList(
                                "select indexname from pg_indexes where"
                                        + " tablename='browser_refresh_token'",
                                String.class))
                .contains("ix_browser_refresh_expiry", "ix_browser_refresh_user");
        jdbc.update("delete from auth_user where id=?", id);
        assertThat(jdbc.queryForObject("select count(*) from browser_refresh_token", Long.class))
                .isZero();
    }
}
