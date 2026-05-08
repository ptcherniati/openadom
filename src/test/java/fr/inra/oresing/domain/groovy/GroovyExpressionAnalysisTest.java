package fr.inra.oresing.domain.groovy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GroovyExpressionAnalysis — analyse statique et décision de mise en cache")
@Tag("domain.model")
class GroovyExpressionAnalysisTest {

    @Test
    @DisplayName("empty() est cacheable, sans colonnes ni références")
    void emptyIsCacheableAndHasNoColumnsOrRefs() {
        GroovyExpressionAnalysis analysis = GroovyExpressionAnalysis.empty();
        assertThat(analysis.isCacheable()).isTrue();
        assertThat(analysis.datumColumns()).isEmpty();
        assertThat(analysis.referencesAccessed()).isEmpty();
        assertThat(analysis.usesCurrentRowNumber()).isFalse();
        assertThat(analysis.hasUnresolvableAccess()).isFalse();
    }

    @Test
    @DisplayName("unanalyzable() n'est pas cacheable et a hasUnresolvableAccess=true")
    void unanalyzableIsNotCacheable() {
        GroovyExpressionAnalysis analysis = GroovyExpressionAnalysis.unanalyzable();
        assertThat(analysis.isCacheable()).isFalse();
        assertThat(analysis.hasUnresolvableAccess()).isTrue();
        assertThat(analysis.datumColumns()).isEmpty();
        assertThat(analysis.referencesAccessed()).isEmpty();
    }

    @Test
    @DisplayName("usesCurrentRowNumber=true rend le résultat non cacheable")
    void usesCurrentRowNumberDisablesCache() {
        GroovyExpressionAnalysis analysis = new GroovyExpressionAnalysis(
                Set.of(), Set.of(), true, false);
        assertThat(analysis.isCacheable()).isFalse();
        assertThat(analysis.usesCurrentRowNumber()).isTrue();
    }

    @Test
    @DisplayName("usesCurrentRowNumber=false et hasUnresolvableAccess=false → cacheable")
    void bothFalseIsCacheable() {
        GroovyExpressionAnalysis analysis = new GroovyExpressionAnalysis(
                Set.of("col1"), Set.of("ref1"), false, false);
        assertThat(analysis.isCacheable()).isTrue();
    }

    @Test
    @DisplayName("Record avec colonnes et références accédées expose les bons accesseurs")
    void recordWithColumnsAndRefs() {
        Set<String> cols = Set.of("site", "annee");
        Set<String> refs = Set.of("tr_ref", "autre_ref");
        GroovyExpressionAnalysis analysis = new GroovyExpressionAnalysis(cols, refs, false, false);
        assertThat(analysis.datumColumns()).containsExactlyInAnyOrder("site", "annee");
        assertThat(analysis.referencesAccessed()).containsExactlyInAnyOrder("tr_ref", "autre_ref");
        assertThat(analysis.isCacheable()).isTrue();
    }

    @Test
    @DisplayName("hasUnresolvableAccess=true et usesCurrentRowNumber=false → non cacheable")
    void unresolvableAccessAloneDisablesCache() {
        GroovyExpressionAnalysis analysis = new GroovyExpressionAnalysis(
                Set.of(), Set.of(), false, true);
        assertThat(analysis.isCacheable()).isFalse();
    }
}
