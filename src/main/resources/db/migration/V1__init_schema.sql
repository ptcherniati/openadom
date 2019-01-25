CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- check les foreign key pour le colonne references de la table data
CREATE OR REPLACE FUNCTION fk_check(targetTable TEXT, uid UUID[])
RETURNS BOOLEAN AS $$
DECLARE
    result TEXT;
BEGIN
    -- TODO
    EXECUTE format('select count(id) > 0 from %s where id = $1;', targetTable) INTO result USING uid;
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
    config EntityRef REFERENCES BinaryFile(id)
);

create table ReferenceType (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    application EntityRef REFERENCES Application(id),
    description jsonb,
    binaryFile EntityRef REFERENCES BinaryFile(id)
);

create table ReferenceValue (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    referenceType EntityRef REFERENCES ReferenceType(id),
    label Text
);

create table Data (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    binaryFile EntityRef REFERENCES BinaryFile(id),
    refs ListEntityRef CHECK(fk_check('ReferenceValue', refs)),
    jsonData jsonb[]
);
