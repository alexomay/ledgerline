package mx.alexomay.ledgerline.outbox;

import java.util.UUID;

/** Destino de los eventos de la bandeja de salida. */
public interface EventPublisher {

    record OutgoingEvent(long id, UUID eventId, String type, UUID aggregateId, String payload) {}

    /** Debe lanzar una excepción si el destino no confirmó la recepción. */
    void publish(OutgoingEvent event) throws Exception;
}
