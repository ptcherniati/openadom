-- ----------------------------------------------------------------------------
-- V9 : index GIN sur oresiauthorization . oresiusers ( array de entityref )
-- ----------------------------------------------------------------------------
-- Le repository {@code AuthorizationRepository . findAuthorizationsByUserId}
-- execute :
--
--   SELECT to_jsonb ( t )
--     FROM <schema> . oresiauthorization t
--    WHERE t . application = :applicationId
--      AND ARRAY [ :userId :: entityref ] <@ t . oresiusers ;
--
-- L'operateur {@code <@} ( contained-by ) sur la colonne array
-- {@code oresiusers} ( type entityref [ ] ) ne peut etre indexe par btree
-- standard ; sans index GIN , le planner tombe sur un Seq Scan integral
-- de la table - acceptable a 0 lignes ( instance locale ) , catastrophique
-- des que la prod atteint 10 K - 50 K rows par application ( latence x100
-- a x1000 ) .
--
-- Le {@code gin ( <array_col> )} natif PostgreSQL supporte les operateurs
-- {@code @>} , {@code <@} , {@code &&} , {@code =} sur les array . Pas
-- besoin d'opclass particulier : la classe par defaut suffit pour le
-- type {@code entityref} qui est defini comme un domain sur uuid dans le
-- schema main V1 .
--
-- Iso-resultat : un index ne change pas le row set retourne par le
-- planner ; il modifie uniquement le path d'execution ( Seq Scan ->
-- Bitmap Index Scan + Bitmap Heap Scan ) . Aucun risque RLS car la
-- policy {@code Auth_<app>_userManager_ALL} reste sur le qual de la
-- query , appliquee post-index .

CREATE INDEX IF NOT EXISTS oresiauthorization_oresiusers_gin_idx
    ON ${applicationSchema}.oresiauthorization USING gin (oresiusers);

ANALYZE ${applicationSchema}.oresiauthorization;
