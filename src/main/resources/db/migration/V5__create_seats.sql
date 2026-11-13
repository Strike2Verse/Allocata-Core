CREATE TABLE seats (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid        NOT NULL REFERENCES tenants (id),
    event_id    uuid        NOT NULL REFERENCES events (id),
    label       text        NOT NULL,
    price_cents integer     NOT NULL CHECK (price_cents >= 0),
    status      text        NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'SOLD')),
    version     integer     NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (event_id, label)
);
