package fr.inra.oresing.checker.decorators;

/**
 * Configuration de la décoration d'une donnée.
 */
public interface DecoratorConfiguration {

    /**
     * Indique que la donnée doit être transformée pour subir un échappement afin d'être utilisable comme clé
     */
    boolean isCodify();

    /**
     * Indique que la validation doit remonter une erreur si donnée est absente
     */
    boolean isRequired();

    /**
     * Avant d'être vérifiée, la donnée doit être transformée en appliquant cette expression.
     */
    String getGroovy();

    /**
     * Les référentiels qui devront être chargés puis injectés dans le contexte au moment de
     * l'évaluation de l'expression {@link #getGroovy()}
     */
    String getReferences();
}
