package fr.inra.oresing.domain.data.deposit.validation.transformer;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.Datum;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des transformateurs de ligne.
 *
 * Ces transformateurs sont critiques dans le pipeline de dépôt CSV :
 * TransformOneLineElementTransformer.transform() est appelé pour chaque cellule
 * du datum lors du traitement d'un chunk. Une erreur ici silencieuse causerait
 * des données corrompues ou des colonnes vides.
 */
@Tag("domain.model")
@DisplayName("Line element transformers — pipeline dépôt CSV")
class LineElementTransformerTest {

    // ─── GroovyExpressionOnOneLineElementTransformer ──────────────────────────

    @Nested
    @DisplayName("GroovyExpressionOnOneLineElementTransformer")
    class GroovyExpressionOnOneLineElementTransformerTest {

        @Test
        @DisplayName("target() retourne la cible passée au constructeur")
        void targetReturnsConstructorArgument() {
            CheckerTarget target = new DataColumn("monChamp");
            StringGroovyExpression expr = StringGroovyExpression.forExpression("'hello'", Set.of());
            GroovyExpressionOnOneLineElementTransformer t = new GroovyExpressionOnOneLineElementTransformer(
                    expr, ImmutableMap.of(), target, Set.of());
            assertThat(t.target()).isSameAs(target);
        }

        @Test
        @DisplayName("transform() évalue l'expression Groovy et retourne un StringType")
        void transformEvaluatesGroovyExpression() {
            CheckerTarget target = new DataColumn("champ");
            StringGroovyExpression expr = StringGroovyExpression.forExpression("'resultat'", Set.of());
            GroovyExpressionOnOneLineElementTransformer t = new GroovyExpressionOnOneLineElementTransformer(
                    expr, ImmutableMap.of(), target, Set.of());

            Datum datum = new Datum();
            datum.put("champ", StringType.getStringTypeFromStringValue("initial"));

            FieldType<?> result = t.transform(datum, StringType.getStringTypeFromStringValue("initial"));

            assertThat(result).isInstanceOf(StringType.class);
            assertThat(result.toString()).isEqualTo("resultat");
        }

        @Test
        @DisplayName("transform() injecte le contexte statique dans l'évaluation Groovy")
        void transformInjectsStaticContext() {
            CheckerTarget target = new DataColumn("col");
            // Expression qui utilise une variable du contexte statique
            StringGroovyExpression expr = StringGroovyExpression.forExpression("staticVal", Set.of());
            ImmutableMap<String, Object> ctx = ImmutableMap.of("staticVal", "contextValue");
            GroovyExpressionOnOneLineElementTransformer t = new GroovyExpressionOnOneLineElementTransformer(
                    expr, ctx, target, Set.of());

            Datum datum = new Datum();
            FieldType<?> result = t.transform(datum, StringType.getStringTypeFromStringValue(""));
            assertThat(result.toString()).isEqualTo("contextValue");
        }

        @Test
        @DisplayName("toString() inclut la représentation de l'expression et de la cible")
        void toStringIncludesExpression() {
            CheckerTarget target = new DataColumn("col");
            StringGroovyExpression expr = StringGroovyExpression.forExpression("'x'", Set.of());
            GroovyExpressionOnOneLineElementTransformer t = new GroovyExpressionOnOneLineElementTransformer(
                    expr, ImmutableMap.of(), target, Set.of());
            assertThat(t.toString()).isNotBlank();
        }
    }

    // ─── TransformOneLineElementTransformer (default methods) ─────────────────

    @Nested
    @DisplayName("TransformOneLineElementTransformer — default transform(Datum)")
    class TransformOneLineElementTransformerDefaultMethodTest {

        @Test
        @DisplayName("transform(Datum) met à jour la colonne cible dans le datum copié")
        void transformDatumUpdatesTargetColumn() {
            CheckerTarget target = new DataColumn("valeur");
            StringGroovyExpression expr = StringGroovyExpression.forExpression("'nouveau'", Set.of());
            TransformOneLineElementTransformer transformer = new GroovyExpressionOnOneLineElementTransformer(
                    expr, ImmutableMap.of(), target, Set.of());

            Datum datum = new Datum();
            datum.put("valeur", StringType.getStringTypeFromStringValue("ancien"));

            Datum result = transformer.transform(datum);

            assertThat(result.get("valeur").toString()).isEqualTo("nouveau");
        }

        @Test
        @DisplayName("transform(Datum) retourne une copie, sans modifier l'original")
        void transformDatumReturnsCopy() {
            CheckerTarget target = new DataColumn("x");
            StringGroovyExpression expr = StringGroovyExpression.forExpression("'new'", Set.of());
            TransformOneLineElementTransformer transformer = new GroovyExpressionOnOneLineElementTransformer(
                    expr, ImmutableMap.of(), target, Set.of());

            Datum original = new Datum();
            original.put("x", StringType.getStringTypeFromStringValue("old"));

            Datum result = transformer.transform(original);

            // Original inchangé
            assertThat(original.get("x").toString()).isEqualTo("old");
            // Copie modifiée
            assertThat(result.get("x").toString()).isEqualTo("new");
        }
    }
}
