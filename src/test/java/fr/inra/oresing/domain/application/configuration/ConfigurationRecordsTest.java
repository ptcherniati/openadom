package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationAdditionalFile;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationComponent;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des records de configuration légers.
 */
@Tag("domain.model")
@DisplayName("Configuration records légers")
class ConfigurationRecordsTest {

    // ─── HierarchicalNode ────────────────────────────────────────────────────

    @Nested
    @DisplayName("DependsParent / DependsRecursive / DependsReferences")
    class DependsTest {
        @Test
        void dependsParentAccessors() {
            DependsParent d = new DependsParent(Depends.DependsType.DependsParent, "ref", "comp");
            assertThat(d.type()).isEqualTo(Depends.DependsType.DependsParent);
            assertThat(d.references()).isEqualTo("ref");
            assertThat(d.component()).isEqualTo("comp");
        }

        @Test
        void dependsRecursiveAccessors() {
            DependsRecursive d = new DependsRecursive(Depends.DependsType.DependsRecursive, "r", "c");
            assertThat(d.type()).isEqualTo(Depends.DependsType.DependsRecursive);
            assertThat(d).isInstanceOf(Depends.class);
        }

        @Test
        void dependsReferencesAccessors() {
            DependsReferences d = new DependsReferences(Depends.DependsType.DependsReferences, "r2", "c2");
            assertThat(d.type()).isEqualTo(Depends.DependsType.DependsReferences);
        }

        @Test
        void dependsTypeHasThreeValues() {
            assertThat(Depends.DependsType.values()).hasSize(3);
        }
    }

    // ─── ColumnConstantHeaders ───────────────────────────────────────────────

    @Nested
    @DisplayName("ColumnConstantHeaderByColumnNumber")
    class ColumnConstantHeaderByColumnNumberTest {
        @Test
        void accessors() {
            ColumnConstantHeaderByColumnNumber h = new ColumnConstantHeaderByColumnNumber(
                    ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByColumnNumber, 1, 2);
            assertThat(h.rowNumber()).isEqualTo(1);
            assertThat(h.columnNumber()).isEqualTo(2);
            assertThat(h.type()).isEqualTo(ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByColumnNumber);
        }
    }

    @Nested
    @DisplayName("ColumnConstantHeaderByHeaderName")
    class ColumnConstantHeaderByHeaderNameTest {
        @Test
        void accessors() {
            ColumnConstantHeaderByHeaderName h = new ColumnConstantHeaderByHeaderName(
                    ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByHeaderName,
                    0, "myHeader", java.util.List.of(Locale.FRENCH));
            assertThat(h.headerName()).isEqualTo("myHeader");
            assertThat(h.langRestrictions()).contains(Locale.FRENCH);
        }
    }

    @Nested
    @DisplayName("FileColumnConstantHeader")
    class FileColumnConstantHeaderTest {
        @Test
        void accessors() {
            FileColumnConstantHeader h = new FileColumnConstantHeader(
                    ConstantImportHeader.ConstantImportHeaderType.FileConstantHeader, 3, 5);
            assertThat(h.rowNumber()).isEqualTo(3);
            assertThat(h.columnNumber()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("SubmissionConstantHeader")
    class SubmissionConstantHeaderTest {
        @Test
        void accessors() {
            SubmissionConstantHeader h = new SubmissionConstantHeader(
                    ConstantImportHeader.ConstantImportHeaderType.SubmissionComponent);
            assertThat(h.type()).isEqualTo(ConstantImportHeader.ConstantImportHeaderType.SubmissionComponent);
        }
    }

    @Nested
    @DisplayName("ConstantImportHeaderType enum")
    class ConstantImportHeaderTypeTest {
        @Test
        void allValues() {
            assertThat(ConstantImportHeader.ConstantImportHeaderType.values()).hasSize(5);
        }
    }

    // ─── AuthorizationScopeComponentData ────────────────────────────────────

    @Nested
    @DisplayName("AuthorizationScopeComponentData")
    class AuthorizationScopeComponentDataTest {
        @Test
        void accessors() {
            AuthorizationScopeComponentData d = new AuthorizationScopeComponentData("comp", "data");
            assertThat(d.component()).isEqualTo("comp");
            assertThat(d.data()).isEqualTo("data");
        }
    }

    // ─── ValidationParams ────────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidationParams")
    class ValidationParamsTest {
        @Test
        void accessors() {
            ValidationParams p = new ValidationParams(ConfigurationException.exception, Map.of("k", "v"), "path.field");
            assertThat(p.exception()).isEqualTo(ConfigurationException.exception);
            assertThat(p.params()).containsEntry("k", "v");
            assertThat(p.path()).isEqualTo("path.field");
        }
    }

    // ─── Internationalization classes ────────────────────────────────────────

    @Nested
    @DisplayName("InternationalizationTitle")
    class InternationalizationTitleTest {
        @Test
        void settersAndGetters() {
            InternationalizationTitle t = new InternationalizationTitle();
            t.setTitle(Map.of(Locale.FRENCH, "Titre"));
            t.setDescription(Map.of(Locale.ENGLISH, "Desc"));
            assertThat(t.getTitle()).containsKey(Locale.FRENCH);
            assertThat(t.getDescription()).containsKey(Locale.ENGLISH);
        }

        @Test
        void constants() {
            assertThat(InternationalizationTitle.TITLE).isEqualTo("title");
            assertThat(InternationalizationTitle.DESCRIPTION).isEqualTo("description");
        }
    }

    @Nested
    @DisplayName("InternationalizationComponent")
    class InternationalizationComponentTest {
        @Test
        void settersAndGetters() {
            InternationalizationComponent c = new InternationalizationComponent();
            InternationalizationTitle t = new InternationalizationTitle();
            c.setExportHeader(t);
            assertThat(c.getExportHeader()).isSameAs(t);
        }

        @Test
        void constants() {
            assertThat(InternationalizationComponent.EXPORT_HEADER).isEqualTo("exportHeader");
        }
    }

    @Nested
    @DisplayName("InternationalizationAdditionalFile")
    class InternationalizationAdditionalFileTest {
        @Test
        void settersAndGetters() {
            InternationalizationAdditionalFile af = new InternationalizationAdditionalFile();
            InternationalizationTitle t = new InternationalizationTitle();
            af.setI18n(t);
            af.setFields(Map.of("f1", t));
            assertThat(af.getI18n()).isSameAs(t);
            assertThat(af.getFields()).containsKey("f1");
        }
    }
}
