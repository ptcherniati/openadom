package fr.inra.oresing.workflow.cascade.config.rules;

import fr.inra.oresing.workflow.cascade.config.ConsistencyRule;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * WARNING : DIRECT_COPY + PER_CONNECTION_TEMP force sinkParallelism=1
 * ( connection sticky ) . Suggere SHARED_UNLOGGED ou PER_WORKFLOW_TABLE
 * quand le user veut exploiter le parallélisme sink .
 */
@Component
public class DirectCopyPerConnectionStickyWarning implements ConsistencyRule {

    @Override public String code()         { return "DIRECT_COPY_PER_CONNECTION_FORCES_SERIAL_SINK"; }
    @Override public Severity severity()   { return Severity.WARNING; }

    @Override
    public Optional<String> check(EffectiveConfig c) {
        if ("DIRECT_COPY".equals(c.stringValue("sinkStrategy"))
                && "PER_CONNECTION_TEMP".equals(c.stringValue("stagingStrategy"))) {
            return Optional.of(
                    "DIRECT_COPY + PER_CONNECTION_TEMP force sinkParallelism = 1 "
                            + "( connection sticky unique ) . Pour exploiter le parallélisme "
                            + "sink , utiliser SHARED_UNLOGGED quand cela sera supporte .");
        }
        return Optional.empty();
    }
}
