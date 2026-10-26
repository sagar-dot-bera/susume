-- recommendation_sessions.user_id holds the tenant's end-user id (the externalUserId of the
-- recommendation request), not a dashboard account, so it must not reference users(id).
-- It is nullable because sessions can be generated without a user (e.g. trending).
ALTER TABLE recommendation_sessions DROP CONSTRAINT IF EXISTS recommendation_sessions_user_id_fkey;
ALTER TABLE recommendation_sessions ALTER COLUMN user_id DROP NOT NULL;
