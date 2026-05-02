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
        if ("MERGE_FILE".equals(c.stringValue("sinkStrategy"))
                && c.stringValue("stagingStrategy") != null) {
            return Optional.of(
                    "MERGE_FILE n'utilise pas stagingStrategy ; la valeur sera ignorée "
                            + "( aucun staging table en mode merge-file -> storeAll ) .");
        }
        return Optional.empty();
    }
}
