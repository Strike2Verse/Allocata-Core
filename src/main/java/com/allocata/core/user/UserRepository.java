package com.allocata.core.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	/** Email is unique per tenant, used at login. */
	Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);
}
