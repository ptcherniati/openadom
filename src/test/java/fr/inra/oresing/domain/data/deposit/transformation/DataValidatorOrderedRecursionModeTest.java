package fr.inra.oresing.domain.data.deposit.transformation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.recursion.WithRecursion;
import fr.inra.oresing.domain.data.deposit.recursion.WithoutRecursion;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests TDD pour le mode « récursion ordonnée » ({@code orderedRecursionMode}).
 *
 * <p>Ce mode suppose que les parents apparaissent toujours <em>avant</em> leurs
 * enfants dans le fichier CSV récursif. Si un parent est introuvable au moment
 * de traiter un enfant, l'erreur est remontée immédiatement (au lieu d'être
 * différée dans {@code missingParentLine}).
 *
 * @see WithRecursion#isOrderedMode()
 * @see WithRecursion#ordered(AsynchroneFileImporterContext)
 * @see DataValidator#registerErrors
 */
@Tag("domain.model")
@DisplayName("Mode « récursion ordonnée » — DataValidator + WithRecursion")
class DataValidatorOrderedRecursionModeTest {

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static AsynchroneFileImporterContext ctxWithRealMissingMap() {
        AsynchroneFileImporterContext ctx = mock(AsynchroneFileImporterContext.class);
        ConcurrentHashMap<Ltree, List<RowWithReferenceDatum>> missingLines = new ConcurrentHashMap<>();
        when(ctx.missingParentLine()).thenReturn(missingLines);
        return ctx;
    }

    private static ReferenceValidationCheckResult invalidRefResult(String targetColumn) {
        DataColumn target = new DataColumn(targetColumn);
        return ReferenceValidationCheckResult.error(target, "parent_val",
                "invalidReferenceWithComponent", ImmutableMap.of(), null);
    }

    @SuppressWarnings("unchecked")
    private static LineChecker<StringType> recursiveLineChecker(String targetColumn, String componentKey) {
        ReferenceChecker refChecker = new ReferenceChecker(
                CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey,
                Multiplicity.ONE,
                false,
                "taxon",
                true,
                false
        );
        return new LineChecker.OneChecker<>(
                new StringType(""),
                new DataColumn(targetColumn),
                LineChecker.LineTransformer.NULL_LINE_TRANSFORMER,
                refChecker
        );
    }

    private static RowWithReferenceDatum rowAtLine(long lineNumber) {
        return new RowWithReferenceDatum(lineNumber, "", new DataDatum(), Map.of());
    }

    /**
     * Crée un datum contenant la colonne componentKey avec une valeur factice.
     * Nécessaire car {@link fr.inra.oresing.domain.data.DataDatum#get} lève
     * IllegalArgumentException si la colonne est absente.
     */
    private static DataDatum datumWithParentColumn(String componentKey) {
        DataDatum datum = new DataDatum();
        // StringType.getStringTypeFromStringValue fixe la valeur (vs le constructeur qui fixe le pattern)
        datum.put(new DataColumn(componentKey),
                new DataColumnSingleValue(StringType.getStringTypeFromStringValue("parentkey")));
        return datum;
    }

    // ─── 1. isOrderedMode() sur les stratégies ─────────────────────────────

    @Nested
    @DisplayName("isOrderedMode() — valeur par stratégie")
    class IsOrderedMode {

        @Test
        @DisplayName("WithRecursion(ctx) — mode standard → isOrderedMode() = false")
        void withRecursion_default_isNotOrdered() {
            WithRecursion strategy = new WithRecursion(ctxWithRealMissingMap());
            assertThat(strategy.isOrderedMode()).isFalse();
        }

        @Test
        @DisplayName("WithRecursion.ordered(ctx) — mode ordonné → isOrderedMode() = true")
        void withRecursion_ordered_isOrdered() {
            WithRecursion strategy = WithRecursion.ordered(ctxWithRealMissingMap());
            assertThat(strategy.isOrderedMode()).isTrue();
        }

        @Test
        @DisplayName("WithoutRecursion → isOrderedMode() = false (défaut de l'interface)")
        void withoutRecursion_isNotOrdered() {
            AsynchroneFileImporterContext ctx = mock(AsynchroneFileImporterContext.class);
            WithoutRecursion strategy = new WithoutRecursion(ctx);
            assertThat(strategy.isOrderedMode()).isFalse();
        }

        @Test
        @DisplayName("Les deux instances ordonnée et non ordonnée sont indépendantes")
        void deux_instances_independantes() {
            WithRecursion standard = new WithRecursion(ctxWithRealMissingMap());
            WithRecursion ordered  = WithRecursion.ordered(ctxWithRealMissingMap());
            assertThat(standard.isOrderedMode()).isFalse();
            assertThat(ordered.isOrderedMode()).isTrue();
        }
    }

    // ─── 2. registerErrors — mode ordonné ──────────────────────────────────

    @Nested
    @DisplayName("registerErrors — mode ordonné : parent manquant → erreur immédiate")
    class RegisterErrorsOrdered {

        @Test
        @DisplayName("Mode ordonné : parent manquant → erreur ajoutée au builder, missingParentLine vide")
        void ordonne_parentManquant_erreurImmediate() {
            AsynchroneFileImporterContext ctx = ctxWithRealMissingMap();
            RecursionStrategy orderedStrategy = WithRecursion.ordered(ctx);
            ImmutableList.Builder<CsvRowValidationCheckResult> errorsBuilder = ImmutableList.builder();

            DataValidator.registerErrors(
                    orderedStrategy,
                    rowAtLine(7),
                    recursiveLineChecker("taxon_col", "taxon_superieur"),
                    invalidRefResult("taxon_col"),
                    new DataDatum(),
                    errorsBuilder
            );

            assertThat(errorsBuilder.build())
                    .as("En mode ordonné, l'erreur de parent manquant doit être dans le builder")
                    .hasSize(1);
            assertThat(ctx.missingParentLine())
                    .as("En mode ordonné, missingParentLine doit rester vide")
                    .isEmpty();
        }

        @Test
        @DisplayName("Mode ordonné : registerErrors renvoie null")
        void ordonne_registerErrors_renvoieNull() {
            RecursionStrategy orderedStrategy = WithRecursion.ordered(ctxWithRealMissingMap());
            ImmutableList.Builder<CsvRowValidationCheckResult> errorsBuilder = ImmutableList.builder();

            List<ReferenceDatumAfterChecking> result = DataValidator.registerErrors(
                    orderedStrategy,
                    rowAtLine(3),
                    recursiveLineChecker("col", "parent_col"),
                    invalidRefResult("col"),
                    new DataDatum(),
                    errorsBuilder
            );

            assertThat(result)
                    .as("En mode ordonné, registerErrors doit renvoyer null (pas de déféré)")
                    .isNull();
        }

        @Test
        @DisplayName("Mode ordonné : l'erreur dans le builder référence bien le numéro de ligne")
        void ordonne_erreurAvecNumeroLigne() {
            RecursionStrategy orderedStrategy = WithRecursion.ordered(ctxWithRealMissingMap());
            ImmutableList.Builder<CsvRowValidationCheckResult> errorsBuilder = ImmutableList.builder();

            DataValidator.registerErrors(
                    orderedStrategy,
                    rowAtLine(42),
                    recursiveLineChecker("col", "parent_col"),
                    invalidRefResult("col"),
                    new DataDatum(),
                    errorsBuilder
            );

            ImmutableList<CsvRowValidationCheckResult> errors = errorsBuilder.build();
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).lineNumber()).isEqualTo(42);
        }
    }

    // ─── 3. registerErrors — mode standard ─────────────────────────────────

    @Nested
    @DisplayName("registerErrors — mode standard : parent manquant → ligne différée (List.of())")
    class RegisterErrorsStandard {

        @Test
        @DisplayName("Mode standard : parent manquant → retourne List.of(), builder vide")
        void standard_parentManquant_ligneDisferee() {
            AsynchroneFileImporterContext ctx = ctxWithRealMissingMap();
            RecursionStrategy standardStrategy = new WithRecursion(ctx);
            ImmutableList.Builder<CsvRowValidationCheckResult> errorsBuilder = ImmutableList.builder();

            // datum WITH componentKey column so registerMissingLine doesn't throw
            DataDatum datum = datumWithParentColumn("taxon_superieur");
            List<ReferenceDatumAfterChecking> result = DataValidator.registerErrors(
                    standardStrategy,
                    rowAtLine(5),
                    recursiveLineChecker("taxon_col", "taxon_superieur"),
                    invalidRefResult("taxon_col"),
                    datum,
                    errorsBuilder
            );

            assertThat(result)
                    .as("En mode standard, registerErrors retourne List.of() (ligne différée)")
                    .isNotNull()
                    .isEmpty();
            assertThat(errorsBuilder.build())
                    .as("En mode standard, pas d'erreur dans le builder pour un parent manquant")
                    .isEmpty();
        }
    }

    // ─── 4. WithRecursion — cohérence de orderedMode ───────────────────────

    @Nested
    @DisplayName("WithRecursion — cohérence de la propriété orderedMode")
    class WithRecursionOrderedModeProperty {

        @Test
        @DisplayName("Constructeur canonique (ctx, map, false) → mode standard")
        void constructeurCanonique_false() {
            WithRecursion strategy = new WithRecursion(ctxWithRealMissingMap(), new ConcurrentHashMap<>(), false);
            assertThat(strategy.isOrderedMode()).isFalse();
        }

        @Test
        @DisplayName("Constructeur canonique (ctx, map, true) → mode ordonné")
        void constructeurCanonique_true() {
            WithRecursion strategy = new WithRecursion(ctxWithRealMissingMap(), new ConcurrentHashMap<>(), true);
            assertThat(strategy.isOrderedMode()).isTrue();
        }

        @Test
        @DisplayName("ordered(ctx) crée une instance avec parentReferenceMap vide")
        void factoryOrdered_parentMapVide() {
            WithRecursion strategy = WithRecursion.ordered(ctxWithRealMissingMap());
            assertThat(strategy.parentReferenceMap()).isEmpty();
        }
    }

    // ─── 5. ImportProperties — propriété orderedRecursionMode ──────────────

    @Nested
    @DisplayName("ImportProperties — orderedRecursionMode")
    class ImportPropertiesOrderedMode {

        @Test
        @DisplayName("Valeur par défaut = false (mode standard)")
        void defaultIsFalse() {
            fr.inra.oresing.workflow.cascade.config.ImportProperties props =
                    new fr.inra.oresing.workflow.cascade.config.ImportProperties();
            assertThat(props.isOrderedRecursionMode())
                    .as("Le mode récursion ordonnée doit être désactivé par défaut")
                    .isFalse();
        }

        @Test
        @DisplayName("setter/getter fonctionnels")
        void setterGetter() {
            fr.inra.oresing.workflow.cascade.config.ImportProperties props =
                    new fr.inra.oresing.workflow.cascade.config.ImportProperties();
            props.setOrderedRecursionMode(true);
            assertThat(props.isOrderedRecursionMode()).isTrue();
            props.setOrderedRecursionMode(false);
            assertThat(props.isOrderedRecursionMode()).isFalse();
        }
    }
}