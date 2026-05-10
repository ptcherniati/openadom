-- ----------------------------------------------------------------------------
-- V6 : index composite ( application_name , status , start_time DESC ) sur
-- oa_audit . workflow_log
-- ----------------------------------------------------------------------------
-- DashboardService . listHistory builds {@code WHERE application_name = ?
-- AND status = ?} ( and similar predicate combos ) plus
-- {@code ORDER BY start_time DESC LIMIT ? OFFSET ?} . The existing V2
-- indexes ( idx_workflow_log_application , idx_workflow_log_status ,
-- idx_workflow_log_type_time ) cover single-predicate cases but force
-- a Sort + partial Bitmap when both filters are combined - acceptable
-- at 150 rows ( current local instance ) , degrades linearly as
-- production approaches the 100 K target .
--
-- The DESC keyword in the index aligns the btree order with the
-- ORDER BY DESC clause , avoiding a top-N heap operation . The leading
-- ( application_name , status ) lets the planner prune the search to
-- the matching app + status partition before walking start_time .
--
-- Iso-resultat : aucun changement de logique , l'index est purement
-- accelerateur . Pas de RLS sur oa_audit . workflow_log ( verifie via
-- pg_class . relrowsecurity = false et pg_policies vide pour ce schema ) ;
-- l'index est donc neutre vis-a-vis du modele de securite .

CREATE INDEX IF NOT EXISTS idx_workflow_log_app_status_starttime
    ON oa_audit.workflow_log (application_name, status, start_time DESC);
