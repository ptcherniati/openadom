-- Heartbeat non bloquant sur oa_audit.workflow_log .
--
-- Contexte : la transaction d'un import détient un verrou de ligne sur SA row
-- workflow_log pendant toute la durée de l'import ( la row IN_PROGRESS est
-- écrite dans cette même transaction ) . L'ancien beat_workflow faisait un
-- UPDATE bloquant sur cette row : il attendait donc la fin de l'import entier
-- ( ~15 s par appel mesuré en profil mono-thread ) juste pour rafraîchir un
-- timestamp . Le verrou est d'ailleurs déjà la preuve que le workflow est
-- vivant ( mark_zombie_workflows ignore les rows verrouillées via SKIP LOCKED ) ,
-- donc battre pendant ce verrou est redondant .
--
-- Correctif : sonder la row avec FOR UPDATE NOWAIT . Si elle est verrouillée
-- ( workflow actif ) , on saute le beat silencieusement au lieu de bloquer .
-- Iso-comportement pour les cas normaux : row libre -> UPDATE + true ;
-- row terminale / inconnue -> 0 ligne -> false . Seul le cas « row verrouillée »
-- change : false immédiat au lieu de true après blocage ( le caller met de
-- toute façon à jour le registre heartbeat en mémoire indépendamment du retour ) .

CREATE OR REPLACE FUNCTION oa_audit.beat_workflow(
    p_correlation_id uuid
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    -- Sonde non bloquante : si la row est verrouillée par la transaction active
    -- du workflow , lock_not_available est levé et on saute le beat .
    PERFORM 1
       FROM oa_audit.workflow_log
      WHERE correlation_id = p_correlation_id
        AND status = 'IN_PROGRESS'
        FOR UPDATE NOWAIT;
    UPDATE oa_audit.workflow_log
       SET last_heartbeat_at = now()
     WHERE correlation_id = p_correlation_id
       AND status = 'IN_PROGRESS';
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n > 0;
EXCEPTION
    WHEN lock_not_available THEN
        -- Row verrouillée = workflow démontrablement vivant -> beat superflu .
        RETURN false;
END;
$$;

ALTER FUNCTION oa_audit.beat_workflow(uuid) OWNER TO "openAdomTechUser";
