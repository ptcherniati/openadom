package fr.inra.oresing.workflow.cascade.config.rules;

import fr.inra.oresing.workflow.cascade.config.ConsistencyRule;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * BLOCKING : MERGE_FILE + ASYNC + directWriteParallel = chunks dans le
 * désordre dans le merged.csv ( sink merge-file séquentiel par nature ) .
 */
@Component
public class MergeFileAsyncParallelRule implements ConsistencyRule {

    @Override public String code()         { return "MERGE_FILE_ASYNC_PARALLEL_INCONSISTENT"; }
    @Override public Severity severity()   { return Severity.BLOCKING; }

    @Override
    public Optional<String> check(EffectiveConfig c) {
        boolean isMergeFile  = "MERGE_FILE".equals(c.stringValue("sinkStrategy"));
        boolean isAsync      = "ASYNC".equals(c.stringValue("executionMode"));
        boolean parallel     = c.boolValue("directWriteParallel");
        if (isMergeFile && isAsync && parallel) {
            return Optional.of(
                    "MERGE_FILE + ASYNC + directWriteParallel = chunks dans le désordre "
                            + "dans le merged.csv ( sink merge-file est séquentiel par nature ) . "
                            + "Désactiver directWriteParallel ou passer en DIRECT_COPY .");
        }
        return Optional.empty();
    }
}
