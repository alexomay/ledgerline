package mx.alexomay.ledgerline.account;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import mx.alexomay.ledgerline.common.ApiException;
import mx.alexomay.ledgerline.common.ErrorCode;
import mx.alexomay.ledgerline.config.LedgerlineProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    /** Un renglón del estado de cuenta. Montos en centavos. */
    public record Movement(
            long id,
            Instant at,
            String kind,
            long amount,
            long balanceAfter,
            String description,
            String counterparty,
            String concept,
            String reference) {}

    public record MovementPage(List<Movement> items, Long nextCursor) {}

    public record Holder(String clabe, String maskedName) {}

    private final AccountRepository accounts;
    private final JdbcClient jdbc;
    private final LedgerlineProperties props;

    AccountService(AccountRepository accounts, JdbcClient jdbc, LedgerlineProperties props) {
        this.accounts = accounts;
        this.jdbc = jdbc;
        this.props = props;
    }

    @Transactional
    public Account openCustomerAccount(UUID userId) {
        String clabe;
        do {
            clabe = Clabe.generate(props.clabe().bankCode(), props.clabe().branchCode());
        } while (accounts.existsByClabe(clabe));
        return accounts.save(Account.customer(userId, clabe));
    }

    @Transactional(readOnly = true)
    public Account customerAccount(UUID userId) {
        return accounts.findCustomerAccount(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "Este usuario no tiene cuenta."));
    }

    /** Valida la CLABE y devuelve la cuenta de cliente a la que pertenece. */
    @Transactional(readOnly = true)
    public Account byClabe(String clabe) {
        if (!Clabe.isValid(clabe)) {
            throw new ApiException(ErrorCode.INVALID_CLABE, "La CLABE no es válida; revisa los 18 dígitos.");
        }
        return accounts.findByClabe(clabe)
                .filter(a -> a.getKind() == Account.Kind.CUSTOMER)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND, "No hay ninguna cuenta con esa CLABE."));
    }

    /** Nombre parcial del titular, para confirmar el destino antes de enviar (como en la app del banco). */
    @Transactional(readOnly = true)
    public Holder holder(String clabe) {
        Account account = byClabe(clabe);
        String name = jdbc.sql("select full_name from users where id = ?")
                .param(account.getUserId())
                .query(String.class)
                .single();
        return new Holder(clabe, mask(name));
    }

    /** Primer nombre completo y el resto solo con inicial: "Ana García López" → "Ana G*** L***". */
    static String mask(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(' ').append(parts[i].charAt(0)).append("***");
        }
        return sb.toString();
    }

    /**
     * Estado de cuenta con paginación por cursor (el id del último movimiento
     * visto). A diferencia de OFFSET, no se salta ni repite renglones cuando
     * entran movimientos nuevos mientras el usuario navega.
     */
    @Transactional(readOnly = true)
    public MovementPage movements(UUID accountId, Long before, int limit) {
        List<Movement> rows = jdbc.sql("""
                        select p.id, p.created_at, e.kind, p.amount, p.balance_after, e.description,
                               case
                                   when t.id is not null and p.amount < 0 then tu.full_name
                                   when t.id is not null then fu.full_name
                                   else d.sender_name || ' (' || d.sender_bank || ')'
                               end as counterparty,
                               t.concept,
                               coalesce(t.id::text, d.tracking_key) as reference
                        from postings p
                        join journal_entries e on e.id = p.entry_id
                        left join transfers t on t.entry_id = e.id
                        left join accounts fa on fa.id = t.from_account_id
                        left join users fu on fu.id = fa.user_id
                        left join accounts ta on ta.id = t.to_account_id
                        left join users tu on tu.id = ta.user_id
                        left join spei_deposits d on d.entry_id = e.id
                        where p.account_id = :account and (cast(:before as bigint) is null or p.id < :before)
                        order by p.id desc
                        limit :limit
                        """)
                .param("account", accountId)
                .param("before", before)
                .param("limit", limit + 1)
                .query((rs, i) -> new Movement(
                        rs.getLong("id"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getString("kind"),
                        rs.getLong("amount"),
                        rs.getLong("balance_after"),
                        rs.getString("description"),
                        rs.getString("counterparty"),
                        rs.getString("concept"),
                        rs.getString("reference")))
                .list();
        Long next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            next = rows.getLast().id();
        }
        return new MovementPage(rows, next);
    }
}
