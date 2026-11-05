package mx.alexomay.ledgerline.outbox;

import java.util.concurrent.TimeUnit;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
class KafkaPublisherConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublisherConfig.class);

    /**
     * Con Kafka activo, cada evento se publica con el id de la cuenta como llave:
     * todos los eventos de una misma cuenta caen en la misma partición y se
     * consumen en orden.
     */
    @Bean
    @ConditionalOnProperty(name = "ledgerline.kafka.enabled", havingValue = "true")
    EventPublisher kafkaEventPublisher(KafkaTemplate<String, String> kafka, LedgerlineProperties props) {
        String topic = props.kafka().topic();
        return event -> {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, event.aggregateId().toString(), event.payload());
            record.headers().add("event-type", event.type().getBytes());
            record.headers().add("event-id", event.eventId().toString().getBytes());
            kafka.send(record).get(10, TimeUnit.SECONDS);
        };
    }

    @Bean
    @ConditionalOnProperty(name = "ledgerline.kafka.enabled", havingValue = "true")
    NewTopic ledgerlineEventsTopic(LedgerlineProperties props) {
        return TopicBuilder.name(props.kafka().topic()).partitions(3).replicas(1).build();
    }

    /** Sin Kafka (demo y desarrollo) los eventos solo se registran en el log. */
    @Bean
    @ConditionalOnProperty(name = "ledgerline.kafka.enabled", havingValue = "false", matchIfMissing = true)
    EventPublisher loggingEventPublisher() {
        return event -> log.info("evento {} {} {}", event.type(), event.eventId(), event.payload());
    }
}
