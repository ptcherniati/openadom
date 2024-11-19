package fr.inra.oresing.domain.groovy.predefined.script;

import fr.inra.oresing.domain.groovy.predefined.builder.naturalkey.NaturalKeyBuilder;

import java.util.List;
import java.util.Map;

/**
 * Fournisseur de constantes pour la gestion des clés naturelles.
 * Ajoute une instance de NaturalKeyBuilder au contexte de script.
 */
public record NaturalKeyProvider() implements ScriptConstantProvider {

    @Override
    public void bindToContext(Map<String, Object> context) {
        NaturalKeyBuilder getNaturalKey = new NaturalKeyBuilder(context);
        context.put("OA_naturalKeyBuilder", getNaturalKey);
    }
}