DO $$
BEGIN
	BEGIN
		CREATE TYPE account_status_v3 AS ENUM ('ACTIVE', 'FROZEN', 'CLOSED');
	EXCEPTION
		WHEN duplicate_object THEN NULL;
	END;
END $$;

ALTER TABLE accounts
	ALTER COLUMN status DROP DEFAULT;

ALTER TABLE accounts
	ALTER COLUMN status TYPE account_status_v3
	USING status::text::account_status_v3;

DROP TYPE IF EXISTS account_status;

ALTER TYPE account_status_v3 RENAME TO account_status;

ALTER TABLE accounts
	ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS frozen_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS status_reason VARCHAR(256);

UPDATE accounts
SET status_changed_at = COALESCE(status_changed_at, created_at),
    status_reason = COALESCE(status_reason,
        CASE
            WHEN status = 'FROZEN' THEN 'MIGRATED_ACCOUNT_FROZEN'
            WHEN status = 'CLOSED' THEN 'MIGRATED_ACCOUNT_CLOSED'
            ELSE 'MIGRATED_ACCOUNT_OPENED'
        END);

UPDATE accounts
SET frozen_at = COALESCE(frozen_at, status_changed_at)
WHERE status = 'FROZEN';

UPDATE accounts
SET closed_at = COALESCE(closed_at, status_changed_at)
WHERE status = 'CLOSED';

ALTER TABLE accounts
    ALTER COLUMN status_changed_at SET NOT NULL,
    ALTER COLUMN status_changed_at SET DEFAULT now(),
    ALTER COLUMN status_reason SET NOT NULL,
    ALTER COLUMN status_reason SET DEFAULT 'ACCOUNT_OPENED';
