-- SÜLD schema V31 (MySQL / MariaDB, H2 in MySQL mode): referrals, /urih (docs/REFERRAL.md).
--   * one row per invitee, written once: the inviter never changes.
--   * rewarded_at: the invitee reached the reward level and was paid; inviter_paid_at: the inviter was paid (when online,
--     else at their next login). Both are set by compare-and-set updates, so each side is paid once.
CREATE TABLE IF NOT EXISTS suld_referrals (
    invitee_uuid     CHAR(36)  NOT NULL PRIMARY KEY,
    inviter_uuid     CHAR(36)  NOT NULL,
    created_at       BIGINT    NOT NULL,
    rewarded_at      BIGINT    NULL,
    inviter_paid_at  BIGINT    NULL,
    KEY ix_referrals_inviter (inviter_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
