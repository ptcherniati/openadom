package fr.inra.oresing.domain.groovy.predefined.script;

import fr.inra.oresing.domain.application.configuration.Ltree;
import groovy.lang.Closure;

import java.util.Map;

/**
 * Fournisseur de constantes pour l'échappement des étiquettes.
 * Ajoute une fonction d'échappement au contexte de script.
 */
public record EscapeLabelProvider() implements ScriptConstantProvider {

    /**
     * Lie la fonction d'échappement d'étiquettes au contexte de script.
     *
     * @param context le contexte de script où la constante doit être ajoutée
     */
    @Override
    public void bindToContext(Map<String, Object> context) {
        Closure<String> escapeLabel = new Closure<String>(this) {
            public String doCall(String label) {
                return Ltree.escapeToLabel(label);
            }
        };
        context.put("OA_escapeLabel", escapeLabel);
    }
}