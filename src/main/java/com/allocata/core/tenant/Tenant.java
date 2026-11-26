package com.allocata.core.tenant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An organizer that owns events. Maps to the tenants table. */
@Entity
@Table(name = "tenants")
public class Tenant {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, columnDefinition = "text")
	private String name;

	@Column(nullable = false, unique = true, columnDefinition = "text")
	private String slug;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected Tenant() {
	}

	public Tenant(String name, String slug) {
		this.name = name;
		this.slug = slug;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
