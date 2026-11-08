package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

@SpringBootTest(
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.enabled=true",
            "jwt.issuer=https://auth.test",
            "jwt.audience=transport-test",
            "jwt.ttl=PT15M",
            "monitoring.password=test-monitoring-password",
            "auth.bootstrap.username=",
            "auth.bootstrap.password=",
            "auth.registration.enabled=true",
            "spring.mail.properties.mail.smtp.auth=false",
            "spring.mail.properties.mail.smtp.starttls.enable=false",
            "spring.mail.properties.mail.smtp.starttls.required=false"
        })
@AutoConfigureMockMvc
class SmtpAccountLifecycleSmokeTest {
    static GreenMail smtp;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        smtp.start();
        smtp.setUser("smoke@test.invalid", "smoke", "test-only");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> smtp.getSmtp().getPort());
        TestPostgres.createDatabase("smtp_smoke");
        String url = TestPostgres.INSTANCE.getJdbcUrl("postgres", "smtp_smoke");
        for (String prefix : List.of("spring.datasource.", "spring.flyway.")) {
            registry.add(prefix + "url", () -> url);
            registry.add(
                    prefix + (prefix.contains("flyway") ? "user" : "username"), () -> "postgres");
            registry.add(prefix + "password", () -> "postgres");
        }
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        AuthenticationIntegrationTest.keys(registry);
    }

    @AfterAll
    static void stopSmtp() {
        if (smtp != null) smtp.stop();
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void registerReceiveRealSmtpVerifyLoginAndLogout() throws Exception {
        String credentials =
                json.writeValueAsString(
                        Map.of(
                                "username",
                                "smtp-smoke",
                                "email",
                                "smoke@test.invalid",
                                "password",
                                "smtp-smoke-password"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(credentials))
                .andExpect(status().isAccepted());
        assertThat(smtp.waitForIncomingEmail(10000, 1)).isTrue();
        String body = (String) smtp.getReceivedMessages()[0].getContent();
        String code =
                body.lines()
                        .filter(line -> line.matches("[A-Za-z0-9_-]{43}"))
                        .findFirst()
                        .orElseThrow();
        mvc.perform(
                        post("/api/auth/verify-email")
                                .contentType("application/json")
                                .content(json.writeValueAsString(Map.of("token", code))))
                .andExpect(status().isOk());
        var result =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(credentials))
                        .andExpect(status().isOk())
                        .andReturn();
        String token =
                json.readTree(result.getResponse().getContentAsString())
                        .get("accessToken")
                        .asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("role").value("USER"));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
