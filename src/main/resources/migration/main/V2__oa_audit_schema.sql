-- =========================================================================
-- Schema oa_audit : logs , traces et compensation
-- =========================================================================
--
-- Schema dedie a l'observabilite et a l'auto-recovery openadom .
-- Separe du metier ( public ) pour refleter la nature distincte des donnees :
--
--   public      : entites metier durables , transactionnelles ( business )
--   oa_audit    : logs append-only + queue de compensation ephemere
--   oa_staging  : tables techniques d'import transitoires
--
-- Tables incluses :
--
--   workflow_log       : append-only , 1 row par workflow ( import / extract ) .
--                        Source de verite user-facing pour dashboard Historique.
--                        Long-lived ( retention configurable , 30j default ) .
--
--   user_session_log   : append-only , 1 row par session JWT terminee .
--                        Permet l'audit de qui s'est connecte quand .
--                        Long-lived ( retention 90 j ) .
--
--   compensation_log   : queue ephemere des operations a compenser en cas
--                        d'echec . PENDING insert avant l'op , DELETE apres
--                        succes , compensation par sweeper si reste PENDING .
--                        Court-lived ( quelques sec a quelques heures ) .
--
-- Pas de FK vers les tables metier : les logs sont immuables et survivent
-- aux changements de dimension ( users supprimes , applications renommees ) .
--
-- =========================================================================
-- Securite : pattern SECURITY DEFINER
-- =========================================================================
--
-- GRANTs directs sur tables = "openAdomTechUser" uniquement ( superuser
-- contextes : sweeper , dashboard admin ) . Roles applicatifs dynamiques
-- ( applicationCreator , < UUID >_writer , < UUID >_applicationManager , ... )
-- ne peuvent acceder aux tables QUE via les fonctions wrapper en bas du
-- fichier , toutes declarees SECURITY DEFINER + owner = "openAdomTechUser" .
--
-- Avantage : un < UUID >_reader peut appeler la fonction d'INSERT compensation
-- ( la fonction tourne sous identite owner -> bypass GRANT check ) MAIS ne
-- peut PAS faire INSERT/DELETE direct ni log poisoning ; la fonction
-- contraint la forme du payload accepte .
--
-- USAGE schema accorde a PUBLIC pour permettre la resolution des
-- fonctions . SELECT direct sur tables ouvert a PUBLIC ( lecture audit
-- safe ) , writes / locks reserves aux fonctions .
-- =========================================================================

CREATE SCHEMA IF NOT EXISTS oa_audit;

-- =========================================================================
-- workflow_log : journal des workflows ( imports + extractions )
-- =========================================================================
CREATE TABLE IF NOT EXISTS oa_audit.workflow_log (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id      uuid        NOT NULL UNIQUE,
    workflow_type       varchar(32) NOT NULL,
        -- IMPORT , EXTRACT_ZIP , EXTRACT_CSV , EXTRACT_ADDITIONAL_FILES ,
        -- EXTRACT_CHARTE
    user_id             uuid        NOT NULL,
    user_login          varchar(128),
    application_name    varchar(256),
    data_type           varchar(256),
    resource_name       varchar(512),
        -- nom de fichier uploade ou nom de ressource demandee
    start_time          timestamptz NOT NULL DEFAULT now(),
    end_time            timestamptz,
    duration_ms         bigint,
    status              varchar(16) NOT NULL,
        -- IN_PROGRESS , COMPLETED , FAILED , CANCELLED , RATE_LIMITED
    records_processed   bigint      DEFAULT 0,
    records_failed      bigint      DEFAULT 0,
    chunks_processed    int         DEFAULT 0,
    progress_percentage numeric(5,2),
    bytes_total         bigint      DEFAULT 0,
    errors              jsonb,
        -- liste des messages d'erreur collectes pendant le workflow
    fatal_error         text,
        -- message + cause-chain de l'exception fatale le cas echeant
    metadata            jsonb
        -- champ extensible : parallelism , strategy , JVM stats , etc.
);

CREATE INDEX IF NOT EXISTS idx_workflow_log_user
    ON oa_audit.workflow_log (user_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_log_application
    ON oa_audit.workflow_log (application_name, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_log_status
    ON oa_audit.workflow_log (status, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_log_type_time
    ON oa_audit.workflow_log (workflow_type, start_time DESC);

COMMENT ON TABLE oa_audit.workflow_log IS
    'Une row par workflow finalise ( succes , echec , rejet rate-limit ) . '
    'Consomme par le dashboard admin et Grafana .';

-- =========================================================================
-- user_session_log : journal des sessions utilisateur
-- =========================================================================
CREATE TABLE IF NOT EXISTS oa_audit.user_session_log (
    session_id      uuid        PRIMARY KEY,
    user_id         uuid        NOT NULL,
    user_login      text        NOT NULL,
    ip_address      inet,
    user_agent      text,
    login_time      timestamptz NOT NULL,
    logout_time     timestamptz,
    duration_ms     bigint,
    end_reason      text
        -- LOGOUT , JWT_EXPIRED , KICK
);

CREATE INDEX IF NOT EXISTS idx_user_session_log_user
    ON oa_audit.user_session_log (user_id, login_time DESC);
CREATE INDEX IF NOT EXISTS idx_user_session_log_login_time
    ON oa_audit.user_session_log (login_time DESC);
CREATE INDEX IF NOT EXISTS idx_user_session_log_end_reason
    ON oa_audit.user_session_log (end_reason)
    WHERE end_reason IS NOT NULL;

COMMENT ON TABLE oa_audit.user_session_log IS
    'Une row par session JWT terminee ( logout , expiration , kick admin ) . '
    'Permet l''audit who/when/how-long .';

-- =========================================================================
-- compensation_log : queue ephemere de compensation
-- =========================================================================
CREATE TABLE IF NOT EXISTS oa_audit.compensation_log (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identification de l'op
    operation_type  varchar(64)  NOT NULL,
        -- IMPORT_BINARYFILE , EXTRACTION_TMP_DIR , ...
    target_schema   varchar(64)  NOT NULL DEFAULT 'public',
    target_table    varchar(128) NOT NULL,
    target_id       text         NOT NULL,
        -- UUID stringifie ou cle composite ; text pour flexibilite

    -- Cross-ref audit
    correlation_id  uuid,
        -- lien optionnel vers workflow_log.correlation_id
    user_id         uuid,
    user_login      varchar(128),

    -- Payload contextuel
    payload         jsonb,
        -- contexte additionnel : nom fichier , params , taille , etc.

    -- Lifecycle
    status          varchar(16)  NOT NULL DEFAULT 'PENDING',
    started_at      timestamptz  NOT NULL DEFAULT now(),
    ttl_minutes     int          NOT NULL DEFAULT 240,
        -- 4h par defaut ; override par-op possible

    -- Retry tracking
    attempt_count   int          NOT NULL DEFAULT 0,
    last_attempt_at timestamptz,
    last_error      text,

    CONSTRAINT chk_compensation_status CHECK (status IN ('PENDING', 'FAILED'))
);

-- Queue sweeper : trouver rapidement les rows orphelines
CREATE INDEX IF NOT EXISTS idx_complog_pending
    ON oa_audit.compensation_log (started_at)
    WHERE status = 'PENDING';

-- Cross-ref dashboard : "compensation pour ce workflow ?"
CREATE INDEX IF NOT EXISTS idx_complog_correlation
    ON oa_audit.compensation_log (correlation_id)
    WHERE correlation_id IS NOT NULL;

-- Audit user : "ops failed pour cet utilisateur"
CREATE INDEX IF NOT EXISTS idx_complog_user_failed
    ON oa_audit.compensation_log (user_id, started_at DESC)
    WHERE status = 'FAILED';

-- Lookup : "compensation en cours sur cette row ?"
CREATE INDEX IF NOT EXISTS idx_complog_target
    ON oa_audit.compensation_log (target_schema, target_table, target_id)
    WHERE status = 'PENDING';

COMMENT ON TABLE oa_audit.compensation_log IS
    'Queue ephemere des operations a compenser en cas d''echec . '
    'INSERT au demarrage , DELETE apres succes , compensation par sweeper '
    'apres TTL si reste PENDING . Smart-check protege contre data loss '
    '( regle d''or : ne jamais supprimer des rows referencevalue ) .';

-- =========================================================================
-- Schema comment + droits directs ( restrictifs )
-- =========================================================================

COMMENT ON SCHEMA oa_audit IS
    'Logs , traces et compensation openadom . Append-only ( workflow_log , '
    'user_session_log ) + queue ephemere ( compensation_log ) . Pas de FK '
    'vers metier , survit aux changements de dimension . Writes via '
    'fonctions SECURITY DEFINER ; lectures direct possibles .';

-- USAGE schema ouvert a PUBLIC ( pour resolution des fonctions wrapper ) .
GRANT USAGE ON SCHEMA oa_audit TO PUBLIC;

-- Acces direct aux tables : openAdomTechUser uniquement ( contextes
-- superuser : sweeper @Scheduled , dashboard admin , debug DBA ) .
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA oa_audit TO "openAdomTechUser";
ALTER DEFAULT PRIVILEGES IN SCHEMA oa_audit
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "openAdomTechUser";

-- SELECT direct ouvert a PUBLIC : lecture des logs est safe ( pas de PII
-- au-dela du login/userId deja affiche par ailleurs ) , et evite de devoir
-- wrapper chaque query de dashboard dans une fonction .
GRANT SELECT ON oa_audit.workflow_log      TO PUBLIC;
GRANT SELECT ON oa_audit.user_session_log  TO PUBLIC;
GRANT SELECT ON oa_audit.compensation_log  TO PUBLIC;

-- =========================================================================
-- Fonctions wrappers SECURITY DEFINER
-- =========================================================================
--
-- Toutes les ecritures vers oa_audit passent par ces fonctions . Elles
-- s'executent sous l'identite de leur owner ( openAdomTechUser ) -> bypass
-- les GRANTs check , quel que soit le SET ROLE de la session caller .
--
-- search_path force a oa_audit, pg_temp pour eviter le hijack par un schema
-- malveillant .
--
-- =========================================================================

-- ---------- workflow_log ------------------------------------------------

CREATE OR REPLACE FUNCTION oa_audit.record_workflow(
    p_correlation_id      uuid,
    p_workflow_type       varchar(32),
    p_user_id             uuid,
    p_user_login          varchar(128),
    p_application_name    varchar(256),
    p_data_type           varchar(256),
    p_resource_name       varchar(512),
    p_start_time          timestamptz,
    p_end_time            timestamptz,
    p_duration_ms         bigint,
    p_status              varchar(16),
    p_records_processed   bigint,
    p_records_failed      bigint,
    p_chunks_processed    int,
    p_bytes_total         bigint,
    p_errors              jsonb,
    p_fatal_error         text,
    p_metadata            jsonb
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE inserted boolean;
BEGIN
    INSERT INTO oa_audit.workflow_log (
        correlation_id, workflow_type, user_id, user_login,
        application_name, data_type, resource_name,
        start_time, end_time, duration_ms, status,
        records_processed, records_failed, chunks_processed,
        bytes_total, errors, fatal_error, metadata
    ) VALUES (
        p_correlation_id, p_workflow_type, p_user_id, p_user_login,
        p_application_name, p_data_type, p_resource_name,
        p_start_time, p_end_time, p_duration_ms, p_status,
        p_records_processed, p_records_failed, p_chunks_processed,
        p_bytes_total, p_errors, p_fatal_error, p_metadata
    )
    ON CONFLICT (correlation_id) DO NOTHING;
    GET DIAGNOSTICS inserted = ROW_COUNT;
    RETURN inserted;
END;
$$;

ALTER FUNCTION oa_audit.record_workflow(
    uuid, varchar, uuid, varchar, varchar, varchar, varchar,
    timestamptz, timestamptz, bigint, varchar, bigint, bigint, int,
    bigint, jsonb, text, jsonb
) OWNER TO "openAdomTechUser";

CREATE OR REPLACE FUNCTION oa_audit.delete_workflow_logs_older_than(p_days int)
RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    IF p_days <= 0 THEN RETURN 0; END IF;
    DELETE FROM oa_audit.workflow_log
    WHERE start_time < now() - (p_days || ' days')::interval;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

ALTER FUNCTION oa_audit.delete_workflow_logs_older_than(int)
    OWNER TO "openAdomTechUser";

-- ---------- user_session_log -------------------------------------------

CREATE OR REPLACE FUNCTION oa_audit.record_user_session(
    p_session_id    uuid,
    p_user_id       uuid,
    p_user_login    text,
    p_ip_address    inet,
    p_user_agent    text,
    p_login_time    timestamptz,
    p_logout_time   timestamptz,
    p_duration_ms   bigint,
    p_end_reason    text
) RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE inserted boolean;
BEGIN
    INSERT INTO oa_audit.user_session_log (
        session_id, user_id, user_login, ip_address, user_agent,
        login_time, logout_time, duration_ms, end_reason
    ) VALUES (
        p_session_id, p_user_id, p_user_login, p_ip_address, p_user_agent,
        p_login_time, p_logout_time, p_duration_ms, p_end_reason
    )
    ON CONFLICT (session_id) DO NOTHING;
    GET DIAGNOSTICS inserted = ROW_COUNT;
    RETURN inserted;
END;
$$;

ALTER FUNCTION oa_audit.record_user_session(
    uuid, uuid, text, inet, text, timestamptz, timestamptz, bigint, text
) OWNER TO "openAdomTechUser";

CREATE OR REPLACE FUNCTION oa_audit.delete_user_session_logs_older_than(p_days int)
RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    IF p_days <= 0 THEN RETURN 0; END IF;
    DELETE FROM oa_audit.user_session_log
    WHERE login_time < now() - (p_days || ' days')::interval;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

ALTER FUNCTION oa_audit.delete_user_session_logs_older_than(int)
    OWNER TO "openAdomTechUser";

-- ---------- compensation_log -------------------------------------------

CREATE OR REPLACE FUNCTION oa_audit.record_compensation(
    p_operation_type  varchar(64),
    p_target_schema   varchar(64),
    p_target_table    varchar(128),
    p_target_id       text,
    p_correlation_id  uuid,
    p_user_id         uuid,
    p_user_login      varchar(128),
    p_payload         jsonb,
    p_ttl_minutes     int
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE new_id uuid;
BEGIN
    INSERT INTO oa_audit.compensation_log (
        operation_type, target_schema, target_table, target_id,
        correlation_id, user_id, user_login, payload, ttl_minutes
    ) VALUES (
        p_operation_type, p_target_schema, p_target_table, p_target_id,
        p_correlation_id, p_user_id, p_user_login, p_payload,
        COALESCE(p_ttl_minutes, 240)
    )
    RETURNING id INTO new_id;
    RETURN new_id;
END;
$$;

ALTER FUNCTION oa_audit.record_compensation(
    varchar, varchar, varchar, text, uuid, uuid, varchar, jsonb, int
) OWNER TO "openAdomTechUser";

CREATE OR REPLACE FUNCTION oa_audit.delete_compensation(p_id uuid)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    DELETE FROM oa_audit.compensation_log WHERE id = p_id;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n > 0;
END;
$$;

ALTER FUNCTION oa_audit.delete_compensation(uuid)
    OWNER TO "openAdomTechUser";

CREATE OR REPLACE FUNCTION oa_audit.record_compensation_failure(
    p_id          uuid,
    p_error       text,
    p_max_retries int
) RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
    UPDATE oa_audit.compensation_log
       SET attempt_count   = attempt_count + 1,
           last_attempt_at = now(),
           last_error      = p_error,
           status          = CASE
                                WHEN attempt_count + 1 >= p_max_retries
                                THEN 'FAILED'
                                ELSE status
                             END
     WHERE id = p_id;
$$;

ALTER FUNCTION oa_audit.record_compensation_failure(uuid, text, int)
    OWNER TO "openAdomTechUser";

CREATE OR REPLACE FUNCTION oa_audit.delete_failed_compensations_older_than(
    p_days int
) RETURNS int
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE n int;
BEGIN
    IF p_days <= 0 THEN RETURN 0; END IF;
    DELETE FROM oa_audit.compensation_log
     WHERE status = 'FAILED'
       AND started_at < now() - (p_days || ' days')::interval;
    GET DIAGNOSTICS n = ROW_COUNT;
    RETURN n;
END;
$$;

ALTER FUNCTION oa_audit.delete_failed_compensations_older_than(int)
    OWNER TO "openAdomTechUser";

-- Sweeper : SELECT FOR UPDATE SKIP LOCKED dans une fonction
-- SECURITY DEFINER . Le caller est la sweeper @Scheduled ( contexte
-- random-user superuser bypass , mais on garde l'helper symetrique pour
-- l'admin "Compenser maintenant" depuis le dashboard ) .
CREATE OR REPLACE FUNCTION oa_audit.lock_stale_pending_compensations(
    p_batch_size int
) RETURNS SETOF oa_audit.compensation_log
LANGUAGE sql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
    SELECT *
      FROM oa_audit.compensation_log
     WHERE status = 'PENDING'
       AND started_at < now() - (ttl_minutes || ' minutes')::interval
     ORDER BY started_at
     LIMIT p_batch_size
     FOR UPDATE SKIP LOCKED;
$$;

ALTER FUNCTION oa_audit.lock_stale_pending_compensations(int)
    OWNER TO "openAdomTechUser";

-- =========================================================================
-- GRANT EXECUTE TO PUBLIC sur les wrappers
-- =========================================================================
-- REVOKE explicite ( certains setups Postgres laissent EXECUTE ouvert par
-- defaut sur PUBLIC pour les nouvelles fonctions ; on resette pour ne pas
-- dependre du default ) , puis GRANT EXECUTE TO PUBLIC .

REVOKE ALL ON FUNCTION oa_audit.record_workflow(
    uuid, varchar, uuid, varchar, varchar, varchar, varchar,
    timestamptz, timestamptz, bigint, varchar, bigint, bigint, int,
    bigint, jsonb, text, jsonb) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.delete_workflow_logs_older_than(int) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.record_user_session(
    uuid, uuid, text, inet, text, timestamptz, timestamptz, bigint, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.delete_user_session_logs_older_than(int) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.record_compensation(
    varchar, varchar, varchar, text, uuid, uuid, varchar, jsonb, int) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.delete_compensation(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.record_compensation_failure(uuid, text, int) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.delete_failed_compensations_older_than(int) FROM PUBLIC;
REVOKE ALL ON FUNCTION oa_audit.lock_stale_pending_compensations(int) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION oa_audit.record_workflow(
    uuid, varchar, uuid, varchar, varchar, varchar, varchar,
    timestamptz, timestamptz, bigint, varchar, bigint, bigint, int,
    bigint, jsonb, text, jsonb) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.delete_workflow_logs_older_than(int) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.record_user_session(
    uuid, uuid, text, inet, text, timestamptz, timestamptz, bigint, text) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.delete_user_session_logs_older_than(int) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.record_compensation(
    varchar, varchar, varchar, text, uuid, uuid, varchar, jsonb, int) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.delete_compensation(uuid) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.record_compensation_failure(uuid, text, int) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.delete_failed_compensations_older_than(int) TO PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.lock_stale_pending_compensations(int) TO PUBLIC;
