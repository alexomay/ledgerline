package mx.alexomay.ledgerline.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    public enum Kind {
        CUSTOMER,
        SYSTEM
    }

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String code;
    private String clabe;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    private String currency;

    /** Saldo en centavos. Solo lo modifica {@code LedgerService}. */
    private long balance;

    @Column(name = "allow_negative")
    private boolean allowNegative;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Account() {}

    public static Account customer(UUID userId, String clabe) {
        Account a = new Account();
        a.id = UUID.randomUUID();
        a.userId = userId;
        a.clabe = clabe;
        a.kind = Kind.CUSTOMER;
        a.currency = "MXN";
        return a;
    }

    public void applyPosting(long amount) {
        long next = Math.addExact(balance, amount);
        if (next < 0 && !allowNegative) {
            throw new IllegalStateException("La cuenta " + id + " quedaría en negativo");
        }
        balance = next;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCode() {
        return code;
    }

    public String getClabe() {
        return clabe;
    }

    public Kind getKind() {
        return kind;
    }

    public String getCurrency() {
        return currency;
    }

    public long getBalance() {
        return balance;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
