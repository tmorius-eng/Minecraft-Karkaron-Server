-- SÜLD schema V16: the credit-grant ledger (audit 2026-10-11 SEC-003). Every /credits give carries the store's
-- transaction id; the grant and the balance change commit together, so a retried or replayed store command is a no-op.
CREATE TABLE IF NOT EXISTS suld_credit_grants (
    txid            VARCHAR(64)  NOT NULL,
    player_uuid     CHAR(36)     NOT NULL,
    amount          BIGINT       NOT NULL,
    granted_at      BIGINT       NOT NULL,
    actor           VARCHAR(64)  NOT NULL,
    PRIMARY KEY (txid),
    KEY idx_credit_grants_player (player_uuid, granted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
