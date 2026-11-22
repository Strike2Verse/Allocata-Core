CREATE UNIQUE INDEX uq_seat_holds_active_seat ON seat_holds (seat_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_seat_holds_expires_at ON seat_holds (expires_at);
