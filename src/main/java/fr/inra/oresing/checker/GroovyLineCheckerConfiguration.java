package fr.inra.oresing.checker;

/**
 * Configuration pour un checker de type "Expression Groovy"
 */
public interface GroovyLineCheckerConfiguration extends LineCheckerConfiguration {

    /**
     * L'expression groovy elle-même. Elle doit retourner un booléen.
     */
    String getExpression();

    /**
     * Les référentiels qui devront être chargés puis injectés dans le contexte au moment de
     * l'évaluation de l'expression {@link #getExpression()}
     */
    String getReferences();

    /**
     * Les types de données qui devront être chargées puis injectés dans le contexte au moment de
     * l'évaluation de l'expression {@link #getExpression()}
     */
    String getDatatypes();
}
