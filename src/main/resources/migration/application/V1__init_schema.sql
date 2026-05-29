CREATE TYPE ${applicationSchema}.requiredAuthorizations AS
(
    ${requiredAuthorizations}
);
-- types for authorizationScope
CREATE TYPE ${applicationSchema}."authorization" AS
(
    requiredAuthorizations ${applicationSchema}.requiredAuthorizations,
    timescope              tsrange
);

CREATE OR REPLACE FUNCTION ${applicationSchema}.getAuthorization(params jsonb)
    RETURNS ${applicationSchema}.authorization AS
$$
DECLARE
    result TEXT;
BEGIN
    select (
            jsonb_populate_record(
                null::${applicationSchema}."requiredauthorizations",
                params::jsonb #> '{binaryfiledataset, requiredauthorizations}'
            ),
            tsrange(
                COALESCE(params #>> '{binaryfiledataset,from}', '-infinity')::date,
                COALESCE(params #>> '{binaryfiledataset,to}', 'infinity')::date)
               )::${applicationSchema}."authorization"
    into result;
    return case
               when result is null then null::${applicationSchema}."authorization"
               else result::${applicationSchema}.authorization end;
END;
$$ language 'plpgsql' IMMUTABLE;

CREATE OR REPLACE FUNCTION ${applicationSchema}.isAuthorized("authorization" ${applicationSchema}."authorization",
                                                             "authorizedArray" ${applicationSchema}."authorization"[])
    RETURNS BOOLEAN AS
$$
DECLARE
    result TEXT;
BEGIN
    select exists(select 1
                  into result
                  from unnest("authorizedArray") authorized
                  where ${requiredAuthorizationscomparing}
        ((("authorized").timescope = '(,)'::tsrange) or
         (authorized).timescope @> COALESCE(("authorization").timescope, '[,]'::tsrange)));
    return result;
END;
$$ language 'plpgsql';
CREATE OPERATOR public.@> (
    LEFTARG = ${applicationSchema}."authorization",
    RIGHTARG = ${applicationSchema}."authorization"[],
    FUNCTION = ${applicationSchema}.isAuthorized
    );
CREATE OR REPLACE FUNCTION ${applicationSchema}.isAuthorized("_authorizations" ${applicationSchema}."authorization"[],
                                                             "authorizedArray" ${applicationSchema}."authorization"[])
    RETURNS BOOLEAN AS
$$
DECLARE
    result TEXT;
BEGIN
    select exists(select 1
                  into result
                  from unnest("_authorizations") authorizations
                  where isAuthorized("_authorizations", authorizedArray));
    return result;
END;
$$ language 'plpgsql';
CREATE OPERATOR public.@> (
    LEFTARG = ${applicationSchema}."authorization"[],
    RIGHTARG = ${applicationSchema}."authorization"[],
    FUNCTION = ${applicationSchema}.isAuthorized
    );

-- create table BinaryFile
create table BinaryFile
(
    id              EntityId PRIMARY KEY,
    creationDate    DateOrNow,
    updateDate      DateOrNow,
    application     EntityRef REFERENCES Application (id),
    name            Text,
    comment         TEXT NOT NULL,
    size            INT,
    fileData        bytea,
    params          jsonb,
    data            text COLLATE pg_catalog."default" GENERATED ALWAYS AS ((params #> '{binaryfiledataset,data}'::text[])) STORED,
    "authorization" ${applicationSchema}."authorization" GENERATED ALWAYS AS (${applicationSchema}.getauthorization(params)) STORED
);
CREATE INDEX binary_file_params_index ON BinaryFile USING gin (params);

-- create table ReferenceValue
create table ReferenceValue
(
    id                                   EntityId PRIMARY KEY,
    patternColumnName                    PatternColumnName,
    creationDate                         DateOrNow,
    updateDate                           DateOrNow,
    application                          EntityRef REFERENCES Application (id),
    referenceType                        TEXT CHECK (name_check(application, 'data', referenceType)),
    hierarchicalKey                      ltree                                                                                                                          NOT NULL,
    naturalKey                           ltree                                                                                                                          NOT NULL,
    refsLinkedTo                         jsonb,
    refValues                            jsonb,
    binaryFile                           EntityRef REFERENCES BinaryFile (id),
    lineHierarchicalKeyPatternColumnName LineIdentityPatternColumnName GENERATED ALWAYS AS ((hierarchicalKey, patternColumnName)::LineIdentityPatternColumnName) STORED NOT NULL,
    lineNaturalKeyPatternColumnName      LineIdentityPatternColumnName GENERATED ALWAYS AS ((naturalKey, patternColumnName)::LineIdentityPatternColumnName) STORED      NOT NULL,
    "authorization"                      ${applicationSchema}.authorization, --default '("(,)",{},"(,)")' NOT NULL,


    CONSTRAINT "hierarchicalKey_uniqueness" UNIQUE (application, referenceType, hierarchicalKey, patternColumnName)
);
CREATE INDEX IF NOT EXISTS "nk_patternColumnNam_type"
    ON ReferenceValue USING btree
        (referencetype, naturalkey, patterncolumnname COLLATE pg_catalog."default" ASC NULLS LAST)
    WITH (deduplicate_items=True);

create table Reference_Reference
(
    referenceId  entityid REFERENCES ReferenceValue (id) ON DELETE CASCADE,
    referencesBy entityid REFERENCES ReferenceValue (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT "Reference_Reference_PK" PRIMARY KEY (referenceId, referencesBy)
);
CREATE INDEX IF NOT EXISTS ref_refslinkedto_index
    ON ReferenceValue USING gin (refsLinkedTo);
-- Note : l'ancien GIN statique global « referenceType_refValue_gin_idx »
-- ( ((referencetype)::jsonb, refvalues jsonb_path_ops) ) a ete supprime .
-- Il etait entierement redondant avec les GIN partiels par datatype crees
-- par AuthorizationIndex.createIndex() ( USING gin (refvalues jsonb_path_ops)
-- WHERE referencetype = '<dt>' ) , plus petits et plus selectifs , et il
-- neutralisait la differentiation des filtres ( cf.
-- documentations/features/ACCELERATED_FILTERS.md §3 ) . Les schemas existants
-- sont nettoyes par la migration V5 .

CREATE INDEX IF NOT EXISTS referencetype_idx
    ON referencevalue USING btree
        (referencetype COLLATE pg_catalog."default" ASC NULLS LAST)
    WITH (deduplicate_items=True)
    TABLESPACE pg_default;



/**
  This function allows you to retrieve the nodes necessary for constructing the 'authorization' and 'submission' menus.
    Treetype is the type of menu we want to create: 'authorization' or "submission".
 */
CREATE or replace FUNCTION ${applicationSchema}.getnodes(treetype text) RETURNS SETOF node AS $$
with recursive d(key, "data") as (
    select
        (jsonb_each("configuration" #>'{datadescription}')).*,
        "configuration"
    from application
    where name = '${applicationSchema}'
),
               exportheader as (
                   select
                       treetype,
                       key,
                       authorizationscope.component,
                       Coalesce(("configuration" #> array['i18n', 'data', key, 'components']  #>> array[authorizationscope.component,'exportheader', 'title', 'fr'] ),authorizationscope.component) exportheader_fr,
                       Coalesce(("configuration" #> array['i18n', 'data', key, 'components']  #>> array[authorizationscope.component,'exportheader', 'title', 'en'] ),authorizationscope.component) exportheader_en,
                        CASE
                        WHEN treetype = 'authorization' then authorizationscope.data
                        WHEN treetype = 'submission' then authorizationscope.reference
                        end  reference,
                       "configuration"
                   from
                       d,
                       jsonb_to_recordset(
                           CASE
                               WHEN treetype = 'authorization' THEN
                                    COALESCE (
                                        "configuration"#> array['datadescription', key, 'authorization', 'authorizationscope'],
                                        '[]'::jsonb
                                        )
                               WHEN treetype = 'submission' THEN
                                    CASE
                                        WHEN configuration #> Array['datadescription', key, 'submission', 'submissionscope'] @@ ('$.referencescopes == null')
                                        THEN '[]'::jsonb
                                        ELSE COALESCE("configuration"#> array['datadescription', key, 'submission', 'submissionscope', 'referencescopes'], '[]'::jsonb)
                                    END
                               end
                       ) as authorizationscope(component text, reference text, "data" text)
               ),
               nodes as (
                   select
                       (
                        (key, reference, treetype, exportheader_fr, exportheader_en)::nodecontext,
                        rv2.hierarchicalkey,
                        rv2.referencetype,
                        rv2.naturalkey,
                        (
                            case
                                when nlevel(rv2.hierarchicalkey)-1>0
                                    then ((regexp_match(subpath(rv2.hierarchicalkey, nlevel(rv2.hierarchicalkey)-2, 1
                                                        )::text, '([a-z0-9_]*)K(.*)'))[1])::ltree end)::text ,
                        case
                            when nlevel(rv2.hierarchicalkey)-1>0
                                then (
                                (
                                    regexp_match(
                                        subpath(rv2.hierarchicalkey, nlevel(rv2.hierarchicalkey)-2, 1)::text,
                                        '([a-z0-9_]*)K(.*)')
                                    )[2]
                                )::ltree end,
                        jsonb_build_object(
                            'nodetype', node[1],
                            'value', node[2],
                            'node_key', rv2.hierarchicalkey,
                            'node_NK', rv2.naturalkey,
                            'fr', coalesce(rv2.refvalues ->> '__display_fr',rv2.refvalues ->> '__display_default') ,
                            'en', coalesce(rv2.refvalues ->> '__display_en',rv2.refvalues ->> '__display_default')
                        )
                           )::node node
                   from
                       ${applicationSchema}.referencevalue rv,
                       exportheader
                           LEFT JOIN LATERAL unnest(regexp_split_to_array(hierarchicalkey::text, '\.'))
                           WITH ORDINALITY AS hk(hk) ON TRUE
                           LEFT JOIN LATERAL regexp_match(hk::text, '([a-z0-9_]*)K(.*)')
                           WITH ORDINALITY AS node(node) ON TRUE
                           LEFT JOIN (
                           select
                               hierarchicalkey, naturalkey, referencetype, refvalues
                           from ${applicationSchema}.referencevalue rv2
                       ) rv2 on rv2.naturalkey = node[2]::ltree
                           and rv2.referencetype = node[1]
                   where rv.referencetype = reference
                   order by rv.hierarchicalkey, index(rv.hierarchicalkey, hk::ltree)
               )
select distinct on((node).context, (node).node_type, (node).node_nk) * from nodes n1
order by (node).context, (node).node_type, (node).node_nk
$$ LANGUAGE SQL;

-- create table OreSiAuthorization
CREATE TABLE OreSiAuthorization
(
    id             EntityId PRIMARY KEY,
    name           Text NOT NULL ,
    description    Text NOT NULL ,
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    oreSiUsers     EntityRef[] CHECK ( checks_users(oreSiUsers::uuid[]) ),
    application    EntityRef REFERENCES Application (id),
    authorizations jsonb,
    CONSTRAINT "auth_unique_name" UNIQUE (name)
);

-- create table AdditionalBinaryFile
create table AdditionalBinaryFile
(
    id             EntityId PRIMARY KEY,
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    creationUser   EntityId REFERENCES public.OreSiUser (id),
    updateUser     EntityId REFERENCES public.OreSiUser (id),
    application    EntityRef REFERENCES Application (id),
    fileType       Text,
    fileName       Text,
    comment        TEXT NOT NULL,
    size           INT,
    data           bytea,
    fileinfos      jsonb,
    associates     ${applicationSchema}.OreSiAuthorization[],
    forapplication boolean default false
);
ALTER TABLE IF EXISTS additionalbinaryfile
    ADD CONSTRAINT "UKByFileTypeAndFileName" UNIQUE (filetype, filename);
--CREATE INDEX additional_binary_file_params_index ON AdditionalBinaryFile USING gin (params);
CREATE INDEX additional_binary_file_info_index ON AdditionalBinaryFile USING gin (fileinfos);
-- create table OreSiAuthorizationAdditionalFiles
CREATE TABLE OreSiAuthorizationAdditionalFiles
(
    id              EntityId PRIMARY KEY,
    name           Text NOT NULL ,
    description    Text NOT NULL ,
    creationDate    DateOrNow,
    updateDate      DateOrNow,
    oreSiUsers      EntityRef[] CHECK ( checks_users(oreSiUsers::uuid[]) ),
    application     EntityRef REFERENCES Application (id),
    additionalFiles jsonb,
    CONSTRAINT auth_additional_unique_name UNIQUE (name)
);

-- create table RightsRequest
create table RightsRequest
(
    id                EntityId PRIMARY KEY,
    "user"            EntityRef REFERENCES oresiuser (id),
    creationDate      DateOrNow,
    updateDate        DateOrNow,
    application       EntityRef REFERENCES Application (id),
    comment           TEXT NOT NULL,
    rightsRequestForm jsonb,
    rightsRequest     ${applicationSchema}.OreSiAuthorization,
    setted            boolean

);
--CREATE INDEX rightsRequest_info_index ON AdditionalBinaryFile USING gin (params);
CREATE INDEX rightsRequest_info_index ON RightsRequest USING gin (rightsRequestForm);

--ALTER TABLE ReferenceValue ENABLE ROW LEVEL SECURITY;
ALTER TABLE ReferenceValue
    ENABLE ROW LEVEL SECURITY;


-- create table OreSiSynthesis
CREATE TABLE oresisynthesis
(
    id                     entityid NOT NULL,
    updatedate             dateornow,
    application            entityref,
    datatype               text COLLATE pg_catalog."default",
    variable               text COLLATE pg_catalog."default",
    requiredAuthorizations ${applicationSchema}.requiredAuthorizations,
    aggregation            text COLLATE pg_catalog."default",
    ranges                 tsrange[],
    CONSTRAINT oresisynthesis_pkey PRIMARY KEY (id)--,
    --CONSTRAINT synthesis_uk UNIQUE (application, datatype, variable, requiredAuthorizations, aggregation)
);
CREATE INDEX by_datatype_index ON oresisynthesis (application, aggregation, datatype);
CREATE INDEX by_datatype_variable_index ON oresisynthesis (application, aggregation, datatype, variable);

GRANT ALL PRIVILEGES ON BinaryFile TO "openAdomAdmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON ReferenceValue TO "openAdomAdmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON Reference_Reference TO "openAdomAdmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON OreSiAuthorization TO "openAdomAdmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON OreSiSynthesis TO "openAdomAdmin" WITH GRANT OPTION;

GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON BinaryFile TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ReferenceValue TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON Reference_Reference TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON OreSiAuthorization TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON OreSiSynthesis TO public;

GRANT ALL PRIVILEGES ON RightsRequest TO "openAdomAdmin" WITH GRANT OPTION;
GRANT USAGE ON SCHEMA ${applicationSchema} TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON RightsRequest TO public;
GRANT ALL PRIVILEGES ON AdditionalBinaryFile TO "openAdomAdmin" WITH GRANT OPTION;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON AdditionalBinaryFile TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON OreSiAuthorizationAdditionalFiles TO public;
GRANT ALL PRIVILEGES ON OreSiAuthorizationAdditionalFiles TO "openAdomAdmin" WITH GRANT OPTION;

-- RLS
--ALTER TABLE BinaryFile ENABLE ROW LEVEL SECURITY;
ALTER TABLE BinaryFile
    ENABLE ROW LEVEL SECURITY;
--ALTER TABLE AdditionalBinaryFile ENABLE ROW LEVEL SECURITY;
ALTER TABLE AdditionalBinaryFile
    ENABLE ROW LEVEL SECURITY;
--ALTER TABLE ReferenceValue ENABLE ROW LEVEL SECURITY;
ALTER TABLE ReferenceValue
    ENABLE ROW LEVEL SECURITY;
--ALTER TABLE oresiauthorization ENABLE ROW LEVEL SECURITY;
ALTER TABLE oresiauthorization
    ENABLE ROW LEVEL SECURITY;
--ALTER TABLE Rightsrequest ENABLE ROW LEVEL SECURITY;
ALTER TABLE RightsRequest
    ENABLE ROW LEVEL SECURITY;
--ALTER TABLE ReferenceValue ENABLE ROW LEVEL SECURITY;
ALTER TABLE AdditionalBinaryFile
    ENABLE ROW LEVEL SECURITY;
CREATE POLICY allforcharte
    ON additionalbinaryfile
    AS PERMISSIVE
    FOR SELECT
    TO public
    USING ((filetype = '__charte__'::text));