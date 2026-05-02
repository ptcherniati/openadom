-- =====================================================================
-- Schema oa_staging : tables techniques d'import transitoires
-- =====================================================================
--
-- Schema dedie aux tables de staging cascade ( strategie SHARED_UNLOGGED ) .
-- Donnees ephemeres , jamais lues directement par l'utilisateur final ,
-- nettoyees par :
--   ( 1 ) le finalize hook a la fin de chaque workflow ,
--   ( 2 ) le sweeper orphan cascade au demarrage du prochain workflow
--         ( default 60 min TTL , configurable ) .
--
-- Separe de public.* pour clarte architecturale et permissions granulaires .
--
-- =====================================================================
-- Securite : pourquoi GRANT a PUBLIC ici ( vs SECURITY DEFINER pour oa_audit )
-- =====================================================================
--
-- Les operations cascade SHARED_UNLOGGED utilisent COPY FROM bulk .
-- COPY est un statement top-level , il ne peut pas etre encapsule dans
-- une fonction PL/pgSQL ( contrairement aux INSERT/UPDATE/DELETE de
-- oa_audit qui sont wrappes en SECURITY DEFINER ) . Imposer une fonction
-- wrapper sur le COPY casserait le chemin perf-critique du sink ( tens
-- of thousands of rows/sec via le COPY protocol natif Postgres ) .
--
-- Risques mitiges par le design :
--   * isolation par {@code correlation_id} : chaque ligne porte le UUID
--     du workflow d'import , les FinalizeHook + sweeper filtrent dessus
--     -> les imports concurrents ne se voient pas .
--   * UNLOGGED : pas de WAL , perte data acceptable au crash , la table
--     est de toute facon transitoire .
--   * pas de business data : les rows sont des fragments JSON intermediaires
--     transformes vers public.referencevalue puis purges .
--   * aucune FK pointee : la corruption d'une row n'a pas d'effet bord .
--
-- TODO ( backlog ) : creer un role meta {@code oa_staging_writer} grante
-- a la creation de chaque SI via SchemaFlywayCallback , puis revoke
-- PUBLIC . Granulaire mais necessite refactor cote Java .
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS oa_staging;

-- =====================================================================
-- referencevalue_import_shared : staging UNLOGGED partagee
-- =====================================================================
--
-- Utilisee par cascade StagingPostgresSink avec strategie SHARED_UNLOGGED .
-- Plusieurs sink workers paralleles font COPY dedans , chacun tagguant ses
-- rows avec workflow correlation_id . Le FinalizeHook UPSERT staging vers
-- referencevalue final puis nettoie .
--
-- UNLOGGED : pas de WAL ( perte data acceptable au crash , rebuild au
-- prochain import ) , bulk-COPY plus rapide .
-- =====================================================================

CREATE UNLOGGED TABLE IF NOT EXISTS oa_staging.referencevalue_import_shared (
    correlation_id uuid        NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    data           jsonb       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rv_import_shared_corrid
    ON oa_staging.referencevalue_import_shared (correlation_id);

CREATE INDEX IF NOT EXISTS idx_rv_import_shared_created_at
    ON oa_staging.referencevalue_import_shared (created_at);

-- ---------- droits ------------------------------------------------------

-- USAGE schema ouvert a PUBLIC ( COPY a besoin de resoudre la table ) .
GRANT USAGE ON SCHEMA oa_staging TO PUBLIC;

-- DML staging ouvert a PUBLIC ( COPY non encapsulable en fonction ; cf.
-- justification securite en tete de fichier ) .
GRANT SELECT, INSERT, DELETE
    ON oa_staging.referencevalue_import_shared TO PUBLIC;

-- Privileges complets pour openAdomTechUser ( contexte sweeper / DBA ) .
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA oa_staging TO "openAdomTechUser";
ALTER DEFAULT PRIVILEGES IN SCHEMA oa_staging
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "openAdomTechUser";

COMMENT ON SCHEMA oa_staging IS
    'Tables techniques transitoires pour l''import cascade ( SHARED_UNLOGGED ) . '
    'Donnees ephemeres , jamais lues directement par l''utilisateur final . '
    'Cleanup : finalize hook + orphan sweep TTL ( default 60 min ) . '
    'GRANT PUBLIC justifie par COPY non encapsulable - cf en-tete migration .';

COMMENT ON TABLE oa_staging.referencevalue_import_shared IS
    'cascade SHARED_UNLOGGED staging . Rows tagged correlation_id , '
    'filtrees par workflow lors du UPSERT staging -> referencevalue final .';
