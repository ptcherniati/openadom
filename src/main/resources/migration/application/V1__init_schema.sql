create table BinaryFile (
    id EntityId PRIMARY KEY,
    creationDate DateOrNow,
    updateDate DateOrNow,
    application EntityRef REFERENCES Application(id),
    name Text,
    size INT,
    data bytea
);

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
    timeScope tsrange NOT NULL,
    refsLinkedTo ListEntityRef CHECK(refs_check('${applicationSchema}', application, refsLinkedTo)),
    dataValues jsonb,
    binaryFile EntityRef REFERENCES BinaryFile(id)
);

GRANT ALL PRIVILEGES ON BinaryFile TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON ReferenceValue TO "superadmin" WITH GRANT OPTION;
GRANT ALL PRIVILEGES ON Data TO "superadmin" WITH GRANT OPTION;

GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON BinaryFile TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON ReferenceValue TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON Data TO public;

--ALTER TABLE BinaryFile ENABLE ROW LEVEL SECURITY;
--ALTER TABLE ReferenceValue ENABLE ROW LEVEL SECURITY;
ALTER TABLE Data ENABLE ROW LEVEL SECURITY;