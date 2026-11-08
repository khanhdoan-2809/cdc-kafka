package com.example.transport.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpVerificationEmailSenderTest {
    @Test
    void smtpMessageContainsCodeAndUsesConfiguredSenderWithoutExposingItInDtoStrings() {
        var sender = mock(JavaMailSender.class);
        var adapter = new SmtpVerificationEmailSender(sender, "verify@test.invalid");
        var email = new VerificationEmail("user@test.invalid", "secret-code");
        adapter.send(email);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("verify@test.invalid");
        assertThat(message.getValue().getTo()).containsExactly("user@test.invalid");
        assertThat(message.getValue().getText()).contains("secret-code", "24 hours");
        assertThat(email.toString()).doesNotContain("secret-code", "user@test.invalid");
        assertThat(
                        new com.example.transport.web.RegistrationController.RegisterRequest(
                                        "user", "user@test.invalid", "secret-password")
                                .toString())
                .doesNotContain("secret-password");
    }
}
