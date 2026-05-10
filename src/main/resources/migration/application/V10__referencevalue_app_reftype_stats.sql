-- ----------------------------------------------------------------------------
-- V10 : extended statistics ( application , referencetype ) sur referencevalue
-- ----------------------------------------------------------------------------
-- Sur les EXPLAIN executes lors de l'audit du 10 / 05 / 26 , le planner
-- sous-estime massivement la cardinalite des queries qui combinent
-- {@code WHERE application = ? AND ReferenceType = ?} : il considere
-- les deux predicats comme statistiquement independants alors qu'il y a
-- une dependance fonctionnelle forte ( un schema applicatif n'a qu'une
-- seule application_id , donc {@code application} est presque constant
-- a l'interieur d'un schema ) .
--
-- Mesure observee sur si_acbb : rows estime = 200 vs actual = 5 595 497
-- ( facteur 28 000x ) sur la query findAllByReferenceType pour
-- {@code t_soil_temperature_ste} . Cette mauvaise estimation pousse le
-- planner sur un Sort + HashAggregate avec des batches dimensionnes pour
-- 200 rows alors qu'il en arrive 5.6 M , causant le spill 4 . 9 GB sur
-- disque .
--
-- L'extension statistics ( {@code dependencies , ndistinct , mcv} ) :
-- - {@code dependencies} : capture la dependance " application -> ref " ;
-- - {@code ndistinct} : meilleure estimation du nombre de combinaisons
--   uniques ( application , referencetype ) ;
-- - {@code mcv} : top-K combinaisons les plus frequentes pour les seq
--   scans selectifs .
--
-- L'analyse subsequente recharge les histogrammes pour que le planner
-- les utilise immediatement . Cout one-shot : ~2 sec sur referencevalue
-- 9 . 9 M lignes .
--
-- Iso-resultat : les statistiques ne changent pas le plan EXECUTE ,
-- juste le plan CHOISI par le planner . Aucun risque RLS .

CREATE STATISTICS IF NOT EXISTS referencevalue_app_reftype_stats
    (dependencies, ndistinct, mcv)
    ON application, referencetype
    FROM ${applicationSchema}.referencevalue;

ANALYZE ${applicationSchema}.referencevalue;
