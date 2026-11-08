package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AccountAdminContextTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path secrets;
    @Test
    void operatorEntryPointBootsWithoutAWebServerOrPublicAdministrationApi() throws Exception {
        java.nio.file.Files.writeString(secrets.resolve("redis-password"), "test-redis-password\n");
        var configuration = Class.forName("com.example.transport.admin.AccountAdminCli$AdminConfiguration");
        new ApplicationContextRunner()
                .withInitializer(new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
                .withUserConfiguration(configuration)
                .withPropertyValues("spring.config.import=optional:configtree:" + secrets.toString().replace('\\', '/') + "/",
                        "spring.profiles.active=account-admin",
                        "spring.datasource.url=jdbc:h2:mem:admin_cli;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa", "spring.datasource.password=",
                        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
                        "spring.data.redis.repositories.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AccountSecurityService.class)
                            .hasSingleBean(RedisSessionStore.class).hasSingleBean(AccountRepository.class);
                    assertThat(context.getEnvironment().getProperty("spring.data.redis.password")).isEqualTo("test-redis-password");
                    assertThat(context).doesNotHaveBean(org.springframework.boot.web.server.WebServer.class);
                    assertThat(context).doesNotHaveBean(com.example.transport.web.AuthController.class);
                    var account = new Account("operator-test", "unused", "USER", "operator@test.invalid");
                    context.getBean(AccountRepository.class).saveAndFlush(account);
                    assertThatThrownBy(() -> context.getBean(AccountSecurityService.class)
                            .apply(account.getId(), "unsupported", ""))
                            .isInstanceOf(IllegalArgumentException.class);
                });
    }
}
