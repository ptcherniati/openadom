package fr.inra.oresing.model;

/**
 * Exprime pour un format de fichier CSV si la présence d'une colonne est exigée ou facultative.
 */
public enum ColumnPresenceConstraint {

    /**
     * Obligatoire, le fichier doit avoir la colonne : on doit pouvoir trouver un entête
     */
    MANDATORY,

    /**
     * Facultatif, la colonne peut être absente du fichier CSV.
     */
    OPTIONAL;

    public boolean isMandatory() {
        return MANDATORY == this;
    }
}
