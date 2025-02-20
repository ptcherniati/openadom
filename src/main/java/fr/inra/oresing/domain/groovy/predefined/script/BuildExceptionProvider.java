package fr.inra.oresing.domain.groovy.predefined.script;

import fr.inra.oresing.domain.groovy.exception.GroovyException;
import groovy.lang.Closure;

import java.util.Map;

/**
 * Fournisseur de constantes pour la construction d'exceptions.
 * Ajoute une fonction de construction d'exception au contexte de script.
 */
public record BuildExceptionProvider() implements ScriptConstantProvider {

    /**
     * Lie la fonction de construction d'exception au contexte de script.
     *
     * @param context le contexte de script où la constante doit être ajoutée
     */
    @Override
    public void bindToContext(Map<String, Object> context) {
        Closure<GroovyException> buildException = new Closure<GroovyException>(this) {
            public GroovyException doCall(String message, Map<String, Object> params) {
                return new GroovyException(message, params);
            }
        };
        context.put("OA_buildException", buildException);
    }
}