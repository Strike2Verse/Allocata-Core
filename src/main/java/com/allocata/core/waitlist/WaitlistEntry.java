package com.allocata.core.waitlist;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A buyer waiting for a seat at one event. Maps to the waitlist_entries table.
 * The database allows one entry per buyer per event; createdAt records join order.
 */
@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private UUID eventId;

	@Column(nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private WaitlistStatus status;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected WaitlistEntry() {
	}

	/** A new entry starts as WAITING, like the database default. */
	public WaitlistEntry(UUID tenantId, UUID eventId, UUID userId) {
		this.tenantId = tenantId;
		this.eventId = eventId;
		this.userId = userId;
		this.status = WaitlistStatus.WAITING;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getEventId() {
		return eventId;
	}

	public UUID getUserId() {
		return userId;
	}

	public WaitlistStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
