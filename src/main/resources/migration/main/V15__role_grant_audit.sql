-- =========================================================================
-- V15 : oa_audit.role_grant_audit + wrapper function
-- =========================================================================
--
-- Trace append-only des operations GRANT / REVOKE sur les roles
-- ( applicatifs + globaux ) declenchees via l'onglet "Utilisateurs" de
-- oa-live . Permet d'afficher dans le detail user "qui a accorde ce
-- role , quand , et a quel scope" sans recourir aux logs systeme .
--
-- Postgres ne stocke ni la date du GRANT ni le timestamp dans
-- pg_auth_members ( seul {@code grantor} est expose ) . Cette table est
-- donc la source de verite pour la traçabilite metier .
--
-- Tracabilite :
--   - 1 row par operation ( GRANT ou REVOKE )
--   - immutable ( jamais d'UPDATE ; on insere une nouvelle row pour
--     refleter la sequence d'evenements )
--   - granted_by = UUID de l'admin / applicationManager qui a fait
--     l'action ( recupere cote service via
--     {@code AuthenticationService.getCurrentUserRoles().userId()} )
--   - application_id NULL = role global ( ex {@code openAdomAdmin} )
--
-- Backfill : aucun . Les roles existants au deploiement n'ont pas de
-- trace ; le service renvoie {@code grantedAt=null , grantedBy=null}
-- a l'API pour ces cas-la et l'UI affiche "Date non tracée ( accordé
-- avant l'activation de l'audit )" .
--
-- Ecriture : reservee a la fonction SECURITY DEFINER ci-dessous .
-- Lecture : ouverte a PUBLIC ( aucune PII au-dela des UUIDs deja
-- exposes par l'API admin ) .

-- ---------- table ------------------------------------------------------

CREATE TABLE IF NOT EXISTS oa_audit.role_grant_audit (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL,
    role_name       varchar(64) NOT NULL,
        -- nom logique du role : applicationManager , userManager , reader ,
        -- writer , openAdomAdmin . Pas le SQL identifier ( reconstruit cote
        -- service en concatenant application_id + role_name ) .
    application_id  uuid,
        -- NULL = role global ( openAdomAdmin , ... )
    action          varchar(8)  NOT NULL,
        -- GRANT ou REVOKE
    granted_at      timestamptz NOT NULL DEFAULT now(),
    granted_by      uuid,
        -- NULL toleree pour les backfills futurs ou les operations system
        -- ( ex auto-provisioning ) , mais en pratique toujours rempli par
        -- l'UI .
    CONSTRAINT chk_role_grant_audit_action CHECK (action IN ('GRANT','REVOKE'))
);

-- Index pour "trace d'un user" ( cas dominant : findDetail ) .
CREATE INDEX IF NOT EXISTS idx_role_grant_audit_user
    ON oa_audit.role_grant_audit (user_id, granted_at DESC);

-- Index pour "trace par application" ( debug admin app-scope ) .
CREATE INDEX IF NOT EXISTS idx_role_grant_audit_app
    ON oa_audit.role_grant_audit (application_id, granted_at DESC)
    WHERE application_id IS NOT NULL;

COMMENT ON TABLE oa_audit.role_grant_audit IS
    'Append-only trail des GRANT / REVOKE de roles applicatifs et globaux . '
    '1 row par operation ( immutable ) . Source de verite metier pour '
    'l''onglet Utilisateurs ( Postgres ne stocke pas le timestamp natif ) .';

-- ---------- grants -----------------------------------------------------

GRANT SELECT, INSERT ON oa_audit.role_grant_audit TO "openAdomTechUser";

-- Lecture ouverte a PUBLIC ( pattern identique workflow_log /
-- user_session_log : evite de wrapper chaque query d'affichage admin ) .
GRANT SELECT ON oa_audit.role_grant_audit TO PUBLIC;

-- ---------- wrapper function ------------------------------------------
--
-- Pattern identique aux fonctions oa_audit existantes : caller ( random-user
-- contexte sweeper OU applicationManager_X contexte UI ) ne peut pas faire
-- d'INSERT direct ( aucun GRANT INSERT a PUBLIC ) . La fonction tourne
-- sous identite openAdomTechUser ( owner ) et contraint la forme du
-- payload .

CREATE OR REPLACE FUNCTION oa_audit.record_role_grant_audit(
    p_user_id        uuid,
    p_role_name      varchar(64),
    p_application_id uuid,
    p_action         varchar(8),
    p_granted_by     uuid
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = oa_audit, pg_temp
AS $$
DECLARE new_id uuid;
BEGIN
    IF p_action NOT IN ('GRANT','REVOKE') THEN
        RAISE EXCEPTION 'invalid action %, expected GRANT or REVOKE', p_action;
    END IF;
    INSERT INTO oa_audit.role_grant_audit (
        user_id, role_name, application_id, action, granted_by
    ) VALUES (
        p_user_id, p_role_name, p_application_id, p_action, p_granted_by
    )
    RETURNING id INTO new_id;
    RETURN new_id;
END;
$$;

ALTER FUNCTION oa_audit.record_role_grant_audit(uuid, varchar, uuid, varchar, uuid)
    OWNER TO "openAdomTechUser";

REVOKE ALL ON FUNCTION oa_audit.record_role_grant_audit(uuid, varchar, uuid, varchar, uuid)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION oa_audit.record_role_grant_audit(uuid, varchar, uuid, varchar, uuid)
    TO PUBLIC;
