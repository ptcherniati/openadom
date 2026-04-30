-- =====================================================================
-- cascade 1.7.0 : SHARED_UNLOGGED staging table
-- =====================================================================
--
-- Used by the cascade StagingPostgresSink with strategy SHARED_UNLOGGED .
-- Multiple parallel sink workers COPY into this table , each tagging its
-- rows with the workflow correlation_id . The cascade FinalizeHook moves
-- the rows from this table to the application target tables and cleans
-- them up at the end of the workflow .
--
-- UNLOGGED : the table writes no WAL ( safer for crash : data is lost
-- but only the staging set , which is rebuilt at the next import ) and
-- accepts faster bulk-COPY .
--
-- Created in the public schema so it is shared across all application
-- schemas ; rows are tagged by correlation_id ( UUID ) which already
-- isolates concurrent imports across applications .
-- =====================================================================

CREATE UNLOGGED TABLE IF NOT EXISTS public.referencevalue_import_shared (
    correlation_id uuid        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    data           jsonb       NOT NULL
);

CREATE INDEX IF NOT EXISTS referencevalue_import_shared_corrid_idx
    ON public.referencevalue_import_shared (correlation_id);

CREATE INDEX IF NOT EXISTS referencevalue_import_shared_created_at_idx
    ON public.referencevalue_import_shared (created_at);

-- Allow application tech user to read / write / cleanup .
GRANT SELECT, INSERT, DELETE ON public.referencevalue_import_shared TO PUBLIC;

COMMENT ON TABLE public.referencevalue_import_shared IS
    'cascade 1.7.0 staging table for SHARED_UNLOGGED strategy . Rows are tagged with the workflow correlation_id . Cleaned up by the StagingPostgresSink finalize hook + orphan sweep ( default 60 min TTL ) .';
