-- ============================================================
-- V12 : Differencier statut zombie selon le type de workflow
-- ============================================================
--
-- Avant : oa_audit.mark_zombie_workflows(p_minutes int) marquait
--         TOUS les workflows zombies en CANCELLED indistinctement .
--
-- Apres : workflow_type IN ( UNPUBLISH , DELETE_FILE ) -> FAILED
--         ( signale a l'utilisateur que l'operation a echoue mid-flight
--           et necessite une relance manuelle ) ;
--         autres types -> CANCELLED ( comportement legacy ) .
--
-- Pourquoi distinguer : UNPUBLISH / DELETE_FILE peuvent laisser des
-- lignes referencevalue partiellement supprimees ( DELETE chunked
-- interrompu mid-flight ) . L'utilisateur doit relancer l'operation
-- pour terminer le nettoyage . FAILED le signale clairement dans l'UI
-- ; CANCELLED prete a confusion ( implique action utilisateur ) .
--
-- Note : le retry est idempotent grace au filtre WHERE binaryfile=?
-- dans removeByFileIdChunked - relance traite uniquement les lignes
-- restantes . Aucune corruption , aucun orphan FK possible .

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
                            WHEN z.workflow_type IN ('UNPUBLISH', 'DELETE_FILE')
                                THEN 'FAILED'
                            ELSE 'CANCELLED'
                         END,
           end_time    = now(),
           duration_ms = EXTRACT(EPOCH FROM (now() - w.start_time)) * 1000,
           fatal_error = CASE
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
'V12 : marque les workflows IN_PROGRESS sans heartbeat depuis p_minutes ; '
'UNPUBLISH/DELETE_FILE -> FAILED ( retry user requis ) , autres -> CANCELLED .';
