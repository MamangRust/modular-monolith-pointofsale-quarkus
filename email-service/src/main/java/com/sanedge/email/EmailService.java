package com.sanedge.email;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Inject
    Vertx vertx;

    @Inject
    ReactiveMailer mailer;

    private KafkaConsumer<String, JsonObject> consumer;

    void onStart(@Observes StartupEvent ev) {
        log.info("📧 Starting Email Service...");

        // 1. Setup Kafka Consumer
        Map<String, String> kafkaConfig = new HashMap<>();
        kafkaConfig.put("bootstrap.servers", System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092"));
        kafkaConfig.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kafkaConfig.put("value.deserializer", "io.vertx.kafka.client.serialization.JsonObjectDeserializer");
        kafkaConfig.put("group.id", "email-service-group");
        kafkaConfig.put("auto.offset.reset", "earliest");

        consumer = KafkaConsumer.create(vertx, kafkaConfig);

        // List of topics to subscribe to
        List<String> topics = Arrays.asList(
            "email-service-topic-auth-register",
            "email-service-topic-auth-forgot-password",
            "email-service-topic-auth-verify-code-success",
            "email-service-topic-saldo-create",
            "email-service-topic-topup-create",
            "email-service-topic-transaction-create",
            "email-service-topic-transfer-create",
            "email-service-topic-merchant-create",
            "email-service-topic-merchant-update-status",
            "email-service-topic-merchant-document-create",
            "email-service-topic-merchant-document-update-status"
        );

        consumer.handler(record -> {
            JsonObject emailReq = record.value();
            log.info("📥 Received message from topic {}: {}", record.topic(), emailReq.encode());
            sendEmail(emailReq);
        });

        consumer.subscribe(new java.util.HashSet<>(topics))
            .onSuccess(v -> log.info("📧 Email Service successfully started and subscribed to {} topics", topics.size()))
            .onFailure(err -> log.error("❌ Failed to start Email Service subscription", err));
    }

    private void sendEmail(JsonObject payload) {
        try {
            String email = payload.getString("email");
            String subject = payload.getString("subject");
            String body = payload.getString("body");

            if (email == null || subject == null || body == null) {
                log.warn("⚠️ Received incomplete email payload: {}", payload.encode());
                return;
            }

            Mail mail = Mail.withHtml(email, subject, body);

            mailer.send(mail)
                .subscribe().with(
                    item -> log.info("✅ Email successfully sent to {}", email),
                    err -> log.error("❌ Failed to send email to {}", email, err)
                );

        } catch (Exception e) {
            log.error("❌ Error processing email record", e);
        }
    }

    void onStop(@Observes ShutdownEvent ev) {
        log.info("📧 Stopping Email Service...");
        if (consumer != null) {
            consumer.close()
                .onSuccess(v -> log.info("📧 Kafka consumer closed successfully"))
                .onFailure(err -> log.error("❌ Failed to close Kafka consumer", err));
        }
    }
}
