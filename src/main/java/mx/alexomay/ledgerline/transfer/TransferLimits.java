package mx.alexomay.ledgerline.transfer;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import mx.alexomay.ledgerline.common.Money;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Límites por operación y por día (el día corre en horario de la Ciudad de México). */
@Component
public class TransferLimits {

    public record Usage(long perTransfer, long daily, long usedToday, long remainingToday) {}

    private final TransferRepository transfers;
    private final LedgerlineProperties props;
    private final Clock clock;

    TransferLimits(TransferRepository transfers, LedgerlineProperties props, Clock clock) {
        this.transfers = transfers;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Usage usage(UUID accountId) {
        long perTransfer = Money.toCents(props.limits().perTransfer());
        long daily = Money.toCents(props.limits().daily());
        long used = transfers.sentSince(accountId, startOfToday());
        return new Usage(perTransfer, daily, used, Math.max(0, daily - used));
    }

    Instant startOfToday() {
        LocalDate today = LocalDate.now(clock.withZone(props.timezone()));
        return today.atStartOfDay(props.timezone()).toInstant();
    }
}
