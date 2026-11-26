package com.allocata.core.outbox;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An event saved in the same transaction as the change it describes,
 * to be published later by a relay. Maps to the outbox_events table.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false, columnDefinition = "text")
	private String eventType;

	/** The seat, order or other row the event is about. No foreign key, it can point to different tables. */
	@Column(nullable = false)
	private UUID aggregateId;

	/**
	 * Raw JSON text. The cast makes Postgres store it as jsonb and reject text
	 * that is not valid JSON.
	 */
	@ColumnTransformer(write = "?::jsonb")
	@Column(nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private OutboxStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected OutboxEvent() {
	}

	/** A new event starts as PENDING with no attempts, like the database defaults. */
	public OutboxEvent(UUID tenantId, String eventType, UUID aggregateId, String payload) {
		this.tenantId = tenantId;
		this.eventType = eventType;
		this.aggregateId = aggregateId;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
		this.attempts = 0;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getEventType() {
		return eventType;
	}

	public UUID getAggregateId() {
		return aggregateId;
	}

	public String getPayload() {
		return payload;
	}

	public OutboxStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
