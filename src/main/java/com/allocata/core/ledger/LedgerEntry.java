package com.allocata.core.ledger;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of the double-entry payment ledger. Maps to the ledger_entries table.
 * Insert-only: @Immutable stops Hibernate from sending updates, and the database
 * blocks UPDATE, DELETE and TRUNCATE as well. All entries of one transaction must
 * be saved in one database transaction, because the balance is checked at commit.
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	/** Groups the entries that must add up to zero. */
	@Column(nullable = false)
	private UUID transactionId;

	@Column(nullable = false, columnDefinition = "text")
	private String account;

	/** Signed whole cents: positive is a debit, negative is a credit. */
	@Column(nullable = false)
	private int amountCents;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected LedgerEntry() {
	}

	public LedgerEntry(UUID tenantId, UUID transactionId, String account, int amountCents) {
		this.tenantId = tenantId;
		this.transactionId = transactionId;
		this.account = account;
		this.amountCents = amountCents;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	public String getAccount() {
		return account;
	}

	public int getAmountCents() {
		return amountCents;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
