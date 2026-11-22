CREATE TABLE orders (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid        NOT NULL REFERENCES tenants (id),
    user_id     uuid        NOT NULL REFERENCES users (id),
    seat_id     uuid        NOT NULL REFERENCES seats (id),
    hold_id     uuid        NOT NULL UNIQUE REFERENCES seat_holds (id),
    price_cents integer     NOT NULL CHECK (price_cents >= 0),
    status      text        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')),
    created_at  timestamptz NOT NULL DEFAULT now()
);
