CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- check les foreign key pour le colonne references de la table data
CREATE OR REPLACE FUNCTION refs_check(application UUID, refValues UUID[])
RETURNS BOOLEAN AS $$
DECLARE
    result TEXT;
BEGIN
    EXECUTE 'select count(id) = array_length($2, 1) from ReferenceValue where application=$1 AND id = ANY ($2);' INTO result USING application, refValues;
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

create table BinaryFile (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    name Text,
    size INT,
    data bytea
);

create table Application (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    name Text,
    referenceType TEXT[], -- liste des types de references existantes
    dataType TEXT[],      -- liste des types de data existants
    configuration jsonb,  -- le fichier de configuration sous forme json
    configFile uuid REFERENCES BinaryFile(id) -- can be null
);

CREATE INDEX application_referenceType_gin_idx ON application USING gin (referenceType);
CREATE INDEX application_dataType_gin_idx ON application USING gin (dataType);

create table ReferenceValue (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    application EntityRef REFERENCES Application(id),
    referenceType TEXT CHECK(name_check(application, 'referenceType', referenceType)),
    refValues jsonb,
    binaryFile EntityRef REFERENCES BinaryFile(id)
);

--CREATE INDEX referenceType_columnDataMapping_hash_idx ON ReferenceValue USING HASH (columnDataMapping);
CREATE INDEX referenceType_refValue_gin_idx ON ReferenceValue USING gin (refValues);

create table Data (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    application EntityRef REFERENCES Application(id),
    dataType TEXT CHECK(name_check(application, 'dataType', dataType)),
    refsLinkedTo ListEntityRef CHECK(refs_check(application, refsLinkedTo)),
    dataValues jsonb,
    binaryFile EntityRef REFERENCES BinaryFile(id)
);
