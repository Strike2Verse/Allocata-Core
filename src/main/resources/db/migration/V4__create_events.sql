CREATE TABLE events (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    name       text        NOT NULL,
    venue      text        NOT NULL,
    starts_at  timestamptz NOT NULL,
    status     text        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ON_SALE', 'CLOSED')),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_tenant_id ON events (tenant_id);
