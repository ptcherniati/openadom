package fr.inra.oresing.domain.groovy.predefined.script;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Interface scellée pour les fournisseurs de constantes de script.
 * Permet de lier des constantes au contexte de script et de découvrir dynamiquement les implémentations.
 */
public sealed interface ScriptConstantProvider permits BuildCompositeKey, BuildExceptionProvider, BuildManyCompositeKey, EscapeLabelProvider, NaturalKeyProvider {

    void bindToContext(Map<String, Object> context);

    static void addAllToContext(Map<String, Object> context) {
        // Utilisation de getPermittedSubclasses pour obtenir les implémentations
        Class<?>[] implementations = ScriptConstantProvider.class.getPermittedSubclasses();

        for (Class<?> impl : implementations) {
            try {
                // Instancier le record en utilisant le constructeur par défaut
                ScriptConstantProvider provider = (ScriptConstantProvider) impl.getDeclaredConstructor().newInstance();
                provider.bindToContext(context);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Erreur lors de l'instanciation de " + impl.getName(), e);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("Le constructeur sans arguments est manquant pour " + impl.getName(), e);
            }
        }
    }
}