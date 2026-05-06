-- =================================================================
-- V4 — workflow_log.final_count : compteur authoritatif post-UPSERT
-- =================================================================
--
-- Cf AUDIT 06-05-26 #1 . Avant cette migration , chaque poll de
-- IntegrityView ( /api/dashboard/integrity/staging-vs-final ) ou de
-- WorkflowFinalizeBadge ( /api/dashboard/workflows/{cid}/finalize )
-- declenchait un SELECT COUNT(*) FROM <app>.referencevalue WHERE
-- binaryfile = ? pour fournir le compteur final . Avec ~74 binaryFiles
-- par schema et plusieurs onglets ouverts , ces COUNTs saturaient le
-- pool Hikari et entraient en contention LWLock:BufferMapping avec
-- l'UPSERT staging -> table finale en cours .
--
-- Strategie : on persiste UN compteur authoritatif au moment de la
-- complétion ( markCompleted lambda dans CascadeImportPipeline ) ,
-- post-afterCommit Phase B . Les vues lisent ensuite cette colonne
-- au lieu de refaire COUNT(*) .
--
-- NULL = workflow legacy ( pre-V4 ) ou COUNT failed lors de markCompleted
-- ( DB transitoirement down ) . Dans ces cas IntegrityService retombe
-- sur un COUNT direct avec cache TTL 5 min ( fallback ) .

ALTER TABLE oa_audit.workflow_log
    ADD COLUMN IF NOT EXISTS final_count bigint;

COMMENT ON COLUMN oa_audit.workflow_log.final_count IS
    'Compteur authoritatif COUNT(*) FROM <app>.referencevalue WHERE binaryfile = ? '
    'capture au markCompleted ( post-afterCommit Phase B ) . NULL = legacy ou '
    'COUNT failed lors de la complétion . Lu en priorite par IntegrityService '
    'et DashboardService.finalizeProgress pour eviter les COUNT répétés au poll .';
