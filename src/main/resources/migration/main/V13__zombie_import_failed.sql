-- ============================================================
-- V13 : Etendre le marquage zombie au workflow IMPORT ( depot )
-- ============================================================
--
-- Avant ( V12 ) : seuls UNPUBLISH / DELETE_FILE etaient marques
--                 FAILED ; IMPORT zombies tombaient en CANCELLED .
--
-- Apres : workflow_type IN ( IMPORT , UNPUBLISH , DELETE_FILE )
--         -> FAILED ( relance utilisateur requise ) ;
--         autres types -> CANCELLED ( comportement legacy ) .
--
-- Pourquoi etendre a IMPORT : un depot interrompu mid-flight
-- ( OOM crash , backend kill , timeout ) laisse l'utilisateur sans
-- feedback - workflow_log restait IN_PROGRESS jusqu'au sweeper qui
-- le passait en CANCELLED ( ambigu , implique action utilisateur ) .
-- IMPORT zombie = FAILED + email admin signale clairement que le
-- depot a echoue ; user peut redeposer le fichier ( idempotent : un
-- nouveau workflow IMPORT est cree , le precedent reste trace dans
-- l'historique en FAILED ) .
--
-- Atomicite : le pipeline cascade IMPORT ecrit dans des tables
-- staging puis UPSERT transactionnel vers referencevalue . Si
-- interrompu , la tx UPSERT roll back ; les rows referencevalue
-- restent intactes . Seules les tables staging peuvent persister ,
-- recyclees par le flow STAGING_CLEANUP de compensation .
--
-- Note : retry est simplement un nouveau depot - aucune coherence
-- a rattraper cote referencevalue ( contrairement a UNPUBLISH qui
-- peut avoir DELETE chunked partiel ) .

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
        SELECT correlation_id, workflow_type
        FROM oa_audit.workflow_log
        WHERE status = 'IN_PROGRESS'
          AND COALESCE(last_heartbeat_at, start_time)
                < now() - (p_minutes || ' minutes')::interval
        FOR UPDATE SKIP LOCKED
    )
    UPDATE oa_audit.workflow_log w
       SET status      = CASE
                            WHEN z.workflow_type IN ('IMPORT', 'UNPUBLISH', 'DELETE_FILE')
                                THEN 'FAILED'
                            ELSE 'CANCELLED'
                         END,
           end_time    = now(),
           duration_ms = EXTRACT(EPOCH FROM (now() - w.start_time)) * 1000,
           fatal_error = CASE
                            WHEN z.workflow_type = 'IMPORT'
                                THEN 'Depot interrompu ( pas de heartbeat depuis '
                                     || p_minutes::text || ' min ) . '
                                     || 'Les donnees n''ont pas ete ingerees ( transaction rollback ) . '
                                     || 'Relancer le depot du fichier pour reprendre l''operation .'
                            WHEN z.workflow_type IN ('UNPUBLISH', 'DELETE_FILE')
                                THEN 'Operation interrompue ( pas de heartbeat depuis '
                                     || p_minutes::text || ' min ) . '
                                     || 'Les donnees peuvent etre partiellement supprimees . '
                                     || 'Relancer la depublication / suppression pour terminer le nettoyage .'
                            ELSE 'presumed dead ( no heartbeat / start signal in '
                                 || p_minutes::text || ' min ; status was IN_PROGRESS )'
                         END
     FROM zombies z
    WHERE w.correlation_id = z.correlation_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

COMMENT ON FUNCTION oa_audit.mark_zombie_workflows(int) IS
'V13 : marque les workflows IN_PROGRESS sans heartbeat depuis p_minutes ; '
'IMPORT/UNPUBLISH/DELETE_FILE -> FAILED ( retry user requis ) , autres -> CANCELLED .';
