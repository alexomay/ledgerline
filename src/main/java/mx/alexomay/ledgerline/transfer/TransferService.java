package mx.alexomay.ledgerline.transfer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.alexomay.ledgerline.account.Account;
import mx.alexomay.ledgerline.account.AccountService;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import mx.alexomay.ledgerline.common.Money;
import mx.alexomay.ledgerline.ledger.JournalEntry;
import mx.alexomay.ledgerline.ledger.LedgerService;
import mx.alexomay.ledgerline.outbox.OutboxService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    public record Command(UUID userId, String idempotencyKey, String toClabe, long amount, String concept) {}

    public record Result(Transfer transfer, boolean replayed) {}

    private final AccountService accounts;
    private final LedgerService ledger;
    private final TransferRepository transfers;
    private final TransferLimits limits;
    private final OutboxService outbox;
    private final JdbcClient jdbc;

    TransferService(
            AccountService accounts,
            LedgerService ledger,
            TransferRepository transfers,
            TransferLimits limits,
            OutboxService outbox,
            JdbcClient jdbc) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
        this.limits = limits;
        this.outbox = outbox;
        this.jdbc = jdbc;
    }

    /**
     * Transferencia entre cuentas de clientes, todo en una sola transacción:
     *
     * <ol>
     *   <li>Reserva la llave de idempotencia. Si otra petición con la misma llave
     *       está en curso, PostgreSQL hace esperar a esta hasta que la otra termine.
     *   <li>Bloquea las dos cuentas en orden de id (sin interbloqueos).
     *   <li>Con las cuentas bloqueadas, valida saldo y límites: nadie más puede
     *       mover esas cuentas entre la validación y el cargo.
     *   <li>Registra el asiento, la transferencia y el evento de salida.
     * </ol>
     *
     * Si algo falla, se revierte todo, incluida la llave, y el cliente puede
     * reintentar con la misma llave.
     */
    @Transactional
    public Result transfer(Command cmd) {
        if (cmd.amount() <= 0) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "El monto debe ser mayor a cero.");
        }
        String hash = requestHash(cmd);

        int reserved = jdbc.sql("""
                        insert into idempotency_keys (user_id, idem_key, request_hash)
                        values (?, ?, ?)
                        on conflict do nothing
                        """)
                .params(cmd.userId(), cmd.idempotencyKey(), hash)
                .update();
        if (reserved == 0) {
            return replay(cmd, hash);
        }

        Account source = accounts.customerAccount(cmd.userId());
        Account target = accounts.byClabe(cmd.toClabe());
        if (source.getId().equals(target.getId())) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT, "No puedes transferirte a tu propia cuenta.");
        }

        Map<UUID, Account> locked = ledger.lock(List.of(source.getId(), target.getId()));
        Account from = locked.get(source.getId());

        TransferLimits.Usage usage = limits.usage(from.getId());
        if (cmd.amount() > usage.perTransfer()) {
            throw new ApiException(
                    ErrorCode.LIMIT_EXCEEDED,
                    "El máximo por transferencia es " + Money.format(usage.perTransfer()) + ".");
        }
        if (cmd.amount() > usage.remainingToday()) {
            throw new ApiException(
                    ErrorCode.LIMIT_EXCEEDED,
                    "Superas tu límite diario. Hoy aún puedes enviar " + Money.format(usage.remainingToday()) + ".");
        }
        if (from.getBalance() < cmd.amount()) {
            throw new ApiException(
                    ErrorCode.INSUFFICIENT_FUNDS,
                    "Saldo insuficiente. Tu saldo disponible es " + Money.format(from.getBalance()) + ".");
        }

        LedgerService.PostedEntry posted = ledger.post(
                JournalEntry.Kind.TRANSFER,
                "Transferencia: " + cmd.concept(),
                List.of(
                        new LedgerService.Leg(from.getId(), -cmd.amount()),
                        new LedgerService.Leg(target.getId(), cmd.amount())));

        Transfer transfer = transfers.saveAndFlush(new Transfer(
                posted.entry().getId(),
                from.getId(),
                target.getId(),
                cmd.amount(),
                cmd.concept(),
                cmd.userId(),
                posted.entry().getCreatedAt()));

        jdbc.sql("update idempotency_keys set transfer_id = ? where user_id = ? and idem_key = ?")
                .params(transfer.getId(), cmd.userId(), cmd.idempotencyKey())
                .update();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("transferId", transfer.getId());
        event.put("fromAccountId", from.getId());
        event.put("toAccountId", target.getId());
        event.put("amountCents", cmd.amount());
        event.put("currency", "MXN");
        event.put("concept", cmd.concept());
        event.put("occurredAt", transfer.getCreatedAt());
        outbox.record("transfer.completed", from.getId(), event);

        return new Result(transfer, false);
    }

    private Result replay(Command cmd, String hash) {
        record Stored(String requestHash, UUID transferId) {}
        Stored stored = jdbc.sql("select request_hash, transfer_id from idempotency_keys where user_id = ? and idem_key = ?")
                .params(cmd.userId(), cmd.idempotencyKey())
                .query((rs, i) -> new Stored(rs.getString(1), rs.getObject(2, UUID.class)))
                .single();
        if (!stored.requestHash().equals(hash)) {
            throw new ApiException(
                    ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "Esta llave de idempotencia ya se usó con otros datos. Genera una nueva para otra operación.");
        }
        return new Result(transfers.findById(stored.transferId()).orElseThrow(), true);
    }

    static String requestHash(Command cmd) {
        String canonical = cmd.toClabe() + "|" + cmd.amount() + "|" + cmd.concept();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
