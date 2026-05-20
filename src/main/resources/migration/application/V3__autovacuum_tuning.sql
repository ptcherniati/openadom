-- =================================================================
-- V3 : autovacuum tuning aggressif sur tables a forte volumetrie d'ecriture
-- =================================================================
-- Objectif : reduire le bloat post-UPSERT et accelerer la convergence
-- du visibility map sur les tables qui subissent des cycles
-- DELETE+INSERT massifs (publish/unpublish/finalize) , afin de
-- minimiser la degradation des lectures concurrentes ( /data ,
-- /filters ) suite aux gros workflows .
--
-- Defauts Postgres ( pour reference ) :
--   autovacuum_vacuum_scale_factor   = 0.20  ( 20% dead tuples )
--   autovacuum_vacuum_threshold      = 50
--   autovacuum_analyze_scale_factor  = 0.10
--   autovacuum_analyze_threshold     = 50
--
-- Valeurs choisies ( source : tuning HOT update / bulk upsert prod ) :
--   scale_factor 0.05 ( 5% dead -> vacuum ) permet de declencher
--   autovacuum tot apres un UPSERT moyen ( ~100k lignes ) plutot
--   qu'attendre 20% de bloat ( jusqu'a quelques M lignes mortes ) .
--   threshold 1000 evite le bruit sur les petites tables ( count_stats )
--   sans empecher le declenchement sur les grosses .
--
-- Note : ALTER TABLE SET ne deplace pas de donnees ; lock AccessExclusive
-- bref ( quelques ms ) , sans interaction avec les longues operations
-- runtime . Idempotent ( ALTER TABLE SET ecrase la valeur existante ) .
-- =================================================================

-- referencevalue : table principale , UPSERT massif ( cascade -> finalize ) ,
-- DELETE complete au republish , taux d'ecriture le plus eleve de l'app .
ALTER TABLE ${applicationSchema}.referencevalue SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_analyze_threshold = 500
);

-- Reference_Reference : refref rebuild = DELETE total + INSERT massif
-- ( cf. RefrefRebuildSql ) post-cascade UPSERT . Bloat instantane si pas
-- de vacuum aggressif derriere .
ALTER TABLE ${applicationSchema}.Reference_Reference SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_analyze_threshold = 500
);

-- oresisynthesis : DELETE + INSERT par dataType a chaque commitVisibleFlagAndSynthesis
-- ( publish phase 2 ) , relativement petite mais frequemment churned .
ALTER TABLE ${applicationSchema}.oresisynthesis SET (
    autovacuum_vacuum_scale_factor = 0.05,
    autovacuum_vacuum_threshold = 200,
    autovacuum_analyze_scale_factor = 0.05,
    autovacuum_analyze_threshold = 200
);

-- referencevalue_count_stats : table d'agregats , DELETE + INSERT a chaque
-- recompute ( admin ) . Petite mais lue par les endpoints stats / /filters .
ALTER TABLE ${applicationSchema}.referencevalue_count_stats SET (
    autovacuum_vacuum_scale_factor = 0.10,
    autovacuum_vacuum_threshold = 50,
    autovacuum_analyze_scale_factor = 0.10,
    autovacuum_analyze_threshold = 50
);
