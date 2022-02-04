package fr.inra.oresing.checker;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.assertj.core.util.Streams;

import java.util.Set;

public interface GroovyLineCheckerConfiguration extends LineCheckerConfiguration {
    String getExpression();

    /**
     * Ensemble des référentiels qu'il faut charger dans le contexte pour l'évaluation de l'expression {@link #getExpression()}
     */
    String getReferences();

    default Set<String> doGetReferencesAsCollection() {
        return Streams.stream(Splitter.on(",").split(getReferences())).collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Ensemble des données qu'il faut charger dans le contexte pour l'évaluation de l'expression {@link #getExpression()}
     */
    String getDatatypes();

    default Set<String> doGetDataTypesAsCollection() {
        return Streams.stream(Splitter.on(",").split(getDatatypes())).collect(ImmutableSet.toImmutableSet());
    }
}
