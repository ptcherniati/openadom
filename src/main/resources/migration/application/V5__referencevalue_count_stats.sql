-- =============================================================================
-- AUDIT OA_FULL_REVIEW (8/5/26) - count des lignes par referencetype.
-- =============================================================================
--
-- Avant : DataRepository.buildReferenceSynthesis() executait
--   SELECT referencetype, count(*) FROM referencevalue GROUP BY referencetype
-- Sur si_acbb ( 10M rows ) : ~12s a chaud / 2-5min a 200M rows ( cible ACBB ).
--
-- Solution : table de stats persistante par schema , maintenue par 2 triggers
-- statement-level ( AFTER INSERT et AFTER DELETE ) sur referencevalue.
-- Lookup PRIMARY KEY <1ms quel que soit le volume source.
--
-- IMPORTANT : tous les identifiants ( table , trigger , fonction ) sont
-- qualifies par le placeholder Flyway ${applicationSchema} pour eviter les
-- problemes de search_path lors de l'execution des triggers ( la fonction
-- trigger , appelee par PostgreSQL au moment d'un INSERT/DELETE , herite
-- du search_path du caller ; sans qualification , elle ne trouverait pas
-- la table referencevalue_count_stats si le caller a un search_path
-- different , ce qui se produit dans le pipeline cascade ).
-- =============================================================================

-- 1. Table de stats : 1 ligne par valeur distincte de referencetype.
CREATE TABLE IF NOT EXISTS ${applicationSchema}.referencevalue_count_stats (
    referencetype TEXT PRIMARY KEY,
    line_count    BIGINT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Bootstrap initial : peuple la table depuis l'etat actuel de
--    referencevalue. Pour les apps existantes ( si_acbb ) avec deja N rows,
--    on calcule le count exact une fois pour toutes.
INSERT INTO ${applicationSchema}.referencevalue_count_stats (referencetype, line_count, updated_at)
SELECT referencetype, count(*), now()
FROM ${applicationSchema}.referencevalue
GROUP BY referencetype
ON CONFLICT (referencetype) DO UPDATE
SET line_count = EXCLUDED.line_count,
    updated_at = EXCLUDED.updated_at;

-- 3. Fonction trigger AFTER INSERT statement-level.
--    Toutes les references qualifiees par ${applicationSchema} pour
--    fonctionner quel que soit le search_path du caller.
--
--    SECURITY DEFINER : la fonction s'execute avec les droits du USER qui
--    l'a creee ( typiquement openAdomTechUser SUPERUSER ), pas avec ceux
--    du caller. Indispensable car les triggers fire pendant des INSERT/
--    DELETE declenches par des utilisateurs non-admin ( ex. depot ou
--    depublication par un applicationDataWriter ) qui n'ont pas le droit
--    UPDATE sur referencevalue_count_stats. Sans SECURITY DEFINER , le
--    trigger leve "permission denied" -> BadSqlGrammarException -> 406.
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

-- 4. Fonction trigger AFTER DELETE statement-level.
--    Cf. commentaire SECURITY DEFINER ci-dessus.
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

-- 5. Triggers binds.
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

-- 6. Grants : lecture publique , ecriture reservee a openAdomAdmin
--    ( pour le bouton recompute manuel ).
GRANT SELECT ON ${applicationSchema}.referencevalue_count_stats TO PUBLIC;
GRANT INSERT, UPDATE, DELETE, TRUNCATE ON ${applicationSchema}.referencevalue_count_stats TO "openAdomAdmin";
