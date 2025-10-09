package com.example.emailsender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;

@SpringBootApplication
public class EmailSenderApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(EmailSenderApplication.class, args);
    }

    @Override
    public void run(String... args) {
        if (args.length == 0 || Arrays.stream(args).noneMatch(s -> s.startsWith("--to"))) {
            log.info("Usage: java -jar email-sender.jar --host <host> --port <port> --user <user> --pass <pass> --to <recipient> [optional flags...]");
            log.info("Example: java -jar email-sender.jar --host smtp.example.com --port 587 --tls true --user user@example.com --pass secret --to someone@example.com --subject \"Test\" --body \"Hello\"");
            return; // Don't exit during tests
        }

        Map<String, String> params = parseArgs(args);

        // --- Argument Parsing ---
        String host = params.get("host");
        int port = Integer.parseInt(params.getOrDefault("port", "587"));
        boolean tls = Boolean.parseBoolean(params.getOrDefault("tls", "true"));
        String user = params.get("user");
        String pass = params.get("pass");
        String from = params.getOrDefault("from", user);
        String to = params.get("to");
        String subject = params.getOrDefault("subject", "No Subject");
        String body = params.getOrDefault("body", "");
        boolean html = Boolean.parseBoolean(params.getOrDefault("html", "false"));
        String inReplyTo = params.get("inReplyTo");
        String references = params.get("references");

        // Basic validation
        if (host == null || to == null) {
            log.error("Missing required arguments: --host and --to are mandatory.");
            return; // Don't exit during tests
        }

        List<File> attachments = new ArrayList<>();
        if (params.containsKey("attach")) {
            String attachStr = params.get("attach");
            String[] files = attachStr.split(",");
            for (String filePath : files) {
                File f = new File(filePath.trim());
                if (f.exists()) {
                    attachments.add(f);
                } else {
                    log.warn("Attachment file not found: {}", filePath);
                }
            }
        }

        log.info("Preparing to send email from {} to {}...", from, to);
        log.debug("SMTP host={}, port={}, tls={}, user={}", host, port, tls, user);

        // Create EmailSender instance manually
        EmailSender sender = new EmailSender(host, port, tls, user, pass, from);

        try {
            // Async call - wait until complete
            CompletableFuture<String> future = sender.sendAsync(to, subject, body, html, attachments, inReplyTo, references);

            // Block and wait for the email to finish sending
            String messageId = future.join();

            log.info("Email sending task completed. Exiting application...");

            // Print Message-ID for Python to capture
            if (messageId != null) {
                System.out.println("MESSAGE_ID:" + messageId);
            }
        } catch (Exception e) {
            log.error("Error sending email: {}", e.getMessage(), e);
        } finally {
            sender.shutdown();
        }
    }

    private Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--")) {
                // Handle both --key=value and --key value formats
                if (arg.contains("=")) {
                    String[] parts = arg.substring(2).split("=", 2);
                    if (parts.length == 2) {
                        params.put(parts[0], parts[1]);
                    }
                }
            }
        }
        return params;
    }
}