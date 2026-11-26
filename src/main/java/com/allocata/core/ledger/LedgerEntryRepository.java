package com.allocata.core.ledger;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Extends the base Repository instead of JpaRepository on purpose, so no update
 * or delete method exists in Java. Only these methods are available.
 */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

	LedgerEntry save(LedgerEntry entry);

	List<LedgerEntry> saveAll(Iterable<LedgerEntry> entries);

	List<LedgerEntry> findByTransactionId(UUID transactionId);
}
