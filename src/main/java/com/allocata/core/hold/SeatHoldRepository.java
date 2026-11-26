package com.allocata.core.hold;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {

	/** With ACTIVE there is at most one result, the database enforces it. */
	Optional<SeatHold> findBySeatIdAndStatus(UUID seatId, HoldStatus status);

	/** For the expiry sweep: ACTIVE holds whose time has run out. */
	List<SeatHold> findByStatusAndExpiresAtBefore(HoldStatus status, Instant time);
}
