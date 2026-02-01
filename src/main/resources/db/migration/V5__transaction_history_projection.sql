CREATE SEQUENCE IF NOT EXISTS ledger_events_global_sequence_seq;

ALTER TABLE ledger_events
    ADD COLUMN IF NOT EXISTS global_sequence BIGINT;

ALTER TABLE ledger_events
    DISABLE TRIGGER trg_no_update_ledger_events;

WITH ranked AS (
    SELECT event_id,
           row_number() OVER (ORDER BY occurred_at ASC, event_id ASC) AS rn
    FROM ledger_events
    WHERE global_sequence IS NULL
)
UPDATE ledger_events le
SET global_sequence = ranked.rn
FROM ranked
WHERE le.event_id = ranked.event_id;

ALTER TABLE ledger_events
    ENABLE TRIGGER trg_no_update_ledger_events;

SELECT setval(
    'ledger_events_global_sequence_seq',
    (SELECT COALESCE(MAX(global_sequence), 1) FROM ledger_events),
    (SELECT COUNT(*) > 0 FROM ledger_events)
);

ALTER TABLE ledger_events
    ALTER COLUMN global_sequence SET DEFAULT nextval('ledger_events_global_sequence_seq');

ALTER TABLE ledger_events
    ALTER COLUMN global_sequence SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_ledger_events_global_sequence'
    ) THEN
        ALTER TABLE ledger_events
            ADD CONSTRAINT uq_ledger_events_global_sequence UNIQUE (global_sequence);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ledger_events_global_sequence
    ON ledger_events (global_sequence);

CREATE TABLE projection_checkpoints (
    projection_type      VARCHAR(64) PRIMARY KEY,
    last_sequence_number BIGINT NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projection_event_dedup (
    event_id         UUID NOT NULL,
    projection_type  VARCHAR(64) NOT NULL,
    processed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (event_id, projection_type)
);

CREATE TABLE transaction_history_projection (
    projection_id    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id         UUID NOT NULL,
    transaction_id   UUID NOT NULL,
    reference_key    VARCHAR(128) NOT NULL,
    account_id       UUID NOT NULL,
    direction        entry_type NOT NULL,
    amount           NUMERIC(20,6) NOT NULL,
    currency         CHAR(3) NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL,
    sequence_number  BIGINT NOT NULL,
    correlation_id   VARCHAR(128) NOT NULL
);

ALTER TABLE transaction_history_projection
    ADD CONSTRAINT uq_transaction_history_event_account_direction
        UNIQUE (event_id, account_id, direction);

CREATE INDEX idx_transaction_history_account_occurred_desc
    ON transaction_history_projection (account_id, occurred_at DESC);

CREATE INDEX idx_transaction_history_transaction_id
    ON transaction_history_projection (transaction_id);
