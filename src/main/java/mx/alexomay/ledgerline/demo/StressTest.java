package mx.alexomay.ledgerline.demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.Money;
import mx.alexomay.ledgerline.ledger.ReconciliationService;
import mx.alexomay.ledgerline.transfer.TransferService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Prueba de carga en vivo para la demo: lanza transferencias simultáneas entre
 * las cuentas de Ana y Luis, en ambos sentidos, y al final concilia el libro.
 * Es la misma idea que la prueba automatizada de concurrencia, pero visible.
 */
@Component
class StressTest {

    record Result(
            int requested,
            int threads,
            int succeeded,
            Map<String, Integer> rejected,
            double elapsedMs,
            double perSecond,
            String totalBefore,
            String totalAfter,
            boolean moneyConserved,
            boolean reconciliationHealthy) {}

    private record Party(UUID userId, String clabe) {}

    static final int TRANSFERS = 100;
    static final int THREADS = 8;

    private final TransferService transfers;
    private final ReconciliationService reconciliation;
    private final JdbcClient jdbc;

    StressTest(TransferService transfers, ReconciliationService reconciliation, JdbcClient jdbc) {
        this.transfers = transfers;
        this.reconciliation = reconciliation;
        this.jdbc = jdbc;
    }

    synchronized Result run() throws Exception {
        List<Party> parties = jdbc.sql("""
                        select u.id, a.clabe from users u join accounts a on a.user_id = u.id
                        where u.email in ('ana@demo.mx', 'luis@demo.mx') order by u.email
                        """)
                .query((rs, i) -> new Party(rs.getObject(1, UUID.class), rs.getString(2)))
                .list();
        if (parties.size() != 2) {
            throw new IllegalStateException("Faltan las cuentas demo");
        }
        long before = demoTotal();

        AtomicInteger ok = new AtomicInteger();
        Map<String, Integer> rejected = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        long t0;
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < TRANSFERS; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    int from = rnd.nextInt(2);
                    try {
                        transfers.transfer(new TransferService.Command(
                                parties.get(from).userId(),
                                UUID.randomUUID().toString(),
                                parties.get(1 - from).clabe(),
                                100 + rnd.nextLong(4_900),
                                "Prueba de carga"));
                        ok.incrementAndGet();
                    } catch (ApiException e) {
                        rejected.merge(e.code().name(), 1, Integer::sum);
                    }
                    return null;
                }));
            }
            t0 = System.nanoTime();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        long after = demoTotal();
        return new Result(
                TRANSFERS,
                THREADS,
                ok.get(),
                new TreeMap<>(rejected),
                Math.round(ms * 10) / 10.0,
                Math.round(TRANSFERS / (ms / 1000) * 10) / 10.0,
                Money.format(before),
                Money.format(after),
                before == after,
                reconciliation.run().healthy());
    }

    private long demoTotal() {
        return jdbc.sql("""
                        select coalesce(sum(a.balance), 0) from accounts a join users u on u.id = a.user_id
                        where u.email in ('ana@demo.mx', 'luis@demo.mx')
                        """)
                .query(Long.class)
                .single();
    }
}
