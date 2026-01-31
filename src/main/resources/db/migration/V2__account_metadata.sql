DO $$
BEGIN
    CREATE TYPE account_type AS ENUM ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE');
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

ALTER TABLE accounts
    ADD COLUMN code VARCHAR(64),
    ADD COLUMN name VARCHAR(128),
    ADD COLUMN type account_type;

UPDATE accounts
SET code = COALESCE(code, 'ACC-' || replace(id::text, '-', '')),
    name = COALESCE(name, 'Account ' || id::text),
    type = COALESCE(type, 'ASSET');

ALTER TABLE accounts
    ALTER COLUMN code SET NOT NULL,
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN type SET NOT NULL;

ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_code UNIQUE (code);
