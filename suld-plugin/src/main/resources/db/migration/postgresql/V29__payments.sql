-- SÜLD schema V29 (PostgreSQL): the payment ledger (docs/PAYMENT_ARCHITECTURE.md §Data, directive Phase 7).
--   * suld_payment_intents: one row per purchase a player asked for. Only the provider's signed event moves it, by
--     compare-and-set on version; once confirmed, the provider's id for the payment is unique.
--   * suld_payment_events: every provider call received, append-only: the SHA-256 of the body (never the body), whether
--     its signature held, what it named and what was done with it.
-- The credits themselves are granted through suld_credit_grants (V16) under the intent's own transaction id.
CREATE TABLE IF NOT EXISTS suld_payment_intents (
    intent_id        UUID         PRIMARY KEY,
    player_uuid      UUID         NOT NULL,
    product          VARCHAR(32)  NOT NULL,
    credits          BIGINT       NOT NULL,
    amount           BIGINT       NOT NULL,
    currency         VARCHAR(8)   NOT NULL,
    provider         VARCHAR(16)  NOT NULL,
    state            VARCHAR(16)  NOT NULL,
    created_at       BIGINT       NOT NULL,
    expires_at       BIGINT       NOT NULL,
    invoice_id       VARCHAR(64),
    provider_txn_id  VARCHAR(64),
    confirmed_at     BIGINT,
    fulfilled_at     BIGINT,
    version          INTEGER      NOT NULL,
    CONSTRAINT uq_payment_provider_txn UNIQUE (provider, provider_txn_id),
    CONSTRAINT ck_payment_state CHECK (state IN ('PENDING', 'CONFIRMED', 'FAILED', 'EXPIRED', 'REFUNDED')),
    CONSTRAINT ck_payment_amount CHECK (credits > 0 AND amount > 0)
);
CREATE INDEX IF NOT EXISTS idx_payment_intents_player ON suld_payment_intents (player_uuid, created_at);
CREATE INDEX IF NOT EXISTS idx_payment_intents_state ON suld_payment_intents (state, expires_at);
CREATE TABLE IF NOT EXISTS suld_payment_events (
    id            BIGSERIAL    PRIMARY KEY,
    received_at   BIGINT       NOT NULL,
    provider      VARCHAR(16)  NOT NULL,
    body_sha256   CHAR(64)     NOT NULL,
    signature_ok  BOOLEAN      NOT NULL,
    reference     VARCHAR(64),
    invoice_id    VARCHAR(64),
    status        VARCHAR(64),
    outcome       VARCHAR(32)  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_payment_events_reference ON suld_payment_events (reference, received_at);
