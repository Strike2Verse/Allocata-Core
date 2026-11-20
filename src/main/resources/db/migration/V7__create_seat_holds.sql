CREATE TABLE seat_holds (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    seat_id    uuid        NOT NULL REFERENCES seats (id),
    user_id    uuid        NOT NULL REFERENCES users (id),
    status     text        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'RELEASED', 'CONVERTED')),
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
