package fr.inra.oresing.workflow.cascade.config.rules;

import fr.inra.oresing.workflow.cascade.config.ConsistencyRule;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * BLOCKING : DIRECT_COPY + NO_STAGING n'a pas de chemin sink implemente
 * a ce jour . NO_STAGING est valable uniquement avec MERGE_FILE
 * ( merge sur disque puis COPY direct vers la table finale ) . Pour
 * DIRECT_COPY l'utilisateur doit choisir une staging strategy explicite
 * ( PER_CONNECTION_TEMP , SHARED_UNLOGGED , PER_WORKFLOW_TABLE ) .
 */
@Component
public class DirectCopyNoStagingNotSupportedRule implements ConsistencyRule {

    @Override public String code()         { return "DIRECT_COPY_NO_STAGING_NOT_SUPPORTED"; }
    @Override public Severity severity()   { return Severity.BLOCKING; }

    @Override
    public Optional<String> check(EffectiveConfig c) {
        if ("DIRECT_COPY".equals(c.stringValue("sinkStrategy"))
                && "NO_STAGING".equals(c.stringValue("stagingStrategy"))) {
            return Optional.of(
                    "DIRECT_COPY + NO_STAGING n'est pas encore implemente . "
                            + "Choisir PER_CONNECTION_TEMP , SHARED_UNLOGGED ou "
                            + "PER_WORKFLOW_TABLE pour DIRECT_COPY ; ou basculer "
                            + "vers MERGE_FILE pour le mode bypass-staging .");
        }
        return Optional.empty();
    }
}
