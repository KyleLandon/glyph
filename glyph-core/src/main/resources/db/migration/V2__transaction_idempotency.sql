-- Phase 3 economy support (GDD sections 19-20, 134).
-- Idempotency infrastructure: a transaction submitted twice with the same key
-- is rejected by this unique index inside the transfer's DB transaction.

ALTER TABLE transactions
    ADD COLUMN idempotency_key VARCHAR(64);

CREATE UNIQUE INDEX uq_transactions_idempotency
    ON transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- /baltop reads the largest balances constantly; index them.
CREATE INDEX idx_accounts_balance ON accounts (balance DESC)
    WHERE owner_type = 'PLAYER';
