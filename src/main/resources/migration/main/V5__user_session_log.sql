-- =========================================================================
-- Schema oa_metrics : table user_session_log ( phase Sessions , issue #62 )
-- =========================================================================
--
-- Cette table archive UNE row par session utilisateur authentifiee
-- ( login -> logout ou JWT_EXPIRED ) . Pattern identique a
-- oa_metrics.workflow_log : append-only , aucune FK metier , identifiants
-- denormalises ( les logs survivent aux deletes / renames d'entites ) .
--
-- Inseree en async par fr.inra.oresing.monitoring.session.UserSessionLogWriter
-- ( meme design que WorkflowLogWriter ) .
--
-- -------------------------------------------------------------------------
-- APPLICATION AUTOMATIQUE
-- -------------------------------------------------------------------------
-- Sur une base vierge : Flyway applique automatiquement .
-- Sur une base existante : appliquer manuellement puis inserer la row dans
-- public.flyway_schema_history ( cf. V2__oa_metrics_schema.sql pour la
-- procedure detaillee ) .
-- =========================================================================

-- Schema existe deja ( cree par V2 ) ; CREATE TABLE seul .
CREATE TABLE IF NOT EXISTS oa_metrics.user_session_log (
    session_id      uuid        PRIMARY KEY,
    user_id         uuid        NOT NULL,
    user_login      text        NOT NULL,
    ip_address      inet,
    user_agent      text,
    login_time      timestamptz NOT NULL,
    logout_time     timestamptz,
    duration_ms     bigint,
    end_reason      text                                                      -- LOGOUT | JWT_EXPIRED | KICK
);

-- Index frequents : par user pour l'historique perso , par login_time pour
-- le tri timeline , par end_reason pour les filtres ( "voir tous les
-- logout explicites" ) .
CREATE INDEX IF NOT EXISTS idx_session_log_user        ON oa_metrics.user_session_log (user_id, login_time DESC);
CREATE INDEX IF NOT EXISTS idx_session_log_login_time  ON oa_metrics.user_session_log (login_time DESC);
CREATE INDEX IF NOT EXISTS idx_session_log_end_reason  ON oa_metrics.user_session_log (end_reason);
