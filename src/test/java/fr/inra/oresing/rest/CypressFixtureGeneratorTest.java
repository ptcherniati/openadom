package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.rest.fixtures.CypressFixtureWriter;
import fr.inra.oresing.rest.fixtures.MonSoereFixture;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test dédié à la <strong>génération des fixtures Cypress</strong> pour l'application ORE / MONSORE.
 *
 * <p>Ce test est distinct des tests d'intégration ordinaires : son seul objectif est de produire
 * des fichiers JSON/TXT exploitables par la suite de tests Cypress du frontend. Il doit être lancé
 * explicitement (tag {@value #TAG}) et NE fait PAS partie des suites CI ordinaires.
 *
 * <h2>Fonctionnalités clés</h2>
 * <ul>
 *   <li>Création des utilisateurs de test {@code monsoresimple}, {@code withrigths},
 *       {@code lambda} et {@code poussin} (admin).</li>
 *   <li>Chargement complet de l'application {@code monsoresimple} avec ses référentiels
 *       et ses données PEM.</li>
 *   <li>Normalisation des UUID volatils (application, utilisateurs) vers des UUID stables
 *       prédéfinis, afin que les fixtures soient <em>identiques d'une exécution à l'autre</em>
 *       et puissent être committés en VCS.</li>
 *   <li>Écriture d'un fichier {@code aliases.json} qui mappe les clés logiques
 *       (ex. {@code MONSORESIMPLE_APP_ID}) aux UUID stables, utilisable par les tests
 *       Cypress pour construire dynamiquement leurs interceptions.</li>
 *   <li>Chemin de sortie configurable via la propriété système
 *       {@value fr.inra.oresing.rest.fixtures.CypressFixtureWriter#BASE_DIR_PROPERTY}
 *       (défaut : répertoire de travail courant du JVM).</li>
 * </ul>
 *
 * <h2>Lancement</h2>
 * <pre>{@code
 * # Local – fixtures écrites dans ./ui/cypress/fixtures/
 * mvn test -Dgroups=GENERATE_CYPRESS_FIXTURES -Dtest=CypressFixtureGeneratorTest
 *
 * # CI – fixtures écrites dans /tmp/cypress-fixtures/
 * mvn test -Dgroups=GENERATE_CYPRESS_FIXTURES \
 *          -Dcypress.fixtures.base.dir=/tmp/cypress-fixtures
 * }</pre>
 */
@SpringBootTest(classes = {OreSiNg.class})
@Tag(CypressFixtureGeneratorTest.TAG)
@Tag("docker-required")
@DisplayName("Génération des fixtures Cypress – ORE/MONSORE")
@Slf4j
public class CypressFixtureGeneratorTest extends AbstractIntegrationTest {

    /** Tag JUnit 5 permettant de sélectionner ce test dans Maven ({@code -Dgroups=GENERATE_CYPRESS_FIXTURES}). */
    public static final String TAG = "GENERATE_CYPRESS_FIXTURES";

    // ── Utilisateurs de test ──────────────────────────────────────────────
    private final Fixtures.CreateUser monsoresimple = new Fixtures.CreateUser(
            "monsoresimple", "xxxxxxxx", "monsoresimple@inrae.fr");
    private final Fixtures.CreateUser withRightsUser = new Fixtures.CreateUser(
            "withrigths", "xxxxxxxx", "withrigths@inrae.fr");

    // ── Références partagées entre les dynamic tests ──────────────────────
    private CypressFixtureWriter writer;
    private final AtomicReference<String> appId = new AtomicReference<>();

    // ─────────────────────────────────────────────────────────────────────
    //  Initialisation et nettoyage
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Initialise le {@link CypressFixtureWriter} après que {@code AbstractIntegrationTest.baseSetUp()}
     * a été appelé par JUnit 5 (qui découvre les {@code @BeforeEach} par réflexion, même
     * package-private).  Ici on se contente d'initialiser le writer ; les fixtures ({@code mockMvc},
     * {@link fr.inra.oresing.rest.Fixtures}) sont déjà prêtes.
     */
    @BeforeEach
    void initWriter() {
        writer = CypressFixtureWriter.defaultWriter();
        log.info("Répertoire de sortie des fixtures : {}", writer.getBaseDir().toAbsolutePath());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Test factory principal
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Point d'entrée principal : enchaîne la création des utilisateurs, le chargement de
     * l'application et la génération des fixtures en une seule suite de dynamic tests.
     */
    @TestFactory
    @DisplayName("Génération complète des fixtures MONSORE")
    Stream<DynamicNode> generateAllFixtures() {

        MonSoereFixture monSoereFixture =
                new MonSoereFixture(fixtures, mockMvc, userRepository, jsonRowMapper);

        return Stream.of(

                // ── Étape 1 : créer les utilisateurs ─────────────────────
                DynamicContainer.dynamicContainer("1 – Créer les utilisateurs de test", Stream.of(

                        DynamicTest.dynamicTest("Créer l'utilisateur monsoresimple", () -> {
                            fixtures.monsoresimpleConnection =
                                    fixtures.createUserForUserDefinition(monsoresimple, true, false);
                            Assertions.assertThat(fixtures.getMonsoresimpleConnection()).isNotNull();
                            log.info("Utilisateur monsoresimple UUID = {}",
                                    fixtures.getMonsoresimpleConnection().userResult().userId());
                        }),

                        DynamicTest.dynamicTest("Créer l'utilisateur withrigths", () -> {
                            fixtures.withRightsUserConnection =
                                    fixtures.createUserForUserDefinition(withRightsUser, true, false);
                            Assertions.assertThat(fixtures.getWithRightsUserConnection()).isNotNull();
                            log.info("Utilisateur withrigths UUID = {}",
                                    fixtures.getWithRightsUserConnection().userResult().userId());
                        })
                )),

                // ── Étape 2 : configurer le writer avec les UUID ──────────
                DynamicTest.dynamicTest("2 – Configurer la normalisation des UUID", () -> {
                    // Les UUID sont connus après création des utilisateurs
                    writer
                            .withUuid(
                                    fixtures.getMonsoresimpleConnection().userResult().userId().toString(),
                                    CypressFixtureWriter.MONSORESIMPLE_USER_UUID,
                                    CypressFixtureWriter.KEY_MONSORESIMPLE_USER)
                            .withUuid(
                                    fixtures.getWithRightsUserConnection().userResult().userId().toString(),
                                    CypressFixtureWriter.WITHRIGTHS_USER_UUID,
                                    CypressFixtureWriter.KEY_WITHRIGTHS_USER)
                            .withUuid(
                                    fixtures.lambdaConnection.userResult().userId().toString(),
                                    CypressFixtureWriter.LAMBDA_USER_UUID,
                                    CypressFixtureWriter.KEY_LAMBDA_USER)
                            .withUuid(
                                    fixtures.adminConnection.userResult().userId().toString(),
                                    CypressFixtureWriter.POUSSIN_USER_UUID,
                                    CypressFixtureWriter.KEY_POUSSIN_USER);
                    log.info("Mapping UUID configuré : {} entrées", writer.getDynamicToStableMap().size());
                }),

                // ── Étape 3 : charger l'application ──────────────────────
                DynamicContainer.dynamicContainer("3 – Charger l'application MONSORESIMPLE", Stream.of(

                        DynamicTest.dynamicTest("Donner les droits et charger la configuration", () -> {
                            fixtures.addUserRightCreateApplication(
                                    fixtures.getMonsoresimpleConnection().userResult().userId(),
                                    "monsoresimple");

                            try (InputStream in = Objects.requireNonNull(
                                    getClass().getResourceAsStream(
                                            MonSoereFixture.getMonsoreApplicationConfigurationResourceName()))) {
                                org.springframework.mock.web.MockMultipartFile configFile =
                                        new org.springframework.mock.web.MockMultipartFile(
                                                "file", "monsoresimple.yaml", "text/plain", in);
                                var result = fixtures.loadApplication(
                                        configFile,
                                        fixtures.getMonsoresimpleConnection().jwt(),
                                        "monsoresimple", "");
                                String id = fixtures.getIdFromApplicationResult(result);
                                appId.set(id);
                                log.info("Application monsoresimple chargée – id = {}", id);
                            }
                        }),

                        DynamicTest.dynamicTest("Enregistrer l'UUID de l'application dans le writer", () -> {
                            Assertions.assertThat(appId.get())
                                    .as("L'application doit avoir été chargée avant d'enregistrer son UUID")
                                    .isNotNull().isNotEmpty();
                            writer.withUuid(
                                    appId.get(),
                                    CypressFixtureWriter.MONSORESIMPLE_APP_UUID,
                                    CypressFixtureWriter.KEY_MONSORESIMPLE_APP);
                        }),

                        DynamicContainer.dynamicContainer("Charger les référentiels",
                                MonSoereFixture.getMonsoreReferentielFiles().entrySet().stream()
                                        .map(e -> DynamicTest.dynamicTest("Charger ref : " + e.getKey(), () -> {
                                            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                                                var refFile = new org.springframework.mock.web.MockMultipartFile(
                                                        "file", e.getValue(), "text/plain", refStream);
                                                mockMvc.perform(
                                                                multipart("/api/v1/applications/monsoresimple/data/{refType}", e.getKey())
                                                                        .file(refFile)
                                                                        .header("Authorization", "Bearer " +
                                                                                                 fixtures.getMonsoresimpleConnection().jwt()))
                                                        .andExpect(status().isCreated());
                                            }
                                        }))),

                        DynamicTest.dynamicTest("Charger les données PEM", () -> {
                            try (InputStream pemStream = getClass().getResourceAsStream(
                                    MonSoereFixture.getPemDataResourceName())) {
                                var pemFile = new org.springframework.mock.web.MockMultipartFile(
                                        "file", "data-pem.csv", "text/plain", pemStream);
                                mockMvc.perform(
                                                multipart("/api/v1/applications/monsoresimple/data/pem")
                                                        .file(pemFile)
                                                        .header("Authorization", "Bearer " +
                                                                                 fixtures.getMonsoresimpleConnection().jwt()))
                                        .andExpect(status().isCreated());
                            }
                        })
                )),

                // ── Étape 4 : générer les fixtures ───────────────────────
                DynamicContainer.dynamicContainer("4 – Générer les fichiers de fixtures",
                        monSoereFixture.checkAndRegisterResults(writer))
        );
    }
}