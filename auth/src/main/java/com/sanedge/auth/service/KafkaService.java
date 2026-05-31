package com.sanedge.auth.service;

import java.util.HashMap;
import java.util.Map;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class KafkaService {
    private static final Logger log = LoggerFactory.getLogger(KafkaService.class);

    @Inject
    Vertx vertx;

    private KafkaProducer<String, String> producer;

    @jakarta.annotation.PostConstruct
    void init() {
        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", System.getenv().getOrDefault("KAFKA_BROKERS", "localhost:9092"));
        config.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        config.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        producer = KafkaProducer.create(vertx, config);
    }

    public Uni<Void> sendMessage(String topic, String key, JsonObject payload) {
        KafkaProducerRecord<String, String> record = KafkaProducerRecord.create(topic, key, payload.encode());
        return Uni.createFrom().emitter(emitter -> {
            producer.send(record)
                .onSuccess(metadata -> {
                    log.debug("Sent message to topic {}: {}", topic, payload.encode());
                    emitter.complete(null);
                })
                .onFailure(err -> {
                    log.error("Failed to send message to topic {}", topic, err);
                    emitter.fail(err);
                });
        });
    }
}
