# Migration optionnelle : `@BeforeAll` → `@Container` dans les tests Docker

> **Statut** : option non implémentée — documentée à titre de référence.  
> Les problèmes de blocage HikariCP déjà corrigés par les timeouts dans
> `application-testmail.yml` rendent cette migration non urgente.

---

## Table des matières

1. [Contexte — pattern actuel](#1-contexte--pattern-actuel)
2. [Ce que change `@Container` : Ryuk](#2-ce-que-change-container--ryuk)
3. [Les trois modifications requises](#3-les-trois-modifications-requises)
4. [Compatibilité avec le reste du code](#4-compatibilité-avec-le-reste-du-code)
5. [Analyse coût / bénéfice](#5-analyse-coût--bénéfice)
6. [Quand envisager la migration](#6-quand-envisager-la-migration)

---

## 1. Contexte — pattern actuel

**Fichier :** `src/test/java/fr/inra/oresing/rest/services/AbstractIntegrationTest.java`

Le conteneur PostgreSQL est géré **manuellement** :

```java
public static GenericContainer<?> postgres = new GenericContainer<>("postgres:18.3")
        .withEnv("POSTGRES_DB",       "test")
        .withEnv("POSTGRES_USER",     "test")
        .withEnv("POSTGRES_PASSWORD", "test")
        .withExposedPorts(5432)
        .withCopyFileToContainer(...);

@BeforeAll
static void beforeAll() {
    postgres.start();   // démarrage manuel
}

@AfterAll
static void afterAll() {
    if (postgres != null && postgres.isRunning()) {
        postgres.stop();   // arrêt manuel — NON APPELÉ en cas de crash JVM
    }
}
```

**Conséquence :** si la JVM est interrompue brutalement (`Ctrl+C`, `kill -9`, OOM, timeout Maven),
`@AfterAll` n'est **pas** appelé → le container reste en vie.

Pour nettoyer manuellement après un blocage :

```bash
docker ps | grep postgres
docker ps -q --filter "ancestor=postgres:18.3" | xargs -r docker rm -f
```

---

## 2. Ce que change `@Container` : Ryuk

Testcontainers embarque un daemon appelé **Ryuk** qui surveille les containers et les détruit
automatiquement quand la JVM s'arrête, **même brutalement**.

Ryuk s'active **uniquement** quand Testcontainers gère lui-même le cycle de vie, ce qui est le
cas avec l'annotation `@Container` (ou `try-with-resources`).

Avec le pattern `@BeforeAll`/`@AfterAll` actuel, Ryuk n'est **pas** activé.

> ⚠️ **Prérequis CI** : vérifier que `TESTCONTAINERS_RYUK_DISABLED` n'est pas forcé à `true`
> dans la configuration GitLab CI. Certains environnements Docker-in-Docker le désactivent.

---

## 3. Les trois modifications requises

Toutes les modifications sont dans
`src/test/java/fr/inra/oresing/rest/services/AbstractIntegrationTest.java`.

### 3.1 Ajouter `@Testcontainers` sur la classe

```java
// AVANT
@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("docker-required")
@Slf4j
public abstract class AbstractIntegrationTest {

// APRÈS
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers          // ← ajout
@ActiveProfiles("testmail")
@SpringBootTest(classes = {OreSiNg.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Tag("docker-required")
@Slf4j
public abstract class AbstractIntegrationTest {
```

`@Testcontainers` est méta-annoté avec `@ExtendWith(TestcontainersExtension.class)`, qui est
lui-même `@Inherited` → toutes les sous-classes héritent automatiquement du comportement sans
modification.

### 3.2 Annoter le champ `postgres` avec `@Container`

```java
// AVANT
public static GenericContainer<?> postgres = new GenericContainer<>("postgres:18.3")
        ...;

// APRÈS
import org.testcontainers.junit.jupiter.Container;

@Container               // ← ajout
public static GenericContainer<?> postgres = new GenericContainer<>("postgres:18.3")
        ...;
```

Un champ `static` + `@Container` indique à Testcontainers de partager le container pour toute
la durée de la classe (lifecycle de classe), ce qui correspond exactement au comportement actuel.

### 3.3 Supprimer `beforeAll()` et `afterAll()`

```java
// SUPPRIMER ces deux méthodes :

@BeforeAll
static void beforeAll() {
    postgres.start();
    log.info("Conteneur PostgreSQL démarré (port {})", postgres.getMappedPort(5432));
}

@AfterAll
static void afterAll() {
    if (postgres != null && postgres.isRunning()) {
        postgres.stop();
        log.info("Conteneur PostgreSQL arrêté.");
    }
}
```

L'extension `TestcontainersExtension` prend en charge le démarrage avant les tests et l'arrêt
après, avec Ryuk comme filet de sécurité.

---

## 4. Compatibilité avec le reste du code

### 4.1 `@DynamicPropertySource` + `@DirtiesContext`

```java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () ->
            "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432)
                    + "/" + PG_DATABASE + "?preparedStatementCacheQueries=0");
}
```

✅ **Compatible.** Avec `@Container`, le container est démarré par `TestcontainersExtension`
**avant** la création du contexte Spring. Quand `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` force
la création d'un nouveau contexte pour chaque test, `@DynamicPropertySource` est réévalué, mais
le container est déjà actif avec le même port → aucun problème.

### 4.2 `@AfterEach cleanDatabase()`

```java
@AfterEach
void cleanDatabase() {
    String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%d/%s",
            postgres.getHost(), postgres.getMappedPort(5432), PG_DATABASE);
    // ... nettoyage SQL ...
}
```

✅ **Compatible.** Le container est toujours actif pendant `@AfterEach`.

### 4.3 `TestDatabaseConfig` (bean DataSource alternatif)

```java
// TestDatabaseConfig.java
@Bean @Primary
public DataSource dataSource() {
    String jdbcUrl = String.format(
            "jdbc:postgresql://%s:%d/test?preparedStatementCacheQueries=0",
            AbstractIntegrationTest.postgres.getHost(),
            AbstractIntegrationTest.postgres.getMappedPort(5432));
    // ...
}
```

✅ **Compatible.** Le champ `postgres` reste `public static` → accessible depuis
`TestDatabaseConfig`. Le container sera démarré par Testcontainers avant que ce bean soit
instancié.

### 4.4 `ComparingConfiguration.insertApplicationInDb()`

```java
// ComparingConfiguration.java
String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
        postgres.getHost(), postgres.getMappedPort(5432), "test");
```

✅ **Compatible.** Accès au champ hérité de `AbstractIntegrationTest`, inchangé.

### 4.5 Tableau de synthèse

| Élément | Compatible | Raison |
|---|---|---|
| `@DynamicPropertySource` | ✅ | Container démarré avant le contexte Spring |
| `@DirtiesContext(BEFORE_EACH_TEST_METHOD)` | ✅ | Nouveau contexte Spring par méthode, même container |
| `cleanDatabase()` / `logDatabaseDiagnostics()` | ✅ | Container actif pendant `@AfterEach` |
| `TestDatabaseConfig` | ✅ | Champ `public static` inchangé |
| `ComparingConfiguration` | ✅ | Accès hérité inchangé |
| Sous-classes sans `@Testcontainers` | ✅ | Héritage via `@Inherited` |

---

## 5. Analyse coût / bénéfice

| Critère | Évaluation |
|---|---|
| **Bénéfice principal** | Nettoyage automatique du container en cas de crash JVM (Ryuk) |
| **Bénéfice secondaire** | Suppression de 2 méthodes (boilerplate) |
| **Risque** | Combinaison `@DirtiesContext` + `@Testcontainers` = cas limite non canonique, sujet à régressions lors de montées de version Spring/Testcontainers |
| **Impact sur le problème de blocage HikariCP** | **Aucun** — ce problème est résolu par les timeouts dans `application-testmail.yml` |
| **Fréquence des containers orphelins** | Très faible — sur crash uniquement, nettoyage manuel trivial |
| **En CI (GitLab)** | Runners éphémères — containers nettoyés entre pipelines quoi qu'il arrive |

**Verdict** : la migration est techniquement saine mais le bénéfice pratique est marginal dans
le contexte actuel. Elle ne doit pas être prioritaire.

---

## 6. Quand envisager la migration

Envisager la migration si l'une de ces conditions devient vraie :

- **Containers orphelins récurrents** : plusieurs développeurs partagent la même machine Docker
  et accumulent des containers non nettoyés entre sessions de travail.
- **Adoption de `.withReuse(false)` explicite** : si Testcontainers Reuse est activé pour
  accélérer les tests locaux, la gestion du lifecycle devient plus complexe et `@Container` +
  Ryuk devient un prérequis.
- **Ajout de nouveaux containers** (Redis, Kafka…) : si d'autres services Docker sont ajoutés
  aux tests d'intégration, migrer tous les containers vers `@Container` homogénéise la gestion.

---

## Références

- [`AbstractIntegrationTest.java`](../../src/test/java/fr/inra/oresing/rest/services/AbstractIntegrationTest.java) — classe à modifier
- [`TestDatabaseConfig.java`](../../src/test/java/fr/inra/oresing/TestDatabaseConfig.java) — réutilise `AbstractIntegrationTest.postgres`
- [`application-testmail.yml`](../../src/test/resources/application-testmail.yml) — timeouts HikariCP (fix du blocage)
- [Testcontainers JUnit 5 — doc officielle](https://java.testcontainers.org/test_framework_integration/junit_5/)
- [Ryuk — doc officielle](https://java.testcontainers.org/features/configuration/#disabling-ryuk)