package com.susume.recommendation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailSenderService {
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:no-reply@localhost}")
    private String defaultFrom;

    public EmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendPlainText(String to, String subject, String body, @Nullable String from) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String sender = (from != null && !from.isBlank()) ? from : defaultFrom;
            message.setFrom(sender);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Sent email to {} with subject '{}'", to, subject);
        } catch (Exception e) {
            // Don't fail the flow in dev; log the content for debugging
            log.warn("Failed to send email to {}. Subject: {}. Falling back to log. Reason: {}", to, subject,
                    e.getMessage());
            log.warn("From: {}", from);
            log.info("[DEV EMAIL Fallback] To: {}\nSubject: {}\n{}", to, subject, body);
        }
    }
}
