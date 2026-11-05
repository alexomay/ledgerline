package mx.alexomay.ledgerline.ledger;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.alexomay.ledgerline.account.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Única puerta de entrada para mover dinero. Cada operación se registra como un
 * asiento cuyos movimientos suman cero: el dinero nunca se crea ni se destruye,
 * solo pasa de una cuenta a otra. La base de datos vuelve a verificar la misma
 * regla al hacer commit.
 */
@Service
public class LedgerService {

    /** Un movimiento por registrar, en centavos. Positivo = abono, negativo = cargo. */
    public record Leg(UUID accountId, long amount) {}

    public record PostedEntry(JournalEntry entry, List<Posting> postings) {}

    private final EntityManager entityManager;
    private final JournalEntryRepository entries;
    private final PostingRepository postings;
    private final Clock clock;

    LedgerService(
            EntityManager entityManager, JournalEntryRepository entries, PostingRepository postings, Clock clock) {
        this.entityManager = entityManager;
        this.entries = entries;
        this.postings = postings;
        this.clock = clock;
    }

    /**
     * Bloquea las cuentas ({@code SELECT ... FOR UPDATE}) siempre en el mismo
     * orden. Si dos transferencias cruzadas (A→B y B→A) llegan al mismo tiempo,
     * ambas piden primero la misma cuenta, así que una espera a la otra en lugar
     * de quedar en interbloqueo.
     *
     * <p>Se usa {@code refresh} con bloqueo y no una consulta normal: si la
     * cuenta ya estaba cargada en la sesión de Hibernate, una consulta con
     * bloqueo la bloquea pero conserva el saldo leído antes, y se pierden
     * actualizaciones. La prueba de concurrencia detectó exactamente eso.
     *
     * <p>Quien vaya a validar saldos antes de llamar a {@link #post} debe
     * bloquear primero con este método.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Account> lock(List<UUID> accountIds) {
        Map<UUID, Account> locked = new LinkedHashMap<>();
        for (UUID id : accountIds.stream().distinct().sorted().toList()) {
            Account account = entityManager.find(Account.class, id);
            if (account == null) {
                throw new IllegalArgumentException("La cuenta " + id + " no existe");
            }
            entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
            locked.put(id, account);
        }
        return locked;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PostedEntry post(JournalEntry.Kind kind, String description, List<Leg> legs) {
        if (legs.size() < 2) {
            throw new IllegalArgumentException("Un asiento necesita al menos dos movimientos");
        }
        long sum = 0;
        for (Leg leg : legs) {
            if (leg.amount() == 0) {
                throw new IllegalArgumentException("Un movimiento no puede ser de cero");
            }
            sum = Math.addExact(sum, leg.amount());
        }
        if (sum != 0) {
            throw new IllegalArgumentException("El asiento no cuadra: suma " + sum);
        }

        Map<UUID, Account> locked = lock(legs.stream().map(Leg::accountId).toList());
        // PostgreSQL guarda microsegundos: se trunca para que lo devuelto coincida con lo guardado.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        JournalEntry entry = entries.save(new JournalEntry(kind, description, now));

        List<Posting> saved = new ArrayList<>(legs.size());
        for (Leg leg : legs) {
            Account account = locked.get(leg.accountId());
            account.applyPosting(leg.amount());
            saved.add(postings.save(
                    new Posting(entry.getId(), account.getId(), leg.amount(), account.getBalance(), now)));
        }
        return new PostedEntry(entry, saved);
    }
}
