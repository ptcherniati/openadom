package fr.inra.oresing.checker;

import java.util.Map;

/**
 * Permet de verifier une valeur provenant d'un fichier CSV.
 * Si la valeur n'est pas bonne, il faut lever une exception, sinon ne rien faire.
 * Le check peut avoir des paramètres initialisé avant l'appel à la méthode check.
 * La méthode check peut-être appelée plusieurs fois consécutivement.
 *
 * Dans tous les cas dans les parametres l'identifiant de l'application est envoyé
 * via la clé {@link PARAM_APPLICATION}
 */
public interface Checker {

    String PARAM_APPLICATION = "application";

    default String getName() {
        return this.getClass().getSimpleName().replaceAll("Checker$", "");
    }

    void setParam(Map<String, String> params);
    <T> T check(String value) throws CheckerException;
}
