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

-- =====================================================================
-- PER_WORKFLOW_TABLE : creation / drop de tables UNLOGGED dediees
-- =====================================================================
--
-- 1 table UNLOGGED par workflow : oa_staging.referencevalue_import_<corrid>
-- ( UUID-tirets-en-_ ) . Workers sink paralleles , isolation native ,
-- DROP TABLE atomique apres succes . Le sweeper orphan scanne pg_class
-- pour les tables orphelines apres TTL .
--
-- Probleme avec CREATE TABLE direct depuis l'app :
--   - GRANT USAGE ON SCHEMA oa_staging TO PUBLIC permet la lecture mais
--     pas CREATE TABLE . Les roles per-app ( utilisateurs metier )
--     n'ont pas le droit de creer des tables dans oa_staging .
--   - Si on grant CREATE TO PUBLIC , chaque role pourrait creer des
--     tables arbitraires ; mauvais pour la securite .
--   - Solution : 2 fonctions SECURITY DEFINER , owned by openAdomTechUser ,
--     EXECUTE granted to PUBLIC . L'app les appelle ; elles s'executent
--     avec les privileges du proprietaire ( openAdomTechUser ) qui peut
--     CREATE / DROP dans oa_staging , et grant le DML a PUBLIC sur la
--     table fraichement creee pour que le COPY de l'app passe .
-- =====================================================================

CREATE OR REPLACE FUNCTION oa_staging.create_per_workflow_referencevalue_import(p_corrid text)
    RETURNS text
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = oa_staging, pg_temp
AS $$
DECLARE
    v_table_name text := 'referencevalue_import_' || replace(p_corrid, '-', '_');
    v_full_name  text := 'oa_staging.' || quote_ident(v_table_name);
BEGIN
    EXECUTE format(
        'CREATE UNLOGGED TABLE IF NOT EXISTS %s ( '
        '   correlation_id uuid        NOT NULL , '
        '   created_at     timestamptz NOT NULL DEFAULT now() , '
        '   data           jsonb       NOT NULL '
        ')', v_full_name);
    EXECUTE format('GRANT SELECT , INSERT , DELETE ON %s TO PUBLIC', v_full_name);
    RETURN v_table_name;
END;
$$;

CREATE OR REPLACE FUNCTION oa_staging.drop_per_workflow_referencevalue_import(p_corrid text)
    RETURNS void
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = oa_staging, pg_temp
AS $$
DECLARE
    v_table_name text := 'referencevalue_import_' || replace(p_corrid, '-', '_');
    v_full_name  text := 'oa_staging.' || quote_ident(v_table_name);
BEGIN
    EXECUTE format('DROP TABLE IF EXISTS %s', v_full_name);
END;
$$;

-- Fonctions SECURITY DEFINER s'executent avec les droits du proprietaire ;
-- transferer la propriete a openAdomTechUser pour que CREATE / DROP dans
-- oa_staging soient autorises ( il a les droits CREATE sur le schema ) .
ALTER FUNCTION oa_staging.create_per_workflow_referencevalue_import(text)
    OWNER TO "openAdomTechUser";
ALTER FUNCTION oa_staging.drop_per_workflow_referencevalue_import(text)
    OWNER TO "openAdomTechUser";

GRANT EXECUTE ON FUNCTION oa_staging.create_per_workflow_referencevalue_import(text) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_staging.drop_per_workflow_referencevalue_import(text)   TO PUBLIC;

COMMENT ON FUNCTION oa_staging.create_per_workflow_referencevalue_import(text) IS
    'cascade PER_WORKFLOW_TABLE : cree une table UNLOGGED dediee au workflow . '
    'SECURITY DEFINER : appelable par les roles per-app sans grant CREATE direct .';
COMMENT ON FUNCTION oa_staging.drop_per_workflow_referencevalue_import(text) IS
    'cascade PER_WORKFLOW_TABLE : drop la table dediee apres succes du workflow . '
    'SECURITY DEFINER : appelable par les roles per-app sans grant DROP direct .';
