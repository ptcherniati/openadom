package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.application.configuration.type.StringType;

class StringExampleBuilder {
    public static final StringType NOM_CODIQUE_DU_SITE = new StringType("Nom codique du site");
    public static final StringType FRENCH_SITE_NAME = new StringType("Nom du site en français");
    public static final StringType ENGLISH_SITE_NAME = new StringType("English site name");
    public static final StringType FRENCH_SITE_DESCRIPTION = new StringType("Description du site en français");
    public static final StringType ENGLISH_SITE_DESCRIPTION = new StringType("English site description");
    public static final StringType NOM_DU_SITE_PARENT = new StringType("Nom du site parent");
    public static final StringType TYPE_SITE_CODIC_NAME = new StringType("Nom codique du type de site");
    public static final StringType FRENCH_TYPE_SITE_NAME = new StringType("Nom du type de site en français");
    public static final StringType ENGLISH_TYPE_SITE_NAME = new StringType("English type site name");
    public static final StringType FRENCH_TYPE_SITE_DESCRIPTION = new StringType("Description du type de site en français");
    public static final StringType ENGLISH_TYPE_SITE_DESCRIPTION = new StringType("English type site description");
    public static final StringType NOM_CODIQUE_DU_PROJET = new StringType("Nom codique du projet");
    public static final StringType NOM_DU_PROJET_EN_FRANCAIS = new StringType("Nom du projet en français");
    public static final StringType ENGLISH_PROJECT_NAME = new StringType("English project name");
    public static final StringType DEFINITION_DU_PROJET_EN_FRANCAIS = new StringType("Définition du projet en français");
    public static final StringType ENGLISH_PROJECT_DEFINITION = new StringType("English project definition");
    public static final StringType SPECIES_DEFINITION_FR = new StringType("Défintion de l'espèce en français");
    public static final StringType ENGLISH_SPECIES_DEFINITION = new StringType("English species definition");
    public static final StringType REPOSITORY = new StringType(SubmissionType.OA_VERSIONING.name(), false);
    public static final StringType MONSORE = new StringType("monsore", true);
    public static final StringType INITIAL_VERSION = new StringType("3.0.1", true);
    public static final StringType COMMENT = new StringType("Fichier de test de l'application brokenADOM");
    public static final StringType DEFAULT_LANGUAGE = new StringType("fr");
    public static final StringType ESPECE = new StringType("Espèce");
    public static final StringType DATE = new StringType("Date");
    public static final StringType HEURE = new StringType("Heure");
    public static final StringType QUANTITE = new StringType("Quantité");
    public static final StringType PROPRIETE = new StringType("Proprieté");
    public static final StringType TAXON_NOM = new StringType("Nom du taxon");
    public static final StringType PROPRIETE_TAXON_HEADER_PREFIX = new StringType("pt_");
    public static final StringType PROPRIETE_TAXON_REFERENCE = new StringType("tr_propriete_taxon_ptx");
    public static final StringType PROPRIETE_TAXON_REFERENCE_TO_LOOKUP = new StringType("ptx_propriete");
    public static final StringType DATE_TIME_GRROVY_EXPRESSION = new StringType("return datum.date + \" \" + datum.heure");
    public static final StringType DATA_DATE_TIME_GRROVY_EXPRESSION = new StringType("return datum.dat_date + \" \" + datum.dat_heure");
    public static final StringType OUTIL = new StringType("Outil");
    public static final StringType SITE = new StringType("Site");
    public static final StringType TYPE_DE_SITE = new StringType("Site");
    public static final StringType SITE_PARENT = new StringType("Site parent");
    public static final StringType HIDDEN = new StringType("__HIDDEN__");
    public static final StringType REPETITION = new StringType("Répétition");
    public static final StringType OPENADOM_VERSION = new StringType(Configuration.OPEN_ADOM_VERSION_PATTERN);

}