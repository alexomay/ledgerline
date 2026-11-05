package mx.alexomay.ledgerline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.alexomay.ledgerline.outbox.OutboxRelay;
import mx.alexomay.ledgerline.support.IntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;

/** Kafka real en un contenedor. Se omite si la máquina no tiene Docker; en CI siempre corre. */
@Testcontainers(disabledWithoutDocker = true)
class KafkaOutboxTest extends IntegrationTest {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("ledgerline.kafka.enabled", () -> "true");
    }

    @Autowired
    OutboxRelay relay;

    @Test
    void transferEventReachesKafkaKeyedBySourceAccount() throws Exception {
        Session ana = register("Ana Kafka");
        Session luis = register("Luis Kafka");
        deposit(ana, "100.00");
        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "42.50", "Evento").andExpect(status().isCreated());
        while (relay.relay() > 0) {
            // publica todo lo pendiente
        }

        UUID anaAccount = jdbc.sql("select id from accounts where user_id = ?")
                .param(ana.userId())
                .query(UUID.class)
                .single();

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "prueba-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of("ledgerline.events"));
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && received.stream().noneMatch(r -> r.key().equals(anaAccount.toString()) && r.value().contains("transfer.completed"))) {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
            }
        }

        ConsumerRecord<String, String> event = received.stream()
                .filter(r -> r.key().equals(anaAccount.toString()) && r.value().contains("transfer.completed"))
                .findFirst()
                .orElseThrow();
        JsonNode payload = json.readTree(event.value());
        assertThat(payload.get("type").asString()).isEqualTo("transfer.completed");
        assertThat(payload.get("data").get("amountCents").asLong()).isEqualTo(4_250);
        assertThat(new String(event.headers().lastHeader("event-type").value())).isEqualTo("transfer.completed");
    }
}
