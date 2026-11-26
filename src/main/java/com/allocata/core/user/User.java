package com.allocata.core.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A person in one tenant, either an admin or a buyer. Maps to the users table. */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false)
	private UUID tenantId;

	@Column(nullable = false, columnDefinition = "text")
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, columnDefinition = "text")
	private UserRole role;

	@Column(nullable = false, updatable = false, columnDefinition = "timestamptz")
	private Instant createdAt;

	/** For JPA only. */
	protected User() {
	}

	public User(UUID tenantId, String email, UserRole role) {
		this.tenantId = tenantId;
		this.email = email;
		this.role = role;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getTenantId() {
		return tenantId;
	}

	public String getEmail() {
		return email;
	}

	public UserRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
