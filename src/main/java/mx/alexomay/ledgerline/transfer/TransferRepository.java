package mx.alexomay.ledgerline.transfer;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TransferRepository extends JpaRepository<Transfer, UUID> {

    @Query("select coalesce(sum(t.amount), 0) from Transfer t where t.fromAccountId = :account and t.createdAt >= :since")
    long sentSince(UUID account, Instant since);
}
