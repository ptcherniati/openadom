package fr.inra.oresing.workflow.cascade.config.rules;

import fr.inra.oresing.workflow.cascade.config.ConsistencyRule;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** WARNING : MERGE_FILE + ASYNC = pas d'effet reel ( sink merge-file séquentiel ) . */
@Component
public class MergeFileAsyncNoEffectRule implements ConsistencyRule {

    @Override public String code()       { return "MERGE_FILE_ASYNC_NO_REAL_EFFECT"; }
    @Override public Severity severity() { return Severity.WARNING; }

    @Override
    public Optional<String> check(EffectiveConfig c) {
        if ("MERGE_FILE".equals(c.stringValue("sinkStrategy"))
                && "ASYNC".equals(c.stringValue("executionMode"))) {
            return Optional.of(
                    "MERGE_FILE est un path legacy purement séquentiel ; ASYNC n'a "
                            + "pas d'effet réel ( sink merge-file = 1 thread agrégateur ) .");
        }
        return Optional.empty();
    }
}
