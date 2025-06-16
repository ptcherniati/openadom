CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "ltree";
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;
-- CREATE EXTENSION IF NOT EXISTS "jsonb";

CREATE TYPE nodecontext AS
    (
    datatype text,
    reference text,
    treetype text,
    exportheader_fr text,
    exportheader_en text
    );
CREATE TYPE node AS
    (
    context nodecontext,
    node_key ltree,
    node_type text,
    node_nk ltree,
    parent_type text,
    parent_nk ltree,
    node jsonb
    );

CREATE OR REPLACE FUNCTION fk_check(targetTable TEXT, uid UUID)
    RETURNS BOOLEAN AS
$$
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

CREATE OR REPLACE FUNCTION public.jsonb_count_items(p_json jsonb)
    RETURNS bigint
    LANGUAGE 'sql'
    VOLATILE
    PARALLEL UNSAFE
    COST 100
AS
$BODY$
with elements as (select p_json -> jsonb_object_keys(p_json) element) -- <<-- CORRECTION ICI : 'json' remplacé par 'p_json'
select sum(jsonb_array_length(element))
from elements
$BODY$;

/*-- check les foreign key pour le colonne reference de la table data
CREATE OR REPLACE FUNCTION refs_check(aSchema text, application UUID, refValues UUID[])
RETURNS BOOLEAN AS $$
DECLARE
    result TEXT;
BEGIN
    EXECUTE 'select count(id) = array_length($2, 1) from ' || aSchema || '.ReferenceValue where application=$1 AND id = ANY ($2);' ||
            '' INTO result USING application, refValues;
    RETURN result;
END;
$$ language 'plpgsql';*/

--check if all elements of oreSiUser array are users
CREATE OR REPLACE FUNCTION checks_users(users uuid[])
    RETURNS BOOLEAN AS
$$
DECLARE
    checked BOOLEAN;
BEGIN
    select users <@ array_agg(id)::uuid[] into checked from OreSiUser OSU group by users;
    return checked;
END;
$$ LANGUAGE plpgsql;



CREATE OR REPLACE FUNCTION name_check(application UUID, targetColumn TEXT, val TEXT)
    RETURNS BOOLEAN AS
$$
DECLARE
    result TEXT;
BEGIN
    EXECUTE format('select count(id) > 0 from Application where id=$1 AND $2 = ANY (%s);',
                   targetColumn) INTO result USING application, val;
    RETURN result;
END;
$$ language 'plpgsql';

create domain EntityId as uuid NOT NULL DEFAULT gen_random_uuid();
create domain PatternColumnName as text NOT NULL DEFAULT '';
create domain EntityRef as uuid NOT NULL;
create domain ListEntityRef as uuid[] NOT NULL;
create domain DateOrNow as timestamp DEFAULT current_timestamp;
CREATE TYPE LineIdentityPatternColumnName AS (id ltree, patternColumnName PatternColumnName);



-- creation de l'enumération des états des comptes
DROP TYPE IF EXISTS account_state;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'account_state') THEN
        CREATE TYPE account_state AS ENUM ('idle', 'active', 'pending', 'closed');
    END IF;
    --more types here...
END$$;
-- creation d'un type email
DROP EXTENSION IF EXISTs citext;
CREATE EXTENSION citext;
DROP DOMAIN IF EXISTs domain_email;
CREATE DOMAIN domain_email AS citext
    CHECK(
            VALUE ~ '^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$'
        );

-- Create table OreSiUser
create table OreSiUser
(
    id             EntityId PRIMARY KEY,
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    login          Text UNIQUE NOT NULL,
    password       text        NOT NULL,-- can be null
    authorizations TEXT[] default '{}',
    accountstate account_state default 'idle' NOT NULL,
    email domain_email default gen_random_uuid()::text||'@default.com' NOT NULL UNIQUE,
    chartes jsonb NOT NULL DEFAULT '{}'::jsonb
);
GRANT SELECT on OreSiUser to public;

-- ajout d'un utilisateur public
create role "${publicRoleId}";
INSERT INTO public.oresiuser(id, login, password, authorizations) VALUES ('${publicRoleId}', '_public_', '','{}');

-- ajout d'un role anonymous
DROP ROLE IF EXISTS "anonymous";
CREATE ROLE "anonymous";

-- ajout d'un role openAdomAdmin
DROP ROLE IF EXISTS "openAdomAdmin";
CREATE ROLE "openAdomAdmin" WITH CREATEROLE;

-- ajout d'un role applicationCreator
CREATE ROLE "applicationCreator";

-- Create table Application
create table Application
(
    id            EntityId PRIMARY KEY,
    creator       name default current_user,
    creationDate  DateOrNow,
    updateDate    DateOrNow,
    name          Text,
    data TEXT[], -- liste des types de data existants
    additionalFiles text[],
    configuration jsonb,  -- le fichier de configuration sous forme json
    configFile    uuid CHECK (fk_check(name || '.BinaryFile', configFile)),
    version varchar NOT NULL GENERATED ALWAYS AS ((configuration->'applicationdescription'->'version'->>'version')::varchar) STORED
);

CREATE INDEX application_data_gin_idx ON application USING gin (data);

-- grant on application
GRANT ALL PRIVILEGES ON Application TO "openAdomAdmin" WITH GRANT OPTION;
GRANT INSERT, UPDATE ON Application TO "applicationCreator";
GRANT SELECT  ON Application  TO public ;
GRANT SELECT , UPDATE , DELETE ON OreSiUser TO "openAdomAdmin", "applicationCreator";
GRANT SELECT, UPDATE, DELETE, REFERENCES ON Application TO "applicationCreator","openAdomAdmin";

-- policies on Application
ALTER TABLE Application
    ENABLE ROW LEVEL SECURITY;
CREATE POLICY "openAdomAdmin_Application_insert"
    ON Application AS PERMISSIVE
    TO "openAdomAdmin"
    using (true)
    with check (true);

-- aggregate array
CREATE AGGREGATE jsonb_object_agg(jsonb) (SFUNC = "jsonb_concat", STYPE = jsonb, INITCOND = '{}');
CREATE AGGREGATE aggregate_by_array_concatenation(anycompatiblearray) (SFUNC = "array_cat", STYPE = anycompatiblearray, INITCOND = '{}');

-- type and functions composite_date
create type COMPOSITE_DATE as
(
    datetimestamp "timestamp",
    formattedDate "varchar"
);
CREATE FUNCTION castTextToCompositeDate(Text) RETURNS COMPOSITE_DATE AS
'select (substring($1 from 6 for 19)::timestamp,
         substring($1 from 26))::COMPOSITE_DATE;'
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT;
CREATE CAST (TEXT AS COMPOSITE_DATE) WITH FUNCTION castTextToCompositeDate(Text) AS ASSIGNMENT;
CREATE FUNCTION castCompositeDateToTimestamp(COMPOSITE_DATE) RETURNS TIMESTAMP
AS
'select ($1).datetimestamp;'
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT;
CREATE CAST (COMPOSITE_DATE AS TIMESTAMP) WITH FUNCTION castCompositeDateToTimestamp(COMPOSITE_DATE) AS ASSIGNMENT;

DROP CAST IF EXISTS (COMPOSITE_DATE AS Text);
DROP FUNCTION IF EXISTS castCompositeDateToFormattedDate(COMPOSITE_DATE);

CREATE FUNCTION castCompositeDateToFormattedDate(COMPOSITE_DATE) RETURNS Text
AS
'select to_char(($1)::timestamp,
($1).formattedDate::text);'
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT;
CREATE CAST (COMPOSITE_DATE AS Text) WITH FUNCTION castCompositeDateToFormattedDate(COMPOSITE_DATE) AS ASSIGNMENT;