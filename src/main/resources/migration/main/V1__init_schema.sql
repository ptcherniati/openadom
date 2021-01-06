CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "chkpass";

CREATE OR REPLACE FUNCTION fk_check(targetTable TEXT, uid UUID)
RETURNS BOOLEAN AS $$
DECLARE
    result TEXT;
BEGIN
    IF uid is null THEN
        RETURN true;
    ELSE
        EXECUTE format('select count(id) > 0 from %s where id = $1;', targetTable) INTO result USING uid;
        RETURN result;
    END IF;
END;
$$ language 'plpgsql';

-- check les foreign key pour le colonne references de la table data
CREATE OR REPLACE FUNCTION refs_check(aSchema text, application UUID, refValues UUID[])
RETURNS BOOLEAN AS $$
DECLARE
    result TEXT;
BEGIN
    EXECUTE 'select count(id) = array_length($2, 1) from ' || aSchema || '.ReferenceValue where application=$1 AND id = ANY ($2);' INTO result USING application, refValues;
    RETURN result;
END;
$$ language 'plpgsql';

CREATE OR REPLACE FUNCTION name_check(application UUID, targetColumn TEXT, val TEXT)
RETURNS BOOLEAN AS $$
DECLARE
    result TEXT;
BEGIN
    EXECUTE format('select count(id) > 0 from Application where id=$1 AND $2 = ANY (%s);', targetColumn) INTO result USING application, val;
    RETURN result;
END;
$$ language 'plpgsql';

create domain EntityId as uuid NOT NULL DEFAULT gen_random_uuid();
create domain EntityRef as uuid NOT NULL;
create domain ListEntityRef as uuid[] NOT NULL;
create domain DateOrNow as timestamp DEFAULT current_timestamp;

create table OreSiUser (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    login Text,
    password chkpass
);

create table Application (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    name Text,
    referenceType TEXT[], -- liste des types de references existantes
    dataType TEXT[],      -- liste des types de data existants
    configuration jsonb,  -- le fichier de configuration sous forme json
    configFile uuid CHECK(fk_check(name || '.BinaryFile', configFile))-- can be null
);

CREATE INDEX application_referenceType_gin_idx ON application USING gin (referenceType);
CREATE INDEX application_dataType_gin_idx ON application USING gin (dataType);

DROP ROLE IF EXISTS "anonymous";
CREATE ROLE "anonymous";

DROP ROLE IF EXISTS "superadmin";
CREATE ROLE "superadmin" WITH CREATEROLE;

GRANT ALL PRIVILEGES ON Application TO "superadmin" WITH GRANT OPTION;

CREATE ROLE "applicationCreator";

GRANT INSERT, UPDATE ON Application TO "applicationCreator";

GRANT SELECT, UPDATE, DELETE, REFERENCES ON Application TO public;

ALTER TABLE Application ENABLE ROW LEVEL SECURITY;

CREATE POLICY "applicationCreator_Application_insert" ON Application AS PERMISSIVE
            FOR INSERT TO "applicationCreator"
            WITH CHECK ( true );

CREATE POLICY "applicationCreator_Application_select" ON Application AS PERMISSIVE
            FOR SELECT TO "applicationCreator"
            USING ( true );

CREATE AGGREGATE jsonb_object_agg(jsonb) (SFUNC = 'jsonb_concat', STYPE = jsonb, INITCOND = '{}');

-- creation d'un utilisateur de test qui a le droit de creer des applications
-- on passe superadmin pour simuler la creation via un appel rest
--SET ROLE "superadmin";
--
--INSERT INTO OreSiUser (id, login, password) values ('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uid, 'poussin', 'xxxxxxxx');
--DROP ROLE IF EXISTS "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
--CREATE ROLE "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
--GRANT "applicationCreator" TO "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
