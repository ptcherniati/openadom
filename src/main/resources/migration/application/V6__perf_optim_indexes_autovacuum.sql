-- =================================================================
-- V5 : optims perf montee en charge ( dépôts 5-10M cumules -> ~100M )
-- =================================================================
-- Rejouee sur chaque schema application a chaque boot ( generic app
-- migrations ) . Idempotent : ALTER TABLE SET ecrase , DROP INDEX IF EXISTS .
-- Aucune migration de donnees , locks brefs .
-- =================================================================

-- --- 1) Autovacuum : ajout insert_scale_factor + cost_limit ---------
-- V3 a deja pose scale_factor=0.05 . A 100M , deux manques :
--  - insert_scale_factor ( PG13+ ) : declenche VACUUM/ANALYZE sur la
--    croissance APPEND ( imports additifs ) , garde visibility map + stats
--    fraiches ( index-only scans , freeze ) meme sans UPDATE/DELETE .
--  - cost_limit ↑ : le budget I/O autovacuum par defaut throttle le vacuum
--    sur grosses tables -> il ne suit pas a 100M . On le releve pour que le
--    vacuum termine dans des delais utiles .
ALTER TABLE ${applicationSchema}.referencevalue SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_analyze_threshold = 500,
    autovacuum_vacuum_insert_scale_factor = 0.05,
    autovacuum_vacuum_insert_threshold = 1000,
    autovacuum_vacuum_cost_limit = 2000
);

ALTER TABLE ${applicationSchema}.Reference_Reference SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_analyze_threshold = 500,
    autovacuum_vacuum_insert_scale_factor = 0.05,
    autovacuum_vacuum_insert_threshold = 1000,
    autovacuum_vacuum_cost_limit = 2000
);

-- --- 2) Drop index btree redondant referencetype_idx ----------------
-- referencetype_idx ( referencetype ) est un prefixe strict de
-- nk_patternColumnNam_type ( referencetype , naturalkey , patterncolumnname )
-- ET de referencevalue_referencetype_binaryfile_idx ( referencetype , binaryfile ) .
-- Une egalite WHERE referencetype = ? est servie par le 1er segment de l'un
-- ou l'autre composite -> ce btree autonome est pure maintenance d'ecriture .
-- Drop = 1 index de moins maintenu par ligne a l'import .
DROP INDEX IF EXISTS ${applicationSchema}.referencetype_idx;

-- --- 3) ( NON applique ici ) Drop GIN plein referencetype_refvalue_gin_idx
-- refvalues est indexe GIN 2x : ce GIN plein + les GIN partiels par type
-- ( <type>_refvalues_index , crees par AuthorizationIndex ) . Les partiels
-- couvrent les requetes scopees WHERE referencetype=X . Dropper le plein
-- halverait la maintenance GIN refvalues a l'import . MAIS : a confirmer
-- via pg_stat_user_indexes.idx_scan sur PROD ( aucune requete refvalues
-- cross-type sans filtre referencetype ) avant d'activer . SQL pret :
--   DROP INDEX IF EXISTS ${applicationSchema}.referencetype_refvalue_gin_idx;
-- Voir TODO_PERF_OPTIM_30_05_26.md (P4-A) .
