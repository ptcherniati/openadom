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
    OPTIONAL,

    /**
     * La colonne doit être absente, c'est une donnée calculée.
     */
    ABSENT;

    public boolean isMandatory() {
        return MANDATORY == this;
    }

    /**
     * Si une colonne est attendue dans le fichier CSV
     */
    public boolean isExpected() {
        return ABSENT != this;
    }
}
