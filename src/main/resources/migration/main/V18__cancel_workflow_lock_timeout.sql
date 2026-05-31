-- Cancel non bloquant sur oa_audit.workflow_log .
--
-- Contexte ( bug live ) : la transaction d'un import détient un verrou de ligne
-- sur SA row workflow_log pendant toute la phase synchrone ( transform + COPY +
-- UPSERT finalize ) - cf V17 . L'ancien cancel_workflow faisait un UPDATE
-- bloquant sur cette row : annuler en fin d'UPSERT attendait donc la fin
-- complète du finalize avant de répondre ( endpoint HTTP figé plusieurs minutes ,
-- « l'appli ne répond pas » ) .
--
-- Correctif : borner l'attente du verrou à 2s via SET lock_timeout au niveau de
-- la fonction . Si la row est verrouillée par un finalize en cours , l'UPDATE
-- échoue proprement ( lock_not_available / 55P03 ) au lieu de bloquer ; on
-- retourne -1 pour signaler « cancel différé , row verrouillée » . L'intention
-- d'annulation est de toute façon déjà enregistrée hors-DB par l'appelant
-- ( WorkflowEventBus.cancel + PublishLifecycleCoordinator.markCancelled , en
-- mémoire ) et le signal SQL-level part via pg_cancel_backend ( connexion
-- séparée ) AVANT cet UPDATE . Le endpoint répond ainsi en < 2s quel que soit
-- l'état du finalize . Le worker écrira l'état terminal réel ( CANCELLED si
-- pg_cancel_backend a pu interrompre un batch , COMPLETED si l'UPSERT était déjà
-- au COMMIT - point de non-retour non interruptible ) .
--
-- Iso-comportement pour les cas normaux : row libre -> UPDATE + n>=0 ;
-- row terminale / inconnue -> 0 . Seul le cas « row verrouillée » change :
-- -1 immédiat au lieu d'un blocage jusqu'à la fin du finalize .

CREATE OR REPLACE FUNCTION oa_audit.cancel_workflow(
    p_correlation_id uuid,
    p_reason         text
) RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
SET lock_timeout = '2000'
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
EXCEPTION
    WHEN lock_not_available THEN
        -- Row verrouillée par un finalize actif : cancel différé , on ne bloque pas .
        RETURN -1;
END;
$$;

ALTER FUNCTION oa_audit.cancel_workflow(uuid, text) OWNER TO "openAdomTechUser";
