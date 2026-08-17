-- =====================================================
-- Muhabbet — V21: admin flag on users
-- =====================================================
--
-- The moderation review endpoints (GET /api/v1/moderation/reports/pending and
-- POST /api/v1/moderation/reports/{id}/resolve) are guarded by AuthenticatedUser.requireAdmin(),
-- which reads an "admin" claim from the access token. Nothing ever wrote that claim, because
-- there was nowhere to read the answer from: the users table had no admin column at all. So the
-- guard always threw, and every report a user filed sat in user_reports unread — a complaints
-- mechanism with no action path, which BTK Law 5651 obliges this service to have.
--
-- The same missing claim is why /actuator/metrics and /actuator/prometheus, gated on
-- hasRole("ADMIN") in SecurityConfig, could not be reached by anyone (#303).
--
-- Grant is manual and deliberately so — there is no self-service path to admin and no UI:
--     UPDATE users SET is_admin = TRUE WHERE phone_number = '+90XXXXXXXXXX';
-- The holder must then log in again (or let their access token refresh) before the claim appears
-- in a token. Revoking is the same statement with FALSE, and takes effect at the next refresh
-- rather than instantly — an access token already minted stays valid until it expires.
--
-- No index: admins are a handful of rows and nothing queries by this column.

ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.is_admin IS
    'Grants the moderation review endpoints and the admin-only actuator endpoints. Set by hand.';
