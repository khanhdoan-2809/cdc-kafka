package com.example.transport.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpVerificationEmailSender implements VerificationEmailSender {
    private final JavaMailSender sender;
    private final String from;

    public SmtpVerificationEmailSender(
            JavaMailSender sender, @Value("${auth.verification.from}") String from) {
        this.sender = sender;
        if (from == null || from.isBlank())
            throw new IllegalArgumentException("Verification sender is required");
        try {
            var address = new jakarta.mail.internet.InternetAddress(from, true);
            address.validate();
            if (!from.equals(address.getAddress()) || !from.contains("@"))
                throw new IllegalArgumentException("Invalid verification sender");
        } catch (jakarta.mail.internet.AddressException invalid) {
            throw new IllegalArgumentException("Invalid verification sender", invalid);
        }
        this.from = from;
    }

    @Override
    public void send(VerificationEmail email) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email.recipient());
        message.setSubject("Verify your CDC Kafka account");
        message.setText(
                "Use this verification code with POST /api/auth/verify-email:\n\n"
                        + email.token()
                        + "\n\nIt expires in 24 hours and can only be used once. "
                        + "If you did not request this, you can ignore this email.");
        sender.send(message);
    }
}
