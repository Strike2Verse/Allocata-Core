package com.allocata.core.event;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {

	/** Includes the tenant so one organizer cannot load another organizer's event by id. */
	Optional<Event> findByIdAndTenantId(UUID id, UUID tenantId);
}
