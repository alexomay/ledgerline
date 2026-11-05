package mx.alexomay.ledgerline.outbox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda un evento en la bandeja de salida dentro de la transacción de negocio.
 * Si la operación se revierte, el evento también; si se confirma, el evento
 * queda garantizado aunque Kafka esté caído en ese momento.
 */
@Service
public class OutboxService {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    OutboxService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(String eventType, UUID aggregateId, Map<String, ?> data) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("type", eventType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("data", data);
        jdbc.sql("""
                        insert into outbox_events (event_id, event_type, aggregate_id, payload)
                        values (?, ?, ?, ?)
                        """)
                .params(eventId, eventType, aggregateId, json.writeValueAsString(envelope))
                .update();
        return eventId;
    }
}
