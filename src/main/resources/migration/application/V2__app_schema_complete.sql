-- =================================================================
-- V2 : application schema consolidation
-- =================================================================
-- Migration consolidee qui remplace les anciennes V2 a V14
-- ( pre-prod / dev seulement ; aucun environnement utilise les
-- versions intermediaires ) . Regroupe en une seule unite de
-- migration toutes les evolutions du schema applicatif livrees
-- apres V1 et avant cette consolidation . Ce regroupement :
--   * reduit le bruit Flyway au boot ( 1 fichier au lieu de 13 ) ;
--   * met le schema final dans un seul artefact relisible ;
--   * facilite l'onboarding lors de la lecture chronologique .
--
-- Sections ( ordre logique , pas chronologique ) :
--   1. RightsRequest : suivi de traitement ( ex-V2 + V3 + V4 )
--   2. referencevalue_count_stats : stats par refType ( ex-V5 )
--   3. Indices referencevalue ( ex-V6 + V14 )
--   4. reference_reference referencesby + stats etendues ( ex-V7 )
--   5. data_versioning_scope_cache + triggers ( ex-V8 )
--   6. oresiauthorization GIN ( ex-V9 )
--   7. referencevalue stats ( application , referencetype ) ( ex-V10 )
--   8. binaryfile params fonctionnel B-tree ( ex-V11 )
--   9. binaryfile.processed_data ( oid Large Object ) ( ex-V12 + V13 mergees )

-- -----------------------------------------------------------------
-- 1. RightsRequest : suivi de traitement ( #487 Phase 3 )
-- -----------------------------------------------------------------
-- Persistance du traitement d'une demande de droits par un gestionnaire :
-- identifiant gestionnaire ( treatedBy ) , decision rendue , commentaire
-- interne , corps + sujet du mail envoye au demandeur , autorisations
-- attribuees . Tous nullables ( la demande peut etre en attente ) . Pas
-- de FK vers oresiuser ni vers oresiauthorization : eviter
-- AccessExclusiveLock sur ces tables lors de DDL ulterieure ( cf
-- self-deadlock potentiel avec @PreAuthorize -> getCurrentUser ) ;
-- l'integrite est assuree cote applicatif ( null safe dans
-- RightsRequestResult ) .

ALTER TABLE RightsRequest
    ADD COLUMN IF NOT EXISTS treatedBy                uuid,
    ADD COLUMN IF NOT EXISTS treatmentDecision        text,
    ADD COLUMN IF NOT EXISTS treatmentComment         text,
    ADD COLUMN IF NOT EXISTS treatmentMailBody        text,
    ADD COLUMN IF NOT EXISTS treatmentMailSubject     text,
    ADD COLUMN IF NOT EXISTS linkedAuthorizationIds   uuid[];

-- -----------------------------------------------------------------
-- 2. referencevalue_count_stats : stats par refType
-- -----------------------------------------------------------------
-- Avant cette table , DataRepository.buildReferenceSynthesis executait
-- SELECT referencetype , count ( * ) FROM referencevalue GROUP BY
-- referencetype , qui prenait 12s a 10M rows et 2-5min a 200M rows
-- ( cible ACBB ) . On persiste les counts par refType , maintenus par
-- triggers statement-level AFTER INSERT / DELETE . Lookup PK < 1 ms .
-- Tous les identifiants sont qualifies par ${applicationSchema} pour
-- eviter les problemes de search_path lors de l'execution des triggers
-- ( les fonctions trigger heritent du search_path du caller , qui
-- differe dans le pipeline cascade ) .

CREATE TABLE IF NOT EXISTS ${applicationSchema}.referencevalue_count_stats (
    referencetype TEXT PRIMARY KEY,
    line_count    BIGINT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bootstrap initial : peuple depuis l'etat existant ( si V1 a deja
-- insere des rows lors de la creation d'app applicative ) .
INSERT INTO ${applicationSchema}.referencevalue_count_stats (referencetype, line_count, updated_at)
SELECT referencetype, count(*), now()
FROM ${applicationSchema}.referencevalue
GROUP BY referencetype
ON CONFLICT (referencetype) DO UPDATE
SET line_count = EXCLUDED.line_count,
    updated_at = EXCLUDED.updated_at;

-- SECURITY DEFINER : les triggers fire pendant des INSERT/DELETE
-- declenches par des roles non-admin ( ex applicationDataWriter ) qui
-- n'ont pas UPDATE direct sur referencevalue_count_stats . Sans SECURITY
-- DEFINER , le trigger leve permission denied -> BadSqlGrammarException .
CREATE OR REPLACE FUNCTION ${applicationSchema}.referencevalue_count_stats_after_insert()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    INSERT INTO ${applicationSchema}.referencevalue_count_stats (referencetype, line_count, updated_at)
    SELECT referencetype, count(*), now()
    FROM new_table
    GROUP BY referencetype
    ON CONFLICT (referencetype) DO UPDATE
    SET line_count = ${applicationSchema}.referencevalue_count_stats.line_count + EXCLUDED.line_count,
        updated_at = EXCLUDED.updated_at;
    RETURN NULL;
END $$;

CREATE OR REPLACE FUNCTION ${applicationSchema}.referencevalue_count_stats_after_delete()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    UPDATE ${applicationSchema}.referencevalue_count_stats stats
    SET line_count = GREATEST(0, stats.line_count - deltas.delta),
        updated_at = now()
    FROM (
        SELECT referencetype, count(*)::BIGINT AS delta
        FROM old_table
        GROUP BY referencetype
    ) AS deltas
    WHERE stats.referencetype = deltas.referencetype;
    RETURN NULL;
END $$;

DROP TRIGGER IF EXISTS trg_referencevalue_count_stats_after_insert
    ON ${applicationSchema}.referencevalue;
CREATE TRIGGER trg_referencevalue_count_stats_after_insert
    AFTER INSERT ON ${applicationSchema}.referencevalue
    REFERENCING NEW TABLE AS new_table
    FOR EACH STATEMENT
    EXECUTE FUNCTION ${applicationSchema}.referencevalue_count_stats_after_insert();

DROP TRIGGER IF EXISTS trg_referencevalue_count_stats_after_delete
    ON ${applicationSchema}.referencevalue;
CREATE TRIGGER trg_referencevalue_count_stats_after_delete
    AFTER DELETE ON ${applicationSchema}.referencevalue
    REFERENCING OLD TABLE AS old_table
    FOR EACH STATEMENT
    EXECUTE FUNCTION ${applicationSchema}.referencevalue_count_stats_after_delete();

GRANT SELECT ON ${applicationSchema}.referencevalue_count_stats TO PUBLIC;
GRANT INSERT, UPDATE, DELETE, TRUNCATE ON ${applicationSchema}.referencevalue_count_stats TO "openAdomAdmin";

-- -----------------------------------------------------------------
-- 3. Indices referencevalue
-- -----------------------------------------------------------------
-- Composite ( referencetype , binaryfile ) : accelere getReferencedBinaryFiles
-- ( filtre ( refType = X AND binaryfile IN ( Y... ) ) ) , gain x100 a 10M rows .
-- Dedie ( binaryfile ) : accelere DELETE WHERE binaryfile = ? et SELECT
-- snapshot path . Le composite ne suffit pas car skip-scan sur leading
-- column ( referencetype ) tombe en seq scan a haute cardinalite .

CREATE INDEX IF NOT EXISTS referencevalue_referencetype_binaryfile_idx
    ON ${applicationSchema}.referencevalue (referencetype, binaryfile);

CREATE INDEX IF NOT EXISTS referencevalue_binaryfile_idx
    ON ${applicationSchema}.referencevalue (binaryfile);

-- -----------------------------------------------------------------
-- 4. reference_reference referencesby + statistiques etendues
-- -----------------------------------------------------------------
-- PK existant ( referenceid , referencesby ) ne sert pas WHERE
-- referencesby = ? ( colonne en 2e position du btree ) . Sans index
-- dedie , la query getReferencedBinaryFiles fait un Hash Right Semi
-- Join sur 39M rows ( mesure 30s sur si_acbb 13M rows ) . Avec le
-- secondary btree : nested loop indexe -> latence ~100ms .
-- Statistiques etendues ( refType , binaryfile ) corrige la sur-estimation
-- de cardinalite observable dans EXPLAIN ( rows=410k estime vs 870k reel ) .

CREATE INDEX IF NOT EXISTS reference_reference_referencesby_idx
    ON ${applicationSchema}.reference_reference (referencesby);

CREATE STATISTICS IF NOT EXISTS referencevalue_referencetype_binaryfile_stats
    ON referencetype, binaryfile FROM ${applicationSchema}.referencevalue;

-- -----------------------------------------------------------------
-- 5. data_versioning_scope_cache : cache des dropdowns DataVersioningView
-- -----------------------------------------------------------------
-- Endpoint /applications/{nameOrId}/data/{refType}/{column} renvoie les
-- valeurs distinctes d'une colonne d'un referentiel ( ex liste des sites ) .
-- Sans cache , scan integral de referencevalue filtre par RLS ( SET ROLE ) ;
-- a 1 milliard de lignes referencevalue , bottleneck principal du
-- DataVersioningView . Cache materialise par ( application , refType ,
-- column , user ) ; lazy populated au 1er hit ; invalide a la mutation
-- par trigger statement-level sur referencevalue ( meme pattern que
-- referencevalue_count_stats ) . Cardinalite attendue : ~3K entries par
-- app , plafonnable via OPENADOM_CACHE_DATA_VERSIONING_SCOPE_MAX_ENTRIES_PER_APP .

CREATE TABLE IF NOT EXISTS ${applicationSchema}.data_versioning_scope_cache (
    application      UUID         NOT NULL,
    reference_type   TEXT         NOT NULL,
    column_name      TEXT         NOT NULL,
    user_id          UUID         NOT NULL,
    visible_values   JSONB        NOT NULL,
    computed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (application, reference_type, column_name, user_id)
);

CREATE INDEX IF NOT EXISTS data_versioning_scope_cache_reference_type_idx
    ON ${applicationSchema}.data_versioning_scope_cache (reference_type);

CREATE INDEX IF NOT EXISTS data_versioning_scope_cache_user_id_idx
    ON ${applicationSchema}.data_versioning_scope_cache (user_id);

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

GRANT SELECT
    ON ${applicationSchema}.data_versioning_scope_cache
    TO PUBLIC;
GRANT INSERT, UPDATE, DELETE, TRUNCATE
    ON ${applicationSchema}.data_versioning_scope_cache
    TO "openAdomAdmin";

-- -----------------------------------------------------------------
-- 6. oresiauthorization : index GIN sur la colonne array oresiusers
-- -----------------------------------------------------------------
-- AuthorizationRepository.findAuthorizationsByUserId utilise l'operateur
-- contained-by ARRAY[?] <@ oresiusers . Btree standard ne sert pas ; sans
-- GIN , seq scan integral ( acceptable a 0 rows local , catastrophique a
-- 50K rows prod ) . La classe d'opclass par defaut suffit ( type entityref
-- domain sur uuid ) .

CREATE INDEX IF NOT EXISTS oresiauthorization_oresiusers_gin_idx
    ON ${applicationSchema}.oresiauthorization USING gin (oresiusers);

-- -----------------------------------------------------------------
-- 7. referencevalue : statistiques etendues ( application , referencetype )
-- -----------------------------------------------------------------
-- Le planner sous-estime massivement la cardinalite des queries combinant
-- WHERE application = ? AND referencetype = ? : il considere les 2
-- predicats independants alors qu'il y a dependance fonctionnelle forte
-- ( 1 schema = 1 application_id ) . Mesure : rows=200 estime vs 5.6M reel
-- ( facteur 28000x ) , cause d'un spill 4.9 GB sur disque . Les extended
-- statistics ( dependencies + ndistinct + mcv ) capturent cette dependance
-- et fournissent les histogrammes au planner .

CREATE STATISTICS IF NOT EXISTS referencevalue_app_reftype_stats
    (dependencies, ndistinct, mcv)
    ON application, referencetype
    FROM ${applicationSchema}.referencevalue;

-- -----------------------------------------------------------------
-- 8. binaryfile : B-tree fonctionnel ( application , datatype , published )
-- -----------------------------------------------------------------
-- L'index GIN binary_file_params_index ( opclass jsonb_ops ) accelere
-- @> , <@ , ? , ?& , ?| mais ne couvre pas ->> ni #>> ( extraction texte
-- et chemin ) qu'utilise findPublishedVersions . Sans cet index ,
-- seq scan complet de binaryfile : OK a 116 rows local , 5s par appel a
-- 100K-1M rows prod . B-tree fonctionnel prefixe par application ( filtre
-- le plus selectif ) -> Bitmap Index Scan en lieu et place du Seq Scan .

CREATE INDEX IF NOT EXISTS binaryfile_app_datatype_published_idx
    ON ${applicationSchema}.binaryfile (
        application,
        ((params #>> '{binaryfiledataset, datatype}')),
        (((params ->> 'published')::boolean))
    );

-- -----------------------------------------------------------------
-- 9. binaryfile.processed_data : cache CSV processed ( oid Large Object )
-- -----------------------------------------------------------------
-- Publish FAST path : cache des DataValue JSON serialisees capture au
-- 1er upload , reutilise au republish ( bypass DataImporter ) . Heap
-- O ( chunk ) constant , scaling illimite . Backward compat : colonne
-- nullable . Fichiers pre-feature retournent NULL et tombent dans le
-- path LITE ( decision dans Phase2Handler ) .
--
-- Type oid ( Large Object ) plutot que bytea : le pg_largeobject backend
-- supporte des objets jusqu'a 4 TB et le write JDBC LargeObjectManager
-- est non-borne par le protocole frontend ( bytea via setBinaryStream
-- est limite a ~300 MB pratique , avait erreur " Object is too large to
-- send over the protocol " sur captures 1M+ lignes ) . Lifecycle :
-- lo_create au 1er storeProcessedData ; lo_unlink au clearProcessedData ;
-- les LOs orphelins ( crash entre lo_create et UPDATE column ) sont
-- recuperes par vacuumlo periodique .

ALTER TABLE ${applicationSchema}.binaryfile
    ADD COLUMN IF NOT EXISTS processed_data oid,
    ADD COLUMN IF NOT EXISTS processed_at   timestamp,
    ADD COLUMN IF NOT EXISTS processed_size bigint;

COMMENT ON COLUMN ${applicationSchema}.binaryfile.processed_data IS
    'Publish pipeline : oid referencing the Large Object that stores the
     processed JSON lines cache ( N x DataValue toJson() ) used by the
     Publish FAST path . NULL = no cache . Lifecycle : lo_create at first
     storeProcessedData call , lo_unlink at clearProcessedData ; orphan LOs
     reclaimed by vacuumlo .' ;

COMMENT ON COLUMN ${applicationSchema}.binaryfile.processed_at IS
    'Publish pipeline : timestamp de capture de processed_data .';

COMMENT ON COLUMN ${applicationSchema}.binaryfile.processed_size IS
    'Publish pipeline : taille en octets de processed_data , pour observabilite .';

-- -----------------------------------------------------------------
-- 10. Phase B L5 : FILLFACTOR=90 sur referencevalue + index unique
-- -----------------------------------------------------------------
-- Probleme cible : page splits sur la branche UPDATE de l'UPSERT
-- ON CONFLICT pendant le finalize cascade . Avec FILLFACTOR=100 (defaut) ,
-- aucune marge sur les pages -> UPDATE qui change la taille ( jsonb
-- recompresse plus court / plus long ) declenche un split + bloat index .
-- FILLFACTOR=90 reserve 10 % de marge par page -> HOT updates eligibles
-- quand les colonnes touchees ne sont pas indexees + moins de splits .
-- Cout : 10 % d'espace disque en plus . Iso-resultat ( storage only ) .
-- Gain : ~5-10 % steady-state sur les re-imports + bloat divise par 2-3
-- sur 6 mois .

ALTER TABLE ${applicationSchema}.referencevalue SET (fillfactor = 90);
ALTER INDEX ${applicationSchema}."hierarchicalKey_uniqueness" SET (fillfactor = 90);

-- Note : un REINDEX CONCURRENTLY + VACUUM FULL est necessaire pour
-- materialiser le fillfactor sur les rows / index pages existants .
-- On le declenche pas ici ( bloque pendant minutes sur grosses tables ) .
-- A planifier hors business hours via :
--   REINDEX INDEX CONCURRENTLY ${applicationSchema}."hierarchicalKey_uniqueness";
--   VACUUM FULL ${applicationSchema}.referencevalue;
-- Sans ce step , le fillfactor s'applique uniquement aux nouvelles pages
-- ( gain progressif au fil des inserts ) .

-- -----------------------------------------------------------------
-- 11. Phase D C-3 : autovacuum_scale_factor agressif sur referencevalue
-- -----------------------------------------------------------------
-- Default scale_factor=0.1 = autovacuum fire apres 10 % de DEAD rows .
-- Sur 1-10M rows c'est 100k-1M DEAD avant autovac trigger -> bloat +
-- planner stats stale entre 2 imports . On baisse a 0.02 ( 2 % ) pour
-- garder l'index/heap propre + stats fresh apres chaque import bulk .
-- analyze_scale_factor=0.01 pour rebuild les histogrammes plus souvent
-- ( planner UPSERT critique a la fraicheur des stats ) .
-- Iso-resultat ( autovac timing only , aucun changement requete ) .

ALTER TABLE ${applicationSchema}.referencevalue SET (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.01
);

-- -----------------------------------------------------------------
-- 12. Phase D E-2 : jsonb columns en compression lz4 ( PG14+ )
-- -----------------------------------------------------------------
-- pglz ( default ) plus lent que lz4 a la decompression ( 5-15 % CPU
-- save sur le hot path UPSERT qui re-lit refvalues / refslinkedto par
-- row pour le ON CONFLICT DO UPDATE ) . Affecte UNIQUEMENT les nouvelles
-- valeurs ecrites apres cette migration ; pour migrer l'existant ,
-- VACUUM FULL est requis ( cf note section 10 ) .
-- Iso-resultat ( compression algo only , semantique jsonb identique ) .

ALTER TABLE ${applicationSchema}.referencevalue
    ALTER COLUMN refvalues       SET COMPRESSION lz4,
    ALTER COLUMN refslinkedto    SET COMPRESSION lz4;

-- -----------------------------------------------------------------
-- 13. ANALYZE final : recharge les histogrammes pour le planner
-- -----------------------------------------------------------------
-- Necessaire apres CREATE STATISTICS + multiples CREATE INDEX + ALTER
-- TABLE afin que le planner utilise les nouveaux artefacts des la
-- prochaine query .

ANALYZE ${applicationSchema}.referencevalue;
ANALYZE ${applicationSchema}.reference_reference;
ANALYZE ${applicationSchema}.oresiauthorization;
ANALYZE ${applicationSchema}.binaryfile;
