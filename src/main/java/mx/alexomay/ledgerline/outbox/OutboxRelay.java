package mx.alexomay.ledgerline.outbox;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica los eventos pendientes de la bandeja de salida.
 *
 * <p>Entrega "al menos una vez": si el proceso se cae después de publicar pero
 * antes de marcar el evento, se volverá a enviar. Por eso cada evento lleva un
 * {@code eventId} único con el que los consumidores descartan duplicados.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} permite correr varias instancias: cada una
 * toma un lote distinto sin pisarse.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;

    private final JdbcClient jdbc;
    private final EventPublisher publisher;

    OutboxRelay(JdbcClient jdbc, EventPublisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${ledgerline.outbox.interval:1000}")
    @Transactional
    public int relay() {
        List<EventPublisher.OutgoingEvent> pending = jdbc.sql("""
                        select id, event_id, event_type, aggregate_id, payload
                        from outbox_events
                        where published_at is null
                        order by id
                        limit :batch
                        for update skip locked
                        """)
                .param("batch", BATCH)
                .query((rs, i) -> new EventPublisher.OutgoingEvent(
                        rs.getLong(1),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getObject(4, UUID.class),
                        rs.getString(5)))
                .list();

        int sent = 0;
        for (EventPublisher.OutgoingEvent event : pending) {
            try {
                publisher.publish(event);
            } catch (Exception e) {
                // Se detiene el lote para no publicar eventos posteriores antes que este.
                log.warn("No se pudo publicar el evento {}: {}", event.id(), e.toString());
                jdbc.sql("update outbox_events set attempts = attempts + 1, last_error = left(?, 500) where id = ?")
                        .params(e.toString(), event.id())
                        .update();
                break;
            }
            jdbc.sql("update outbox_events set published_at = now(), attempts = attempts + 1 where id = ?")
                    .param(event.id())
                    .update();
            sent++;
        }
        return sent;
    }
}
