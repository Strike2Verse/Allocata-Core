package com.allocata.core.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	/** Oldest events first. The relay will use its own locking query later. */
	List<OutboxEvent> findTop100ByStatusOrderByCreatedAt(OutboxStatus status);
}
