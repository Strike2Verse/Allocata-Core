package com.allocata.core.hold;

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
 * A short lease on one seat for one buyer. Maps to the seat_holds table.
 * The database allows at most one ACTIVE hold per seat.
 */
@Entity
@Table(name = "seat_holds")
public class SeatHold {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private UUID seatId;

	@Column(nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private HoldStatus status;

	@Column(nullable = false, columnDefinition = "timestamptz")
	private Instant expiresAt;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected SeatHold() {
	}

	/** A new hold starts as ACTIVE, like the database default. The hold length is decided by the caller. */
	public SeatHold(UUID tenantId, UUID seatId, UUID userId, Instant expiresAt) {
		this.tenantId = tenantId;
		this.seatId = seatId;
		this.userId = userId;
		this.expiresAt = expiresAt;
		this.status = HoldStatus.ACTIVE;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getSeatId() {
		return seatId;
	}

	public UUID getUserId() {
		return userId;
	}

	public HoldStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
