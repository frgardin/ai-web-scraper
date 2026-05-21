package com.aiwebscraper.service;

import com.aiwebscraper.config.AppProperties;
import com.aiwebscraper.model.CuratedItem;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDate;
import java.util.List;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties properties;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine,
                        AppProperties properties) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public void sendDigest(List<CuratedItem> items) {
        List<String> recipients = properties.email().recipients();
        if (recipients == null || recipients.isEmpty()) {
            log.warn("No recipients configured — skipping email send");
            return;
        }

        String html = renderTemplate(items);
        String subject = properties.email().subject() + " — " + LocalDate.now();

        for (String recipient : recipients) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(properties.email().from());
                helper.setTo(recipient);
                helper.setSubject(subject);
                helper.setText(html, true);
                mailSender.send(message);
                log.info("Digest sent to {}", recipient);
            } catch (MessagingException e) {
                log.error("Failed to send email to {}: {}", recipient, e.getMessage());
            }
        }
    }

    private String renderTemplate(List<CuratedItem> items) {
        Context ctx = new Context();
        ctx.setVariable("items", items);
        ctx.setVariable("date", LocalDate.now());
        return templateEngine.process("news-email", ctx);
    }
}
