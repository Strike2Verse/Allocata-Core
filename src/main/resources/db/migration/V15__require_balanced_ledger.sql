-- Every ledger transaction must add up to exactly zero (double entry).
-- A plain CHECK sees one row only, so a constraint trigger checks the whole group.
-- LG002 is our own error code, separate from LG001 (append-only).
CREATE FUNCTION require_balanced_ledger_transaction() RETURNS trigger AS $$
BEGIN
    IF (SELECT sum(amount_cents)
          FROM ledger_entries
         WHERE tenant_id = NEW.tenant_id
           AND transaction_id = NEW.transaction_id) <> 0 THEN
        RAISE EXCEPTION 'ledger transaction % does not balance', NEW.transaction_id
            USING ERRCODE = 'LG002';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Deferred: the check runs at COMMIT, so both rows of a pair can be inserted first.
CREATE CONSTRAINT TRIGGER ledger_entries_require_balance
    AFTER INSERT ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_balanced_ledger_transaction();

-- The check looks up all rows of one transaction.
CREATE INDEX idx_ledger_entries_transaction_id
    ON ledger_entries (tenant_id, transaction_id);
