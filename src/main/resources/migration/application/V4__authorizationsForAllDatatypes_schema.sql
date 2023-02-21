--move authorization by datatype in single authorization with authorizations are by datatypes

UPDATE ${applicationSchema}.oresiauthorization
SET "authorizations"=jsonb_build_object(datatype, "authorizations");

ALTER TABLE IF EXISTS ${applicationSchema}.oresiauthorization
    DROP COLUMN IF EXISTS "datatype";

ALTER TABLE ${applicationSchema}.binaryfile
ADD COLUMN datatype text COLLATE pg_catalog."default" GENERATED ALWAYS AS ((params #> '{binaryfiledataset,datatype}'::text[])) STORED;