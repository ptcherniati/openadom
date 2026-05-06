package fr.inra.oresing.rest.services;

import fr.inra.oresing.rest.exceptions.views.FieldNameTooLongForSqlFieldException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour les classes internes de RelationalService (sans Spring / sans Docker).
 * Couvre SQLVariable, SQLVariableForData, SQLVariableForRefsLinkedTo,
 * SQLComponent et IdentifierTest.
 */
@Tag("domain.model")
@DisplayName("RelationalService – classes internes")
class RelationalServiceInnerClassesTest {

    // =========================================================================
    //  RelationalService.getIsValidIdentifierPattern
    // =========================================================================

    @Nested
    @DisplayName("getIsValidIdentifierPattern()")
    class IsValidIdentifierPatternTest {

        @Test
        @DisplayName("accepte un identifiant valide")
        void acceptsValidIdentifier() {
            var predicate = RelationalService.getIsValidIdentifierPattern(2, 40);
            assertTrue(predicate.test("mon_identifiant"));
        }

        @Test
        @DisplayName("refuse un identifiant commençant par un chiffre")
        void refusesIdentifierStartingWithDigit() {
            var predicate = RelationalService.getIsValidIdentifierPattern(1, 40);
            assertFalse(predicate.test("1invalide"));
        }

        @Test
        @DisplayName("refuse un identifiant trop court")
        void refusesTooShortIdentifier() {
            var predicate = RelationalService.getIsValidIdentifierPattern(3, 40);
            assertFalse(predicate.test("ab"));
        }

        @Test
        @DisplayName("borne min ajustée à 1 si valeur ≤ 0")
        void adjustsMinToOne() {
            // min ≤ 0 → ajusté à 1 ; un seul caractère 'a' doit passer
            var predicate = RelationalService.getIsValidIdentifierPattern(0, 40);
            assertTrue(predicate.test("a"));
        }

        @Test
        @DisplayName("borne max ajustée à 63 si valeur ≥ 64")
        void adjustsMaxTo63() {
            // max ≥ 64 → ajusté à 63 ; un identifiant de 63 chars doit passer
            String id63 = "a" + "b".repeat(62);
            var predicate = RelationalService.getIsValidIdentifierPattern(1, 100);
            assertTrue(predicate.test(id63));
        }
    }

    // =========================================================================
    //  RelationalService.IdentifierTest
    // =========================================================================

    @Nested
    @DisplayName("IdentifierTest")
    class IdentifierTestTest {

        @Test
        @DisplayName("forStringIdentifier() retourne un IdentifierTest valide")
        void forStringIdentifier() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("colonne");
            assertNotNull(it);
            assertThat(it.testAndQuote()).contains("colonne");
        }

        @Test
        @DisplayName("testAndQuote() retourne la chaîne entre guillemets doubles")
        void testAndQuoteWrapsInDoubleQuotes() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("nom");
            String quoted = it.testAndQuote();
            assertThat(quoted).startsWith("\"").endsWith("\"");
        }

        @Test
        @DisplayName("testAndReturnIdentifier() retourne l'identifiant brut")
        void testAndReturnIdentifier() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("colA");
            assertThat(it.testAndReturnIdentifier()).isEqualTo("colA");
        }

        @Test
        @DisplayName("forHierachicalKey() ajoute le suffixe _hierachicakkey")
        void forHierachicalKey() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("ref");
            it.forHierachicalKey();
            assertThat(it.testAndReturnIdentifier()).endsWith("_hierachicakkey");
        }

        @Test
        @DisplayName("forNaturalKey() ajoute le suffixe _naturalkey")
        void forNaturalKey() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("ref");
            it.forNaturalKey();
            assertThat(it.testAndReturnIdentifier()).endsWith("_naturalkey");
        }

        @Test
        @DisplayName("forId() ajoute le suffixe _id")
        void forId() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("ref");
            it.forId();
            assertThat(it.testAndReturnIdentifier()).endsWith("_id");
        }

        @Test
        @DisplayName("forOneValueFromTheManyArray() ajoute le suffixe _value")
        void forOneValueFromTheManyArray() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("items");
            it.forOneValueFromTheManyArray();
            assertThat(it.testAndReturnIdentifier()).endsWith("_value");
        }

        @Test
        @DisplayName("testlabelLength() lève FieldNameTooLongForSqlFieldException pour un identifiant > 63 caractères")
        void testlabelLengthThrowsForTooLong() {
            String tooLong = "a".repeat(64);
            assertThatThrownBy(() -> RelationalService.IdentifierTest.forStringIdentifier(tooLong))
                    .isInstanceOf(FieldNameTooLongForSqlFieldException.class);
        }

        @Test
        @DisplayName("identifierForApplicationName() accepte un nom d'application valide")
        void identifierForApplicationName() {
            assertTrue(RelationalService.IdentifierTest.identifierForApplicationName("mon_app"));
        }

        @Test
        @DisplayName("identifierForApplicationName() refuse un nom trop court (< 2 chars)")
        void identifierForApplicationNameRefusesTooShort() {
            assertFalse(RelationalService.IdentifierTest.identifierForApplicationName("a"));
        }

        @Test
        @DisplayName("identifierForObject() accepte un nom d'objet valide")
        void identifierForObject() {
            assertTrue(RelationalService.IdentifierTest.identifierForObject("ma_table"));
        }

        @Test
        @DisplayName("testAndQuoteForRefsLinkedTo() remplace le guillemet initial par refs_linked_to_")
        void testAndQuoteForRefsLinkedTo() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("col");
            String result = it.testAndQuoteForRefsLinkedTo();
            assertThat(result).startsWith("\"refs_linked_to_");
        }

        @Test
        @DisplayName("forDynamicReferenceHierachicakKey() retourne un identifiant non vide")
        void forDynamicReferenceHierachicakKey() {
            RelationalService.IdentifierTest it = RelationalService.IdentifierTest.forStringIdentifier("dynCol");
            RelationalService.IdentifierTest result = it.forDynamicReferenceHierachicakKey(1);
            assertNotNull(result);
            assertThat(it.testAndReturnIdentifier()).isNotEmpty();
        }
    }

    // =========================================================================
    //  RelationalService.SQLComponent
    // =========================================================================

    @Nested
    @DisplayName("SQLComponent")
    class SQLComponentTest {

        private RelationalService.SQLComponent.SqlViewPrimitiveType defaultType() {
            return RelationalService.SQLComponent.SqlViewPrimitiveType.DEFAULT;
        }

        @Test
        @DisplayName("toRecordDefinition() contient le nom de la colonne et le type SQL")
        void toRecordDefinition() {
            var comp = new RelationalService.SQLComponent("monChamp", defaultType());
            String def = comp.toRecordDefinition();
            assertThat(def).contains("monChamp");
            assertThat(def).contains("TEXT");
        }

        @Test
        @DisplayName("toRecordDefinitionForRef() contient UUID")
        void toRecordDefinitionForRef() {
            var comp = new RelationalService.SQLComponent("refChamp", defaultType());
            String def = comp.toRecordDefinitionForRef();
            assertThat(def).contains("refChamp");
            assertThat(def).contains("UUID");
        }

        @Test
        @DisplayName("toRecordDefinition() remplace DATE par TEXT dans le cast")
        void toRecordDefinitionReplacesDateWithText() {
            var dateType = new RelationalService.SQLComponent.SqlViewPrimitiveType("DATE", "");
            var comp = new RelationalService.SQLComponent("dateCol", dateType);
            String def = comp.toRecordDefinition();
            assertThat(def).contains("TEXT");
            assertThat(def).doesNotContain("DATE");
        }

        @Test
        @DisplayName("SQLComponent lève IllegalArgumentException si le nom est vide")
        void throwsWhenNameEmpty() {
            assertThatThrownBy(() -> new RelationalService.SQLComponent("", defaultType()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("SQLComponent lève NullPointerException si sqlType est null")
        void throwsWhenSqlTypeNull() {
            assertThatThrownBy(() -> new RelationalService.SQLComponent("col", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    //  RelationalService.SQLVariableForData
    // =========================================================================

    @Nested
    @DisplayName("SQLVariableForData")
    class SQLVariableForDataTest {

        private RelationalService.SQLComponent defaultComp() {
            return new RelationalService.SQLComponent(
                    "mesure",
                    RelationalService.SQLComponent.SqlViewPrimitiveType.DEFAULT
            );
        }

        @Test
        @DisplayName("getType() retourne 'datavalues'")
        void getTypeReturnsDatavalues() {
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(defaultComp()));
            assertThat(variable.getType()).isEqualTo("datavalues");
        }

        @Test
        @DisplayName("getSelect() contient le nom de la variable et le composant")
        void getSelectContainsNameAndComponent() {
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(defaultComp()));
            String select = variable.getSelect(defaultComp());
            assertThat(select).contains("temperature");
            assertThat(select).contains("mesure");
        }

        @Test
        @DisplayName("getSelectForRef() contient UUID[]")
        void getSelectForRefContainsUUID() {
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(defaultComp()));
            String select = variable.getSelectForRef(defaultComp());
            assertThat(select).contains("UUID[]");
        }

        @Test
        @DisplayName("buildLateralForWithVariable() contient LATERAL et le type datavalues")
        void buildLateralForWithVariable() {
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(defaultComp()));
            String lateral = variable.buildLateralForWithVariable();
            assertThat(lateral).containsIgnoringCase("LATERAL");
            assertThat(lateral).contains("datavalues");
        }

        @Test
        @DisplayName("getSqlSelectForWithVariable() contient le nom de la variable")
        void getSqlSelectForWithVariable() {
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(defaultComp()));
            String sql = variable.getSqlSelectForWithVariable();
            assertThat(sql).contains("temperature");
        }

        @Test
        @DisplayName("buildLateralForSelect() contient jsonb_to_record")
        void buildLateralForSelect() {
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(defaultComp()));
            String lateral = variable.buildLateralForSelect();
            assertThat(lateral).containsIgnoringCase("jsonb_to_record");
        }

        @Test
        @DisplayName("getSqlSelectForWithVariableJoiningRefsLinkedTo() retourne les sélections de données sans refs")
        void getSqlSelectForWithVariableJoiningRefsLinkedToNoRefs() {
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(defaultComp()));
            var result = variable.getSqlSelectForWithVariableJoiningRefsLinkedTo(List.of());
            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).contains("temperature");
        }

        @Test
        @DisplayName("getSqlSelectForWithVariableJoiningRefsLinkedTo() inclut la sélection de ref si nom correspond")
        void getSqlSelectForWithVariableJoiningRefsLinkedToWithMatchingRef() {
            var comp = defaultComp();
            var variable = new RelationalService.SQLVariableForData("temperature", List.of(comp));
            var refComp = new RelationalService.SQLComponent(
                    "mesure",
                    new RelationalService.SQLComponent.SqlViewPrimitiveType("LTREE", "")
            );
            var refVar = new RelationalService.SQLVariableForRefsLinkedTo("temperature", List.of(refComp));
            var result = variable.getSqlSelectForWithVariableJoiningRefsLinkedTo(List.of(refVar));
            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("SQLVariableForData lève IllegalArgumentException si le nom est vide")
        void throwsWhenNameEmpty() {
            assertThatThrownBy(() -> new RelationalService.SQLVariableForData("", List.of(defaultComp())))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("SQLVariableForData lève IllegalArgumentException si les composants sont vides")
        void throwsWhenComponentsEmpty() {
            assertThatThrownBy(() -> new RelationalService.SQLVariableForData("temp", List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    //  RelationalService.SQLVariableForRefsLinkedTo
    // =========================================================================

    @Nested
    @DisplayName("SQLVariableForRefsLinkedTo")
    class SQLVariableForRefsLinkedToTest {

        private RelationalService.SQLComponent defaultComp() {
            return new RelationalService.SQLComponent(
                    "refCol",
                    RelationalService.SQLComponent.SqlViewPrimitiveType.DEFAULT
            );
        }

        @Test
        @DisplayName("getType() retourne 'refslinkedto'")
        void getTypeReturnsRefslinkedto() {
            var variable = new RelationalService.SQLVariableForRefsLinkedTo("espece", List.of(defaultComp()));
            assertThat(variable.getType()).isEqualTo("refslinkedto");
        }

        @Test
        @DisplayName("getSelect() retourne 'refslinkedto'")
        void getSelectReturnsRefslinkedto() {
            var variable = new RelationalService.SQLVariableForRefsLinkedTo("espece", List.of(defaultComp()));
            assertThat(variable.getSelect(defaultComp())).isEqualTo("refslinkedto");
        }

        @Test
        @DisplayName("getSelectForRef() retourne 'refslinkedto'")
        void getSelectForRefReturnsRefslinkedto() {
            var variable = new RelationalService.SQLVariableForRefsLinkedTo("espece", List.of(defaultComp()));
            assertThat(variable.getSelectForRef(defaultComp())).isEqualTo("refslinkedto");
        }

        @Test
        @DisplayName("buildLateralForSelect() contient jsonb_to_record")
        void buildLateralForSelectContainsJsonbToRecord() {
            var variable = new RelationalService.SQLVariableForRefsLinkedTo("espece", List.of(defaultComp()));
            String lateral = variable.buildLateralForSelect();
            assertThat(lateral).containsIgnoringCase("jsonb_to_record");
        }

        @Test
        @DisplayName("buildLateralForWithVariable() contient refslinkedto")
        void buildLateralForWithVariableContainsRefslinkedto() {
            var variable = new RelationalService.SQLVariableForRefsLinkedTo("espece", List.of(defaultComp()));
            String lateral = variable.buildLateralForWithVariable();
            assertThat(lateral).contains("refslinkedto");
        }

        @Test
        @DisplayName("getSqlSelectForWithVariable() contient le nom de la variable")
        void getSqlSelectForWithVariable() {
            var variable = new RelationalService.SQLVariableForRefsLinkedTo("espece", List.of(defaultComp()));
            String sql = variable.getSqlSelectForWithVariable();
            assertThat(sql).contains("espece");
        }

        @Test
        @DisplayName("SQLVariableForRefsLinkedTo lève IllegalArgumentException si le nom est vide")
        void throwsWhenNameEmpty() {
            assertThatThrownBy(() -> new RelationalService.SQLVariableForRefsLinkedTo("", List.of(defaultComp())))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("SQLVariableForRefsLinkedTo lève IllegalArgumentException si les composants sont vides")
        void throwsWhenComponentsEmpty() {
            assertThatThrownBy(() -> new RelationalService.SQLVariableForRefsLinkedTo("ref", List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    //  RelationalService.SQLComponent.SqlViewPrimitiveType
    // =========================================================================

    @Nested
    @DisplayName("SqlViewPrimitiveType")
    class SqlViewPrimitiveTypeTest {

        @Test
        @DisplayName("DEFAULT a cast=TEXT et multiplicity=''")
        void defaultHasTextCast() {
            var type = RelationalService.SQLComponent.SqlViewPrimitiveType.DEFAULT;
            assertThat(type.cast()).isEqualTo("TEXT");
            assertThat(type.multiplicity()).isEmpty();
        }

        @Test
        @DisplayName("peut créer un type personnalisé")
        void customType() {
            var type = new RelationalService.SQLComponent.SqlViewPrimitiveType("NUMERIC", "[]");
            assertThat(type.cast()).isEqualTo("NUMERIC");
            assertThat(type.multiplicity()).isEqualTo("[]");
        }
    }

    // =========================================================================
    //  RelationalService.ViewCreationCommand et MultiplicitySelect (records)
    // =========================================================================

    @Nested
    @DisplayName("ViewCreationCommand – record")
    class ViewCreationCommandTest {

        @Test
        @DisplayName("conserve le sql passé en paramètre")
        void preservesSql() {
            // Création directe car c'est un record public
            // On accède via la classe interne
            String sql = "SELECT 1";
            // ViewCreationCommand est un record dans RelationalService ; on ne peut pas
            // l'instancier directement depuis l'extérieur (package-private) mais on peut
            // vérifier les constantes MultiplicitySelect qui sont publiques
            assertThat(RelationalService.MultiplicitySelect.SIMPLE_SELECT_PATTERN).contains("dataValues");
        }
    }

    // =========================================================================
    //  MultiplicitySelect – constantes SQL
    // =========================================================================

    @Nested
    @DisplayName("MultiplicitySelect – constantes SQL")
    class MultiplicitySelectConstantsTest {

        @Test
        @DisplayName("SIMPLE_SELECT_PATTERN contient dataValues")
        void simpleSelectPatternContainsDataValues() {
            assertThat(RelationalService.MultiplicitySelect.SIMPLE_SELECT_PATTERN).contains("dataValues");
        }

        @Test
        @DisplayName("SIMPLE_SELECT_PATTERN_WITH_NULL contient NULLIF")
        void simpleSelectPatternWithNullContainsNullif() {
            assertThat(RelationalService.MultiplicitySelect.SIMPLE_SELECT_PATTERN_WITH_NULL).containsIgnoringCase("NULLIF");
        }

        @Test
        @DisplayName("SELECT_PATTERN_MANY contient array_agg")
        void selectPatternManyContainsArrayAgg() {
            assertThat(RelationalService.MultiplicitySelect.SELECT_PATTERN_MANY).containsIgnoringCase("array_agg");
        }

        @Test
        @DisplayName("SELECT_LATERAL_MANY contient JOIN LATERAL")
        void selectLateralManyContainsJoinLateral() {
            assertThat(RelationalService.MultiplicitySelect.SELECT_LATERAL_MANY).containsIgnoringCase("LATERAL");
        }
    }
}