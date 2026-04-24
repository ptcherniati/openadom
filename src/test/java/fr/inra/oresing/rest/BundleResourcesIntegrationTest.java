package fr.inra.oresing.rest;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.filesenderclient.BuildBundleReport;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveType;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import fr.inra.oresing.rest.reactive.ReactiveTypeInfo;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import fr.inra.oresing.rest.usecases.data.SendZipLinkByMailUseCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Test d'intégration de bout en bout du cycle de vie bundle :
 * <ol>
 *   <li>Chargement de l'application <em>pattern</em> + dépôt des référentiels</li>
 *   <li>Sauvegarde via l'endpoint {@code GET /upload-bundle} (mock du service FileSender)</li>
 *   <li>Recréation de l'application sous un nom différent ({@code patternrestored})</li>
 *   <li>Restauration du zip via l'endpoint {@code POST /download-bundle}</li>
 *   <li>Vérification que les données sont bien présentes dans l'application restaurée</li>
 * </ol>
 *
 * <p>{@code SendZipLinkByMailUseCase} est remplacé par un {@code @MockitoBean} afin
 * d'intercepter le chemin du zip généré <em>sans</em> solliciter le service FileSender externe.
 */
@Tag("integration.bundle")
@Tag("docker-required")
@DisplayName("Cycle de vie complet bundle — Pattern : sauvegarde & restauration")
@Slf4j
class BundleResourcesIntegrationTest extends AbstractIntegrationTest {

    /** Remplacement du service d'envoi de zip par mail pour capturer le zip généré. */
    @MockitoBean
    SendZipLinkByMailUseCase sendZipLinkByMailUseCase;

    // ── État partagé entre les DynamicTests ─────────────────────────────────

    /** Identifiant de l'application "pattern" créée au cours du test. */
    private final AtomicReference<String> patternId = new AtomicReference<>();

    /** Bytes du zip bundle capturé lors du GET upload-bundle. */
    private final AtomicReference<byte[]> capturedZipBytes = new AtomicReference<>();

    /** Identifiant de l'application "patternrestored". */
    private final AtomicReference<String> restoredId = new AtomicReference<>();

    /**
     * Verrou utilisé pour attendre la fin asynchrone de la création du bundle zip.
     * Le bundle est créé dans {@code heavyExecutorService}; le latch est déclenché
     * à l'intérieur du {@code doAnswer} du mock de {@code sendZipLinkByMailUseCase}.
     */
    private final CountDownLatch bundleLatch = new CountDownLatch(1);

    // ── Point d'entrée du test ───────────────────────────────────────────────

    @TestFactory
    @DisplayName("Sauvegarde et restauration de l'application Pattern")
    Stream<DynamicNode> patternBundleLifecycle() {

        // ── Configuration du mock AVANT le démarrage des dynamic tests ──────────
        doAnswer(invocation -> {
            // On ne capture QUE l'appel provenant de l'upload-bundle (BuildBundleReport)
            if (invocation.getArgument(1) instanceof BuildBundleReport) {
                Path zipPath = invocation.getArgument(0);
                log.info("Bundle zip capturé : {}", zipPath);
                capturedZipBytes.set(Files.readAllBytes(zipPath));
                bundleLatch.countDown();
            }
            // Pour l'appel du download-bundle (BundleReport) : ne rien faire
            return null;
        }).when(sendZipLinkByMailUseCase).execute(any(), any(), any());

        return Stream.of(

                // ── 1. Initialisation ───────────────────────────────────────────────
                dynamicContainer("1. Initialisation des droits", Stream.of(
                        dynamicTest("Autorisation de création d'application 'pattern*'", () ->
                                fixtures.addUserRightCreateApplication(
                                        fixtures.adminConnection.userResult().userId(), "pattern%")
                        )
                )),

                // ── 2. Chargement de l'application source ───────────────────────────
                dynamicContainer("2. Chargement de l'application Pattern", Stream.of(
                        dynamicTest("Chargement de la configuration YAML", () -> {
                            try (InputStream in = getClass().getResourceAsStream(
                                    Fixtures.getPatternApplicationConfigurationResourceName())) {
                                assertThat(in)
                                        .as("Le fichier pattern.yaml doit être présent dans les ressources de test")
                                        .isNotNull();
                                MockMultipartFile config = new MockMultipartFile(
                                        "file", "pattern.yaml", "text/plain", in);
                                String id = fixtures.getIdFromApplicationResult(
                                        fixtures.loadApplication(config,
                                                fixtures.adminConnection.jwt(), "pattern", ""));
                                patternId.set(id);
                                assertThat(id).isNotBlank();
                                log.info("Application 'pattern' créée : {}", id);
                            } catch (Throwable t) {
                                throw new OreSiTechnicalException(t.getMessage(), t);
                            }
                        })
                )),

                // ── 3. Dépôt des référentiels dans l'application source ──────────────
                dynamicContainer("3. Dépôt des référentiels Pattern",
                        Fixtures.getPatternReferentielOrderFiles().entrySet().stream()
                                .map(e -> dynamicTest("Dépôt du référentiel : " + e.getKey(), () -> {
                                    try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                                        assertThat(refStream)
                                                .as("Ressource introuvable : " + e.getValue())
                                                .isNotNull();
                                        MockMultipartFile refFile = new MockMultipartFile(
                                                "file", e.getValue(), "text/plain", refStream);
                                        mockMvc.perform(
                                                        multipart("/api/v1/applications/pattern/data/{refType}", e.getKey())
                                                                .file(refFile)
                                                                .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                                .andExpect(status().isCreated());
                                    }
                                }))
                ),

                // ── 4. Vérification des données source ──────────────────────────────
                dynamicContainer("4. Vérification des données déposées dans 'pattern'", Stream.of(
                        dynamicTest("Les données 'taxon' sont bien présentes", () ->
                                mockMvc.perform(
                                                get("/api/v1/applications/pattern/data/taxon/json")
                                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                        .andExpect(status().is2xxSuccessful())
                                        .andExpect(jsonPath("$.rows").isArray())
                                        .andExpect(jsonPath("$.rows", hasSize(greaterThan(0))))
                        )
                )),

                // ── 5. Sauvegarde bundle ────────────────────────────────────────────
                dynamicContainer("5. Sauvegarde bundle (upload-bundle)", Stream.of(

                        dynamicTest("Déclenchement de la création du bundle avec données", () ->
                                // L'endpoint retourne 200 immédiatement et délègue en async
                                mockMvc.perform(
                                                get("/api/v1/applications/pattern/upload-bundle")
                                                        .param("withData", "true")
                                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                        .andExpect(status().isOk())
                        ),

                        dynamicTest("Attente de la fin de la création asynchrone du bundle", () -> {
                            boolean completed = bundleLatch.await(120, TimeUnit.SECONDS);
                            assertThat(completed)
                                    .as("Le bundle zip devrait être créé en moins de 120 secondes")
                                    .isTrue();
                            assertThat(capturedZipBytes.get())
                                    .as("Le zip capturé ne doit pas être vide")
                                    .isNotEmpty();
                            log.info("Bundle capturé : {} octets", capturedZipBytes.get().length);
                        })
                )),

                // ── 6. Création de l'application de restauration ────────────────────
                dynamicContainer("6. Création de l'application destination 'patternrestored'", Stream.of(
                        dynamicTest("Chargement de la configuration avec le nouveau nom", () -> {
                            try (InputStream in = getClass().getResourceAsStream(
                                    Fixtures.getPatternApplicationConfigurationResourceName())) {
                                assertThat(in).isNotNull();
                                MockMultipartFile config = new MockMultipartFile(
                                        "file", "pattern.yaml", "text/plain", in);
                                String id = fixtures.getIdFromApplicationResult(
                                        fixtures.loadApplication(config,
                                                fixtures.adminConnection.jwt(), "patternrestored", ""));
                                restoredId.set(id);
                                assertThat(id).isNotBlank();
                                log.info("Application 'patternrestored' créée : {}", id);
                            } catch (Throwable t) {
                                throw new OreSiTechnicalException(t.getMessage(), t);
                            }
                        })
                )),

                // ── 7. Restauration du bundle ───────────────────────────────────────
                dynamicContainer("7. Restauration du bundle (download-bundle)", Stream.of(
                        dynamicTest("Upload du zip vers 'patternrestored' et vérification du flux NDJSON", () -> {
                            byte[] zipBytes = capturedZipBytes.get();
                            assertThat(zipBytes)
                                    .as("Le zip doit avoir été capturé avant la restauration")
                                    .isNotEmpty();

                            MockMultipartFile bundleFile = new MockMultipartFile(
                                    "file", "pattern-bundle.zip", "application/zip", zipBytes);

                            // L'endpoint download-bundle retourne un Flux<ReactiveResult> en NDJSON
                            MvcResult asyncResult = mockMvc.perform(
                                            multipart("/api/v1/applications/patternrestored/download-bundle")
                                                    .file(bundleFile)
                                                    .accept(MediaType.APPLICATION_NDJSON_VALUE)
                                                    .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                    .andExpect(request().asyncStarted())
                                    .andReturn();

                            MvcResult restoreResult = mockMvc.perform(asyncDispatch(asyncResult))
                                    .andExpect(status().isOk())
                                    .andReturn();

                            // ── Analyse du flux NDJSON ───────────────────────────────
                            List<ReactiveTypeError> errors = Fixtures.getErrors(restoreResult);
                            assertThat(errors)
                                    .as("La restauration ne doit pas contenir d'erreurs : %s", errors)
                                    .isEmpty();

                            Map<ReactiveType, List<ReactiveResult>> reactive =
                                    Fixtures.getReactiveResultFromResult(restoreResult);

                            List<ReactiveTypeInfo> infos = reactive
                                    .getOrDefault(ReactiveType.REACTIVE_INFO, List.of())
                                    .stream()
                                    .filter(ReactiveTypeInfo.class::isInstance)
                                    .map(ReactiveTypeInfo.class::cast)
                                    .toList();

                            // Vérification du chunk MANIFEST
                            assertThat(infos)
                                    .as("Un événement REACTIVE_INFO de type MANIFEST doit être présent")
                                    .anyMatch(i -> "MANIFEST".equals(i.result()));

                            // Vérification des chunks LOADED_DATA
                            long loadedDataCount = infos.stream()
                                    .filter(i -> "LOADED_DATA".equals(i.result()))
                                    .count();
                            assertThat(loadedDataCount)
                                    .as("Au moins un événement LOADED_DATA doit être présent")
                                    .isPositive();

                            log.info("Restauration réussie : {} événement(s) LOADED_DATA", loadedDataCount);
                        })
                )),

                // ── 8. Vérification des données restaurées ──────────────────────────
                dynamicContainer("8. Vérification des données dans 'patternrestored'", Stream.of(
                        dynamicTest("Les données 'taxon' sont bien restaurées", () ->
                                mockMvc.perform(
                                                get("/api/v1/applications/patternrestored/data/taxon/json")
                                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                        .andExpect(status().is2xxSuccessful())
                                        .andExpect(jsonPath("$.rows").isArray())
                                        .andExpect(jsonPath("$.rows", hasSize(greaterThan(0))))
                        ),

                        dynamicTest("Les données 'site' sont bien restaurées", () ->
                                mockMvc.perform(
                                                get("/api/v1/applications/patternrestored/data/site/json")
                                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                        .andExpect(status().is2xxSuccessful())
                                        .andExpect(jsonPath("$.rows").isArray())
                                        .andExpect(jsonPath("$.rows", hasSize(greaterThan(0))))
                        ),

                        dynamicTest("Les données 'proprietes_taxon' sont bien restaurées", () ->
                                mockMvc.perform(
                                                get("/api/v1/applications/patternrestored/data/proprietes_taxon/json")
                                                        .header("Authorization", "Bearer " + fixtures.adminConnection.jwt()))
                                        .andExpect(status().is2xxSuccessful())
                                        .andExpect(jsonPath("$.rows").isArray())
                                        .andExpect(jsonPath("$.rows", hasSize(greaterThan(0))))
                        )
                ))
        );
    }
}