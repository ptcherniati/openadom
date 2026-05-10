-- ----------------------------------------------------------------------------
-- V8 : table de cache pour les valeurs distinctes des dropdowns de scope
--      ( ecran DataVersioningView ) , avec invalidation par triggers
--      statement-level sur referencevalue .
-- ----------------------------------------------------------------------------
-- Probleme :
--    L'endpoint /applications/{nameOrId}/data/{refType}/{column} renvoie les
--    valeurs distinctes d'une colonne d'un referentiel ( ex. liste des
--    sites pour le dropdown "Site" du DataVersioningView ) . Ces valeurs
--    sont calculees par DataRepository.findDataColumn qui scanne
--    referencevalue filtre par ( application , referencetype ) . Le filtre
--    user-side se fait via SET ROLE Postgres ( RLS ) , donc le resultat
--    depend du role appelant .
--
--    A 1 milliard de lignes referencevalue , ce scan devient le bottleneck
--    principal du DataVersioningView ( aujourd'hui aucun cache ) .
--
-- Solution :
--    Cache materialise par ( application , reference_type , column_name ,
--    user_id ) . Lazy populated au 1er hit ; invalide a la mutation par
--    trigger statement-level sur referencevalue ( meme pattern que
--    referencevalue_count_stats ) .
--
-- Cardinalite attendue :
--    100 users x 10 refTypes x ~3 columns par dropdown = ~3K entries par
--    app ( ~75 KB jsonb par entry compresse = ~225 MB par app worst case ;
--    plafonnable via OPENADOM_CACHE_DATA_VERSIONING_SCOPE_MAX_ENTRIES_PER_APP ) .

CREATE TABLE IF NOT EXISTS ${applicationSchema}.data_versioning_scope_cache (
    application      UUID         NOT NULL,
    reference_type   TEXT         NOT NULL,
    column_name      TEXT         NOT NULL,
    user_id          UUID         NOT NULL,
    visible_values   JSONB        NOT NULL,
    computed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (application, reference_type, column_name, user_id)
);

-- Accelere DELETE WHERE reference_type = X ( trigger SQL ci-dessous ) .
CREATE INDEX IF NOT EXISTS data_versioning_scope_cache_reference_type_idx
    ON ${applicationSchema}.data_versioning_scope_cache (reference_type);

-- Accelere DELETE WHERE user_id = X ( hook AuthorizationService grant/revoke ) .
CREATE INDEX IF NOT EXISTS data_versioning_scope_cache_user_id_idx
    ON ${applicationSchema}.data_versioning_scope_cache (user_id);


-- ----------------------------------------------------------------------------
-- Trigger fonctions : invalident les entries cache des referencetypes mutes .
-- ----------------------------------------------------------------------------
-- Pattern aligne sur referencevalue_count_stats ( V5 ) : statement-level
-- pour profiter du REFERENCING NEW/OLD TABLE et ne firer qu'1 fois par
-- statement INSERT/DELETE ( indispensable pour les imports cascade qui
-- inserent par batch de centaines de milliers de rows ) .

CREATE OR REPLACE FUNCTION ${applicationSchema}.dvsc_invalidate_after_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog'
AS $$
BEGIN
    DELETE FROM ${applicationSchema}.data_versioning_scope_cache cache
    WHERE cache.reference_type IN (
        SELECT DISTINCT new_table.referencetype FROM new_table
    );
    RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION ${applicationSchema}.dvsc_invalidate_after_delete()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'pg_catalog'
AS $$
BEGIN
    DELETE FROM ${applicationSchema}.data_versioning_scope_cache cache
    WHERE cache.reference_type IN (
        SELECT DISTINCT old_table.referencetype FROM old_table
    );
    RETURN NULL;
END $$;


-- ----------------------------------------------------------------------------
-- Triggers : 1 par direction de mutation ( INSERT / DELETE ) .
-- ----------------------------------------------------------------------------
-- Pas de trigger UPDATE car les UPDATEs sur referencevalue ne touchent
-- pas referencetype ( la colonne discriminante du cache ) ; les modifs
-- affectent les refvalues eux-memes mais le cache est invalide via le
-- DELETE / INSERT du flow d'import ( unPublishVersions + import ) qui
-- traverse forcement les triggers INSERT / DELETE ci-dessus .

DROP TRIGGER IF EXISTS trg_dvsc_invalidate_after_insert
    ON ${applicationSchema}.referencevalue;
CREATE TRIGGER trg_dvsc_invalidate_after_insert
    AFTER INSERT ON ${applicationSchema}.referencevalue
    REFERENCING NEW TABLE AS new_table
    FOR EACH STATEMENT
    EXECUTE FUNCTION ${applicationSchema}.dvsc_invalidate_after_insert();

DROP TRIGGER IF EXISTS trg_dvsc_invalidate_after_delete
    ON ${applicationSchema}.referencevalue;
CREATE TRIGGER trg_dvsc_invalidate_after_delete
    AFTER DELETE ON ${applicationSchema}.referencevalue
    REFERENCING OLD TABLE AS old_table
    FOR EACH STATEMENT
    EXECUTE FUNCTION ${applicationSchema}.dvsc_invalidate_after_delete();


-- ----------------------------------------------------------------------------
-- Grants : lecture publique , ecriture reservee a openAdomAdmin ( pour
-- l'invalidation manuelle via /admin/cache endpoint ) . Le tech user
-- backend insere via SECURITY DEFINER de la function ; les autres roles
-- ne peuvent que lire .
-- ----------------------------------------------------------------------------
GRANT SELECT
    ON ${applicationSchema}.data_versioning_scope_cache
    TO PUBLIC;
GRANT INSERT, UPDATE, DELETE, TRUNCATE
    ON ${applicationSchema}.data_versioning_scope_cache
    TO "openAdomAdmin";
