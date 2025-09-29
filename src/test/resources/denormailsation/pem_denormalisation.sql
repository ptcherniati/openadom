drop schema if exists monsore_dn cascade;
create schema monsore_dn;
-- génération des tables
create table monsore_dn.referenceDisplay as
    (select id,
            hierarchicalkey,
            COALESCE(
                    NULLIF(refvalues ->> '__display_fr', ''),
                    refvalues ->> '__display_default'
            ) display_fr,
            COALESCE(
                    NULLIF(refvalues ->> '__display_en', ''),
                    NULLIF(refvalues ->> '__display_fr', ''),
                    refvalues ->> '__display_default'
            ) display_en
     from monsore.referencevalue);
-- especes
create table monsore_dn.especes as (select referencevalue.id,
                                           referencevalue.naturalkey,
                                           referencevalue.hierarchicalkey,
                                           esp_nom,
                                           esp_definition_fr,
                                           esp_definition_en
                                    FROM monsore.referencevalue,
                                         JSON_TABLE(refvalues, '$'
                                             COLUMNS (
	esp_nom text PATH '$.esp_nom',
	esp_definition_fr text PATH '$.esp_definition_fr',
	esp_definition_en text PATH '$.esp_definition_en'
	)
                                         ) AS val
                                    where referencetype = 'especes');

ALTER TABLE monsore_dn.especes
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.especes
    ADD CONSTRAINT especes_pk PRIMARY KEY (id);


-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.especes TO public;
ALTER TABLE monsore_dn.especes
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'especes'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "especes_%s"
            ON monsore_dn.especes
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


-- projet
create table monsore_dn.projet as (select referencevalue.id,
                                          referencevalue.naturalkey,
                                          referencevalue.hierarchicalkey,
                                          nom_key,
                                          nom_fr,
                                          nom_en,
                                          definition_fr,
                                          definition_en
                                   FROM monsore.referencevalue,
                                        JSON_TABLE(refvalues, '$'
                                            COLUMNS (
	nom_key text PATH '$.nom_key',
	nom_fr text PATH '$.nom_fr',
	nom_en text PATH '$.nom_en',
	definition_fr text PATH '$.definition_fr',
	definition_en text PATH '$.definition_en'
	)
                                        ) AS val
                                   where referencetype = 'projet');

ALTER TABLE monsore_dn.projet
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.projet
    ADD CONSTRAINT projet_pk PRIMARY KEY (id);


-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.projet TO public;
ALTER TABLE monsore_dn.projet
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'projet'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "projet_%s"
            ON monsore_dn.projet
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;

-- themes
create table monsore_dn.themes as (select referencevalue.id,
                                          referencevalue.naturalkey,
                                          referencevalue.hierarchicalkey,
                                          nom_key,
                                          nom_fr,
                                          nom_en,
                                          description_fr,
                                          description_en
                                   FROM monsore.referencevalue,
                                        JSON_TABLE(refvalues, '$'
                                            COLUMNS (
	nom_key text PATH '$.nom_key',
	nom_fr text PATH '$.nom_fr',
	nom_en text PATH '$.nom_en',
	description_fr text PATH '$.description_fr',
	description_en text PATH '$.description_en'
	)
                                        ) AS val
                                   where referencetype = 'themes');

ALTER TABLE monsore_dn.themes
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.themes
    ADD CONSTRAINT themes_pk PRIMARY KEY (id);


-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.themes TO public;
ALTER TABLE monsore_dn.themes
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'themes'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "themes_%s"
            ON monsore_dn.themes
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;
-- unites
create table monsore_dn.unites as (select referencevalue.id,
                                          referencevalue.naturalkey,
                                          referencevalue.hierarchicalkey,
                                          nom_key,
                                          nom_fr,
                                          nom_en,
                                          code_key,
                                          code_fr,
                                          code_en
                                   FROM monsore.referencevalue,
                                        JSON_TABLE(refvalues, '$'
                                            COLUMNS (
	nom_key text PATH '$.nom_key',
	nom_fr text PATH '$.nom_fr',
	nom_en text PATH '$.nom_en',
	code_key text PATH '$.code_key',
	code_fr text PATH '$.code_fr',
	code_en text PATH '$.code_en'
	)
                                        ) AS val
                                   where referencetype = 'unites');

ALTER TABLE monsore_dn.unites
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.unites
    ADD CONSTRAINT unites_pk PRIMARY KEY (id);


-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.unites TO public;
ALTER TABLE monsore_dn.unites
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'unites'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "unites_%s"
            ON monsore_dn.unites
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;
--type_de_sites
create table monsore_dn.type_de_sites as (select referencevalue.id,
                                                 referencevalue.naturalkey,
                                                 referencevalue.hierarchicalkey,
                                                 tze_nom_key,
                                                 tze_nom_fr,
                                                 tze_nom_en,
                                                 tze_definition_fr,
                                                 tze_definition_en
                                          FROM monsore.referencevalue,
                                               JSON_TABLE(refvalues, '$'
                                                   COLUMNS (
	tze_nom_key text PATH '$.tze_nom_key',
	tze_nom_fr text PATH '$.tze_nom_key',
	tze_nom_en text PATH '$.tze_nom_key',
	tze_definition_fr text PATH '$.tze_definition_fr',
	tze_definition_en text PATH '$.tze_definition_en'
	)
                                               ) AS val
                                          where referencetype = 'type_de_sites');
ALTER TABLE monsore_dn.type_de_sites
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.type_de_sites
    ADD CONSTRAINT type_de_sites_pk PRIMARY KEY (id);


-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.type_de_sites TO public;
ALTER TABLE monsore_dn.type_de_sites
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'type_de_sites'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "type_de_sites_%s"
            ON monsore_dn.type_de_sites
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--sites

create table monsore_dn.sites as (select referencevalue.id,
                                         referencevalue.naturalkey,
                                         referencevalue.hierarchicalkey,
                                         zet_nom_key,
                                         zet_nom_fr,
                                         zet_nom_en,
                                         zet_description_fr,
                                         zet_description_en,
                                         refs.tze_type_nom            tze_type_nom,
                                         tze_type_nom.display_fr      tze_type_nom_fr,
                                         tze_type_nom.display_en      tze_type_nom_en,
                                         refs.zet_chemin_parent       zet_chemin_parent,
                                         zet_chemin_parent.display_fr zet_chemin_parent_fr,
                                         zet_chemin_parent.display_en zet_chemin_parent_en
                                  FROM monsore.referencevalue,
                                       JSON_TABLE(refvalues, '$'
                                           COLUMNS (
	zet_nom_key text PATH '$.zet_nom_key',
	zet_nom_fr text PATH '$.zet_nom_key',
	zet_nom_en text PATH '$.zet_nom_key',
	zet_description_fr text PATH '$.zet_description_fr',
	zet_description_en text PATH '$.zet_description_en'
	)
                                       ) AS val,
                                       JSON_TABLE(refslinkedto, '$' COLUMNS (
                                    tze_type_nom uuid PATH '$.type_de_sites.tze_type_nom.*.uuids[0]',
									zet_chemin_parent uuid PATH '$.sites.zet_chemin_parent.*.uuids[0]'

                                    )
                                       ) AS refs
                                           left join monsore_dn.referenceDisplay tze_type_nom
                                                     on tze_type_nom.id = refs.tze_type_nom
                                           left join monsore_dn.referenceDisplay zet_chemin_parent
                                                     on zet_chemin_parent.id = refs.zet_chemin_parent
                                  where referencetype = 'sites');

ALTER TABLE monsore_dn.sites
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.sites
    ADD CONSTRAINT sites_pk PRIMARY KEY (id);
ALTER TABLE IF EXISTS monsore_dn.sites
    ADD CONSTRAINT tze_type_nom_fk FOREIGN KEY (tze_type_nom)
        REFERENCES monsore_dn.type_de_sites (id);
ALTER TABLE IF EXISTS monsore_dn.sites
    ADD CONSTRAINT zet_chemin_parent_fk FOREIGN KEY (zet_chemin_parent)
        REFERENCES monsore_dn.sites (id);

-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.sites TO public;
ALTER TABLE monsore_dn.sites
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'sites'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "sites_%s"
            ON monsore_dn.sites
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;
-- valeurs_qualitatives
create table monsore_dn.valeurs_qualitatives as (select referencevalue.id,
                                                        referencevalue.naturalkey,
                                                        referencevalue.hierarchicalkey,
                                                        nom_key,
                                                        nom_fr,
                                                        nom_en,
                                                        valeur_key,
                                                        valeur_fr,
                                                        valeur_en
                                                 FROM monsore.referencevalue,
                                                      JSON_TABLE(refvalues, '$'
                                                          COLUMNS (
	nom_key text PATH '$.nom_key',
	nom_fr text PATH '$.nom_fr',
	nom_en text PATH '$.nom_en',
	valeur_key text PATH '$.valeur_key',
	valeur_fr text PATH '$.valeur_fr',
	valeur_en text PATH '$.valeur_en'
	)
                                                      ) AS val
                                                 where referencetype = 'valeurs_qualitatives');

ALTER TABLE monsore_dn.valeurs_qualitatives
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.valeurs_qualitatives
    ADD CONSTRAINT valeurs_qualitatives_pk PRIMARY KEY (id);


-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.valeurs_qualitatives TO public;
ALTER TABLE monsore_dn.valeurs_qualitatives
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'valeurs_qualitatives'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "valeurs_qualitatives_%s"
            ON monsore_dn.valeurs_qualitatives
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;

-- variables
create table monsore_dn.variables as (select referencevalue.id,
                                             referencevalue.naturalkey,
                                             referencevalue.hierarchicalkey,
                                             nom_key,
                                             nom_fr,
                                             nom_en,
                                             definition_fr,
                                             definition_en,
                                             is_qualitative
                                      FROM monsore.referencevalue,
                                           JSON_TABLE(refvalues, '$'
                                               COLUMNS (
	nom_key text PATH '$.nom_key',
	nom_fr text PATH '$.nom_fr',
	nom_en text PATH '$.nom_en',
	definition_fr text PATH '$.definition_fr',
	definition_en text PATH '$.definition_en',
	is_qualitative boolean PATH '$.is_qualitative'
	)
                                           ) AS val
                                      where referencetype = 'variables');

ALTER TABLE monsore_dn.variables
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.variables
    ADD CONSTRAINT variables_pk PRIMARY KEY (id);


-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.variables TO public;
ALTER TABLE monsore_dn.variables
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'variables'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "variables_%s"
            ON monsore_dn.variables
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;

--site_theme_datatype

create table monsore_dn.site_theme_datatype as (select referencevalue.id,
                                                       referencevalue.naturalkey,
                                                       referencevalue.hierarchicalkey,
                                                       datatype,
                                                       refs.site         site,
                                                       site.display_fr   site_fr,
                                                       site.display_en   site_en,
                                                       refs.projet       projet,
                                                       projet.display_fr projet_fr,
                                                       projet.display_en projet_en,
                                                       refs.theme        theme,
                                                       theme.display_fr  theme_fr,
                                                       theme.display_en  theme_en
                                                FROM monsore.referencevalue,
                                                     JSON_TABLE(refvalues, '$'
                                                         COLUMNS (
	datatype text PATH '$.datatype'
	)
                                                     ) AS val,
                                                     JSON_TABLE(refslinkedto, '$' COLUMNS (
									site uuid PATH '$.sites.site.*.uuids[0]',
									projet uuid PATH '$.projet.projet.*.uuids[0]',
									theme uuid PATH '$.themes.theme.*.uuids[0]'

                                    )
                                                     ) AS refs
                                                         left join monsore_dn.referenceDisplay site on site.id = refs.site
                                                         left join monsore_dn.referenceDisplay projet on projet.id = refs.projet
                                                         left join monsore_dn.referenceDisplay theme on theme.id = refs.theme
                                                where referencetype = 'site_theme_datatype');

ALTER TABLE monsore_dn.site_theme_datatype
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.site_theme_datatype
    ADD CONSTRAINT site_theme_datatype_pk PRIMARY KEY (id);
ALTER TABLE IF EXISTS monsore_dn.site_theme_datatype
    ADD CONSTRAINT site_fk FOREIGN KEY (site)
        REFERENCES monsore_dn.sites (id);
ALTER TABLE IF EXISTS monsore_dn.site_theme_datatype
    ADD CONSTRAINT projet_fk FOREIGN KEY (projet)
        REFERENCES monsore_dn.projet (id);
ALTER TABLE IF EXISTS monsore_dn.site_theme_datatype
    ADD CONSTRAINT theme_fk FOREIGN KEY (theme)
        REFERENCES monsore_dn.themes (id);

-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.site_theme_datatype TO public;
ALTER TABLE monsore_dn.site_theme_datatype
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'site_theme_datatype'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "site_theme_datatype_%s"
            ON monsore_dn.site_theme_datatype
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--variables_et_unites_par_types_de_donnees

create table monsore_dn.variables_et_unites_par_types_de_donnees as (select referencevalue.id,
                                                                            referencevalue.naturalkey,
                                                                            referencevalue.hierarchicalkey,
                                                                            datatype,
                                                                            refs.unite          unite,
                                                                            unite.display_fr    unite_fr,
                                                                            unite.display_en    unite_en,
                                                                            refs.variable       variable,
                                                                            variable.display_fr variable_fr,
                                                                            variable.display_en variable_en
                                                                     FROM monsore.referencevalue,
                                                                          JSON_TABLE(refvalues, '$'
                                                                              COLUMNS (
	datatype text PATH '$.datatype'
	)
                                                                          ) AS val,
                                                                          JSON_TABLE(refslinkedto, '$' COLUMNS (
									unite uuid PATH '$.unites.unite.*.uuids[0]',
									variable uuid PATH '$.variables.variable.*.uuids[0]'

                                    )
                                                                          ) AS refs
                                                                              left join monsore_dn.referenceDisplay unite on unite.id = refs.unite
                                                                              left join monsore_dn.referenceDisplay variable on variable.id = refs.variable
                                                                     where referencetype = 'variables_et_unites_par_types_de_donnees');

ALTER TABLE monsore_dn.variables_et_unites_par_types_de_donnees
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.variables_et_unites_par_types_de_donnees
    ADD CONSTRAINT variables_et_unites_par_types_de_donnees_pk PRIMARY KEY (id);
ALTER TABLE IF EXISTS monsore_dn.variables_et_unites_par_types_de_donnees
    ADD CONSTRAINT unite_fk FOREIGN KEY (unite)
        REFERENCES monsore_dn.unites (id);
ALTER TABLE IF EXISTS monsore_dn.variables_et_unites_par_types_de_donnees
    ADD CONSTRAINT variable_fk FOREIGN KEY (variable)
        REFERENCES monsore_dn.variables (id);

-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.variables_et_unites_par_types_de_donnees TO public;
ALTER TABLE monsore_dn.variables_et_unites_par_types_de_donnees
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text AS id, application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'variables_et_unites_par_types_de_donnees'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "variables_et_unites_par_types_de_donnees_%s"
            ON monsore_dn.variables_et_unites_par_types_de_donnees
            AS PERMISSIVE
            TO "%s"
            USING (true)
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;
--pem
create table monsore_dn.pem as (select referencevalue.id,
                                       referencevalue.naturalkey,
                                       referencevalue.hierarchicalkey,
                                       (val.date::composite_date)::timestamp ts_date,
                                       (val.date::composite_date) ::text     date,
                                       refs.site                             site,
                                       site.hierarchicalkey                  site_hk,
                                       site.display_fr                       site_fr,
                                       site.display_en                       site_en,
                                       refs.chemin                           chemin,
                                       chemin.display_fr                     chemin_fr,
                                       chemin.display_en                     chemin_en,
                                       refs.projet                           projet,
                                       projet.hierarchicalkey                projet_hk,
                                       projet.display_fr                     projet_fr,
                                       projet.display_en                     projet_en,
                                       refs.color_unit                       color_unit,
                                       color_unit.display_fr                 color_unit_fr,
                                       color_unit.display_en                 color_unit_en,
                                       refs.individusNumber_unit             individusNumber_unit,
                                       individusNumber_unit.display_fr       individusNumber_unit_fr,
                                       individusNumber_unit.display_en       individusNumber_unit_en,
                                       refs.espece                           espece,
                                       espece.display_fr                     espece_fr,
                                       espece.display_en                     espece_en,
                                       refs.color_value                      color_value,
                                       color_value.display_fr                color_value_fr,
                                       color_value.display_en                color_value_en,
                                       val.plateforme,
                                       val.individusNumbervalue
                                FROM monsore.referencevalue,
                                     JSON_TABLE(refvalues, '$' COLUMNS (
                                    date text PATH '$.date', plateforme text PATH '$.plateforme', 
                                    individusNumbervalue float PATH '$.individusNumbervalue'
                                    )
                                     ) AS val,
                                     JSON_TABLE(refslinkedto, '$' COLUMNS (
                                        site uuid PATH '$.sites.site.*.uuids[0]',
                                         chemin uuid PATH '$.sites.chemin.*.uuids[0]', 
                                         projet uuid PATH '$.projet.projet.*.uuids[0]', 
                                         color_unit uuid PATH '$.unites.color_unit.*.uuids[0]', 
                                         individusNumber_unit uuid PATH '$.unites.individusNumber_unit.*.uuids[0]', 
                                         espece uuid PATH '$.especes.espece.*.uuids[0]', 
                                         color_value uuid PATH '$.valeurs_qualitatives.color_value.*.uuids[0]'
                                    )
                                     ) AS refs
                                         left join monsore_dn.referenceDisplay site on site.id = refs.site
                                         left join monsore_dn.referenceDisplay chemin on chemin.id = refs.chemin
                                         left join monsore_dn.referenceDisplay projet on projet.id = refs.projet
                                         left join monsore_dn.referenceDisplay color_unit
                                                   on color_unit.id = refs.color_unit
                                         left join monsore_dn.referenceDisplay individusNumber_unit
                                                   on individusNumber_unit.id = refs.individusNumber_unit
                                         left join monsore_dn.referenceDisplay espece on espece.id = refs.espece
                                         left join monsore_dn.referenceDisplay color_value
                                                   on color_value.id = refs.color_value

                                where referencetype = 'pem');
ALTER TABLE monsore_dn.pem
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.pem
    ADD CONSTRAINT pem_pk PRIMARY KEY (id);

CREATE INDEX IF NOT EXISTS date_idx
    ON monsore_dn.pem USING brin
        (ts_date timestamp_minmax_multi_ops)
    WITH (pages_per_range =128, autosummarize= False)
    TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS site_idx
    ON monsore_dn.pem USING btree (site ASC NULLS LAST);

CREATE INDEX IF NOT EXISTS site_hk_idx
    ON monsore_dn.pem USING btree (site_hk ASC NULLS LAST);

CREATE INDEX IF NOT EXISTS projet_idx
    ON monsore_dn.pem USING btree (projet ASC NULLS LAST);

CREATE INDEX IF NOT EXISTS projet_hk_idx
    ON monsore_dn.pem USING btree (projet ASC NULLS LAST);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT sites_fk FOREIGN KEY (site)
        REFERENCES monsore_dn.sites (id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT chemin_fk FOREIGN KEY (chemin)
        REFERENCES monsore_dn.sites (id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT color_unit_fk FOREIGN KEY (color_unit)
        REFERENCES monsore_dn.unites (id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT individusnumber_unit_fk FOREIGN KEY (individusnumber_unit)
        REFERENCES monsore_dn.unites (id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT espece_fk FOREIGN KEY (espece)
        REFERENCES monsore_dn.especes (id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT color_value_fk FOREIGN KEY (color_value)
        REFERENCES monsore_dn.valeurs_qualitatives (id);

-- ajout des droits sur la table
GRANT SELECT ON monsore_dn.pem TO public;
ALTER TABLE monsore_dn.pem
    ENABLE ROW LEVEL SECURITY;

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role,
                   (authorizations #>> '{pem, timescope}')::tsrange                 AS timescope,
                   (authorizations #>> '{pem, authorizationscope,sites,0}')         AS site,
                   (authorizations #>> '{pem, authorizationscope,projet,0}')        AS projet
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'pem'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "pem_%s"
            ON monsore_dn.pem
            AS PERMISSIVE
            TO "%s"
            USING (
                ts_date <@ '%s'::tsrange
                AND site_hk <@ '%s'::ltree
                AND projet_hk <@ '%s'::ltree
            )
        $fmt$, rec.id, rec.role, rec.timescope, rec.site, rec.projet);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;

drop table monsore_dn.referenceDisplay