package fr.inra.oresing.domain.application.normalized;

import fr.inra.oresing.domain.application.configuration.BasicComponent;
import fr.inra.oresing.domain.application.configuration.DynamicComponent;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescriptionBuilder;
import fr.inra.oresing.domain.application.configuration.ComponentDescriptionBuilder;
import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link Component}.
 * Aucun contexte Spring ni base de données.
 */
@org.junit.jupiter.api.Tag("domain.model")
@DisplayName("Component — factory of() et buildRequests()")
class ComponentTest {

    private static final UUID APP_ID = UUID.randomUUID();

    // ────────────────────────────────────────────────────────────────────────
    // factory Component.of()
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Component.of() — BasicComponent")
    class FactoryBasicComponentTest {

        @Test
        @DisplayName("BasicComponent sans checker → type null, multiplicity ONE, isDynamic false")
        void basicWithoutChecker() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("myField")
                    .build();

            Component c = Component.of("myField", desc, false, false, "");

            assertThat(c.fieldName()).isEqualTo("myField");
            assertThat(c.fieldPath()).isEqualTo("myField");
            assertThat(c.type()).isNull();
            assertThat(c.multiplicity()).isEqualTo(Multiplicity.ONE);
            assertThat(c.isDynamic()).isFalse();
            assertThat(c.refType()).isNull();
            assertThat(c.isAuthorizationTimeScopeField()).isFalse();
            assertThat(c.isAuthorizationAuthorizationScopeField()).isFalse();
        }

        @Test
        @DisplayName("BasicComponent avec StringChecker")
        void basicWithStringChecker() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("label")
                    .checker(CheckerDescriptionBuilder.stringChecker().build())
                    .build();

            Component c = Component.of("label", desc, false, false, "");

            assertThat(c.fieldName()).isEqualTo("label");
            assertThat(c.type()).isEqualTo(CheckerDescription.CheckerDescriptionType.StringChecker);
        }

        @Test
        @DisplayName("BasicComponent avec DateChecker → type DateChecker")
        void basicWithDateChecker() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("date_col")
                    .checker(CheckerDescriptionBuilder.dateChecker().build())
                    .build();

            Component c = Component.of("date_col", desc, true, false, "");

            assertThat(c.type()).isEqualTo(CheckerDescription.CheckerDescriptionType.DateChecker);
            assertThat(c.isAuthorizationTimeScopeField()).isTrue();
        }

        @Test
        @DisplayName("BasicComponent avec FloatChecker → type FloatChecker")
        void basicWithFloatChecker() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("amount")
                    .checker(CheckerDescriptionBuilder.floatChecker().build())
                    .build();

            Component c = Component.of("amount", desc, false, false, "");

            assertThat(c.type()).isEqualTo(CheckerDescription.CheckerDescriptionType.FloatChecker);
        }

        @Test
        @DisplayName("BasicComponent avec IntegerChecker → type IntegerChecker")
        void basicWithIntegerChecker() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("count")
                    .checker(CheckerDescriptionBuilder.integerChecker().build())
                    .build();

            Component c = Component.of("count", desc, false, false, "");

            assertThat(c.type()).isEqualTo(CheckerDescription.CheckerDescriptionType.IntegerChecker);
        }

        @Test
        @DisplayName("BasicComponent avec BooleanChecker → type BooleanChecker")
        void basicWithBooleanChecker() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("active")
                    .checker(CheckerDescriptionBuilder.booleanChecker().build())
                    .build();

            Component c = Component.of("active", desc, false, false, "");

            assertThat(c.type()).isEqualTo(CheckerDescription.CheckerDescriptionType.BooleanChecker);
        }

        @Test
        @DisplayName("BasicComponent avec ReferenceChecker → type ReferenceChecker, refType peuplé")
        void basicWithReferenceChecker() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("species")
                    .checker(CheckerDescriptionBuilder.referenceChecker().refType("speciesRef").build())
                    .build();

            Component c = Component.of("species", desc, false, true, "");

            assertThat(c.type()).isEqualTo(CheckerDescription.CheckerDescriptionType.ReferenceChecker);
            assertThat(c.refType()).isEqualTo("speciesRef");
            assertThat(c.isAuthorizationAuthorizationScopeField()).isTrue();
        }

        @Test
        @DisplayName("BasicComponent avec ReferenceChecker MANY")
        void basicWithReferenceCheckerMany() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("tags")
                    .checker(CheckerDescriptionBuilder.referenceChecker()
                            .refType("tagRef")
                            .multiplicity(Multiplicity.MANY)
                            .build())
                    .build();

            Component c = Component.of("tags", desc, false, false, "");

            assertThat(c.multiplicity()).isEqualTo(Multiplicity.MANY);
        }
    }

    @Nested
    @DisplayName("Component.of() — DynamicComponent")
    class FactoryDynamicComponentTest {

        @Test
        @DisplayName("DynamicComponent → isDynamic true, fieldPath = fieldName")
        void dynamicComponent() {
            DynamicComponent desc = ComponentDescriptionBuilder.dynamicComponent()
                    .componentKey("dynField")
                    .reference("dynRef")
                    .build();

            Component c = Component.of("dynField", desc, false, false, "");

            assertThat(c.isDynamic()).isTrue();
            assertThat(c.fieldName()).isEqualTo("dynField");
            assertThat(c.fieldPath()).isEqualTo("dynField");
        }
    }

    @Nested
    @DisplayName("Component.of() — PatternComponent")
    class FactoryPatternComponentTest {

        @Test
        @DisplayName("PatternComponent → fieldName = componentKey(), fieldPath contient __VALUE__")
        void patternComponent() {
            PatternComponent desc = ComponentDescriptionBuilder.patternComponent()
                    .componentKey("patKey")
                    .build();

            Component c = Component.of("ignored", desc, false, false, "");

            assertThat(c.fieldName()).isEqualTo("patKey");
            assertThat(c.fieldPath()).isEqualTo("patKey.__VALUE__");
        }
    }

    @Nested
    @DisplayName("Component.of() — PatternComponentAdjacents")
    class FactoryPatternComponentAdjacentsTest {

        @Test
        @DisplayName("PatternComponentAdjacents → fieldName = innerName__fieldName")
        void patternComponentAdjacents() {
            PatternComponentAdjacents desc = ComponentDescriptionBuilder.patternComponentAdjacents()
                    .componentKey("adj")
                    .build();

            Component c = Component.of("myAdj", desc, false, false, "inner");

            assertThat(c.fieldName()).isEqualTo("inner__myAdj");
            assertThat(c.fieldPath()).isEqualTo("\"inner::myAdj\"");
        }
    }

    @Nested
    @DisplayName("Component.of() — PatternComponentQualifiers")
    class FactoryPatternComponentQualifiersTest {

        @Test
        @DisplayName("PatternComponentQualifiers → fieldName = innerName__fieldName")
        void patternComponentQualifiers() {
            PatternComponentQualifiers desc = ComponentDescriptionBuilder.patternComponentQualifiers()
                    .componentKey("qual")
                    .build();

            Component c = Component.of("myQual", desc, false, false, "outer");

            assertThat(c.fieldName()).isEqualTo("outer__myQual");
            assertThat(c.fieldPath()).isEqualTo("\"outer::myQual\"");
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // buildRequests()
    // ────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildRequests() — retour anticipé si isHidden")
    class BuildRequestsHiddenTest {

        @Test
        @DisplayName("isHidden → buildRequests ne modifie pas le Sql")
        void hiddenFieldSkipped() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("hiddenField")
                    .tags(Set.of(Tag.HiddenTag.instance()))
                    .build();

            Component c = Component.of("hiddenField", desc, false, false, "");

            Sql sqls = new Sql("mySchema", "myTable", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).isEmpty();
            assertThat(sqls.refValuesTable()).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildRequests() — type null → sqlForTextField")
    class BuildRequestsNullTypeTest {

        @Test
        @DisplayName("type null → select et refValuesTable peuplés (champ texte simple)")
        void nullTypeProducesTextField() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("simpleText")
                    .build();

            Component c = Component.of("simpleText", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).hasSize(1);
            assertThat(sqls.select().get(0)).contains("simpleText");
            assertThat(sqls.refValuesTable()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("buildRequests() — DateChecker")
    class BuildRequestsDateTest {

        @Test
        @DisplayName("DateChecker → produit deux colonnes select (ts_ et texte) et une colonne refValuesTable")
        void dateCheckerProducesTsAndText() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("eventDate")
                    .checker(CheckerDescriptionBuilder.dateChecker().build())
                    .build();

            Component c = Component.of("eventDate", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).hasSize(1);
            assertThat(sqls.select().get(0)).contains("ts_eventDate").contains("eventDate");
            assertThat(sqls.refValuesTable()).hasSize(1);
        }

        @Test
        @DisplayName("DateChecker + isAuthorizationTimeScopeField → index brin ajouté")
        void dateCheckerTimeScopeAddsIndex() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("periodDate")
                    .checker(CheckerDescriptionBuilder.dateChecker().build())
                    .build();

            Component c = Component.of("periodDate", desc, true, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.indexes()).isNotEmpty();
            assertThat(sqls.timescopes()).contains("ts_periodDate");
        }
    }

    @Nested
    @DisplayName("buildRequests() — FloatChecker et IntegerChecker")
    class BuildRequestsNumericTest {

        @Test
        @DisplayName("FloatChecker → champ texte simple (sqlForTextField)")
        void floatCheckerProducesTextField() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("price")
                    .checker(CheckerDescriptionBuilder.floatChecker().build())
                    .build();

            Component c = Component.of("price", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).hasSize(1);
            assertThat(sqls.select().get(0)).contains("price");
        }

        @Test
        @DisplayName("IntegerChecker → champ texte simple (sqlForTextField)")
        void integerCheckerProducesTextField() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("quantity")
                    .checker(CheckerDescriptionBuilder.integerChecker().build())
                    .build();

            Component c = Component.of("quantity", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("buildRequests() — BooleanChecker")
    class BuildRequestsBooleanTest {

        @Test
        @DisplayName("BooleanChecker → champ texte simple (sqlForTextField)")
        void booleanCheckerProducesTextField() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("active")
                    .checker(CheckerDescriptionBuilder.booleanChecker().build())
                    .build();

            Component c = Component.of("active", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("buildRequests() — ReferenceChecker ONE")
    class BuildRequestsReferenceOneTest {

        @Test
        @DisplayName("ReferenceChecker ONE → referenceJoin, select UUID+display_fr+display_en, index")
        void referenceCheckerOne() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("species")
                    .checker(CheckerDescriptionBuilder.referenceChecker().refType("speciesRef").build())
                    .build();

            Component c = Component.of("species", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.referenceJoin()).hasSize(1);
            assertThat(sqls.select()).hasSize(3); // _id, _fr, _en
            assertThat(sqls.indexes()).hasSize(1);
            assertThat(sqls.foreignKeys()).containsKey("speciesRef");
        }

        @Test
        @DisplayName("ReferenceChecker ONE + isAuthorizationAuthorizationScopeField → _hk dans select")
        void referenceCheckerOneWithAuthScope() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("site")
                    .checker(CheckerDescriptionBuilder.referenceChecker().refType("siteRef").build())
                    .build();

            Component c = Component.of("site", desc, false, true, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            // _id, _hk, _fr, _en
            assertThat(sqls.select()).hasSize(4);
            assertThat(sqls.indexes()).hasSize(2);
            assertThat(sqls.authorizationScopes()).containsKey("siteRef");
        }
    }

    @Nested
    @DisplayName("buildRequests() — ReferenceChecker MANY")
    class BuildRequestsReferenceManyTest {

        @Test
        @DisplayName("ReferenceChecker MANY → ARRAY_AGG dans le select")
        void referenceCheckerMany() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("tags")
                    .checker(CheckerDescriptionBuilder.referenceChecker()
                            .refType("tagRef")
                            .multiplicity(Multiplicity.MANY)
                            .build())
                    .build();

            Component c = Component.of("tags", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.referenceJoin()).hasSize(1);
            assertThat(sqls.select()).hasSize(3); // _id (array), _fr (array), _en (array)
            String selectId = sqls.select().get(0);
            assertThat(selectId).containsIgnoringCase("array_agg");
        }

        @Test
        @DisplayName("ReferenceChecker MANY + authorizationScopeField → _hk array dans select")
        void referenceCheckerManyWithAuthScope() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("regions")
                    .checker(CheckerDescriptionBuilder.referenceChecker()
                            .refType("regionRef")
                            .multiplicity(Multiplicity.MANY)
                            .build())
                    .build();

            Component c = Component.of("regions", desc, false, true, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).hasSize(4); // _id, _hk, _fr, _en arrays
            assertThat(sqls.authorizationScopes()).containsKey("regionRef");
            assertThat(sqls.normalizedJoinManyToManies()).containsKey("regionRef");
        }
    }

    @Nested
    @DisplayName("buildRequests() — DynamicComponent (isDynamic=true)")
    class BuildRequestsDynamicTest {

        @Test
        @DisplayName("DynamicComponent → sqlForDynamicComponent : ARRAY_AGG + referenceJoin")
        void dynamicComponentBuildRequests() {
            DynamicComponent desc = ComponentDescriptionBuilder.dynamicComponent()
                    .componentKey("dynSpecies")
                    .reference("dynSpeciesRef")
                    .build();

            Component c = Component.of("dynSpecies", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.referenceJoin()).hasSize(1);
            assertThat(sqls.select()).hasSize(1);
            String selectStr = sqls.select().get(0);
            assertThat(selectStr).containsIgnoringCase("array_agg");
        }
    }

    @Nested
    @DisplayName("buildRequests() — multiplicité MANY pour champ texte")
    class BuildRequestsManyTextTest {

        @Test
        @DisplayName("StringChecker MANY → select avec []")
        void stringManyProducesArrayType() {
            BasicComponent desc = ComponentDescriptionBuilder.basicComponent()
                    .componentKey("multiLabels")
                    .checker(CheckerDescriptionBuilder.stringChecker().multiplicity(Multiplicity.MANY).build())
                    .build();

            Component c = Component.of("multiLabels", desc, false, false, "");
            Sql sqls = new Sql("sch", "tbl", APP_ID);
            c.buildRequests(sqls);

            assertThat(sqls.select()).hasSize(1);
            assertThat(sqls.select().get(0)).contains("[]");
        }
    }
}
