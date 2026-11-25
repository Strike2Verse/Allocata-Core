-- The ledger is append-only: rows are never edited or removed.
-- A mistake is fixed by adding a new reversing entry.
-- LG001 is our own error code so a test can tell this rejection apart from others.
CREATE FUNCTION reject_ledger_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'LG001';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_block_row_changes
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_change();

-- Row triggers do not fire for TRUNCATE, so it needs its own trigger.
CREATE TRIGGER ledger_entries_block_truncate
    BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION reject_ledger_change();
