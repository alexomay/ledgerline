package mx.alexomay.ledgerline.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Movimiento de un asiento sobre una cuenta. Positivo = abono, negativo = cargo. */
@Entity
@Immutable
@Table(name = "postings")
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "account_id")
    private UUID accountId;

    private long amount;

    @Column(name = "balance_after")
    private long balanceAfter;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Posting() {}

    Posting(UUID entryId, UUID accountId, long amount, long balanceAfter, Instant createdAt) {
        this.entryId = entryId;
        this.accountId = accountId;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public long getAmount() {
        return amount;
    }

    public long getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
