package com.allocata.core.outbox;

/** Matches the status check on the outbox_events table. */
public enum OutboxStatus {
	PENDING,
	SENT,
	FAILED
}
