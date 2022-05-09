CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "ltree";

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

CREATE OR REPLACE FUNCTION public.jsonb_count_items(IN json jsonb)
    RETURNS bigint
    LANGUAGE 'sql'
    VOLATILE
    PARALLEL UNSAFE
    COST 100
AS
$BODY$
with elements as (select json -> jsonb_object_keys(json) element)
select sum(jsonb_array_length(element))
from elements
$BODY$;

/*-- check les foreign key pour le colonne references de la table data
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
create domain EntityRef as uuid NOT NULL;
create domain ListEntityRef as uuid[] NOT NULL;
create domain DateOrNow as timestamp DEFAULT current_timestamp;

create table OreSiUser
(
    id             EntityId PRIMARY KEY,
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    login          Text UNIQUE NOT NULL,
    password       text        NOT NULL,-- can be null
    authorizations TEXT[]
);

create table Application
(
    id            EntityId PRIMARY KEY,
    creator       name default current_user,
    creationDate  DateOrNow,
    updateDate    DateOrNow,
    name          Text,
    comment       TEXT NOT NULL,
    referenceType TEXT[], -- liste des types de references existantes
    dataType      TEXT[], -- liste des types de data existants
    configuration jsonb,  -- le fichier de configuration sous forme json
    configFile    uuid CHECK (fk_check(name || '.BinaryFile', configFile))
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

GRANT SELECT  ON Application  TO public ;

GRANT SELECT , UPDATE , DELETE ON OreSiUser TO "superadmin", "applicationCreator";

GRANT SELECT, UPDATE, DELETE, REFERENCES ON Application TO "applicationCreator",superadmin;

ALTER TABLE Application
    ENABLE ROW LEVEL SECURITY;
CREATE POLICY "superadmin_Application_insert"
    ON Application AS PERMISSIVE
    TO superadmin
    using (true)
    with check (true);


CREATE AGGREGATE jsonb_object_agg(jsonb) (SFUNC = 'jsonb_concat', STYPE = jsonb, INITCOND = '{}');
CREATE AGGREGATE aggregate_by_array_concatenation(anyarray) (SFUNC = 'array_cat', STYPE = anyarray, INITCOND = '{}');

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
CREATE FUNCTION castCompositeDateToFormattedDate(COMPOSITE_DATE) RETURNS Text
AS
'select ($1).formattedDate;'
    LANGUAGE SQL
    IMMUTABLE
    RETURNS NULL ON NULL INPUT;
CREATE CAST (COMPOSITE_DATE AS Text) WITH FUNCTION castCompositeDateToFormattedDate(COMPOSITE_DATE) AS ASSIGNMENT;