package fr.inra.oresing.workflow.cascade.config.rules;

import fr.inra.oresing.workflow.cascade.config.ConsistencyRule;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** WARNING : SYNC + directWriteParallel = gain marginal ; preferer ASYNC . */
@Component
public class SyncDirectParallelRule implements ConsistencyRule {

    @Override public String code()       { return "SYNC_DIRECT_PARALLEL_MARGINAL"; }
    @Override public Severity severity() { return Severity.WARNING; }

    @Override
    public Optional<String> check(EffectiveConfig c) {
        if ("SYNC".equals(c.stringValue("executionMode")) && c.boolValue("directWriteParallel")) {
            return Optional.of(
                    "directWriteParallel a un effet uniquement en SYNC ; combinable "
                            + "mais le gain est marginal vs passer en ASYNC .");
        }
        return Optional.empty();
    }
}
