package mx.alexomay.ledgerline.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByClabe(String clabe);

    Optional<Account> findByCode(String code);

    @Query("select a from Account a where a.userId = :userId and a.kind = 'CUSTOMER'")
    Optional<Account> findCustomerAccount(UUID userId);

    boolean existsByClabe(String clabe);
}
