CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE account_status AS ENUM (
    'ACTIVE',
    'FROZEN'
);

CREATE TYPE entry_type AS ENUM (
    'DEBIT',
    'CREDIT'
);

CREATE TYPE transaction_status AS ENUM (
    'PENDING',
    'POSTED'
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    currency        CHAR(3) NOT NULL,
    status          account_status NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_currency_uppercase
        CHECK (currency = upper(currency))
);


CREATE TABLE transactions (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_key   VARCHAR(128) NOT NULL,
    status          transaction_status NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_transactions_reference
        UNIQUE (reference_key)
);

CREATE TABLE transaction_entries (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id  UUID NOT NULL,
    account_id      UUID NOT NULL,
    entry_type      entry_type NOT NULL,
    amount          NUMERIC(20,6) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_entries_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES transactions(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_entries_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_amount_positive
        CHECK (amount > 0)
);

CREATE INDEX idx_entries_account_created
    ON transaction_entries (account_id, created_at);

CREATE INDEX idx_entries_transaction
    ON transaction_entries (transaction_id);

CREATE INDEX idx_transactions_created
    ON transactions (created_at);

CREATE TABLE account_balances (
    account_id      UUID PRIMARY KEY,
    balance         NUMERIC(20,6) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_balance_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(id)
        ON DELETE RESTRICT
);

CREATE OR REPLACE FUNCTION enforce_double_entry_balance()
RETURNS TRIGGER AS $$
DECLARE
    debit_total  NUMERIC(20,6);
    credit_total NUMERIC(20,6);
BEGIN
    SELECT
        COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount END), 0),
        COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount END), 0)
    INTO debit_total, credit_total
    FROM transaction_entries
    WHERE transaction_id = NEW.transaction_id;

    IF debit_total <> credit_total THEN
        RAISE EXCEPTION
            'Double-entry violation: debit=% credit=%',
            debit_total, credit_total;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_enforce_double_entry
AFTER INSERT OR UPDATE ON transaction_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_double_entry_balance();

CREATE OR REPLACE FUNCTION forbid_ledger_update_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_no_update_ledger
BEFORE UPDATE OR DELETE ON transaction_entries
FOR EACH ROW
EXECUTE FUNCTION forbid_ledger_update_delete();
