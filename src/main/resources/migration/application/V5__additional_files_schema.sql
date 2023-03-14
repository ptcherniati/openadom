/* définition des fichiers additionnels */


CREATE OR REPLACE FUNCTION ${applicationSchema}.isAuthorized("authorizations" ${applicationSchema}."authorization"[],
                                                             "authorizedArray" ${applicationSchema}."authorization"[])
    RETURNS BOOLEAN AS
$$
DECLARE
    result TEXT;
BEGIN
    select exists(select 1
                  into result
                  from unnest("authorizations") authorizations
                  where isAuthorized(authorizations, authorizedArray)
               );
    return result;
END;
$$ language 'plpgsql';

CREATE OPERATOR public.@> (
    LEFTARG = ${applicationSchema}."authorization"[],
    RIGHTARG = ${applicationSchema}."authorization"[],
    FUNCTION = ${applicationSchema}.isAuthorized
    );

create table AdditionalBinaryFile
(
    id              EntityId PRIMARY KEY,
    creationDate   DateOrNow,
    updateDate     DateOrNow,
    creationUser      EntityId REFERENCES public.OreSiUser (id),
    updateUser      EntityId REFERENCES public.OreSiUser (id),
    application     EntityRef REFERENCES Application (id),
    fileType            Text,
    fileName            Text,
    comment         TEXT NOT NULL,
    size            INT,
    data            bytea,
    fileinfos       jsonb,
    associates ${applicationSchema}.OreSiAuthorization[]
);
--CREATE INDEX additional_binary_file_params_index ON AdditionalBinaryFile USING gin (params);
CREATE INDEX additional_binary_file_info_index ON AdditionalBinaryFile USING gin (fileinfos);

GRANT ALL PRIVILEGES ON AdditionalBinaryFile TO "superadmin" WITH GRANT OPTION;
GRANT SELECT, INSERT, UPDATE, DELETE, REFERENCES ON AdditionalBinaryFile TO public;
ALTER TABLE AdditionalBinaryFile
    ENABLE ROW LEVEL SECURITY;