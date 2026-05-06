package fr.inra.oresing.domain.data.read.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour les enums et méthodes statiques des DownloadDatasetQuery.
 * Ces tests ne nécessitent pas de contexte Spring.
 */
@DisplayName("Tests DownloadDatasetQueryAdvancedSearch.FieldType")
@Tag("domain.model")
class DownloadDatasetQueryAdvancedSearchTest {

    @Nested
    @DisplayName("FieldType.convertToNumber")
    class ConvertToNumberTest {

        @Test
        @DisplayName("Convertit un entier valide en nombre")
        void shouldConvertValidInteger() {
            Number result = DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber("42");
            assertNotNull(result);
            assertEquals(42.0f, result.floatValue(), 0.001f);
        }

        @Test
        @DisplayName("Convertit un flottant valide en nombre")
        void shouldConvertValidFloat() {
            Number result = DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber("3.14");
            assertNotNull(result);
            assertEquals(3.14f, result.floatValue(), 0.001f);
        }

        @Test
        @DisplayName("Retourne null pour une chaîne non numérique")
        void shouldReturnNullForNonNumeric() {
            Number result = DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber("not-a-number");
            assertNull(result);
        }

        @Test
        @DisplayName("Lance NullPointerException pour null (Float.valueOf(null) non capturé)")
        void shouldThrowNPEForNull() {
            // Float.valueOf(null) lance NPE, non capturée car seul NumberFormatException est attrapé
            assertThrows(NullPointerException.class,
                    () -> DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber(null));
        }

        @Test
        @DisplayName("Retourne null pour une chaîne vide")
        void shouldReturnNullForEmpty() {
            Number result = DownloadDatasetQueryAdvancedSearch.FieldType.convertToNumber("");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("FieldType.convertToBoolean")
    class ConvertToBooleanTest {

        @Test
        @DisplayName("Retourne false par défaut (Boolean.getBoolean se base sur les propriétés système)")
        void shouldReturnFalseByDefault() {
            // Boolean.getBoolean("true") retourne false car "true" n'est pas une propriété système
            assertFalse(DownloadDatasetQueryAdvancedSearch.FieldType.convertToBoolean("true"));
            assertFalse(DownloadDatasetQueryAdvancedSearch.FieldType.convertToBoolean("false"));
        }
    }

    @Nested
    @DisplayName("FieldType enum values")
    class FieldTypeEnumTest {

        @Test
        @DisplayName("L'enum contient les 5 valeurs attendues")
        void shouldHaveExpectedValues() {
            DownloadDatasetQueryAdvancedSearch.FieldType[] values = DownloadDatasetQueryAdvancedSearch.FieldType.values();
            assertEquals(5, values.length);
        }

        @Test
        @DisplayName("Résolution correcte de chaque valeur par nom")
        void shouldResolveByName() {
            assertNotNull(DownloadDatasetQueryAdvancedSearch.FieldType.valueOf("date"));
            assertNotNull(DownloadDatasetQueryAdvancedSearch.FieldType.valueOf("time"));
            assertNotNull(DownloadDatasetQueryAdvancedSearch.FieldType.valueOf("datetime"));
            assertNotNull(DownloadDatasetQueryAdvancedSearch.FieldType.valueOf("numeric"));
            assertNotNull(DownloadDatasetQueryAdvancedSearch.FieldType.valueOf("bool"));
        }
    }
}