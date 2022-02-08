package fr.inra.oresing.checker;

/**
 * Configuration pour un checker de type "Expression Groovy"
 */
public interface GroovyLineCheckerConfiguration extends LineCheckerConfiguration {

    /**
     * L'expression groovy elle-même. Elle doit retourner un booléen.
     */
    GroovyConfiguration getGroovy();
}