drop schema if exists monsore_dn cascade;
create schema monsore_dn;

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
--create table themes

create table monsore_dn.themes as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."nom_en")::TEXT  		"nom_en",
        MAX(val."nom_fr")::TEXT  		"nom_fr",
        MAX(val."nom_key")::TEXT  		"nom_key",
        MAX(val."description_en")::TEXT  		"description_en",
        MAX(val."description_fr")::TEXT  		"description_fr"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "nom_en" TEXT PATH '$.nom_en',
						"nom_fr" TEXT PATH '$.nom_fr',
						"nom_key" TEXT PATH '$.nom_key',
						"description_en" TEXT PATH '$.description_en',
						"description_fr" TEXT PATH '$.description_fr'
                    )
        ) AS val



    WHERE referencetype = 'themes'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.themes
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.themes
    ADD CONSTRAINT themes_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'themes'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.themes
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table especes

create table monsore_dn.especes as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."esp_nom")::TEXT  		"esp_nom",
        MAX(val."esp_definition_en")::TEXT  		"esp_definition_en",
        MAX(val."esp_definition_fr")::TEXT  		"esp_definition_fr",
        MAX(val."colonne_homonyme_entre_referentiels")::TEXT  		"colonne_homonyme_entre_referentiels"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "esp_nom" TEXT PATH '$.esp_nom',
						"esp_definition_en" TEXT PATH '$.esp_definition_en',
						"esp_definition_fr" TEXT PATH '$.esp_definition_fr',
						"colonne_homonyme_entre_referentiels" TEXT PATH '$.colonne_homonyme_entre_referentiels'
                    )
        ) AS val



    WHERE referencetype = 'especes'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.especes
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.especes
    ADD CONSTRAINT especes_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'especes'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.especes
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table variables

create table monsore_dn.variables as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."nom_en")::TEXT  		"nom_en",
        MAX(val."nom_fr")::TEXT  		"nom_fr",
        MAX(val."nom_key")::TEXT  		"nom_key",
        MAX(val."definition_en")::TEXT  		"definition_en",
        MAX(val."definition_fr")::TEXT  		"definition_fr",
        MAX(val."is_qualitative")::TEXT  		"is_qualitative"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "nom_en" TEXT PATH '$.nom_en',
						"nom_fr" TEXT PATH '$.nom_fr',
						"nom_key" TEXT PATH '$.nom_key',
						"definition_en" TEXT PATH '$.definition_en',
						"definition_fr" TEXT PATH '$.definition_fr',
						"is_qualitative" TEXT PATH '$.is_qualitative'
                    )
        ) AS val



    WHERE referencetype = 'variables'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.variables
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.variables
    ADD CONSTRAINT variables_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'variables'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.variables
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table type_de_sites

create table monsore_dn.type_de_sites as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."tze_nom_en")::TEXT  		"tze_nom_en",
        MAX(val."tze_nom_fr")::TEXT  		"tze_nom_fr",
        MAX(val."tze_nom_key")::TEXT  		"tze_nom_key",
        MAX(val."tze_definition_en")::TEXT  		"tze_definition_en",
        MAX(val."tze_definition_fr")::TEXT  		"tze_definition_fr"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "tze_nom_en" TEXT PATH '$.tze_nom_en',
						"tze_nom_fr" TEXT PATH '$.tze_nom_fr',
						"tze_nom_key" TEXT PATH '$.tze_nom_key',
						"tze_definition_en" TEXT PATH '$.tze_definition_en',
						"tze_definition_fr" TEXT PATH '$.tze_definition_fr'
                    )
        ) AS val



    WHERE referencetype = 'type_de_sites'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.type_de_sites
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.type_de_sites
    ADD CONSTRAINT type_de_sites_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'type_de_sites'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.type_de_sites
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table site_theme_datatype

create table monsore_dn.site_theme_datatype as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."site")::TEXT  		"site",
        MAX(val."theme")::TEXT  		"theme",
        MAX(val."projet")::TEXT  		"projet",
        MAX(val."datatype")::TEXT  		"datatype"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "site" TEXT PATH '$.site',
						"theme" TEXT PATH '$.theme',
						"projet" TEXT PATH '$.projet',
						"datatype" TEXT PATH '$.datatype'
                    )
        ) AS val



    WHERE referencetype = 'site_theme_datatype'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.site_theme_datatype
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.site_theme_datatype
    ADD CONSTRAINT site_theme_datatype_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'site_theme_datatype'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.site_theme_datatype
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table unites

create table monsore_dn.unites as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."nom_en")::TEXT  		"nom_en",
        MAX(val."nom_fr")::TEXT  		"nom_fr",
        MAX(val."code_en")::TEXT  		"code_en",
        MAX(val."code_fr")::TEXT  		"code_fr",
        MAX(val."nom_key")::TEXT  		"nom_key",
        MAX(val."code_key")::TEXT  		"code_key"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "nom_en" TEXT PATH '$.nom_en',
						"nom_fr" TEXT PATH '$.nom_fr',
						"code_en" TEXT PATH '$.code_en',
						"code_fr" TEXT PATH '$.code_fr',
						"nom_key" TEXT PATH '$.nom_key',
						"code_key" TEXT PATH '$.code_key'
                    )
        ) AS val



    WHERE referencetype = 'unites'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.unites
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.unites
    ADD CONSTRAINT unites_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'unites'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.unites
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table projet

create table monsore_dn.projet as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."nom_en")::TEXT  		"nom_en",
        MAX(val."nom_fr")::TEXT  		"nom_fr",
        MAX(val."nom_key")::TEXT  		"nom_key",
        MAX(val."definition_en")::TEXT  		"definition_en",
        MAX(val."definition_fr")::TEXT  		"definition_fr",
        MAX(val."colonne_homonyme_entre_referentiels")::TEXT  		"colonne_homonyme_entre_referentiels"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "nom_en" TEXT PATH '$.nom_en',
						"nom_fr" TEXT PATH '$.nom_fr',
						"nom_key" TEXT PATH '$.nom_key',
						"definition_en" TEXT PATH '$.definition_en',
						"definition_fr" TEXT PATH '$.definition_fr',
						"colonne_homonyme_entre_referentiels" TEXT PATH '$.colonne_homonyme_entre_referentiels'
                    )
        ) AS val



    WHERE referencetype = 'projet'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.projet
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.projet
    ADD CONSTRAINT projet_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'projet'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.projet
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table valeurs_qualitatives

create table monsore_dn.valeurs_qualitatives as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."nom_en")::TEXT  		"nom_en",
        MAX(val."nom_fr")::TEXT  		"nom_fr",
        MAX(val."nom_key")::TEXT  		"nom_key",
        MAX(val."valeur_en")::TEXT  		"valeur_en",
        MAX(val."valeur_fr")::TEXT  		"valeur_fr",
        MAX(val."valeur_key")::TEXT  		"valeur_key"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "nom_en" TEXT PATH '$.nom_en',
						"nom_fr" TEXT PATH '$.nom_fr',
						"nom_key" TEXT PATH '$.nom_key',
						"valeur_en" TEXT PATH '$.valeur_en',
						"valeur_fr" TEXT PATH '$.valeur_fr',
						"valeur_key" TEXT PATH '$.valeur_key'
                    )
        ) AS val



    WHERE referencetype = 'valeurs_qualitatives'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.valeurs_qualitatives
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.valeurs_qualitatives
    ADD CONSTRAINT valeurs_qualitatives_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'valeurs_qualitatives'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.valeurs_qualitatives
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table type_de_fichiers

create table monsore_dn.type_de_fichiers as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."nom_en")::TEXT  		"nom_en",
        MAX(val."nom_fr")::TEXT  		"nom_fr",
        MAX(val."nom_key")::TEXT  		"nom_key",
        MAX(val."description_en")::TEXT  		"description_en",
        MAX(val."description_fr")::TEXT  		"description_fr"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "nom_en" TEXT PATH '$.nom_en',
						"nom_fr" TEXT PATH '$.nom_fr',
						"nom_key" TEXT PATH '$.nom_key',
						"description_en" TEXT PATH '$.description_en',
						"description_fr" TEXT PATH '$.description_fr'
                    )
        ) AS val



    WHERE referencetype = 'type_de_fichiers'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.type_de_fichiers
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.type_de_fichiers
    ADD CONSTRAINT type_de_fichiers_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'type_de_fichiers'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.type_de_fichiers
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table variables_et_unites_par_types_de_donnees

create table monsore_dn.variables_et_unites_par_types_de_donnees as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."unite")::TEXT  		"unite",
        MAX(val."datatype")::TEXT  		"datatype",
        MAX(val."variable")::TEXT  		"variable"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "unite" TEXT PATH '$.unite',
						"datatype" TEXT PATH '$.datatype',
						"variable" TEXT PATH '$.variable'
                    )
        ) AS val



    WHERE referencetype = 'variables_et_unites_par_types_de_donnees'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.variables_et_unites_par_types_de_donnees
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.variables_et_unites_par_types_de_donnees
    ADD CONSTRAINT variables_et_unites_par_types_de_donnees_pk PRIMARY KEY (id);

-- indexes


-- foreignKeys


-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'variables_et_unites_par_types_de_donnees'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.variables_et_unites_par_types_de_donnees
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table sites

create table monsore_dn.sites as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."zet_nom_en")::TEXT  		"zet_nom_en",
        MAX(val."zet_nom_fr")::TEXT  		"zet_nom_fr",
        MAX(val."zet_nom_key")::TEXT  		"zet_nom_key",
        MAX(refs."tze_type_nom")::UUID		"tze_type_nom_id",
        MAX("tze_type_nom".display_fr)::TEXT		"tze_type_nom_fr",
        MAX("tze_type_nom".display_en)::TEXT		"tze_type_nom_en",
        MAX(refs."zet_chemin_parent")::UUID		"zet_chemin_parent_id",
        MAX("zet_chemin_parent".display_fr)::TEXT		"zet_chemin_parent_fr",
        MAX("zet_chemin_parent".display_en)::TEXT		"zet_chemin_parent_en",
        MAX(val."zet_description_en")::TEXT  		"zet_description_en",
        MAX(val."zet_description_fr")::TEXT  		"zet_description_fr"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "zet_nom_en" TEXT PATH '$.zet_nom_en',
						"zet_nom_fr" TEXT PATH '$.zet_nom_fr',
						"zet_nom_key" TEXT PATH '$.zet_nom_key',
						"zet_description_en" TEXT PATH '$.zet_description_en',
						"zet_description_fr" TEXT PATH '$.zet_description_fr'
                    )
        ) AS val,
        -- add references fields
        JSON_TABLE(
                refslinkedto,
                '$' COLUMNS (
                        NESTED PATH '$.type_de_sites.tze_type_nom.*.uuids[*]' COLUMNS(
"tze_type_nom_id" FOR ORDINALITY,
"tze_type_nom" TEXT PATH '$'
),
				NESTED PATH '$.sites.zet_chemin_parent.*.uuids[*]' COLUMNS(
"zet_chemin_parent_id" FOR ORDINALITY,
"zet_chemin_parent" TEXT PATH '$'
)
                    )
        ) AS refs
            left join monsore_dn.referenceDisplay "tze_type_nom" on "tze_type_nom".id = refs."tze_type_nom"::uuid
            left join monsore_dn.referenceDisplay "zet_chemin_parent" on "zet_chemin_parent".id = refs."zet_chemin_parent"::uuid

    WHERE referencetype = 'sites'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.sites
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.sites
    ADD CONSTRAINT sites_pk PRIMARY KEY (id);

-- indexes
CREATE INDEX IF NOT EXISTS "tze_type_nom_id_idx"
    ON monsore_dn."sites" USING btree ("tze_type_nom_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "zet_chemin_parent_id_idx"
    ON monsore_dn."sites" USING btree ("zet_chemin_parent_id" ASC NULLS LAST);

-- foreignKeys

ALTER TABLE IF EXISTS monsore_dn.sites
    ADD CONSTRAINT "type_de_sites__tze_type_nom_id_fk" FOREIGN KEY ("tze_type_nom_id")
        REFERENCES monsore_dn.type_de_sites(id);

ALTER TABLE IF EXISTS monsore_dn.sites
    ADD CONSTRAINT "sites__zet_chemin_parent_id_fk" FOREIGN KEY ("zet_chemin_parent_id")
        REFERENCES monsore_dn.sites(id);

-- policies

DO
$$
    DECLARE
        rec RECORD;
        sql_policy
            TEXT;
    BEGIN
        FOR rec IN
            SELECT id::text                                                         AS id,
                   application::text || '_mgt_' || SUBSTRING(id::text FROM 1 FOR 8) AS role
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'sites'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%2$s_monsore_sel"
            ON monsore_dn.sites
            AS PERMISSIVE
            TO "%2$s"
            USING (
                true
            )
        $fmt$, rec.id, rec.role);

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


--create table pem

create table monsore_dn.pem as (
    select
        referencevalue.id,
        referencevalue.naturalkey,
        referencevalue.hierarchicalkey,
        MAX(val."date")::composite_date::timestamp "ts_date",
        MAX(val."date")::composite_date::text "date",
        MAX(refs."site")::UUID		"site_id",
        MAX("site".display_fr)::TEXT		"site_fr",
        MAX("site".display_en)::TEXT		"site_en",
        MAX(refs."chemin")::UUID		"chemin_id",
        MAX("chemin".hierarchicalkey::TEXT)::LTREE		"chemin_hk",
        MAX("chemin".display_fr)::TEXT		"chemin_fr",
        MAX("chemin".display_en)::TEXT		"chemin_en",
        MAX(refs."espece")::UUID		"espece_id",
        MAX("espece".display_fr)::TEXT		"espece_fr",
        MAX("espece".display_en)::TEXT		"espece_en",
        MAX(refs."projet")::UUID		"projet_id",
        MAX("projet".hierarchicalkey::TEXT)::LTREE		"projet_hk",
        MAX("projet".display_fr)::TEXT		"projet_fr",
        MAX("projet".display_en)::TEXT		"projet_en",
        MAX(refs."color_unit")::UUID		"color_unit_id",
        MAX("color_unit".display_fr)::TEXT		"color_unit_fr",
        MAX("color_unit".display_en)::TEXT		"color_unit_en",
        MAX(val."plateforme")::TEXT  		"plateforme",
        MAX(refs."color_value")::UUID		"color_value_id",
        MAX("color_value".display_fr)::TEXT		"color_value_fr",
        MAX("color_value".display_en)::TEXT		"color_value_en",
        MAX(refs."individusNumber_unit")::UUID		"individusNumber_unit_id",
        MAX("individusNumber_unit".display_fr)::TEXT		"individusNumber_unit_fr",
        MAX("individusNumber_unit".display_en)::TEXT		"individusNumber_unit_en",
        MAX(val."individusNumbervalue")::FLOAT  		"individusNumbervalue"

    FROM
        monsore.referencevalue,
        -- add simple fields
        JSON_TABLE(
                refvalues,
                '$' COLUMNS (
                        "date" TEXT PATH '$."date"',
						"plateforme" TEXT PATH '$.plateforme',
						"individusNumbervalue" FLOAT PATH '$.individusNumbervalue'
                    )
        ) AS val,
        -- add references fields
        JSON_TABLE(
                refslinkedto,
                '$' COLUMNS (
                        NESTED PATH '$.sites.site.*.uuids[*]' COLUMNS(
"site_id" FOR ORDINALITY,
"site" TEXT PATH '$'
),
				NESTED PATH '$.sites.chemin.*.uuids[*]' COLUMNS(
"chemin_id" FOR ORDINALITY,
"chemin" TEXT PATH '$'
),
				NESTED PATH '$.especes.espece.*.uuids[*]' COLUMNS(
"espece_id" FOR ORDINALITY,
"espece" TEXT PATH '$'
),
				NESTED PATH '$.projet.projet.*.uuids[*]' COLUMNS(
"projet_id" FOR ORDINALITY,
"projet" TEXT PATH '$'
),
				NESTED PATH '$.unites.color_unit.*.uuids[*]' COLUMNS(
"color_unit_id" FOR ORDINALITY,
"color_unit" TEXT PATH '$'
),
				NESTED PATH '$.valeurs_qualitatives.color_value.*.uuids[*]' COLUMNS(
"color_value_id" FOR ORDINALITY,
"color_value" TEXT PATH '$'
),
				NESTED PATH '$.unites.individusNumber_unit.*.uuids[*]' COLUMNS(
"individusNumber_unit_id" FOR ORDINALITY,
"individusNumber_unit" TEXT PATH '$'
)
                    )
        ) AS refs
            left join monsore_dn.referenceDisplay "site" on "site".id = refs."site"::uuid
            left join monsore_dn.referenceDisplay "chemin" on "chemin".id = refs."chemin"::uuid
            left join monsore_dn.referenceDisplay "espece" on "espece".id = refs."espece"::uuid
            left join monsore_dn.referenceDisplay "projet" on "projet".id = refs."projet"::uuid
            left join monsore_dn.referenceDisplay "color_unit" on "color_unit".id = refs."color_unit"::uuid
            left join monsore_dn.referenceDisplay "color_value" on "color_value".id = refs."color_value"::uuid
            left join monsore_dn.referenceDisplay "individusNumber_unit" on "individusNumber_unit".id = refs."individusNumber_unit"::uuid

    WHERE referencetype = 'pem'
    GROUP BY referencevalue.id, referencevalue.naturalkey, referencevalue.hierarchicalkey
);

-- primary key
ALTER TABLE monsore_dn.pem
    ALTER COLUMN id SET NOT NULL;
ALTER TABLE monsore_dn.pem
    ADD CONSTRAINT pem_pk PRIMARY KEY (id);

-- indexes
CREATE INDEX IF NOT EXISTS "ts_date_idx"
    ON monsore_dn."pem" USING brin
        (ts_date timestamp_minmax_multi_ops)
    WITH (pages_per_range =128, autosummarize= False)
    TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS "site_id_idx"
    ON monsore_dn."pem" USING btree ("site_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "chemin_hk_idx"
    ON monsore_dn."pem" USING btree ("chemin_hk" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "chemin_id_idx"
    ON monsore_dn."pem" USING btree ("chemin_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "espece_id_idx"
    ON monsore_dn."pem" USING btree ("espece_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "projet_hk_idx"
    ON monsore_dn."pem" USING btree ("projet_hk" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "projet_id_idx"
    ON monsore_dn."pem" USING btree ("projet_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "color_unit_id_idx"
    ON monsore_dn."pem" USING btree ("color_unit_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "color_value_id_idx"
    ON monsore_dn."pem" USING btree ("color_value_id" ASC NULLS LAST);
CREATE INDEX IF NOT EXISTS "individusNumber_unit_id_idx"
    ON monsore_dn."pem" USING btree ("individusNumber_unit_id" ASC NULLS LAST);

-- foreignKeys

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT "sites__site_id_fk" FOREIGN KEY ("site_id")
        REFERENCES monsore_dn.sites(id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT "sites__chemin_id_fk" FOREIGN KEY ("chemin_id")
        REFERENCES monsore_dn.sites(id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT "especes__espece_id_fk" FOREIGN KEY ("espece_id")
        REFERENCES monsore_dn.especes(id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT "projet__projet_id_fk" FOREIGN KEY ("projet_id")
        REFERENCES monsore_dn.projet(id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT "unites__color_unit_id_fk" FOREIGN KEY ("color_unit_id")
        REFERENCES monsore_dn.unites(id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT "unites__individusNumber_unit_id_fk" FOREIGN KEY ("individusNumber_unit_id")
        REFERENCES monsore_dn.unites(id);

ALTER TABLE IF EXISTS monsore_dn.pem
    ADD CONSTRAINT "valeurs_qualitatives__color_value_id_fk" FOREIGN KEY ("color_value_id")
        REFERENCES monsore_dn.valeurs_qualitatives(id);

-- policies

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
                   (authorizations #>> '{pem, authorizationscope,sites,0}')         AS sites,
                   (authorizations #>> '{pem, authorizationscope,projet,0}')         AS projet
            FROM monsore.oresiauthorization
            WHERE authorizations ? 'pem'
            LOOP
                sql_policy := format($fmt$
            CREATE POLICY "%1$s_monsore_sel"
            ON monsore_dn.pem
            AS PERMISSIVE
            TO "%2$s"
            USING (
                ts_date <@ '%3$s'::tsrange
				AND chemin_hk <@ '%4$s'::ltree
				AND projet_hk <@ '%5$s'::ltree
            );
        $fmt$, rec.id, rec.role, rec.timescope, rec.sites, rec.projet );

                EXECUTE sql_policy;
            END LOOP;
    END
$$;


drop table monsore_dn.referenceDisplay;