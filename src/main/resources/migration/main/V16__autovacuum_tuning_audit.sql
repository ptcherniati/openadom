-- =================================================================
-- V16 : autovacuum tuning aggressif sur tables oa_audit a forte ecriture
-- =================================================================
-- workflow_log + role_grant_audit + compensation_log ( si presente )
-- subissent un INSERT a chaque workflow / change de role / compensation .
-- Sous charge ( dev acbb : 10-100 inserts/min ) , autovacuum default
-- ( 20% bloat ) attend trop longtemps avant de marquer pages mortes ,
-- ce qui ralentit les SELECT history / dashboard concurrents .
--
-- Idempotent ( ALTER TABLE SET ecrase ) , lock AccessExclusive bref ;
-- pas de migration de donnees .
-- =================================================================

ALTER TABLE oa_audit.workflow_log SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_analyze_threshold = 500
);

-- role_grant_audit : INSERT a chaque grant/revoke , historique consulte
-- par UsersView . Volume moindre que workflow_log mais pattern identique .
ALTER TABLE oa_audit.role_grant_audit SET (
    autovacuum_vacuum_scale_factor = 0.10,
    autovacuum_vacuum_threshold = 200,
    autovacuum_analyze_scale_factor = 0.05,
    autovacuum_analyze_threshold = 200
);

-- compensation_log : tuning conditionnel ( table peut ne pas exister
-- sur deploiements anciens ) . Le DO $$ permet de skipper proprement
-- sans casser la migration .
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'oa_audit' AND table_name = 'compensation_log'
    ) THEN
        EXECUTE 'ALTER TABLE oa_audit.compensation_log SET ('
             || ' autovacuum_vacuum_scale_factor = 0.05 ,'
             || ' autovacuum_vacuum_threshold = 500 ,'
             || ' autovacuum_analyze_scale_factor = 0.02 ,'
             || ' autovacuum_analyze_threshold = 200'
             || ')';
    END IF;
END $$;
