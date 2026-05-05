package fr.inra.oresing.domain.application.configuration.migration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriétés de configuration du mécanisme de migration/sécurisation de la base de données
 * lors d'une mise à jour de configuration OpenADOM.
 *
 * <p>Préfixe Spring : {@code openadom.migration}. Toutes les valeurs ont des
 * défauts raisonnables ; surchargeables via variables d'environnement
 * (ex. {@code OPENADOM_MIGRATION_BYPASS_CONFIGURATION_CHECK}).
 *
 * <h2>Fonctionnement</h2>
 * Lors d'une mise à jour de configuration, le système détecte automatiquement
 * les changements entre l'ancienne et la nouvelle configuration et évalue si ces
 * changements sont sûrs pour la base de données (schéma de normalisation).
 *
 * <ul>
 *   <li>Si {@code bypass-configuration-check=false} (mode sécurisé) : les changements
 *       qui modifient le schéma de données de façon incompatible (ex. suppression de composant,
 *       changement de type de checker) bloquent la mise à jour et nécessitent une confirmation
 *       explicite.</li>
 *   <li>Si {@code bypass-configuration-check=true} (mode permissif, valeur par défaut) :
 *       la vérification est ignorée et la mise à jour s'effectue sans contrainte.
 *       Comportement historique avant l'introduction de ce mécanisme.</li>
 * </ul>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "openadom.migration")
public class MigrationProperties {

    /**
     * Désactive les vérifications de compatibilité lors d'une mise à jour de configuration.
     *
     * <p>Quand {@code true} (valeur par défaut), toute mise à jour de configuration est
     * acceptée sans vérification de compatibilité du schéma de base de données.
     * Cela correspond au comportement historique avant l'introduction du mécanisme
     * de sécurisation.
     *
     * <p>Quand {@code false}, les changements incompatibles avec le schéma existant
     * (suppression de datatype, modification structurelle de composants, etc.) bloquent
     * la mise à jour tant qu'ils ne sont pas acceptés explicitement via
     * l'API de migration ({@code /api/v1/applications/{name}/migrate}).
     *
     * <p>Surcharger via {@code OPENADOM_MIGRATION_BYPASS_CONFIGURATION_CHECK=false}
     * pour activer la sécurisation en production.
     */
    private boolean bypassConfigurationCheck = true;
}