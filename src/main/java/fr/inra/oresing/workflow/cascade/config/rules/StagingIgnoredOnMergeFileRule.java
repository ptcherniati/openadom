package fr.inra.oresing.workflow.cascade.config.rules;

import fr.inra.oresing.workflow.cascade.config.ConsistencyRule;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * WARNING : MERGE_FILE n'utilise pas stagingStrategy ; valeur ignorée
 * cote runtime mais on previent l'admin .
 */
@Component
public class StagingIgnoredOnMergeFileRule implements ConsistencyRule {

    @Override public String code()         { return "STAGING_STRATEGY_IGNORED_ON_MERGE_FILE"; }
    @Override public Severity severity()   { return Severity.WARNING; }

    @Override
    public Optional<String> check(EffectiveConfig c) {
        String sink    = c.stringValue("sinkStrategy");
        String staging = c.stringValue("stagingStrategy");
        if ("MERGE_FILE".equals(sink) && staging != null) {
            return Optional.of(
                    "MERGE_FILE n'utilise pas stagingStrategy ; la valeur '" + staging
                            + "' sera ignoree ( les chunks sont concatenes sur disque "
                            + "puis 1 COPY direct vers la table finale , sans staging ) . "
                            + "L'UI masque ce champ quand MERGE_FILE est selectionne ; "
                            + "ce message ne devrait apparaitre que pour des appels API "
                            + "directs qui specifient explicitement la stagingStrategy .");
        }
        return Optional.empty();
    }
}
