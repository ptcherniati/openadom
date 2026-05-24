-- -----------------------------------------------------------------
-- Drop data_versioning_scope_cache : suppression code mort
-- -----------------------------------------------------------------
-- L'infrastructure complete ( table + triggers + service + endpoint )
-- existait pour cacher les dropdowns de DataVersioningView mais aucun
-- caller frontend n'a jamais ete branche . La table est restee vide
-- sur toutes les apps . Migration de cleanup : DROP cascade pour
-- liberer le schema et eviter de la confusion future .
--
-- Les dropdowns DataVersioningView sont peuples par le payload
-- /data/json ( champ referenceScopes via AuthorizationService.getAuthorizationScopes )
-- qui est l'unique mecanisme effectivement utilise .
--
-- Si la feature est reintroduite plus tard ( cf. perf x100-1000 sur
-- gros referentiels ) , re-ajouter via une nouvelle migration en
-- repartant des fonctions reverties ici .

-- Triggers d'abord ( dependances ) .
DROP TRIGGER IF EXISTS trg_dvsc_invalidate_after_insert
    ON ${applicationSchema}.referencevalue;
DROP TRIGGER IF EXISTS trg_dvsc_invalidate_after_delete
    ON ${applicationSchema}.referencevalue;

-- Fonctions trigger ( ne sont plus appelees ) .
DROP FUNCTION IF EXISTS ${applicationSchema}.dvsc_invalidate_after_insert();
DROP FUNCTION IF EXISTS ${applicationSchema}.dvsc_invalidate_after_delete();

-- Table + indexes ( cascade implicite pour les indexes ) .
DROP TABLE IF EXISTS ${applicationSchema}.data_versioning_scope_cache;
