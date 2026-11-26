package com.allocata.core.seat;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

	List<Seat> findByEventIdOrderByLabel(UUID eventId);

	/** For example the number of AVAILABLE seats, used to seed stock in Redis. */
	long countByEventIdAndStatus(UUID eventId, SeatStatus status);
}
