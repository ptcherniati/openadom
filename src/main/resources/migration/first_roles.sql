INSERT INTO public.oresiuser (id, login, password,  authorizations, accountstate, email)
VALUES
       ('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uuid,'openadom', '$2a$12$eF88cLz.KdQDUvWR7ztaiOeN3fstIAqqrDFHLdem0Kq/we1bmxUM2', '{}', 'active', 'openadom@inrae.fr')
ON CONFLICT DO NOTHING;



-- Fonction pour créer un rôle et accorder des privilèges
CREATE OR REPLACE FUNCTION create_role_and_grant(role_id UUID, role_name TEXT)
RETURNS VOID AS $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = role_id::text) THEN
        EXECUTE format('CREATE ROLE "%s"', role_id);
EXECUTE format('COMMENT ON ROLE "%s" IS %L', role_id, role_name);
EXECUTE format('GRANT "openAdomAdmin" TO "%s" WITH INHERIT TRUE', role_id);
EXECUTE format('GRANT "%s" TO "openAdomTechUser" WITH INHERIT TRUE', role_id);
END IF;
END;
$$
LANGUAGE plpgsql;

-- Création des rôles et octroi des privilèges
SELECT create_role_and_grant('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uuid, 'openadom');

-- Suppression de la fonction temporaire
DROP FUNCTION create_role_and_grant(UUID, TEXT);

-- Creation des utilisateurs des tests
INSERT INTO public.oresiuser (id, login, password,  authorizations, accountstate, email)
VALUES
    ('2a0e54c8-c95f-4365-a30e-75848a23a911'::uuid,'acbbCreator', '$2a$12$qWTH86wW4zQlTt.vdgekU.GG6yLGS8CZEaLa.XJZ1vzRmZirI4gH6', '{}', 'active', 'acbbCreator@inrae.fr'),
    ('05372c0d-f070-47a1-957e-a49509b7ad86'::uuid,'acbbManager', '$2a$12$.9qAiypSVG0qrZzl4ABElOgGffMKSi0qyb0WWACbQI5UNaPbDkkfK', '{}', 'active', 'acbbManager@inrae.fr'),
    ('03c9fc7c-347d-43c3-83a4-01989e1e5f28'::uuid,'acbbUser', '$2a$12$7HlZr0DBH9imoxiExcAQgOK7mByFtvP.UXXc86MK20fHULYDgr8nK', '{}', 'active', 'acbbUser@inrae.fr'),
    ('ac02961b-fc42-4b71-b37f-5e9e6d7871de'::uuid,'acbbUserManager', '$2a$12$/4gcNwfXpUyE666twRn6veGZAjPilXClE15dc8trAquyz3ouBW9US', '{}', 'active', 'acbbUserManager@inrae.fr'),
    ('17d8702e-f005-4e5f-af8b-74ae16b9b0b7'::uuid,'otherUser', '$2a$12$6gBMdkKWJyHxXUIgdqtnAe5.ETd0ALAMZj0Qdn0O/i0DJ8u5aYipq', '{}', 'active', 'otherUser@inrae.fr')
    ON CONFLICT DO NOTHING;

CREATE OR REPLACE FUNCTION create_role_and_grant_for_test_user(role_id UUID, role_name TEXT)
RETURNS VOID AS $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = role_id::text) THEN
        EXECUTE format('CREATE ROLE "%s"', role_id);
EXECUTE format('COMMENT ON ROLE "%s" IS %L', role_id, role_name);
EXECUTE format('GRANT "%s" TO "openAdomTechUser" WITH INHERIT TRUE', role_id);
END IF;
END;
$$
LANGUAGE plpgsql;

SELECT create_role_and_grant_for_test_user('2a0e54c8-c95f-4365-a30e-75848a23a911'::uuid, 'acbbCreator');
SELECT create_role_and_grant_for_test_user('05372c0d-f070-47a1-957e-a49509b7ad86'::uuid, 'acbbManager');
SELECT create_role_and_grant_for_test_user('03c9fc7c-347d-43c3-83a4-01989e1e5f28'::uuid, 'acbbUser');
SELECT create_role_and_grant_for_test_user('ac02961b-fc42-4b71-b37f-5e9e6d7871de'::uuid, 'acbbUserManager');
SELECT create_role_and_grant_for_test_user('17d8702e-f005-4e5f-af8b-74ae16b9b0b7'::uuid, 'otherUser');
-- Suppression de la fonction temporaire
DROP FUNCTION create_role_and_grant_for_test_user(UUID, TEXT);