package fr.inra.oresing.rest.model.configuration.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les méthodes statiques de NodeSchemaValidator.
 */
@Tag("core.config")
@DisplayName("NodeSchemaValidator – méthodes statiques")
class NodeSchemaValidatorStaticTest {

    @Test
    @DisplayName("joinPath(String, Set<String>) joint un chemin principal et des sous-chemins")
    void joinPathStringAndSet() {
        Set<String> paths = new LinkedHashSet<>();
        paths.add("child1");
        paths.add("child2");
        String result = NodeSchemaValidator.joinPath("parent", paths);
        assertThat(result).isEqualTo("parent > child1 > child2");
    }

    @Test
    @DisplayName("joinPath(String, Set<String>) avec chemin vide filtre les vides")
    void joinPathStringAndSetWithEmpty() {
        Set<String> paths = new LinkedHashSet<>();
        paths.add("");
        paths.add("child");
        String result = NodeSchemaValidator.joinPath("root", paths);
        assertThat(result).isEqualTo("root > child");
    }

    @Test
    @DisplayName("joinPath(String...) joint les chemins avec > comme séparateur")
    void joinPathVarargs() {
        String result = NodeSchemaValidator.joinPath("a", "b", "c");
        assertThat(result).isEqualTo("a > b > c");
    }

    @Test
    @DisplayName("joinPath(String...) avec un seul segment")
    void joinPathSingleSegment() {
        String result = NodeSchemaValidator.joinPath("lone");
        assertThat(result).isEqualTo("lone");
    }

    @Test
    @DisplayName("joinI18nPath joint avec . comme séparateur")
    void joinI18nPath() {
        String result = NodeSchemaValidator.joinI18nPath("root", "section", "field");
        assertThat(result).isEqualTo("root.section.field");
    }

    @Test
    @DisplayName("joinI18nPath remplace ' > ' par '.'")
    void joinI18nPathReplacesArrow() {
        // joinI18nPath joint avec "." puis remplace " > " par "."
        String result = NodeSchemaValidator.joinI18nPath("a > b", "c");
        // String.join(".", "a > b", "c") = "a > b.c" puis replace(" > ", ".") = "a.b.c"
        assertThat(result).isEqualTo("a.b.c");
    }

    @Test
    @DisplayName("REFERENCE_SCOPES_FOR_FILE a la valeur attendue")
    void referenceScopesForFileConstant() {
        assertThat(NodeSchemaValidator.REFERENCE_SCOPES_FOR_FILE).isEqualTo("referenceScopesForFile");
    }
}