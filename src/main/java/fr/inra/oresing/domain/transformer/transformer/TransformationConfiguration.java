package fr.inra.oresing.domain.transformer.transformer;

import fr.inra.oresing.domain.GroovyDataInjectionConfiguration;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.Set;

/**
 * Indique qu'il faut transformer la donnée (avant de la vérifier) et comment
 */
public interface TransformationConfiguration extends GroovyDataInjectionConfiguration {

    /**
     * Si la valeur doit être transformée en l'échappant pour lui donner la forme d'une clé
     */
    boolean isCodify();
    Set<String> references();
    Set<String> datatypes();
    String expression();
    Set<String> exceptionMessages();
    Multiplicity multiplicity();

    @Override
    default Set<String> getReferences(){
        return references();
    }

    @Override
    default Set<String> getData(){
        return datatypes();
    }
}
