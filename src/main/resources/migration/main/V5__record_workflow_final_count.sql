-- =================================================================
-- V5 — record_workflow : ajoute p_final_count en dernier parametre
-- =================================================================
--
-- Suite a V4 ( colonne workflow_log.final_count ) , la fonction
-- SECURITY DEFINER oa_audit.record_workflow doit accepter et persister
-- ce nouveau champ . Postgres ne supporte pas l'ajout de parametre via
-- CREATE OR REPLACE FUNCTION ; on DROP + CREATE en preservant le
-- proprietaire ( openAdomTechUser ) .
--
-- Backward compat : null = workflow legacy ( pas de count capture au
-- markCompleted ) ; IntegrityService retombe sur COUNT direct cache .

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
