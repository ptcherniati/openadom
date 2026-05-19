-- ============================================================================
-- V11 - Extension pg_prewarm
-- ============================================================================
--
-- Installe l'extension PostgreSQL pg_prewarm permettant de charger
-- proactivement les pages d'une table ou d'un index dans le shared_buffers
-- de PostgreSQL .
--
-- Usage cible : au boot du backend , un service Spring asynchrone
-- ( PgPrewarmBootstrapService ) appelle pg_prewarm sur referencevalue de
-- chaque schema applicatif . Sur un container fraichement redemarre , le
-- shared_buffers PG est vide ; le premier appel a
-- DataRepository.getDataIdPerKeys ( ~110 s sur 3 . 6 M rows cause des
-- ~10 GB d'IO disque cold cache ) bloque la prep cascade pendant que
-- l'utilisateur attend . Pre-charger les pages au boot deplace cette
-- penalite IO hors hot path .
--
-- Idempotent ( IF NOT EXISTS ) , safe a re-run . Si l'utilisateur Flyway
-- n'a pas le privilege CREATE EXTENSION ( prod minimal ) , la migration
-- echoue lisiblement et le service Spring bascule en no-op gracefully
-- ( log warn , pas de crash backend ) .
-- ============================================================================

-- Wrapped in DO + EXCEPTION because CREATE EXTENSION pg_prewarm requires
-- superuser . In production with a restrictive role ( typical INRAE setup ) ,
-- as well as in Testcontainers PostgreSQL ( which runs as a non-superuser
-- application role ) , the CREATE fails with insufficient_privilege . We
-- swallow that specific error so the migration succeeds and the backend
-- boots ; the PgPrewarmBootstrapService already degrades to no-op when
-- pg_prewarm is absent ( catches RuntimeException + log warn ) .
-- Other errors ( unknown extension , already exists , disk full , ... )
-- still propagate so genuine issues are not hidden .
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_prewarm;
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'pg_prewarm extension not installed ( role lacks superuser ) - backend will skip prewarm at boot';
END $$;
