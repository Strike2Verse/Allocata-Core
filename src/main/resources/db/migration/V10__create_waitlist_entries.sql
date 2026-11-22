CREATE TABLE waitlist_entries (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    event_id   uuid        NOT NULL REFERENCES events (id),
    user_id    uuid        NOT NULL REFERENCES users (id),
    status     text        NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'PROMOTED', 'CANCELLED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id)
);
