package fr.inra.oresing.checker;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Streams;

import java.util.Set;

/**
 * Configuration pour un checker de type "Expression Groovy"
 */
public interface GroovyLineCheckerConfiguration extends LineCheckerConfiguration {

    /**
     * L'expression groovy elle-même. Elle doit retourner un booléen.
     */
    String getExpression();

    /**
     * Les référentiels qui devront être chargés puis injectés dans le contexte au moment de
     * l'évaluation de l'expression {@link #getExpression()}
     */
    String getReferences();

    default Set<String> doGetReferencesAsCollection() {
        if (StringUtils.isEmpty(getReferences())) {
            return ImmutableSet.of();
        }
        return Streams.stream(Splitter.on(",").split(getReferences())).collect(ImmutableSet.toImmutableSet());
    }

    /**
     * Les types de données qui devront être chargées puis injectés dans le contexte au moment de
     * l'évaluation de l'expression {@link #getExpression()}
     */
    String getDatatypes();

    default Set<String> doGetDataTypesAsCollection() {
        if (StringUtils.isEmpty(getDatatypes())) {
            return ImmutableSet.of();
        }
        return Streams.stream(Splitter.on(",").split(getDatatypes())).collect(ImmutableSet.toImmutableSet());
    }
}
