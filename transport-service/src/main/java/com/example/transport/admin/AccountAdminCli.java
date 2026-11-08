package com.example.transport.admin;

import com.example.transport.security.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import java.util.UUID;

/** Separate non-web entry point; accessible only to operators with deployment credentials. */
public final class AccountAdminCli {
    private AccountAdminCli() {}

    public static void main(String[] args) {
        int exit = 0;
        try (var context = new SpringApplicationBuilder(AdminConfiguration.class)
                .profiles("account-admin").web(WebApplicationType.NONE).run(args)) {
            var env = context.getEnvironment();
            context.getBean(AccountSecurityService.class).apply(
                    UUID.fromString(env.getRequiredProperty("account-admin.account-id")),
                    env.getRequiredProperty("account-admin.operation"),
                    env.getProperty("account-admin.value", ""));
            System.out.println("Account operation completed; sessions revoked.");
        } catch (Exception failure) {
            System.err.println("Account operation failed; revocation may already have occurred. Check operator configuration and retry.");
            exit = 1;
        }
        System.exit(exit);
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("account-admin")
    @EnableAutoConfiguration
    @EntityScan("com.example.transport.security")
    @EnableJpaRepositories("com.example.transport.security")
    @Import({AccountSecurityService.class, RedisSessionStore.class})
    static class AdminConfiguration {}
}
