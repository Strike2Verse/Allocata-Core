package com.allocata.core.order;

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
 * The checkout of one held seat. Maps to the orders table.
 * The entity name is TicketOrder because ORDER is a keyword in JPA queries.
 */
@Entity(name = "TicketOrder")
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false)
	private UUID userId;

	@Column(nullable = false)
	private UUID seatId;

	/** One hold makes at most one order (unique in the database). */
	@Column(nullable = false, unique = true)
	private UUID holdId;

	/** Whole cents, a unit and not a currency type. */
	@Column(nullable = false)
	private int priceCents;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private OrderStatus status;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected Order() {
	}

	/** A new order starts as PENDING, like the database default. */
	public Order(UUID tenantId, UUID userId, UUID seatId, UUID holdId, int priceCents) {
		this.tenantId = tenantId;
		this.userId = userId;
		this.seatId = seatId;
		this.holdId = holdId;
		this.priceCents = priceCents;
		this.status = OrderStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getSeatId() {
		return seatId;
	}

	public UUID getHoldId() {
		return holdId;
	}

	public int getPriceCents() {
		return priceCents;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
