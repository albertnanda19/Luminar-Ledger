CREATE TABLE ledger_events (
    event_id        UUID PRIMARY KEY,
    aggregate_type  VARCHAR(32) NOT NULL,
    aggregate_id    UUID NOT NULL,
    sequence_number BIGINT NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    reference_id    VARCHAR(128) NOT NULL,
    correlation_id  VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_ledger_events_aggregate_type
        CHECK (aggregate_type = 'LEDGER'),

    CONSTRAINT uq_ledger_events_reference_id
        UNIQUE (reference_id),

    CONSTRAINT uq_ledger_events_aggregate_sequence
        UNIQUE (aggregate_id, sequence_number)
);

CREATE INDEX idx_ledger_events_aggregate_id
    ON ledger_events (aggregate_id);

CREATE INDEX idx_ledger_events_occurred_at
    ON ledger_events (occurred_at);

CREATE OR REPLACE FUNCTION forbid_ledger_events_update_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger events are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_no_update_ledger_events
BEFORE UPDATE OR DELETE ON ledger_events
FOR EACH ROW
EXECUTE FUNCTION forbid_ledger_events_update_delete();
