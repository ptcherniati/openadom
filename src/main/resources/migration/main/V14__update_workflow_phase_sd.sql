-- ============================================================
-- V14 : SECURITY DEFINER function pour update workflow_log.metadata.phase
-- ============================================================
--
-- Probleme : workflow_log table a seulement GRANT SELECT TO PUBLIC ;
-- toutes les ecritures ( INSERT / UPDATE ) doivent passer par des
-- fonctions SECURITY DEFINER ( cf record_workflow_start , record_workflow ,
-- mark_zombie_workflows , beat_workflow ) . Mais
-- WorkflowLogRepository.updatePhase faisait un UPDATE direct qui
-- echouait sur des roles applicatifs sans UPDATE privilege sur la
-- table . Le UPDATE qui echouait laissait la tx outer dans l'etat
-- "aborted" ( SQLState 25P02 ) , poisonnant tous les SQL subsequents
-- de la meme tx ( workflow zombie + UnexpectedRollbackException ) .
--
-- Avant cette migration , updatePhase fonctionnait par accident
-- uniquement dans les contextes ou la connexion JDBC etait deja
-- privilegiee ( ex : openAdomTechUser , admin ) . Pour les contextes
-- HTTP normaux ( CreateDataUseCase tx applicative ) le UPDATE echouait
-- silencieusement ( "best-effort" ) ; mais avec sous-phases visibles
-- dans le flux depot frais ( Phase 2 cascade adoption ) , l'echec
-- aboutit a la mort de la tx outer .
--
-- Fix : SECURITY DEFINER function update_workflow_phase + GRANT
-- EXECUTE TO PUBLIC . Tout role applicatif peut maintenant emettre
-- des phases sur workflow_log via cette fonction , exactement comme
-- record_workflow_start pour les INSERT . Behaviour idempotent
-- preserve ( WHERE status = 'IN_PROGRESS' bloque l'ecrasement des
-- terminaux ) .
--
-- Note : WorkflowLogRepository.updatePhase doit basculer en parallele
-- pour utiliser cette fonction au lieu du UPDATE direct .

CREATE OR REPLACE FUNCTION oa_audit.update_workflow_phase(
    p_correlation_id uuid,
    p_phase          varchar(64)
) RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    UPDATE oa_audit.workflow_log
       SET metadata = jsonb_set(
               COALESCE(metadata, '{}'::jsonb),
               '{phase}',
               to_jsonb(p_phase),
               true)
     WHERE correlation_id = p_correlation_id
       AND status = 'IN_PROGRESS';
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

ALTER FUNCTION oa_audit.update_workflow_phase(uuid, varchar) OWNER TO "openAdomTechUser";

GRANT EXECUTE ON FUNCTION oa_audit.update_workflow_phase(uuid, varchar) TO PUBLIC;

COMMENT ON FUNCTION oa_audit.update_workflow_phase(uuid, varchar) IS
'V14 : SECURITY DEFINER wrapper pour update workflow_log.metadata.phase . '
'Permet aux roles applicatifs sans UPDATE privilege ( ex contexte HTTP ) '
'd''emettre des phases sans casser la tx outer ( evite SQLState 25P02 ) . '
'Cf WorkflowLogRepository.updatePhase qui doit basculer sur cette fonction .';
