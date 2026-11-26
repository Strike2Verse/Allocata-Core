package com.allocata.core.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A show or match that sells seats. Maps to the events table. */
@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false, columnDefinition = "text")
	private String name;

	@Column(nullable = false, columnDefinition = "text")
	private String venue;

	@Column(nullable = false, columnDefinition = "timestamptz")
	private Instant startsAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private EventStatus status;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected Event() {
	}

	/** A new event starts as DRAFT, like the database default. */
	public Event(UUID tenantId, String name, String venue, Instant startsAt) {
		this.tenantId = tenantId;
		this.name = name;
		this.venue = venue;
		this.startsAt = startsAt;
		this.status = EventStatus.DRAFT;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getName() {
		return name;
	}

	public String getVenue() {
		return venue;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public EventStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
