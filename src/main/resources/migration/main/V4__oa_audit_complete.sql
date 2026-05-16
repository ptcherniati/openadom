-- =================================================================
-- V4 : oa_audit consolidation + cancel-divergence safety
-- =================================================================
-- Migration consolidee qui remplace les anciennes V4 a V9
-- ( pre-prod / dev seulement ; aucun environnement utilise les
-- versions intermediaires de ces 6 patchs ) . Regroupe en une seule
-- unite de migration les evolutions du schema oa_audit livrees apres
-- V2/V3 et avant cette consolidation , plus le fix P0 cancel-divergence
-- (FOR UPDATE SKIP LOCKED sur mark_zombie_workflows) . Ce regroupement
-- :
--   * reduit le bruit Flyway au boot ( 1 fichier au lieu de 6 ) ;
--   * met le schema final dans un seul artefact relisible ;
--   * facilite la lecture de l'evolution lors d'un onboarding .
--
-- Ordre logique adopte ici :
--   1. ALTER TABLE final_count                           ( ex-V4 )
--   2. Replace record_workflow (ajout p_final_count)     ( ex-V5 )
--   3. Index composite (app, status, start_time DESC)    ( ex-V6 )
--   4. cancel_workflow ( SECURITY DEFINER , widened )    ( ex-V7+V8 )
--   5. Partial index ( fileId WHERE IN_PROGRESS )        ( ex-V9 )
--   6. Replace mark_zombie_workflows ( SKIP LOCKED )     ( P0 NEW )

-- -----------------------------------------------------------------
-- 1. workflow_log.final_count : compteur authoritatif post-UPSERT
-- -----------------------------------------------------------------
-- Avant cette colonne , chaque poll de IntegrityView ( /api/dashboard/
-- integrity/staging-vs-final ) ou de WorkflowFinalizeBadge ( /api/
-- dashboard/workflows/{cid}/finalize ) declenchait un SELECT COUNT(*)
-- FROM <app>.referencevalue WHERE binaryfile = ? . Avec ~74 binaryFiles
-- par schema et plusieurs onglets ouverts , ces COUNTs saturaient le
-- pool Hikari et entraient en contention LWLock:BufferMapping avec
-- l'UPSERT staging -> table finale .
-- Strategie : on persiste UN compteur authoritatif au markCompleted
-- ( post-afterCommit Phase B ) ; les vues lisent cette colonne .
-- NULL = workflow legacy ou COUNT failed lors de markCompleted ; dans
-- ces cas IntegrityService retombe sur un COUNT direct cache 5 min .

ALTER TABLE oa_audit.workflow_log
    ADD COLUMN IF NOT EXISTS final_count bigint;

COMMENT ON COLUMN oa_audit.workflow_log.final_count IS
    'Compteur authoritatif COUNT(*) FROM <app>.referencevalue WHERE binaryfile = ? '
    'capture au markCompleted ( post-afterCommit Phase B ) . NULL = legacy ou '
    'COUNT failed . Lu en priorite par IntegrityService et DashboardService.finalizeProgress .';

-- -----------------------------------------------------------------
-- 2. record_workflow : ajout p_final_count en dernier parametre
-- -----------------------------------------------------------------
-- Postgres n'accepte pas l'ajout de parametre via CREATE OR REPLACE
-- FUNCTION ; on DROP + CREATE en preservant le proprietaire .
-- Filtre WHERE status = 'IN_PROGRESS' preserve : protege contre les
-- ecritures late venant apres terminaison par cancel/watchdog . Cf
-- WorkflowLogWriter.recordEnd qui log ERROR si inserted=0 ( P0-3 ) .

DROP FUNCTION IF EXISTS oa_audit.record_workflow(
    uuid, varchar, uuid, varchar, varchar, varchar, varchar,
    timestamptz, timestamptz, bigint, varchar, bigint, bigint, int,
    bigint, jsonb, text, jsonb, varchar);

CREATE OR REPLACE FUNCTION oa_audit.record_workflow(
    p_correlation_id      uuid,
    p_workflow_type       varchar(32),
    p_user_id             uuid,
    p_user_login          varchar(128),
    p_application_name    varchar(256),
    p_data_type           varchar(256),
    p_resource_name       varchar(512),
    p_start_time          timestamptz,
    p_end_time            timestamptz,
    p_duration_ms         bigint,
    p_status              varchar(16),
    p_records_processed   bigint,
    p_records_failed      bigint,
    p_chunks_processed    int,
    p_bytes_total         bigint,
    p_errors              jsonb,
    p_fatal_error         text,
    p_metadata            jsonb,
    p_failed_stage        varchar(32),
    p_final_count         bigint
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE affected boolean;
BEGIN
    INSERT INTO oa_audit.workflow_log (
        correlation_id, workflow_type, user_id, user_login,
        application_name, data_type, resource_name,
        start_time, end_time, duration_ms, status,
        records_processed, records_failed, chunks_processed,
        bytes_total, errors, fatal_error, metadata, failed_stage,
        final_count
    ) VALUES (
        p_correlation_id, p_workflow_type, p_user_id, p_user_login,
        p_application_name, p_data_type, p_resource_name,
        p_start_time, p_end_time, p_duration_ms, p_status,
        p_records_processed, p_records_failed, p_chunks_processed,
        p_bytes_total, p_errors, p_fatal_error, p_metadata, p_failed_stage,
        p_final_count
    )
    ON CONFLICT (correlation_id) DO UPDATE
    SET end_time          = EXCLUDED.end_time,
        duration_ms       = EXCLUDED.duration_ms,
        status            = EXCLUDED.status,
        records_processed = EXCLUDED.records_processed,
        records_failed    = EXCLUDED.records_failed,
        chunks_processed  = EXCLUDED.chunks_processed,
        bytes_total       = EXCLUDED.bytes_total,
        errors            = EXCLUDED.errors,
        fatal_error       = EXCLUDED.fatal_error,
        metadata          = COALESCE(EXCLUDED.metadata, oa_audit.workflow_log.metadata),
        failed_stage      = EXCLUDED.failed_stage,
        final_count       = COALESCE(EXCLUDED.final_count, oa_audit.workflow_log.final_count)
    WHERE oa_audit.workflow_log.status = 'IN_PROGRESS';
    GET DIAGNOSTICS affected = ROW_COUNT;
    RETURN affected;
END;
$$;

ALTER FUNCTION oa_audit.record_workflow(
    uuid, varchar, uuid, varchar, varchar, varchar, varchar,
    timestamptz, timestamptz, bigint, varchar, bigint, bigint, int,
    bigint, jsonb, text, jsonb, varchar, bigint
) OWNER TO "openAdomTechUser";

-- -----------------------------------------------------------------
-- 3. Index composite ( application_name , status , start_time DESC )
-- -----------------------------------------------------------------
-- DashboardService.listHistory builds WHERE application_name = ?
-- AND status = ? plus ORDER BY start_time DESC LIMIT ? . Les index
-- single-predicate de V2 forcent un Sort + partial Bitmap ; cet
-- index composite avec DESC aligne l'ordre btree avec ORDER BY DESC ,
-- evite le top-N heap , gain x10 a 100k rows .

CREATE INDEX IF NOT EXISTS idx_workflow_log_app_status_starttime
    ON oa_audit.workflow_log (application_name, status, start_time DESC);

-- -----------------------------------------------------------------
-- 4. oa_audit.cancel_workflow : SECURITY DEFINER , widened states
-- -----------------------------------------------------------------
-- Wrapper SECURITY DEFINER pour permettre le cancel depuis le role
-- applicationManager qui n'a pas UPDATE direct sur oa_audit . Cible
-- TOUT statut non-terminal ( IN_PROGRESS , UPLOADING , CHUNKING ,
-- PROCESSING , LOADING_DB ) , forward-compatible avec de nouveaux
-- etats actifs ajoutes par cascade . Idempotent .
-- Note : ne tue PAS la connection Postgres ; pour interruption
-- SQL-level , cf BackendPidRegistry + pg_cancel_backend .

CREATE OR REPLACE FUNCTION oa_audit.cancel_workflow(
    p_correlation_id uuid,
    p_reason         text
) RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    UPDATE oa_audit.workflow_log
       SET status      = 'CANCELLED',
           end_time    = now(),
           duration_ms = EXTRACT(EPOCH FROM (now() - start_time)) * 1000,
           fatal_error = p_reason
     WHERE correlation_id = p_correlation_id
       AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'RATE_LIMITED');
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

ALTER FUNCTION oa_audit.cancel_workflow(uuid, text) OWNER TO "openAdomTechUser";

COMMENT ON FUNCTION oa_audit.cancel_workflow(uuid, text) IS
    'SECURITY DEFINER wrapper pour cancel admin/owner . Cible tout statut '
    'non-terminal ( IN_PROGRESS , UPLOADING , CHUNKING , PROCESSING , LOADING_DB ) . '
    'Idempotent . Ne tue PAS la connection Postgres ( cf BackendPidRegistry + '
    'pg_cancel_backend pour interruption SQL-level ) .';

-- -----------------------------------------------------------------
-- 5. Partial index : metadata->>'fileId' WHERE status='IN_PROGRESS'
-- -----------------------------------------------------------------
-- Path critique supersedure : FIND_ACTIVE_BY_FILE_ID_SQL appele a
-- chaque Phase 1 publish/unpublish/delete . Sans index dedie : Index
-- Scan sur idx_workflow_log_app_status_starttime puis Filter residuel
-- sur metadata->>'fileId' ( cout 88 buffers a 50k rows ) . Avec partial :
-- 10 buffers ( gain x9 ) . Cardinalite IN_PROGRESS faible -> index
-- compact ( ~10 KB / 1k rows ) .

CREATE INDEX IF NOT EXISTS idx_wfl_inprogress_fileid
    ON oa_audit.workflow_log ((metadata->>'fileId'))
    WHERE status = 'IN_PROGRESS';

COMMENT ON INDEX oa_audit.idx_wfl_inprogress_fileid IS
    'Path critique supersedure : FIND_ACTIVE_BY_FILE_ID_SQL appele a chaque '
    'Phase 1 publish/unpublish/delete . Partial WHERE IN_PROGRESS garde '
    'l''index compact ( cardinalite faible en steady state ) .';

-- -----------------------------------------------------------------
-- 6. P0 NEW : mark_zombie_workflows + FOR UPDATE SKIP LOCKED
-- -----------------------------------------------------------------
-- Fix P0 cancel-divergence ( "presumed dead vs published" observe en
-- prod ) . Cooperation par lock DB entre le sweeper et la finalisation
-- Phase 2 :
--   * Phase 2 ouvre une tx REQUIRES_NEW et acquiert FOR UPDATE sur la
--     row workflow_log AVANT de toggler binaryfile.published
--     ( WorkflowLogRepository.tryLockInProgress ) ;
--   * le sweeper utilise FOR UPDATE SKIP LOCKED pour ne PAS attendre
--     les rows que Phase 2 est en train de finaliser - il skip puis
--     reessayera au prochain cycle ( 5 min plus tard , row sera terminale
--     a ce moment-la donc plus eligible ) .
-- Mutual exclusion garantie par le lock DB , pas par flag in-process .

CREATE OR REPLACE FUNCTION oa_audit.mark_zombie_workflows(
    p_minutes int
) RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    IF p_minutes <= 0 THEN
        RAISE EXCEPTION 'p_minutes must be > 0 ( got % )', p_minutes;
    END IF;
    WITH zombies AS (
        SELECT correlation_id
        FROM oa_audit.workflow_log
        WHERE status = 'IN_PROGRESS'
          AND COALESCE(last_heartbeat_at, start_time)
                < now() - (p_minutes || ' minutes')::interval
        FOR UPDATE SKIP LOCKED
    )
    UPDATE oa_audit.workflow_log w
       SET status      = 'CANCELLED',
           end_time    = now(),
           duration_ms = EXTRACT(EPOCH FROM (now() - w.start_time)) * 1000,
           fatal_error = 'presumed dead ( no heartbeat / start signal in '
                         || p_minutes::text || ' min ; status was IN_PROGRESS )'
     FROM zombies z
    WHERE w.correlation_id = z.correlation_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

ALTER FUNCTION oa_audit.mark_zombie_workflows(int) OWNER TO "openAdomTechUser";

COMMENT ON FUNCTION oa_audit.mark_zombie_workflows(int) IS
    'Passe a CANCELLED les rows IN_PROGRESS dont last_heartbeat est trop ancien . '
    'FOR UPDATE SKIP LOCKED : skip les rows verrouillees par une Phase 2 en '
    'cours de finalisation ( WorkflowLogRepository.tryLockInProgress ) ; '
    'evite la divergence "presumed dead vs binaryfile.published=true" .';
