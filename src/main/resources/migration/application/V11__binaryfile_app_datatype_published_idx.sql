-- ----------------------------------------------------------------------------
-- V11 : index B-tree fonctionnel sur ( application , datatype , published )
-- pour les requetes de filtrage de binaryfile par params jsonb .
-- ----------------------------------------------------------------------------
-- L'index existant {@code binary_file_params_index} ( gin ( params ) avec
-- opclass {@code jsonb_ops} ) accelere les operateurs containment
-- {@code @>} , {@code <@} , existence {@code ?} , {@code ?&} , {@code ?|} ,
-- mais ne couvre PAS les operateurs d'extraction texte {@code ->>} ni
-- d'extraction par chemin {@code #>>} . Ces derniers sont employes par
-- {@code BinaryFileRepository . findPublishedVersions} :
--
--   WHERE application = :application :: uuid
--     AND params #>> '{binaryfiledataset, datatype}' = :datatype
--     AND ( params ->> 'published' ) :: bool
--     AND params -> 'binaryfiledataset' -> 'requiredauthorizations' = ...
--     AND params -> 'binaryfiledataset' ->> 'from' = ...
--     AND params -> 'binaryfiledataset' ->> 'to'   = ...
--
-- Sans index sur les expressions {@code ( params #>> ... )} et
-- {@code ( params ->> 'published' ) :: bool} , le planner fait un Seq Scan
-- complet de la table binaryfile . Acceptable a 116 rows ( si_acbb local )
-- mais lineaire avec la croissance ; sur prod cible 100 K - 1 M binaryfiles ,
-- chaque appel coute 0.5 - 5 sec et la page DataVersioning charge en
-- plusieurs secondes ( cf . SQL_REPORT_10_05_26 . md Q12 ) .
--
-- Un B-tree fonctionnel sur ces 2 expressions , prefixees par {@code application}
-- ( filtre le plus selectif ) , permet au planner d'emettre un Bitmap Index
-- Scan en lieu et place du Seq Scan ; le Bitmap Heap Scan applique ensuite
-- les autres filtres residuels ( requiredauthorizations , from , to ) sur
-- les rows pre-filtrees .
--
-- Iso-resultat : un index ne change pas le row set retourne par le planner ,
-- il modifie uniquement le path d'execution . Aucun risque RLS : la policy
-- {@code BF_<app>_userManager_ALL} sur binaryfile reste sur le qual de la
-- query , appliquee post-index ( meme contrat que les acces seq-scan
-- actuels ) .

CREATE INDEX IF NOT EXISTS binaryfile_app_datatype_published_idx
    ON ${applicationSchema}.binaryfile (
        application,
        ((params #>> '{binaryfiledataset, datatype}')),
        (((params ->> 'published')::boolean))
    );

ANALYZE ${applicationSchema}.binaryfile;
