package mx.alexomay.ledgerline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import mx.alexomay.ledgerline.ledger.ReconciliationService;
import mx.alexomay.ledgerline.support.IntegrationTest;
import mx.alexomay.ledgerline.transfer.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Lo que de verdad importa en un sistema de pagos: que bajo carga concurrente
 * el dinero no se cree, no se pierda y ninguna cuenta quede en negativo.
 */
class ConcurrencyTest extends IntegrationTest {

    @Autowired
    TransferService transfers;

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void randomConcurrentTransfersConserveMoneyAndNeverOverdraw() throws Exception {
        int accounts = 6;
        List<Session> sessions = new ArrayList<>();
        for (int i = 0; i < accounts; i++) {
            Session s = register("Cliente Carga " + i);
            deposit(s, "1000.00");
            sessions.add(s);
        }
        long totalBefore = customerTotal(sessions);

        int threads = 16;
        int perThread = 40;
        AtomicInteger ok = new AtomicInteger();
        Map<ErrorCode, AtomicInteger> rejected = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        long t0;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    for (int i = 0; i < perThread; i++) {
                        int from = rnd.nextInt(accounts);
                        int to = (from + 1 + rnd.nextInt(accounts - 1)) % accounts;
                        // Montos grandes a propósito: muchas operaciones deben fallar por saldo.
                        long cents = 1 + rnd.nextLong(60_000);
                        try {
                            transfers.transfer(new TransferService.Command(
                                    sessions.get(from).userId(),
                                    UUID.randomUUID().toString(),
                                    sessions.get(to).clabe(),
                                    cents,
                                    "carga"));
                            ok.incrementAndGet();
                        } catch (ApiException e) {
                            rejected.computeIfAbsent(e.code(), k -> new AtomicInteger()).incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            t0 = System.nanoTime();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(); // propaga cualquier error inesperado (por ejemplo, un interbloqueo)
            }
        }
        double seconds = (System.nanoTime() - t0) / 1e9;
        System.out.printf(
                "%d transferencias en %.2f s (%.0f/s), %d exitosas, rechazadas: %s%n",
                threads * perThread, seconds, threads * perThread / seconds, ok.get(), rejected);

        assertThat(ok.get()).isPositive();
        assertThat(rejected.keySet()).containsOnly(ErrorCode.INSUFFICIENT_FUNDS);
        assertThat(customerTotal(sessions)).isEqualTo(totalBefore);
        for (Session s : sessions) {
            assertThat(balance(s)).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);
        }
        assertThat(reconciliation.run().healthy()).isTrue();
    }

    @Test
    void crossTransfersInOppositeDirectionsDoNotDeadlock() throws Exception {
        Session a = register("Cruce A");
        Session b = register("Cruce B");
        deposit(a, "5000.00");
        deposit(b, "5000.00");

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                boolean aToB = t % 2 == 0;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 25; i++) {
                        Session from = aToB ? a : b;
                        Session to = aToB ? b : a;
                        transfers.transfer(new TransferService.Command(
                                from.userId(), UUID.randomUUID().toString(), to.clabe(), 100, "cruce"));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }
        // 100 de A a B y 100 de B a A, de $1.00 cada una: los saldos regresan al inicio.
        assertThat(balance(a)).isEqualByComparingTo("5000.00");
        assertThat(balance(b)).isEqualByComparingTo("5000.00");
    }

    @Test
    void sameIdempotencyKeyFromManyThreadsCreatesExactlyOneTransfer() throws Exception {
        Session a = register("Doble Clic A");
        Session b = register("Doble Clic B");
        deposit(a, "100.00");
        String key = UUID.randomUUID().toString();

        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferService.Result>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int t = 0; t < 10; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return transfers.transfer(
                            new TransferService.Command(a.userId(), key, b.clabe(), 4_000, "doble clic"));
                }));
            }
            start.countDown();
        }
        List<TransferService.Result> results = new ArrayList<>();
        for (Future<TransferService.Result> f : futures) {
            results.add(f.get());
        }

        assertThat(results.stream().map(r -> r.transfer().getId()).distinct()).hasSize(1);
        assertThat(results.stream().filter(r -> !r.replayed())).hasSize(1);
        assertThat(balance(a)).isEqualByComparingTo("60.00");
        assertThat(balance(b)).isEqualByComparingTo("40.00");
    }

    private long customerTotal(List<Session> sessions) throws Exception {
        long total = 0;
        for (Session s : sessions) {
            total += balance(s).movePointRight(2).longValueExact();
        }
        return total;
    }
}
