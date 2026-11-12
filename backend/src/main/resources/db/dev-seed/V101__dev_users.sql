-- V101: DEVELOPMENT-ONLY seed users. This migration lives in db/dev-seed, which is on
-- the Flyway locations list ONLY in the `dev` and `test` profiles. A launch without
-- those profiles never creates these users: with no users at all, login is impossible,
-- so the service fails closed instead of shipping a known password.
--
-- The contract defines no user-management endpoint and no user seed, yet a fresh
-- checkout must be runnable locally. These two users (password "demo", BCrypt
-- strength 12) mirror the frontend demo accounts and exist for same-laptop review
-- only. A real deployment provisions its first supervisor out of band (see README:
-- "Provisioning the first supervisor").

insert into users (id, username, password_hash, role) values
    ('11111111-1111-4111-8111-111111111111', 'keeper1', '$2b$12$yKROetkUYF4OttG16O.5Z.qFdk2.Uop8MIJXLGOVDbD2ToBxz0HXy', 'storekeeper'),
    ('22222222-2222-4222-8222-222222222222', 'boss1', '$2b$12$uTir2xnzprieV4RDsLsunO0b6SgYb8EcZRsvTeHUMCy.AOM/jLRya', 'supervisor');
