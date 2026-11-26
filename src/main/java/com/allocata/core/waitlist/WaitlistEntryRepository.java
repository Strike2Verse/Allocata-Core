package com.allocata.core.waitlist;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

	boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

	/** The longest-waiting entry with the given status, for promotion. */
	Optional<WaitlistEntry> findFirstByEventIdAndStatusOrderByCreatedAt(UUID eventId, WaitlistStatus status);
}
