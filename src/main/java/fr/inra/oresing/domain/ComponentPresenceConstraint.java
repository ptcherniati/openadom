package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.SubmissionType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Exprime pour un format de fichier CSV si la présence d'une colonne est exigée ou facultative.
 */
public enum ComponentPresenceConstraint {

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

    public static final Set<String> VALUES = Arrays.stream(values()).map(ComponentPresenceConstraint::name).collect(Collectors.toSet());
    ;

    public boolean isMandatory() {
        return MANDATORY == this;
    }

    /**
     * Si une colonne est attendue dans le fichier CSV
     * @return
     */
    public boolean isExpected() {
        return ABSENT != this;
    }
}
