create table BinaryFile
(
    id           EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate   DateOrNow,
    application  EntityRef REFERENCES Application (id),
    name         Text,
    comment      TEXT NOT NULL,
    size         INT,
    data         bytea,
    params       jsonb
);
CREATE INDEX binary_file_params_index ON BinaryFile USING gin (params);

create table ReferenceValue
(
    id                    EntityId PRIMARY KEY,
    creationDate          DateOrNow,
    updateDate            DateOrNow,
    application           EntityRef REFERENCES Application (id),
    referenceType         TEXT CHECK (name_check(application, 'referenceType', referenceType)),
    hierarchicalKey       ltree NOT NULL,
    hierarchicalReference ltree NOT NULL,
    naturalKey            ltree NOT NULL,
    refsLinkedTo          jsonb ,
    refValues             jsonb,
    binaryFile            EntityRef REFERENCES BinaryFile (id),

    CONSTRAINT "hierarchicalKey_uniqueness" UNIQUE (application, referenceType, hierarchicalKey)
);
create table Reference_Reference
(
    referenceId entityid REFERENCES ReferenceValue(id) ON DELETE CASCADE,
    referencesBy entityid REFERENCES ReferenceValue(id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT "Reference_Reference_PK" PRIMARY KEY (referenceId, referencesBy)
);

CREATE INDEX ref_refslinkedto_index ON ReferenceValue USING gin (refsLinkedTo);
CREATE INDEX ref_refvalues_index ON ReferenceValue USING gin (refValues);


--CREATE INDEX referenceType_columnDataMapping_hash_idx ON ReferenceValue USING HASH (columnDataMapping);
CREATE INDEX referenceType_refValue_gin_idx ON ReferenceValue USING gin (refValues);

CREATE TYPE ${applicationSchema}.requiredAuthorizations AS
(
    ${requiredAuthorizations}
);
CREATE TYPE ${applicationSchema}."authorization" AS
(
    requiredAuthorizations ${applicationSchema}.requiredAuthorizations,
    datagroups              text[],
    timescope              tsrange
);

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
                  ((("authorized").datagroups = array []::TEXT[]) or
                   ((authorized).datagroups @> COALESCE(("authorization").datagroups, array []::TEXT[])))
                      and ((("authorized").timescope = '(,)'::tsrange) or
                           (authorized).timescope @> COALESCE(("authorization").timescope, '[,]'::tsrange))
               );
    return result;
END;
$$ language 'plpgsql';

CREATE OPERATOR public.@> (
    LEFTARG = ${applicationSchema}."authorization",
    RIGHTARG = ${applicationSchema}."authorization"[],
    FUNCTION = ${applicationSchema}.isAuthorized
    );

create table Data
(
    id              EntityId PRIMARY KEY,
    creationDate    DateOrNow,
    updateDate      DateOrNow,
    application     EntityRef REFERENCES Application (id),
    dataType        TEXT
        constraint name_check CHECK (name_check(application, 'dataType', dataType)),
    rowId           TEXT                                                             NOT NULL,
    datagroup       TEXT GENERATED ALWAYS AS (("authorization").datagroups[1]) STORED NOT NULL,
    "authorization" ${applicationSchema}.authorization    NOT NULL check (("authorization").datagroups[1] is not null),
    refsLinkedTo    jsonb ,
    uniqueness      jsonb,
    dataValues      jsonb,
    binaryFile      EntityRef REFERENCES BinaryFile (id),
    constraint refs_check_for_datatype_uniqueness unique (dataType, datagroup, uniqueness)
);

create table Data_Reference
(
    dataId entityid REFERENCES Data(id) ON DELETE CASCADE,
    referencesBy entityid REFERENCES ReferenceValue(id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT "Data_Reference_PK" PRIMARY KEY (dataId, referencesBy)
);

CREATE INDEX data_refslinkedto_index ON Data USING gin (refsLinkedTo jsonb_path_ops);
CREATE INDEX data_refvalues_index ON Data USING gin (dataValues jsonb_path_ops);

ALTER TABLE Data
    ADD CONSTRAINT row_uniqueness UNIQUE (rowId, datagroup);

CREATE TABLE OreSiAuthorization
(
    id             EntityId PRIMARY KEY,
    name           Text,
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    oreSiUsers     EntityRef[] CHECK ( checks_users(oreSiUsers::uuid[]) ),
    application    EntityRef REFERENCES Application (id),
    dataType       TEXT CHECK (name_check(application, 'dataType', dataType)),
    authorizations jsonb
);

CREATE TABLE oresisynthesis
(
    id entityid NOT NULL,
    updatedate dateornow,
    application entityref,
    datatype text COLLATE pg_catalog."default",
    variable text COLLATE pg_catalog."default",
    requiredAuthorizations ${applicationSchema}.requiredAuthorizations,
    aggregation text COLLATE pg_catalog."default",
    ranges tsrange[],
    CONSTRAINT oresisynthesis_pkey PRIMARY KEY (id),
    CONSTRAINT synthesis_uk UNIQUE (application, datatype, variable, requiredAuthorizations, aggregation)
);
CREATE INDEX by_datatype_index ON oresisynthesis(application, aggregation,  datatype);
CREATE INDEX by_datatype_variable_index ON oresisynthesis (application, aggregation, datatype, variable);

GRANT ALL PRIVILEGES ON BinaryFile TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON ReferenceValue TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON Reference_Reference TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON Data TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON Data_Reference TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON OreSiAuthorization TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON OreSiSynthesis TO "superadmin" WITH GRANT OPTION;

GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON BinaryFile TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ReferenceValue TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON Reference_Reference TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON Data TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON Data_Reference TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON OreSiAuthorization TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON OreSiSynthesis TO public;


--ALTER TABLE BinaryFile ENABLE ROW LEVEL SECURITY;
--ALTER TABLE ReferenceValue ENABLE ROW LEVEL SECURITY;
ALTER TABLE Data
    ENABLE ROW LEVEL SECURITY;