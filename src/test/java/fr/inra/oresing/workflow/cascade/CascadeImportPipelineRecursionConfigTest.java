package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que {@link CascadeImportPipeline} applique bien les paramètres
 * "chunk size = Integer.MAX_VALUE" et "parallelism = 1" pour les imports
 * récursifs, afin de ne pas briser le mécanisme {@code missingParentLine} /
 * {@code testLinesRegardingRecursivity} dans {@link
 * fr.inra.oresing.domain.data.deposit.transformation.DataValidator}.
 *
 * <p>En mode « récursion ordonnée » ({@code isStrictOrdered = true}, activé
 * par le tag {@code __ORDER_STRICT__} ou par {@code orderedRecursionMode}),
 * le fichier peut être découpé en chunks normaux car le mécanisme de différé
 * ({@code missingParentLine}) est désactivé.
 *
 * <h2>Régression couverte</h2>
 * Avant ce correctif, la constante {@code "notSplitableDataForChunkedTreatment_"}
 * dans {@link fr.inra.oresing.domain.data.deposit.DataImporter#prepareContextForDataTreatment}
 * marquait l'intention sans avoir d'effet fonctionnel : le fichier était quand
 * même découpé en plusieurs chunks parallèles, provoquant une erreur
 * intermittente {@code missingrecursiveParentReference} (HTTP 400) lors du
 * chargement de {@code taxons_du_phytoplancton.csv} (1 482 lignes).
 *
 * @see CascadeImportPipeline#effectiveChunkSizeLines(ImportProperties, boolean, boolean)
 * @see CascadeImportPipeline#effectiveParallelism(ImportProperties, boolean)
 */
@Tag("domain.model")
@DisplayName("CascadeImportPipeline — configuration chunk/parallelism pour imports récursifs")
class CascadeImportPipelineRecursionConfigTest {

    // ─── import récursif (mode lazy — NON ordonné) ────────────────────────────

    @Nested
    @DisplayName("Import récursif non ordonné (isRecursive=true, isStrictOrdered=false)")
    class RecursiveImport {

        @Test
        @DisplayName("effectiveChunkSizeLines → Integer.MAX_VALUE (fichier entier = 1 chunk)")
        void chunkSizeIsMaxValue() {
            ImportProperties props = propsWithChunkSize(1000);

            int chunkSize = CascadeImportPipeline.effectiveChunkSizeLines(props, true);

            assertThat(chunkSize)
                    .as("Un import récursif non ordonné doit utiliser un seul chunk (Integer.MAX_VALUE)")
                    .isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("effectiveChunkSizeLines(props, true, false) → Integer.MAX_VALUE")
        void chunkSizeIsMaxValueWith3Params() {
            ImportProperties props = propsWithChunkSize(1000);

            int chunkSize = CascadeImportPipeline.effectiveChunkSizeLines(props, true, false);

            assertThat(chunkSize).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        @DisplayName("effectiveParallelism → 1 (traitement strictement séquentiel)")
        void parallelismIsOne() {
            ImportProperties props = propsWithParallelism(4);

            int parallelism = CascadeImportPipeline.effectiveParallelism(props, true);

            assertThat(parallelism)
                    .as("Un import récursif doit tourner sans parallélisme (parallelism = 1)")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Les valeurs configurées sont ignorées (quelle que soit la config)")
        void configuredValuesAreIgnoredForRecursive() {
            ImportProperties props = new ImportProperties();
            props.setChunkSizeLines(500);
            props.setParallelism(8);

            assertThat(CascadeImportPipeline.effectiveChunkSizeLines(props, true))
                    .isEqualTo(Integer.MAX_VALUE);
            assertThat(CascadeImportPipeline.effectiveParallelism(props, true))
                    .isEqualTo(1);
        }
    }

    // ─── import récursif ORDONNÉ (tag __ORDER_STRICT__ ou orderedRecursionMode) ─

    @Nested
    @DisplayName("Import récursif ordonné (isRecursive=true, isStrictOrdered=true)")
    class RecursiveStrictOrderedImport {

        @Test
        @DisplayName("effectiveChunkSizeLines → valeur configurée (pas Integer.MAX_VALUE)")
        void chunkSizeIsConfiguredValueInStrictMode() {
            ImportProperties props = propsWithChunkSize(1000);

            int chunkSize = CascadeImportPipeline.effectiveChunkSizeLines(props, true, true);

            assertThat(chunkSize)
                    .as("Un import récursif ordonné peut utiliser des chunks normaux")
                    .isEqualTo(1000);
        }

        @Test
        @DisplayName("effectiveChunkSizeLines respecte la taille configurée")
        void chunkSizeRespectesConfiguredSize() {
            ImportProperties props = propsWithChunkSize(500);

            assertThat(CascadeImportPipeline.effectiveChunkSizeLines(props, true, true))
                    .isEqualTo(500);
        }

        @Test
        @DisplayName("effectiveParallelism → 1 même en mode ordonné (protection contre les races conditions)")
        void parallelismIsStillOneInStrictMode() {
            ImportProperties props = propsWithParallelism(8);

            int parallelism = CascadeImportPipeline.effectiveParallelism(props, true);

            assertThat(parallelism)
                    .as("Un import récursif ordonné reste séquentiel (parallelism = 1)")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Valeurs par défaut : chunk=1000, parallelism=1 en mode ordonné")
        void defaultValuesInStrictMode() {
            ImportProperties props = new ImportProperties(); // chunk=1000, parallelism=4

            assertThat(CascadeImportPipeline.effectiveChunkSizeLines(props, true, true))
                    .isEqualTo(1000);
            assertThat(CascadeImportPipeline.effectiveParallelism(props, true))
                    .isEqualTo(1);
        }
    }

    // ─── import non-récursif ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Import non-récursif (isRecursive = false)")
    class NonRecursiveImport {

        @Test
        @DisplayName("effectiveChunkSizeLines → valeur configurée dans ImportProperties")
        void chunkSizeIsConfiguredValue() {
            ImportProperties props = propsWithChunkSize(500);

            int chunkSize = CascadeImportPipeline.effectiveChunkSizeLines(props, false);

            assertThat(chunkSize)
                    .as("Un import non-récursif doit utiliser la taille de chunk configurée")
                    .isEqualTo(500);
        }

        @Test
        @DisplayName("effectiveParallelism → valeur configurée dans ImportProperties")
        void parallelismIsConfiguredValue() {
            ImportProperties props = propsWithParallelism(8);

            int parallelism = CascadeImportPipeline.effectiveParallelism(props, false);

            assertThat(parallelism)
                    .as("Un import non-récursif doit utiliser le parallélisme configuré")
                    .isEqualTo(8);
        }

        @Test
        @DisplayName("Les valeurs par défaut (1000 / 4) sont correctement transmises")
        void defaultConfigValuesAreRespected() {
            ImportProperties props = new ImportProperties(); // chunk=1000, parallelism=4

            assertThat(CascadeImportPipeline.effectiveChunkSizeLines(props, false))
                    .isEqualTo(1000);
            assertThat(CascadeImportPipeline.effectiveParallelism(props, false))
                    .isEqualTo(4);
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static ImportProperties propsWithChunkSize(int chunkSize) {
        ImportProperties props = new ImportProperties();
        props.setChunkSizeLines(chunkSize);
        return props;
    }

    private static ImportProperties propsWithParallelism(int parallelism) {
        ImportProperties props = new ImportProperties();
        props.setParallelism(parallelism);
        return props;
    }
}