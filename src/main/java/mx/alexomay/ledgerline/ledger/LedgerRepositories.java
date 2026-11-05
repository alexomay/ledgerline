package mx.alexomay.ledgerline.ledger;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {}

interface PostingRepository extends JpaRepository<Posting, Long> {}
