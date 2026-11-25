CREATE TABLE ledger_entries (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES tenants (id),
    transaction_id uuid        NOT NULL,
    account        text        NOT NULL,
    amount_cents   integer     NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now()
);
