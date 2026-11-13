-- Per-number OTP limit and delivery failures (NFR-28, NFR-37). Additive only (NFR-70).

-- Set when the code could not be delivered: the challenge can never be verified but still counts toward the limit.
ALTER TABLE identity.otp_challenge ADD COLUMN delivery_failed_at timestamptz;

-- Supports counting a number's recent challenges (including failed ones) for the limit
CREATE INDEX otp_challenge_by_target_created ON identity.otp_challenge (target_hash, created_at);
