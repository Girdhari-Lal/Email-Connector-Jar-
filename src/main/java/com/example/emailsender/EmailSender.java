package com.example.emailsender;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmailSender {
    private static final Logger logger = LoggerFactory.getLogger(EmailSender.class);
    private final Session session;
    private final String from;
    private final ExecutorService executorService;

    public EmailSender(String host, int port, boolean useTls, String username, String password, String from) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("'host' must be provided.");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("'port' must be a positive integer.");
        }
        if ((username == null || username.isBlank()) && (from == null || from.isBlank())) {
            throw new IllegalArgumentException("At least one of 'username' or 'from' must be provided.");
        }

        this.from = (from == null || from.isBlank()) ? username : from;
        this.executorService = Executors.newFixedThreadPool(2);

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", (username != null && !username.isBlank()) ? "true" : "false");

        if (useTls) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", host);
        }

        Authenticator auth = null;
        if (username != null && !username.isBlank()) {
            final String u = username;
            final String p = password;
            auth = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(u, p);
                }
            };
        }

        session = Session.getInstance(props, auth);
    }

    public CompletableFuture<Void> sendAsync(String to, String subject, String body, boolean html, List<File> attachments) {
        return sendAsync(to, subject, body, html, attachments, null, null).thenApply(id -> null);
    }

    public CompletableFuture<String> sendAsync(String to, String subject, String body, boolean html, List<File> attachments, String inReplyTo, String references) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String messageId = sendEmailInternal(to, subject, body, html, attachments, inReplyTo, references);
                logger.info("Email sent successfully to {}", to);
                return messageId;
            } catch (MessagingException e) {
                logger.error("Failed to send email to {}: {}", to, e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    private String sendEmailInternal(String to, String subject, String body, boolean html, List<File> attachments, String inReplyTo, String references)
            throws MessagingException {

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject == null ? "" : subject);

        // Add reply headers if provided
        if (inReplyTo != null && !inReplyTo.isBlank()) {
            message.setHeader("In-Reply-To", inReplyTo);
        }
        if (references != null && !references.isBlank()) {
            message.setHeader("References", references);
        }

        if (attachments != null && !attachments.isEmpty()) {
            MimeBodyPart bodyPart = new MimeBodyPart();
            if (html)
                bodyPart.setContent(body == null ? "" : body, "text/html; charset=utf-8");
            else
                bodyPart.setText(body == null ? "" : body, java.nio.charset.StandardCharsets.UTF_8.name());

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(bodyPart);

            for (File f : attachments) {
                if (f == null || !f.exists()) continue;
                MimeBodyPart attachPart = new MimeBodyPart();
                DataSource source = new FileDataSource(f);
                attachPart.setDataHandler(new DataHandler(source));
                attachPart.setFileName(f.getName());
                multipart.addBodyPart(attachPart);
            }

            message.setContent(multipart);
        } else {
            if (html)
                message.setContent(body == null ? "" : body, "text/html; charset=utf-8");
            else
                message.setText(body == null ? "" : body, java.nio.charset.StandardCharsets.UTF_8.name());
        }

        Transport.send(message);

        // Get the Message-ID that was assigned
        String messageId = message.getMessageID();
        return messageId;
    }
}