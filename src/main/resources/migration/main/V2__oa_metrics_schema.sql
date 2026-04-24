-- =========================================================================
-- Schema oa_metrics : historique d'observabilite des workflows
-- Phase 2 observabilite (issue #62)
-- =========================================================================
--
-- Ce schema est deliberement separe du metier pour refleter la nature
-- differente des donnees :
--   - metier ( schema public ) : entites durables , transactionnelles
--   - metrics ( schema oa_metrics ) : evenements d'observation , append-only ,
--     retention limitee , consultables par Grafana en complement de Prometheus
--
-- Le backend y insere en mode append ( une row par workflow finalise ) via
-- le WorkflowLogWriter async. La rotation est assuree par WorkflowLogRetentionTask
-- qui supprime les rows anterieures a N jours ( configurable , defaut 30 ).
--
-- Pas de foreign key vers les tables metier : les logs sont immuables et
-- survivent aux changements de dimension ( users supprimes, applications
-- renommees , etc. ). On stocke les identifiants denormalises.
-- =========================================================================
--
-- -------------------------------------------------------------------------
-- APPLICATION AUTOMATIQUE (installations fraiches)
-- -------------------------------------------------------------------------
-- Sur une base vierge, Flyway applique ce script automatiquement lors du
-- bootstrap initial ( MigrateService.migrateAll / catch BadSqlGrammarException ).
--
-- -------------------------------------------------------------------------
-- APPLICATION MANUELLE (bases de production deja existantes)
-- -------------------------------------------------------------------------
-- Sur une base existante ( V1 deja appliquee, schemas SI deja en place ),
-- Flyway ne rejoue pas les migrations main/ : il faut donc lancer ce script
-- manuellement une seule fois avant de redemarrer le backend avec la
-- fonctionnalite metrics activee. Le script est idempotent ( IF NOT EXISTS
-- partout ) : sans danger si relance par erreur.
--
--   docker exec -i <container-db> \
--     psql -U <db-user> -d <db-name> \
--     -f V2__oa_metrics_schema.sql
--
-- Exemple local :
--   docker exec -i oa-database-1 psql -U random-user -d ore-si \
--     < backend/src/main/resources/migration/main/V2__oa_metrics_schema.sql
--
-- Pour que Flyway reconnaisse ce script comme applique ( evite un conflit
-- lors d'un futur full-bootstrap depuis snapshot ), inserer egalement la
-- ligne correspondante dans public.flyway_schema_history :
--
--   INSERT INTO public.flyway_schema_history
--     (installed_rank, version, description, type, script, checksum,
--      installed_by, installed_on, execution_time, success)
--   SELECT
--     COALESCE(MAX(installed_rank), 0) + 1, '2', 'oa metrics schema',
--     'SQL', 'V2__oa_metrics_schema.sql', NULL,
--     current_user, now(), 0, true
--   FROM public.flyway_schema_history
--   WHERE NOT EXISTS (
--       SELECT 1 FROM public.flyway_schema_history WHERE version = '2'
--   );
-- =========================================================================

CREATE SCHEMA IF NOT EXISTS oa_metrics;

CREATE TABLE IF NOT EXISTS oa_metrics.workflow_log (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id      uuid        NOT NULL UNIQUE,
    workflow_type       varchar(32) NOT NULL,
        -- IMPORT , EXTRACT_ZIP , EXTRACT_CSV , EXTRACT_ADDITIONAL_FILES ,
        -- EXTRACT_CHARTE
    user_id             uuid        NOT NULL,
    user_login          varchar(128),
    application_name    varchar(256),
    data_type           varchar(256),
    resource_name       varchar(512),
        -- nom de fichier uploade ou nom de ressource demandee
    start_time          timestamptz NOT NULL DEFAULT now(),
    end_time            timestamptz,
    duration_ms         bigint,
    status              varchar(16) NOT NULL,
        -- IN_PROGRESS , COMPLETED , FAILED , CANCELLED , RATE_LIMITED
    records_processed   bigint      DEFAULT 0,
    records_failed      bigint      DEFAULT 0,
    chunks_processed    int         DEFAULT 0,
    progress_percentage numeric(5,2),
    bytes_total         bigint      DEFAULT 0,
    errors              jsonb,
        -- liste des messages d'erreur collectes pendant le workflow
    fatal_error         text,
        -- message + stack de l'exception fatale le cas echeant
    metadata            jsonb
        -- champ extensible : headers HTTP , parametres de query , etc.
);

CREATE INDEX IF NOT EXISTS idx_workflow_log_user          ON oa_metrics.workflow_log (user_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_log_application   ON oa_metrics.workflow_log (application_name, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_log_status        ON oa_metrics.workflow_log (status, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_log_type_time     ON oa_metrics.workflow_log (workflow_type, start_time DESC);

COMMENT ON SCHEMA oa_metrics IS
    'Observabilite des workflows openadom ( imports + extractions ). Append-only , rotation configurable.';

COMMENT ON TABLE oa_metrics.workflow_log IS
    'Une row par workflow finalise ( succes , echec , rejet rate-limit ). Consomme par le dashboard admin et Grafana.';
-- ── Droits applicatifs ─────────────────────────────────────────────────────
GRANT USAGE ON SCHEMA oa_metrics TO "openAdomTechUser";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA oa_metrics TO "openAdomTechUser";
ALTER DEFAULT PRIVILEGES IN SCHEMA oa_metrics
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "openAdomTechUser";
