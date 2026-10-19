-- SÜLD schema V31 (PostgreSQL): referrals, /urih (docs/REFERRAL.md).
--   * one row per invitee, written once: the inviter never changes.
--   * rewarded_at: the invitee reached the reward level and was paid; inviter_paid_at: the inviter was paid (when online,
--     else at their next login). Both are set by compare-and-set updates, so each side is paid once.
CREATE TABLE IF NOT EXISTS suld_referrals (
    invitee_uuid     UUID    NOT NULL PRIMARY KEY,
    inviter_uuid     UUID    NOT NULL,
    created_at       BIGINT  NOT NULL,
    rewarded_at      BIGINT,
    inviter_paid_at  BIGINT
);
CREATE INDEX IF NOT EXISTS ix_referrals_inviter ON suld_referrals (inviter_uuid, created_at);
