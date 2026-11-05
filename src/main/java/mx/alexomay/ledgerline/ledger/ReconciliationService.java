package mx.alexomay.ledgerline.ledger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conciliación: revisa que el libro esté sano. Lo corre un administrador desde
 * la API y también se ejecuta solo cada hora.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    public record Mismatch(UUID accountId, long storedBalance, long postingsSum) {}

    public record Report(
            Instant checkedAt,
            boolean healthy,
            long accounts,
            long entries,
            long postings,
            long totalBalance,
            List<Mismatch> balanceMismatches,
            List<UUID> unbalancedEntries,
            long negativeCustomerAccounts) {}

    private final JdbcClient jdbc;
    private final Clock clock;

    ReconciliationService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Repeatable read: todas las consultas ven la misma fotografía del libro,
     * aunque mientras tanto sigan entrando transferencias.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Report run() {
        // 1. El saldo guardado de cada cuenta es igual a la suma de sus movimientos.
        List<Mismatch> mismatches = jdbc.sql("""
                        select a.id, a.balance, coalesce(sum(p.amount), 0) as total
                        from accounts a left join postings p on p.account_id = a.id
                        group by a.id, a.balance
                        having a.balance <> coalesce(sum(p.amount), 0)
                        """)
                .query((rs, i) -> new Mismatch(rs.getObject(1, UUID.class), rs.getLong(2), rs.getLong(3)))
                .list();

        // 2. Cada asiento suma cero.
        List<UUID> unbalanced = jdbc.sql("""
                        select entry_id from postings group by entry_id having sum(amount) <> 0
                        """)
                .query(UUID.class)
                .list();

        // 3. La suma de todos los saldos es cero: lo que entra a clientes sale de cuentas de sistema.
        long total = jdbc.sql("select coalesce(sum(balance), 0) from accounts").query(Long.class).single();

        long negatives = jdbc.sql("select count(*) from accounts where kind = 'CUSTOMER' and balance < 0")
                .query(Long.class)
                .single();
        long accountCount = jdbc.sql("select count(*) from accounts").query(Long.class).single();
        long entryCount = jdbc.sql("select count(*) from journal_entries").query(Long.class).single();
        long postingCount = jdbc.sql("select count(*) from postings").query(Long.class).single();

        boolean healthy = mismatches.isEmpty() && unbalanced.isEmpty() && total == 0 && negatives == 0;
        return new Report(
                clock.instant(),
                healthy,
                accountCount,
                entryCount,
                postingCount,
                total,
                mismatches,
                unbalanced,
                negatives);
    }

    @Scheduled(cron = "0 0 * * * *")
    void hourly() {
        Report report = run();
        if (report.healthy()) {
            log.info("Conciliación correcta: {} cuentas, {} asientos", report.accounts(), report.entries());
        } else {
            log.error("CONCILIACIÓN CON DIFERENCIAS: {}", report);
        }
    }
}
