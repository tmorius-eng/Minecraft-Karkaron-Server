-- SÜLD schema V30 (MySQL, and H2 in MySQL mode): find a profile by its name with the case ignored (docs/AUTHENTICATION.md,
-- hybrid mode). A plain index: H2 has no expression indexes, and MySQL's default collation compares names without
-- case already. The query is WHERE LOWER(name) = LOWER(?) on every database.
CREATE INDEX idx_profiles_name ON suld_profiles (name);
