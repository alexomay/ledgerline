package mx.alexomay.ledgerline.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Asiento contable: agrupa los movimientos de una operación. */
@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    public enum Kind {
        TRANSFER,
        DEPOSIT
    }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String description;

    @Column(name = "created_at")
    private Instant createdAt;

    protected JournalEntry() {}

    JournalEntry(Kind kind, String description, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.kind = kind;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Kind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
