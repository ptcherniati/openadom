package fr.inra.oresing.rest;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Fixtures {

    public String getApplicationName() {
        return "monsore";
    }

    public String getApplicationConfigurationResourceName() {
        return "/data/monsore.yaml";
    }

    public Map<String, String> getReferentielFiles() {
        Map<String, String> referentielFiles = new HashMap<>();
        referentielFiles.put("especes", "/data/refdatas/especes.csv");
        referentielFiles.put("projet", "/data/refdatas/projet.csv");
        referentielFiles.put("sites", "/data/refdatas/sites.csv");
        referentielFiles.put("themes", "/data/refdatas/themes.csv");
        referentielFiles.put("type de fichiers", "/data/refdatas/type_de_fichiers.csv");
        referentielFiles.put("type_de_sites", "/data/refdatas/type_de_sites.csv");
        referentielFiles.put("types_de_donnees_par_themes_de_sites_et_projet", "/data/refdatas/types_de_donnees_par_themes_de_sites_et_projet.csv");
        referentielFiles.put("unites", "/data/refdatas/unites.csv");
        referentielFiles.put("valeurs_qualitatives", "/data/refdatas/valeurs_qualitatives.csv");
        referentielFiles.put("variables", "/data/refdatas/variables.csv");
        referentielFiles.put("variables_et_unites_par_types_de_donnees", "/data/refdatas/variables_et_unites_par_types_de_donnees.csv");
        return referentielFiles;
    }

    public String getPemDataResourceName() {
        return "/data/data-pem.csv";
    }
}
