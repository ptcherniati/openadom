create table RightsRequest
(
    id              EntityId PRIMARY KEY,
    "user"           EntityRef REFERENCES oresiuser (id),
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    application     EntityRef REFERENCES Application (id),
    comment         TEXT NOT NULL,
    rightsRequestForm       jsonb,
    rightsRequest ${applicationSchema}.OreSiAuthorization[],
    setted boolean

);
--CREATE INDEX additional_binary_file_params_index ON AdditionalBinaryFile USING gin (params);
CREATE INDEX rightsRequest_info_index ON RightsRequest USING gin (rightsRequestForm);

GRANT ALL PRIVILEGES ON RightsRequest TO "superadmin" WITH GRANT OPTION;
GRANT USAGE ON SCHEMA ${applicationSchema} TO public;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON RightsRequest TO public;
ALTER TABLE RightsRequest
    ENABLE ROW LEVEL SECURITY;