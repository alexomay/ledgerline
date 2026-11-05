package mx.alexomay.ledgerline.deposit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.alexomay.ledgerline.account.Account;
import mx.alexomay.ledgerline.account.AccountRepository;
import mx.alexomay.ledgerline.account.AccountService;
import mx.alexomay.ledgerline.ledger.JournalEntry;
import mx.alexomay.ledgerline.ledger.LedgerService;
import mx.alexomay.ledgerline.outbox.OutboxService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abona los depósitos que llegan por SPEI. El dinero entra al sistema desde la
 * cuenta puente SPEI_CLEARING, que representa lo que el banco nos debe; por eso
 * es la única cuenta que puede quedar en negativo.
 */
@Service
public class DepositService {

    public static final String CLEARING_ACCOUNT = "SPEI_CLEARING";

    public record SpeiNotice(String trackingKey, String clabe, long amountCents, String senderName, String senderBank) {}

    public record Outcome(UUID depositId, boolean duplicate) {}

    private final AccountService accounts;
    private final AccountRepository accountRepository;
    private final LedgerService ledger;
    private final OutboxService outbox;
    private final JdbcClient jdbc;

    DepositService(
            AccountService accounts,
            AccountRepository accountRepository,
            LedgerService ledger,
            OutboxService outbox,
            JdbcClient jdbc) {
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.ledger = ledger;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    @Transactional
    public Outcome receive(SpeiNotice n) {
        Account target = accounts.byClabe(n.clabe());

        // La clave de rastreo es única: si el banco reenvía la notificación, el
        // insert no hace nada y se responde como duplicado sin abonar otra vez.
        UUID depositId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                        insert into spei_deposits (id, tracking_key, account_id, amount, sender_name, sender_bank)
                        values (?, ?, ?, ?, ?, ?)
                        on conflict (tracking_key) do nothing
                        """)
                .params(depositId, n.trackingKey(), target.getId(), n.amountCents(), n.senderName(), n.senderBank())
                .update();
        if (inserted == 0) {
            UUID existing = jdbc.sql("select id from spei_deposits where tracking_key = ?")
                    .param(n.trackingKey())
                    .query(UUID.class)
                    .single();
            return new Outcome(existing, true);
        }

        Account clearing = accountRepository.findByCode(CLEARING_ACCOUNT).orElseThrow();
        LedgerService.PostedEntry posted = ledger.post(
                JournalEntry.Kind.DEPOSIT,
                "Depósito SPEI de " + n.senderName(),
                List.of(
                        new LedgerService.Leg(clearing.getId(), -n.amountCents()),
                        new LedgerService.Leg(target.getId(), n.amountCents())));

        jdbc.sql("update spei_deposits set entry_id = ? where id = ?")
                .params(posted.entry().getId(), depositId)
                .update();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("depositId", depositId);
        event.put("accountId", target.getId());
        event.put("amountCents", n.amountCents());
        event.put("currency", "MXN");
        event.put("trackingKey", n.trackingKey());
        event.put("occurredAt", posted.entry().getCreatedAt());
        outbox.record("deposit.received", target.getId(), event);

        return new Outcome(depositId, false);
    }
}
