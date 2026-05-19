package fr.inra.oresing.domain.groovy.predefined.script;

import java.util.Map;

/**
 * Interface scellée pour les fournisseurs de constantes de script.
 * Permet de lier des constantes au contexte de script et de découvrir dynamiquement les implémentations.
 */
public sealed interface ScriptConstantProvider permits BuildCompositeKey, BuildExceptionProvider, BuildManyCompositeKey, EscapeLabelProvider, NaturalKeyProvider {

    /**
     * Tableau statique pré-instancié de tous les providers.
     * <p>
     * R-P2-5 — Remplace la boucle {@code getPermittedSubclasses() + newInstance()} qui
     * instanciait 5 providers par réflexion à chaque appel Groovy. Sur un import SWC de
     * 100 lignes × ~48 expressions, cette boucle générait ~21 600 instanciations inutiles.
     * Avec ce tableau statique, le coût est nul après le chargement de la classe.
     */
    ScriptConstantProvider[] PROVIDERS = {
            new BuildCompositeKey(),
            new BuildExceptionProvider(),
            new BuildManyCompositeKey(),
            new EscapeLabelProvider(),
            new NaturalKeyProvider()
    };

    static void addAllToContext(Map<String, Object> context) {
        for (ScriptConstantProvider provider : PROVIDERS) {
            provider.bindToContext(context);
        }
    }

    void bindToContext(Map<String, Object> context);
}