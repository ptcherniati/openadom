-- Table: monsore_dn.especes

-- DROP TABLE IF EXISTS monsore_dn.especes;

CREATE TABLE IF NOT EXISTS monsore_dn.especes
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    esp_nom text COLLATE pg_catalog."default",
    esp_definition_fr text COLLATE pg_catalog."default",
    esp_definition_en text COLLATE pg_catalog."default",
    CONSTRAINT especes_pk PRIMARY KEY (id)
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.especes
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.especes
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.especes FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.especes TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.especes TO dbuser;
-- POLICY: especes_5568f384-0942-42cb-9ece-8c021f01ed3d

-- DROP POLICY IF EXISTS "especes_5568f384-0942-42cb-9ece-8c021f01ed3d" ON monsore_dn.especes;

CREATE POLICY "especes_5568f384-0942-42cb-9ece-8c021f01ed3d"
    ON monsore_dn.especes
    AS PERMISSIVE
    FOR ALL
    TO "7fee4c54-c9c1-468a-9c7f-289382637b51_mgt_5568f384"
    USING (true);

-- Table: monsore_dn.projet

-- DROP TABLE IF EXISTS monsore_dn.projet;

CREATE TABLE IF NOT EXISTS monsore_dn.projet
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    nom_key text COLLATE pg_catalog."default",
    nom_fr text COLLATE pg_catalog."default",
    nom_en text COLLATE pg_catalog."default",
    definition_fr text COLLATE pg_catalog."default",
    definition_en text COLLATE pg_catalog."default",
    CONSTRAINT projet_pk PRIMARY KEY (id)
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.projet
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.projet
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.projet FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.projet TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.projet TO dbuser;
-- POLICY: projet_5568f384-0942-42cb-9ece-8c021f01ed3d

-- DROP POLICY IF EXISTS "projet_5568f384-0942-42cb-9ece-8c021f01ed3d" ON monsore_dn.projet;

CREATE POLICY "projet_5568f384-0942-42cb-9ece-8c021f01ed3d"
    ON monsore_dn.projet
    AS PERMISSIVE
    FOR ALL
    TO "7fee4c54-c9c1-468a-9c7f-289382637b51_mgt_5568f384"
    USING (true);
-- Table: monsore_dn.type_de_sites

-- DROP TABLE IF EXISTS monsore_dn.type_de_sites;

CREATE TABLE IF NOT EXISTS monsore_dn.type_de_sites
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    tze_nom_key text COLLATE pg_catalog."default",
    tze_nom_fr text COLLATE pg_catalog."default",
    tze_nom_en text COLLATE pg_catalog."default",
    tze_definition_fr text COLLATE pg_catalog."default",
    tze_definition_en text COLLATE pg_catalog."default",
    CONSTRAINT type_de_sites_pk PRIMARY KEY (id)
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.type_de_sites
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.type_de_sites
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.type_de_sites FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.type_de_sites TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.type_de_sites TO dbuser;
-- POLICY: type_de_sites_5568f384-0942-42cb-9ece-8c021f01ed3d

-- DROP POLICY IF EXISTS "type_de_sites_5568f384-0942-42cb-9ece-8c021f01ed3d" ON monsore_dn.type_de_sites;

CREATE POLICY "type_de_sites_5568f384-0942-42cb-9ece-8c021f01ed3d"
    ON monsore_dn.type_de_sites
    AS PERMISSIVE
    FOR ALL
    TO "7fee4c54-c9c1-468a-9c7f-289382637b51_mgt_5568f384"
    USING (true);
-- Table: monsore_dn.sites

-- DROP TABLE IF EXISTS monsore_dn.sites;

CREATE TABLE IF NOT EXISTS monsore_dn.sites
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    zet_nom_key text COLLATE pg_catalog."default",
    zet_nom_fr text COLLATE pg_catalog."default",
    zet_nom_en text COLLATE pg_catalog."default",
    zet_description_fr text COLLATE pg_catalog."default",
    zet_description_en text COLLATE pg_catalog."default",
    tze_type_nom uuid,
    tze_type_nom_fr text COLLATE pg_catalog."default",
    tze_type_nom_en text COLLATE pg_catalog."default",
    zet_chemin_parent uuid,
    zet_chemin_parent_fr text COLLATE pg_catalog."default",
    zet_chemin_parent_en text COLLATE pg_catalog."default",
    CONSTRAINT sites_pk PRIMARY KEY (id),
    CONSTRAINT tze_type_nom_fk FOREIGN KEY (tze_type_nom)
        REFERENCES monsore_dn.type_de_sites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT zet_chemin_parent_fk FOREIGN KEY (zet_chemin_parent)
        REFERENCES monsore_dn.sites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.sites
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.sites
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.sites FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.sites TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.sites TO dbuser;
-- POLICY: sites_5568f384-0942-42cb-9ece-8c021f01ed3d

-- DROP POLICY IF EXISTS "sites_5568f384-0942-42cb-9ece-8c021f01ed3d" ON monsore_dn.sites;

CREATE POLICY "sites_5568f384-0942-42cb-9ece-8c021f01ed3d"
    ON monsore_dn.sites
    AS PERMISSIVE
    FOR ALL
    TO "7fee4c54-c9c1-468a-9c7f-289382637b51_mgt_5568f384"
    USING (true);
-- Table: monsore_dn.themes

-- DROP TABLE IF EXISTS monsore_dn.themes;

CREATE TABLE IF NOT EXISTS monsore_dn.themes
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    nom_key text COLLATE pg_catalog."default",
    nom_fr text COLLATE pg_catalog."default",
    nom_en text COLLATE pg_catalog."default",
    description_fr text COLLATE pg_catalog."default",
    description_en text COLLATE pg_catalog."default",
    CONSTRAINT themes_pk PRIMARY KEY (id)
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.themes
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.themes
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.themes FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.themes TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.themes TO dbuser;
-- Table: monsore_dn.site_theme_datatype

-- DROP TABLE IF EXISTS monsore_dn.site_theme_datatype;

CREATE TABLE IF NOT EXISTS monsore_dn.site_theme_datatype
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    datatype text COLLATE pg_catalog."default",
    site uuid,
    site_fr text COLLATE pg_catalog."default",
    site_en text COLLATE pg_catalog."default",
    projet uuid,
    projet_fr text COLLATE pg_catalog."default",
    projet_en text COLLATE pg_catalog."default",
    theme uuid,
    theme_fr text COLLATE pg_catalog."default",
    theme_en text COLLATE pg_catalog."default",
    CONSTRAINT site_theme_datatype_pk PRIMARY KEY (id),
    CONSTRAINT projet_fk FOREIGN KEY (projet)
        REFERENCES monsore_dn.projet (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT site_fk FOREIGN KEY (site)
        REFERENCES monsore_dn.sites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT theme_fk FOREIGN KEY (theme)
        REFERENCES monsore_dn.themes (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.site_theme_datatype
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.site_theme_datatype
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.site_theme_datatype FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.site_theme_datatype TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.site_theme_datatype TO dbuser;

-- Table: monsore_dn.unites

-- DROP TABLE IF EXISTS monsore_dn.unites;

CREATE TABLE IF NOT EXISTS monsore_dn.unites
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    nom_key text COLLATE pg_catalog."default",
    nom_fr text COLLATE pg_catalog."default",
    nom_en text COLLATE pg_catalog."default",
    code_key text COLLATE pg_catalog."default",
    code_fr text COLLATE pg_catalog."default",
    code_en text COLLATE pg_catalog."default",
    CONSTRAINT unites_pk PRIMARY KEY (id)
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.unites
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.unites
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.unites FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.unites TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.unites TO dbuser;
-- POLICY: unites_5568f384-0942-42cb-9ece-8c021f01ed3d

-- DROP POLICY IF EXISTS "unites_5568f384-0942-42cb-9ece-8c021f01ed3d" ON monsore_dn.unites;

CREATE POLICY "unites_5568f384-0942-42cb-9ece-8c021f01ed3d"
    ON monsore_dn.unites
    AS PERMISSIVE
    FOR ALL
    TO "7fee4c54-c9c1-468a-9c7f-289382637b51_mgt_5568f384"
    USING (true);
-- Table: monsore_dn.variables

-- DROP TABLE IF EXISTS monsore_dn.variables;

CREATE TABLE IF NOT EXISTS monsore_dn.variables
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    nom_key text COLLATE pg_catalog."default",
    nom_fr text COLLATE pg_catalog."default",
    nom_en text COLLATE pg_catalog."default",
    definition_fr text COLLATE pg_catalog."default",
    definition_en text COLLATE pg_catalog."default",
    is_qualitative boolean,
    CONSTRAINT variables_pk PRIMARY KEY (id)
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.variables
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.variables
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.variables FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.variables TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.variables TO dbuser;
-- Table: monsore_dn.variables_et_unites_par_types_de_donnees

-- DROP TABLE IF EXISTS monsore_dn.variables_et_unites_par_types_de_donnees;

CREATE TABLE IF NOT EXISTS monsore_dn.variables_et_unites_par_types_de_donnees
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    datatype text COLLATE pg_catalog."default",
    unite uuid,
    unite_fr text COLLATE pg_catalog."default",
    unite_en text COLLATE pg_catalog."default",
    variable uuid,
    variable_fr text COLLATE pg_catalog."default",
    variable_en text COLLATE pg_catalog."default",
    CONSTRAINT variables_et_unites_par_types_de_donnees_pk PRIMARY KEY (id),
    CONSTRAINT unite_fk FOREIGN KEY (unite)
        REFERENCES monsore_dn.unites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT variable_fk FOREIGN KEY (variable)
        REFERENCES monsore_dn.variables (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.variables_et_unites_par_types_de_donnees
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.variables_et_unites_par_types_de_donnees
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.variables_et_unites_par_types_de_donnees FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.variables_et_unites_par_types_de_donnees TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.variables_et_unites_par_types_de_donnees TO dbuser;
-- Table: monsore_dn.pem

-- DROP TABLE IF EXISTS monsore_dn.pem;

CREATE TABLE IF NOT EXISTS monsore_dn.pem
(
    id entityid NOT NULL,
    naturalkey ltree,
    hierarchicalkey ltree,
    ts_date timestamp without time zone,
    date text COLLATE pg_catalog."default",
    site uuid,
    site_hk ltree,
    site_fr text COLLATE pg_catalog."default",
    site_en text COLLATE pg_catalog."default",
    chemin uuid,
    chemin_fr text COLLATE pg_catalog."default",
    chemin_en text COLLATE pg_catalog."default",
    projet uuid,
    projet_hk ltree,
    projet_fr text COLLATE pg_catalog."default",
    projet_en text COLLATE pg_catalog."default",
    color_unit uuid,
    color_unit_fr text COLLATE pg_catalog."default",
    color_unit_en text COLLATE pg_catalog."default",
    individusnumber_unit uuid,
    individusnumber_unit_fr text COLLATE pg_catalog."default",
    individusnumber_unit_en text COLLATE pg_catalog."default",
    espece uuid,
    espece_fr text COLLATE pg_catalog."default",
    espece_en text COLLATE pg_catalog."default",
    color_value uuid,
    color_value_fr text COLLATE pg_catalog."default",
    color_value_en text COLLATE pg_catalog."default",
    plateforme text COLLATE pg_catalog."default",
    individusnumbervalue double precision,
    CONSTRAINT pem_pk PRIMARY KEY (id),
    CONSTRAINT chemin_fk FOREIGN KEY (chemin)
        REFERENCES monsore_dn.sites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT color_unit_fk FOREIGN KEY (color_unit)
        REFERENCES monsore_dn.unites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT color_value_fk FOREIGN KEY (color_value)
        REFERENCES monsore_dn.valeurs_qualitatives (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT espece_fk FOREIGN KEY (espece)
        REFERENCES monsore_dn.especes (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT individusnumber_unit_fk FOREIGN KEY (individusnumber_unit)
        REFERENCES monsore_dn.unites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION,
    CONSTRAINT sites_fk FOREIGN KEY (site)
        REFERENCES monsore_dn.sites (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
)

    TABLESPACE pg_default;

ALTER TABLE IF EXISTS monsore_dn.pem
    OWNER to dbuser;

ALTER TABLE IF EXISTS monsore_dn.pem
    ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE monsore_dn.pem FROM PUBLIC;

GRANT SELECT ON TABLE monsore_dn.pem TO PUBLIC;

GRANT ALL ON TABLE monsore_dn.pem TO dbuser;
-- Index: date_idx

-- DROP INDEX IF EXISTS monsore_dn.date_idx;

CREATE INDEX IF NOT EXISTS date_idx
    ON monsore_dn.pem USING brin
        (ts_date timestamp_minmax_multi_ops)
    WITH (pages_per_range=128, autosummarize=False)
    TABLESPACE pg_default;
-- Index: projet_hk_idx

-- DROP INDEX IF EXISTS monsore_dn.projet_hk_idx;

CREATE INDEX IF NOT EXISTS projet_hk_idx
    ON monsore_dn.pem USING btree
        (projet ASC NULLS LAST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;
-- Index: projet_idx

-- DROP INDEX IF EXISTS monsore_dn.projet_idx;

CREATE INDEX IF NOT EXISTS projet_idx
    ON monsore_dn.pem USING btree
        (projet ASC NULLS LAST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;
-- Index: site_hk_idx

-- DROP INDEX IF EXISTS monsore_dn.site_hk_idx;

CREATE INDEX IF NOT EXISTS site_hk_idx
    ON monsore_dn.pem USING btree
        (site_hk ASC NULLS LAST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;
-- Index: site_idx

-- DROP INDEX IF EXISTS monsore_dn.site_idx;

CREATE INDEX IF NOT EXISTS site_idx
    ON monsore_dn.pem USING btree
        (site ASC NULLS LAST)
    WITH (fillfactor=100, deduplicate_items=True)
    TABLESPACE pg_default;
-- POLICY: pem_5568f384-0942-42cb-9ece-8c021f01ed3d

-- DROP POLICY IF EXISTS "pem_5568f384-0942-42cb-9ece-8c021f01ed3d" ON monsore_dn.pem;

CREATE POLICY "pem_5568f384-0942-42cb-9ece-8c021f01ed3d"
    ON monsore_dn.pem
    AS PERMISSIVE
    FOR ALL
    TO "7fee4c54-c9c1-468a-9c7f-289382637b51_mgt_5568f384"
    USING (((ts_date <@ '["1984-01-01 00:00:00","1984-01-02 00:00:00")'::tsrange) AND (site_hk <@ 'type_de_sitesKbassin_versant.sitesKNULL_KEY__nivelle'::ltree) AND (projet_hk <@ 'projetKprojet_manche'::ltree)));