-- ============================================================================
-- V5 - UNIQUE partial index : 1 seul workflow IN_PROGRESS par fileId
-- ============================================================================
--
-- Defense-in-depth contre la race R1 ( cf PublishLifecycleService.startPhase1
-- javadoc ) : si deux Phase 1 entries quasi-simultanees sur le meme fileId
-- contournent le pg_advisory_xact_lock ( bug applicatif , advisory lock
-- bypass , ou Phase 1 sans jdbcTemplate dans tests ) , l'INSERT du second
-- workflow_log est rejete par PostgreSQL avec UNIQUE_VIOLATION ( SQLSTATE
-- 23505 ) au lieu de creer 2 rows IN_PROGRESS coexistantes .
--
-- Le caller traduit l'exception en HTTP 409 Conflict ( cf GlobalExceptionHandler
-- ou wrapper applicatif ) pour donner un feedback explicite a l'utilisateur
-- ( "Une operation est deja en cours sur ce fichier , veuillez patienter" ) .
--
-- ============================================================================
-- Choix index partial vs UNIQUE constraint :
--   - Index partial : couvre uniquement les rows status='IN_PROGRESS' et
--     workflow_type IN (PUBLISH,UNPUBLISH,DELETE_FILE) ; les rows terminales
--     ( COMPLETED / FAILED / CANCELLED ) gardent l'historique multi-row par
--     fileId , necessaire pour l'audit history endpoint .
--   - UNIQUE constraint plein : aurait empeche tout audit historique , KO .
--
-- Couvre uniquement les actions LIFECYCLE ( PUBLISH , UNPUBLISH , DELETE_FILE ) :
-- le cascade IMPORT child cree par PUBLISH a son propre workflow_log row avec
-- workflow_type=IMPORT ; il n'est pas serialise par cet index ( pas voulu :
-- IMPORT est un workflow technique attache au PARENT PUBLISH , pas un
-- evenement lifecycle utilisateur ) .
--
-- ============================================================================
CREATE UNIQUE INDEX IF NOT EXISTS workflow_log_uniq_active_per_file_idx
    ON oa_audit.workflow_log ((metadata->>'fileId'))
    WHERE status = 'IN_PROGRESS'
      AND workflow_type IN ('PUBLISH', 'UNPUBLISH', 'DELETE_FILE');

COMMENT ON INDEX oa_audit.workflow_log_uniq_active_per_file_idx IS
'Defense-in-depth concurrence R1 : empeche 2 workflows lifecycle ( PUBLISH , '
'UNPUBLISH , DELETE_FILE ) coexistants IN_PROGRESS sur le meme fileId . '
'Backstop du pg_advisory_xact_lock pris dans PublishLifecycleService.startPhase1 . '
'Voir doc PUBLISH_UNPUBLISH.md section Concurrence .';
