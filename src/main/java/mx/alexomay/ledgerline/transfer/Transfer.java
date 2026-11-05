package mx.alexomay.ledgerline.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "from_account_id")
    private UUID fromAccountId;

    @Column(name = "to_account_id")
    private UUID toAccountId;

    /** Centavos. */
    private long amount;

    private String concept;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Transfer() {}

    Transfer(UUID entryId, UUID from, UUID to, long amount, String concept, UUID createdBy, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.entryId = entryId;
        this.fromAccountId = from;
        this.toAccountId = to;
        this.amount = amount;
        this.concept = concept;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public long getAmount() {
        return amount;
    }

    public String getConcept() {
        return concept;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
