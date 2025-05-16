INSERT INTO public.oresiuser (id, login, password,  authorizations, accountstate, email)
VALUES
       ('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uuid,'openadom', '$2a$12$eF88cLz.KdQDUvWR7ztaiOeN3fstIAqqrDFHLdem0Kq/we1bmxUM2', '{}', 'active', 'openadom@inrae.fr'),
       ('35157557-616a-46b8-aee3-487d7450ec23'::uuid,'philippe', '$2a$12$2RXZnc/w9K2rlevPIXUfNeXfTzQGD3GWCWh7Noe8aoFyF2935PRA2', '{.*}', 'active', 'philippe.tcherniatinsky@inrae.fr'),
       ('31bd5755-3433-49f1-856e-7e99f3aef5f4'::uuid,'dmaurice', '$2a$12$VLGZZglGbZfpAHK41.TRyOO7DwhGsXU7Ts2rJGPuxb7SDf.aCcGH6', '{.*}', 'active', 'damien.maurice@inrae.fr'),
       ('3477ed15-15b1-4fee-ad45-6f2a94a3d66f'::uuid,'gmonet', '$2a$12$zyuSWKjmf2ZViwyv7o7P.Ohsdhb73p9.QC067KFXlAkOF0IOsspFq', '{.*}', 'active', 'ghislaine.monet@inrae.fr'),
       ('73de78e0-c16c-4a55-a176-1a1feb0dd290'::uuid,'lvarloteaux', '$2a$12$hFi9JFLQ24BxT0Ht/NokmuaMTHp5oc7YOrg9TgpwSLjeAQBR2/qMy', '{.*}', 'active', 'lucile.varloteaux@inrae.fr'),
       ('45b08ce0-f83b-44b5-8d6a-ec55ebf62548'::uuid,'ryahiaoui', '$2a$12$C/CpmXGxN22OJXUqvXY/DuCzA7PUaS6YbVMPYIFOe7iLKs7Go6XoC', '{.*}', 'active', 'rachid.yahiaoui@inra.fr'),
       ('893dd0f7-a369-4ca2-af3b-21c11719d350'::uuid,'vkoyao', '$2a$12$.nC/NJlNI.eZnyLNN49CXerwwprIk/UHN9mYpQ0172zixwmZGiExS', '{.*}', 'active', 'vivianne.koyao-yayende@inrae.fr'),
       ('7f7dec88-9c0a-44ed-82fa-3ec0362b9889'::uuid, 'hboukir', '$2a$12$YVqXsLZ5FkD0MnIjskvJduUWO91BnLU7X188TVgOfiEZcFpXSyEXS', '{.*}', 'active', 'hakima.boukir@inrae.fr')
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
SELECT create_role_and_grant('35157557-616a-46b8-aee3-487d7450ec23'::uuid, 'Philippe');
SELECT create_role_and_grant('31bd5755-3433-49f1-856e-7e99f3aef5f4'::uuid, 'Damien');
SELECT create_role_and_grant('3477ed15-15b1-4fee-ad45-6f2a94a3d66f'::uuid, 'Ghislaine');
SELECT create_role_and_grant('73de78e0-c16c-4a55-a176-1a1feb0dd290'::uuid, 'Lucile');
SELECT create_role_and_grant('45b08ce0-f83b-44b5-8d6a-ec55ebf62548'::uuid, 'Rachid');
SELECT create_role_and_grant('893dd0f7-a369-4ca2-af3b-21c11719d350'::uuid, 'Viviane');
SELECT create_role_and_grant('7f7dec88-9c0a-44ed-82fa-3ec0362b9889'::uuid, 'Hackima');
SELECT create_role_and_grant('af63dfbd-54eb-4c11-8e3e-b5a00bb27a4f'::uuid, 'Amélie');

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