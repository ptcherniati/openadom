package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;

import static fr.inra.oresing.domain.application.configuration.examples.I18nExampleBuilder.buildI18n;

class TitleExampleBuilder {
    public static final TitleType STANDARD_DEVIATION = buildTitle(
            buildI18n("écart_type", "standard_deviation"),
            null);
    public static final TitleType QUALITY_CLASS = buildTitle(
            buildI18n("Indic de qualité", "Quality class"),
            buildI18n("0 pour une valeur valide ; 2 pour une valeur incorrecte", " 0 for valid value; 2 for bad value"));;
    public static final ConfigurationSchemaNodeType RIGHT_REQUEST_DESCRIPTION = TitleExampleBuilder.buildTitle(
            I18nExampleBuilder.buildI18n(
                    "Formulaire de demande de droits de l'application MONSORE",
                    "MONSORE App Rights Request Form"),
            I18nExampleBuilder.buildI18n(
                    "Vous pouvez demander des droits à l'application monsore en remplissant ce formulaire",
                    "You can request rights to the monsore application by filling out this form")
    );
    public static final TitleType SITES_NOM_FR = TitleExampleBuilder.buildTitle(


            I18nExampleBuilder.buildI18n(
                    "Nom du site",
                    null),
            I18nExampleBuilder.buildI18n(
                    "Le nom du site",
                    null)
    );
    public static final TitleType SITES_NOM_EN = TitleExampleBuilder.buildTitle(


            I18nExampleBuilder.buildI18n(
                    null,
                    "Site name"),
            I18nExampleBuilder.buildI18n(
                    null,
                    "A site name")
    );
    public static final TitleType TYPE_SITES_NOM_FR = TitleExampleBuilder.buildTitle(


            I18nExampleBuilder.buildI18n(
                    "Nom du type de site",
                    null),
            I18nExampleBuilder.buildI18n(
                    "Le nom du type de site",
                    null)
    );
    public static final TitleType TYPE_SITES_NOM_EN = TitleExampleBuilder.buildTitle(


            I18nExampleBuilder.buildI18n(
                    null,
                    "Site type name"),
            I18nExampleBuilder.buildI18n(
                    null,
                    "A site type name")
    );
    public static final TitleType TYPE_SITES_DEFINITION_FR = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    "Description du type de site",
                    null),
            I18nExampleBuilder.buildI18n(
                    "Une description du type de site",
                    null)
    );
    public static final TitleType TYPE_SITES_DEFINITION_EN = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    null,
                    "Site type description"),
            I18nExampleBuilder.buildI18n(
                    null,
                    "A site type description")
    );
    public static final TitleType SITES_DEFINITION_EN = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    null,
                    "Site description"),
            I18nExampleBuilder.buildI18n(
                    null,
                    "A site description")
    );
    public static final TitleType SITES_PARENT = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    "Site parent",
                    "Parent site"),
            I18nExampleBuilder.buildI18n(
                    "La zone d'étude parente contenant le site.",
                    "The parent study area containing the site.")
    );
    public static final TitleType PROJET_NOM_FR = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    "Nom du projet",
                    null),
            I18nExampleBuilder.buildI18n(
                    "Le nom du projet",
                    null)
    );
    public static final TitleType PROJET_NOM_EN = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    null,
                    "Project name"),
            I18nExampleBuilder.buildI18n(
                    null,
                    "The project name")
    );
    public static final TitleType PROJET_DEFINITION_FR = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    "Définition du projet",
                    null),
            I18nExampleBuilder.buildI18n(
                    "Une description du projet",
                    null)
    );
    public static final TitleType PROJET_DEFINITION_EN = TitleExampleBuilder.buildTitle(

            I18nExampleBuilder.buildI18n(
                    null,
                    "Project définition"),
            I18nExampleBuilder.buildI18n(
                    null,
                    "A roject description")
    );
    protected static final TitleType GENERIC_TITLE = buildTitle(
            buildI18n("un titre", "a title"),
            buildI18n("Ceci est un titre", " this is a title"));
    protected static final TitleType SOERE_NAME =
            buildTitle(
                    buildI18n("SOERE mon SOERE", "SOERE my SOERE"),
                    buildI18n("SOERE example basé sur petit fleuve côtiers", "Example of SOERE based on small coastal river")
            );
    protected static final TitleType NOM =
            buildTitle(
                    buildI18n("Nom", "Name"),
                    buildI18n("Nom", "Name")
            );
    protected static final TitleType SITE =
            buildTitle(
                    buildI18n("Site", "Site"),
                    buildI18n("Référentiel des Sites", "Site repository")
            );
    protected static final TitleType TYPE_DE_SITE =
            buildTitle(
                    buildI18n("Types de sites", "Sites types"),
                    buildI18n("Référentiel des types de sites", "Sites types repository")
            );
    protected static final TitleType TAXON =
            buildTitle(
                    buildI18n("Taxon", "Taxa"),
                    buildI18n("Référentiel des taxon", "Taxa repository")
            );

    protected static final TitleType PROPRIETE_TAXON =
            buildTitle(
                    buildI18n("Propriété des taxon", "Taxa properties repository"),
                    buildI18n("Référentiel des propriété des taxon", "Taxa properties")
            );

    protected static final TitleType PROJET =
            buildTitle(
                    buildI18n("Projet", "Project"),
                    buildI18n("Référentiel des projet", "Project repository")
            );


    protected static final TitleType ESPECE =
            buildTitle(
                    buildI18n("Espèce", "Species"),
                    buildI18n("Référentiel des espèces", "Species repository")
            );

    protected static final TitleType DATA =
            buildTitle(
                    buildI18n("Données", "Data"),
                    buildI18n("Référentiel des données", "Data repository")
            );

    protected static final TitleType START_DATE =
            buildTitle(
                    buildI18n("Date de début", "Start Date"),
                    buildI18n("La date de début au format dd/MM/yyyy", "The start date in dd/MM/yyyy format")
            );

    protected static final TitleType ORGANISME =
            buildTitle(
                    buildI18n("Nom de l'organisme de recherche", "Name of research organization"),
                    buildI18n("Renseignez ke nom de votre organisme de recherche", "Enter the name of your research organization")
            );

    protected static final TitleType TYPE_SITE = buildTitle(
            I18nExampleBuilder.buildI18n("Type de zone d'étude", "Site type"),
            I18nExampleBuilder.buildI18n("Type de zone d'étude", "Site type")
    );

    protected static final TitleType TYPE_SITE_COLUMN = buildTitle(
            I18nExampleBuilder.buildI18n("Type de zone d'étude", "Site type"),
            I18nExampleBuilder.buildI18n("Nom du type de zone d'étude", "Site type name")
    );
    protected static final TitleType END_DATE = buildTitle(
            I18nExampleBuilder.buildI18n("Date de fin", "End date"),
            I18nExampleBuilder.buildI18n("Date de fin", "End date")
    );
    protected static final TitleType HEURE = buildTitle(
            I18nExampleBuilder.buildI18n("Heure", "Time"),
            I18nExampleBuilder.buildI18n("Heure", "Time")
    );
    protected static final TitleType MASSE = buildTitle(
            I18nExampleBuilder.buildI18n("Masse", "Mass"),
            I18nExampleBuilder.buildI18n("Masse", "Mass")
    );
    protected static final TitleType OUTIL = buildTitle(
            I18nExampleBuilder.buildI18n("Outil", "Tool"),
            I18nExampleBuilder.buildI18n("Outil", "Tool")
    );
    protected static final TitleType ESPECE_COLUMN = buildTitle(
            I18nExampleBuilder.buildI18n("Espèce", "Species"),
            I18nExampleBuilder.buildI18n("Espèce", "Species")
    );
    protected static final TitleType START_DATE_COLUMN = buildTitle(
            I18nExampleBuilder.buildI18n("Date de début", "Start date"),
            I18nExampleBuilder.buildI18n("Date de début", "Start date")
    );
    protected static final TitleType SITE_COLUMN = buildTitle(
            I18nExampleBuilder.buildI18n("Site", null),
            I18nExampleBuilder.buildI18n("Nom du site", "Site Name")
    );
    protected static final TitleType SITE_PARENT = buildTitle(
            I18nExampleBuilder.buildI18n("Site parent", "Parent site"),
            I18nExampleBuilder.buildI18n("Nom du site parent", "Parent site name")
    );
    protected static final TitleType TYPE_DE_SITES = buildTitle(
            I18nExampleBuilder.buildI18n("Type de site", "Site types"),
            I18nExampleBuilder.buildI18n("Nom du type de site", "Site type name")
    );
    protected static final TitleType REPETITION = buildTitle(
            I18nExampleBuilder.buildI18n("Répétition", "Repetition"),
            I18nExampleBuilder.buildI18n("N° de la répétition", "Repetition number")
    );
    protected static final TitleType PROFONDEUR = buildTitle(
            I18nExampleBuilder.buildI18n("Profondeur", "Depth"),
            I18nExampleBuilder.buildI18n("Profondeur en valeur positive", "Depth in positive value")
    );
    protected static final TitleType DATE_TIME = buildTitle(
            I18nExampleBuilder.buildI18n("Date complète", "Complete date"),
            I18nExampleBuilder.buildI18n("Date complète au format dd/MM/yyyy HH:mm:ss", "Complete date with format dd/MM/yyyy HH:mm:ss")
    );
    protected static final TitleType SWC = buildTitle(
            I18nExampleBuilder.buildI18n("Humidité volumique du sol", "Soil water content"),
            I18nExampleBuilder.buildI18n("Définit l'humidité volumique du sol", "Define the soil water content")
    );
    protected static final TitleType SMP = buildTitle(
            I18nExampleBuilder.buildI18n("Tension d'humdité du sol", "Soil moisture pressure"),
            I18nExampleBuilder.buildI18n("Définit la tension d'humdité du sol", "Define the soil moisture pressure")
    );

    protected static TitleType buildTitle(
            I18nType title,
            I18nType description
    ) {
        return new TitleType(new LinkedHashMap<String, ConfigurationSchemaNodeType>() {{
            put(ConfigurationSchemaNode.OA_TITLE, title);
            put(ConfigurationSchemaNode.OA_DESCRIPTION, description);
        }});
    }
}