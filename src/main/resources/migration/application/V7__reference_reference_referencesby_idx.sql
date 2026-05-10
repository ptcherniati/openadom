-- ----------------------------------------------------------------------------
-- V7 : index secondaire ( referencesby ) sur reference_reference + stats
-- etendues pour le planner .
-- ----------------------------------------------------------------------------
-- Le PK existant est ( referenceid , referencesby ) , ce qui est efficace
-- pour les lookups par referenceid mais inutilisable pour
-- {@code WHERE referencesby = ?} ( colonne en position seconde dans le
-- btree ). La query getReferencedBinaryFiles , qui part d'une CTE filtree
-- sur referencevalue.id ( les rows sources ) , a besoin de remonter via
-- {@code reference_reference rr ON rr.referencesby = src.id} ; sans index
-- sur referencesby , le planner choisit un Hash Right Semi Join sur 39 M
-- rows ( mesure : 30 s sur si_acbb avec 13 M reference_reference rows ) .
--
-- Avec ce secondary btree , le planner peut faire un nested loop indexe
-- sur reference_reference , ramenant la latence a quelques 100 ms par
-- binaryfile filtre .
--
-- Statistiques etendues : la colonne referencetype de referencevalue est
-- correlee a binaryfile ( un fichier ne contient qu'un seul referencetype ) .
-- Sans CREATE STATISTICS , le planner traite ces colonnes comme
-- independantes et sur-estime massivement la cardinalite ( on l'a vu sur
-- les EXPLAIN : rows=410 511 estime vs rows=870 720 reel ) , ce qui peut
-- pousser sur des plans moins optimaux quand plusieurs binaryfileIds sont
-- demandes .

CREATE INDEX IF NOT EXISTS reference_reference_referencesby_idx
    ON ${applicationSchema}.reference_reference (referencesby);

-- Statistiques etendues sur ( referencetype , binaryfile ) pour donner au
-- planner la dependance fonctionnelle reelle . Necessite l'extension
-- pg_statistic_ext ( inclus de base depuis PG 10 ) .
CREATE STATISTICS IF NOT EXISTS referencevalue_referencetype_binaryfile_stats
    ON referencetype, binaryfile FROM ${applicationSchema}.referencevalue;

ANALYZE ${applicationSchema}.referencevalue;
ANALYZE ${applicationSchema}.reference_reference;
