package com.allocata.core.order;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	/** One hold makes at most one order. */
	Optional<Order> findByHoldId(UUID holdId);
}
