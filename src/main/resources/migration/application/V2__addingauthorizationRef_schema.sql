

CREATE TABLE OreSiAuthorizationReference
(
    id             EntityId PRIMARY KEY,
    name           Text,
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    oreSiUsers     EntityRef[] CHECK ( checks_users(oreSiUsers::uuid[]) ),
    application    EntityRef REFERENCES Application (id),
    "references"   jsonb
);
--ALTER TABLE ReferenceValue ENABLE ROW LEVEL SECURITY;
ALTER TABLE ReferenceValue
    ENABLE ROW LEVEL SECURITY;
GRANT ALL PRIVILEGES ON OreSiAuthorizationReference TO "superadmin" WITH GRANT OPTION;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON OreSiAuthorizationReference TO public;