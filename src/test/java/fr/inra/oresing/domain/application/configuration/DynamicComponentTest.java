package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour DynamicComponent.
 */
@Tag("core.config")
@DisplayName("DynamicComponent – instanciation et méthodes")
class DynamicComponentTest {

    private DynamicComponent buildComponent(String key, String prefix, String reference, String colToLookFor) {
        return new DynamicComponent(
                ComponentDescription.ComponentDescriptionType.DynamicComponent,
                key,
                null,
                key + "_export",
                List.of(),
                Set.of(),
                false,
                ComponentPresenceConstraint.OPTIONAL,
                CheckerDescription.NO_CHECKER,
                prefix,
                reference,
                colToLookFor,
                null
        );
    }

    @Test
    @DisplayName("accesseurs du record")
    void accessors() {
        DynamicComponent comp = buildComponent("myKey", "prefix_", "refType", "colHeader");
        assertThat(comp.componentKey()).isEqualTo("myKey");
        assertThat(comp.prefix()).isEqualTo("prefix_");
        assertThat(comp.reference()).isEqualTo("refType");
        assertThat(comp.referenceColumnToLookForHeader()).isEqualTo("colHeader");
        assertThat(comp.submissionAuthorizationScope()).isNull();
    }

    @Test
    @DisplayName("buildImportHeaderForComponent() construit le header attendu")
    void buildImportHeaderForComponent() {
        DynamicComponent comp = buildComponent("key", "myPrefix", "myRef", "myCol");
        String header = comp.buildImportHeaderForComponent();
        assertThat(header).isEqualTo("myPrefixForColumnmyColOfDatamyRef");
    }

    @Test
    @DisplayName("buildImportDataExempleForComponent() retourne une valeur non-null")
    void buildImportDataExempleForComponent() {
        DynamicComponent comp = buildComponent("key", "p", "r", "c");
        assertThat(comp.buildImportDataExempleForComponent()).isNotNull();
    }

    @Test
    @DisplayName("withSubmission() crée un nouveau DynamicComponent avec le bon scope")
    void withSubmission() {
        DynamicComponent comp = buildComponent("key", "p", "r", "c");
        ComponentDescription withSub = comp.withSubmission("newScope");
        assertThat(withSub).isInstanceOf(DynamicComponent.class);
        assertThat(((DynamicComponent) withSub).submissionAuthorizationScope()).isEqualTo("newScope");
        assertThat(withSub.componentKey()).isEqualTo("key");
    }
}