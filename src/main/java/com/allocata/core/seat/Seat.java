package com.allocata.core.seat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** One seat of one event. Maps to the seats table. */
@Entity
@Table(name = "seats")
public class Seat {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private UUID eventId;

	@Column(nullable = false, columnDefinition = "text")
	private String label;

	/** Whole cents, a unit and not a currency type. */
	@Column(nullable = false)
	private int priceCents;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private SeatStatus status;

	/**
	 * Optimistic locking: Hibernate adds "WHERE version = ?" to every update,
	 * so two buyers cannot silently overwrite each other.
	 */
	@Version
	@Column(nullable = false)
	private int version;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected Seat() {
	}

	/** A new seat starts as AVAILABLE, like the database default. */
	public Seat(UUID tenantId, UUID eventId, String label, int priceCents) {
		this.tenantId = tenantId;
		this.eventId = eventId;
		this.label = label;
		this.priceCents = priceCents;
		this.status = SeatStatus.AVAILABLE;
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

	public String getLabel() {
		return label;
	}

	public int getPriceCents() {
		return priceCents;
	}

	public SeatStatus getStatus() {
		return status;
	}

	public int getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
