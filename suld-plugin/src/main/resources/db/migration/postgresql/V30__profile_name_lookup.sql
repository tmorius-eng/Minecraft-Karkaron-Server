-- SÜLD schema V30 (PostgreSQL): find a profile by its name with the case ignored (docs/AUTHENTICATION.md, hybrid
-- mode). Behind AuthMe every account has the offline UUID of its name, so "Dorj" and "dorj" are two UUIDs; the login
-- gate refuses a name that another profile already holds in any case. The query is WHERE LOWER(name) = LOWER(?).
CREATE INDEX IF NOT EXISTS idx_profiles_name_lower ON suld_profiles (lower(name));
