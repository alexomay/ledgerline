package mx.alexomay.ledgerline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import mx.alexomay.ledgerline.ledger.ReconciliationService;
import mx.alexomay.ledgerline.outbox.OutboxRelay;
import mx.alexomay.ledgerline.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * La base de datos es la última línea de defensa: aunque un error en el código
 * intentara romper una regla contable, PostgreSQL la rechaza.
 */
class LedgerIntegrityTest extends IntegrationTest {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ReconciliationService reconciliation;

    @Autowired
    OutboxRelay relay;

    private static final UUID CLEARING = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void databaseRejectsUnbalancedEntryAtCommit() throws Exception {
        Session ana = register("Ana Descuadre");
        UUID account = accountId(ana);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                    UUID entry = UUID.randomUUID();
                    jdbc.sql("insert into journal_entries (id, kind, description) values (?, 'DEPOSIT', 'malo')")
                            .param(entry)
                            .update();
                    // Abono sin cargo correspondiente: dinero creado de la nada.
                    jdbc.sql("insert into postings (entry_id, account_id, amount, balance_after) values (?, ?, 100, 100)")
                            .params(entry, account)
                            .update();
                    jdbc.sql("insert into postings (entry_id, account_id, amount, balance_after) values (?, ?, -99, -99)")
                            .params(entry, CLEARING)
                            .update();
                }))
                .hasMessageContaining("descuadrado");
    }

    @Test
    void postingsCannotBeEditedOrDeleted() throws Exception {
        Session ana = register("Ana Inmutable");
        deposit(ana, "10.00");
        UUID account = accountId(ana);

        assertThatThrownBy(() -> jdbc.sql("update postings set amount = 1000000 where account_id = ?")
                        .param(account)
                        .update())
                .rootCause()
                .hasMessageContaining("no se modifican");
        assertThatThrownBy(() -> jdbc.sql("delete from postings where account_id = ?").param(account).update())
                .rootCause()
                .hasMessageContaining("no se modifican");
    }

    @Test
    void customerBalanceCannotGoNegativeEvenBypassingTheService() throws Exception {
        Session ana = register("Ana Negativo");
        assertThatThrownBy(() -> jdbc.sql("update accounts set balance = -1 where id = ?")
                        .param(accountId(ana))
                        .update())
                .hasMessageContaining("accounts_balance_non_negative");
    }

    @Test
    void reconciliationIsHealthyAndOutboxPublishesEvents() throws Exception {
        Session ana = register("Ana Conciliacion");
        Session luis = register("Luis Conciliacion");
        deposit(ana, "300.00");
        transfer(ana, UUID.randomUUID().toString(), luis.clabe(), "120.00", "Cena");

        ReconciliationService.Report report = reconciliation.run();
        assertThat(report.healthy()).isTrue();
        assertThat(report.totalBalance()).isZero();
        assertThat(report.balanceMismatches()).isEmpty();

        long pendingBefore = pendingEvents();
        assertThat(pendingBefore).isGreaterThanOrEqualTo(2); // depósito + transferencia
        while (relay.relay() > 0) {
            // vacía la bandeja
        }
        assertThat(pendingEvents()).isZero();
        assertThat(jdbc.sql("select count(*) from outbox_events where event_type = 'transfer.completed'")
                        .query(Long.class)
                        .single())
                .isPositive();
    }

    private long pendingEvents() {
        return jdbc.sql("select count(*) from outbox_events where published_at is null")
                .query(Long.class)
                .single();
    }

    private UUID accountId(Session s) {
        return jdbc.sql("select id from accounts where user_id = ?").param(s.userId()).query(UUID.class).single();
    }
}
