# Document de portage — branche `develop`

> **Contexte** : Les branches `fix-develop` et autres branches de correctifs ont disparu parce que
> les tests passent désormais intégralement sur `develop`. Ce document décrit, transformation par
> transformation, ce qu'apporte chaque lot de changement afin de faciliter les revues, le suivi
> de régression et les futurs portages vers d'autres branches.

---

## État avant portage (référence : branche `test2`, 2026-04-13)

| Indicateur | Valeur |
|---|---|
| Issues Sonar ouvertes | **461** |
| Couverture nouveau code | 41,4 % |
| Tests run | 1 125 |
| Tests KO (Failures + Errors) | **125 (111 + 14)** |
| Temps total suite | ≈ 23 min |

---

## État après portage (branche `develop`, 2026-05-05)

| Indicateur | Valeur |
|---|---|
| Tests KO | **0** |
| Tests use-cases | 55 / 55 ✅ |
| Branches correctifs fermées | `fix-develop` et autres |

---

## Transformations réalisées

---

### T1 — Extraction de 46 Use Cases depuis `OreSiResources`

**Motivation**

`OreSiResources` était un monolithe de ≈ 1 500 lignes mélangeant toute la logique métier via un
`ServiceContainer` (anti-pattern service-locator). Cette opacité rendait les tests unitaires
impossibles et la revue difficile.

**Ce qui a été fait**

- Suppression du pattern `ServiceContainer.{service}()` : 28 sites d'appel remplacés par
  injection directe des Use Cases correspondants.
- Création du package `fr.inra.oresing.rest.usecases` avec la structure suivante :

```
rest/usecases/
├── application/            (7 use cases)
│   ├── BuildOpenAdomUseCase
│   ├── ChangeApplicationConfigurationUseCase
│   ├── CreateApplicationUseCase
│   ├── GetApplicationOrAccordingToRightsUseCase
│   ├── GetApplicationUseCase
│   ├── GetApplicationsUseCase
│   └── ValidateConfigurationUseCase
├── data/                   (12 use cases)
│   ├── BuildDataZipUseCase
│   ├── DeleteDataUseCase
│   ├── FindDataUseCase
│   ├── FindReferenceUseCase
│   ├── GetCheckedFormatComponentsUseCase
│   ├── GetDataColumnUseCase
│   ├── GetDataCsvStreamUseCase
│   ├── GetFormatCheckedUseCase
│   ├── GetReferenceDisplaysByIdUseCase
│   ├── ReadEntryUseCase
│   ├── SendZipLinkByMailUseCase
│   └── WriteUploadBundleUseCase
├── messaging/              (1 use case)
│   └── SendUploadErrorsMailUseCase
├── metadata/
│   ├── normalization/      (2 use cases)
│   │   ├── BuildNormalizedSchemaUseCase
│   │   └── GetNormalizedSchemaUseCase
│   ├── rightsrequest/      (2 use cases)
│   │   ├── CreateOrUpdateRightsRequestUseCase
│   │   └── FindRightsRequestUseCase
│   └── synthesis/          (3 use cases)
│       ├── BuildSynthesisUseCase
│       ├── GetSynthesisUseCase
│       └── GetSynthesisWithVariableUseCase
├── security/
│   ├── authentication/     (1 use case)
│   │   └── GetCurrentUserUseCase
│   └── authorization/      (4 use cases)
│       ├── GetAdminAuthorizationsUseCase
│       ├── GetAllUsersUseCase
│       ├── GetApplicationAuthorizationsUseCase
│       └── GetAuthorizationScopesUseCase
└── storage/
    ├── additionalfile/     (4 use cases)
    │   ├── CreateOrUpdateAdditionalFileUseCase
    │   ├── DeleteAdditionalFilesUseCase
    │   ├── FindAdditionalFileUseCase
    │   └── GetAdditionalFilesZipStreamUseCase
    ├── binaryfile/         (8 use cases)
    │   ├── CreateDataUseCase
    │   ├── GetCharteUseCase
    │   ├── GetFileUseCase
    │   ├── GetFileWithDataUseCase
    │   ├── GetFilesOnRepositoryUseCase
    │   ├── GetReferencedBinaryFilesUseCase
    │   ├── GetStoreFileUseCase
    │   └── RemoveFileUseCase
    └── versioning/         (1 use case)
        └── UnPublishVersionBeforeDeleteUseCase
```

**Total : 46 use cases organisés par domaine métier**

**Apport**

- Chaque Use Case est un composant Spring autonome, testable isolément avec `@Tag("use-cases")`.
- `OreSiResources` devient un contrôleur pur (délégation seule, sans logique métier).
- Remplacement complet du `ServiceContainer` (anti-pattern service-locator éliminé).
- 55 tests use-cases ajoutés (profil Maven `use-cases`, job GitLab CI `test_use-cases`).

**Fichiers clés**

| Fichier | Rôle |
|---|---|
| `src/main/java/…/rest/OreSiResources.java` | Contrôleur allégé — injection des Use Cases |
| `src/main/java/…/rest/usecases/**` | 46 Use Cases |
| `src/test/java/…/rest/OreSiResourcesTest.java` | Suite d'intégration (0 échec après portage) |
| `CI_CD_USE_CASES.md` | Documentation CI/CD Use Cases |

---

### T2 — Pipeline d'import CSV via Cascade (`CascadeImportPipeline`)

**Motivation**

L'ancien pipeline d'import effectuait le chargement en base sur le thread HTTP, bloquait la
connexion et ne permettait pas de progression live ni de parallélisme configurable.

**Ce qui a été fait**

Création du package `fr.inra.oresing.workflow.cascade` avec un pipeline complet :

#### T2.1 — Configuration externalisée (`ImportProperties`)

Toutes les propriétés du pipeline sont maintenant lisibles et modifiables à chaud via le
prefix Spring `cascade.import.*` (ou variables d'environnement `CASCADE_IMPORT_*`) :

| Propriété | Défaut | Rôle |
|---|---|---|
| `chunkSizeLines` | 1 000 | Lignes CSV par chunk |
| `maxErrorsThreshold` | 100 | Seuil d'abandon |
| `sinkStrategy` | `MERGE_FILE` | Stratégie de chargement (`MERGE_FILE` / `DIRECT_COPY`) |
| `stagingStrategy` | `SHARED_UNLOGGED` | Stratégie de table staging |
| `pipelineMode` | `STAGED` | Mode pipeline Cascade 2.1.0 |
| `enableMetrics` | `false` | Activation des métriques Micrometer par chunk |
| pools source / transform / sink | 4 | Parallélisme par étape |

#### T2.2 — Stratégies de chargement (`SinkStrategy`)

| Stratégie | Comportement |
|---|---|
| `MERGE_FILE` (legacy) | Chunks → `merged.csv` → un seul `COPY` DB via `storeAll` — compatible avec tous les déploiements |
| `DIRECT_COPY` (production) | Chunks écrits chunk par chunk dans une table staging via `FinalizeHook` — évite le fichier merged, adapté aux très gros volumes |

#### T2.3 — Staging tables (`StagingMode` / `StagingStrategy`)

| Mode | Table | Comportement |
|---|---|---|
| `PER_CONNECTION_TEMP` | `TEMP TABLE` (connexion) | Meurt avec la connexion — atomique, parfait pour les tests |
| `PER_WORKFLOW_TABLE` | `UNLOGGED` dédiée par `correlationId` | Survit au crash JVM, sweepée par `StagingOrphanSweeper` |
| `SHARED_UNLOGGED` | `UNLOGGED` partagée + colonne `correlation_id` | Par défaut en production — meilleure résilience |

#### T2.4 — Déferred finalize (anti-deadlock) — `TxAwareDeferredRunner` / `MergeFileDeferredRunner`

**Problème** : en dehors d'une transaction Spring (`@Transactional`), le thread sink de Cascade
tenterait un `UPSERT` staging → table finale pendant que le thread HTTP tient déjà des row-locks
sur cette même table → **deadlock garanti**.

**Solution** : si une transaction Spring externe est active, le pipeline configure le sink en mode
`DEFERRED_TO_CALLER`. Le `UPSERT` final est alors enregistré comme `TransactionSynchronization`
Spring et déclenché en `afterCommit` (après libération des row-locks) :

- `TxAwareDeferredRunner` : pour la stratégie `DIRECT_COPY` (pont entre `DeferredFinalize`
  Cascade 3.0.0 et la transaction Spring).
- `MergeFileDeferredRunner` : pour la stratégie `MERGE_FILE` (capture le `merged.csv` et exécute
  `storeAll` en `afterCommit`).

En cas de rollback Spring : `afterCompletion` est appelé, le cleanup staging s'exécute et le
workflow est marqué `STATUS_FAILED`.

#### T2.5 — Rate limiters (`ImportRateLimiter` / `ExtractionRateLimiter`)

- Slots par utilisateur : réponse immédiate HTTP 429 si trop d'imports simultanés.
- Slot libéré dans un `finally` (pas de fuite même sur exception).

**Apport**

- Import non-bloquant avec progression en temps réel.
- Anti-deadlock garanti même dans un contexte `@Transactional` existant.
- Stratégies de staging adaptées au contexte (tests vs production).
- Configuration entièrement externalisée et modifiable à chaud.
- Protection contre les abus via rate limiting par utilisateur.

**Fichiers clés**

| Fichier | Rôle |
|---|---|
| `workflow/cascade/CascadeImportPipeline.java` | Orchestrateur principal (1 139 lignes) |
| `workflow/cascade/config/ImportProperties.java` | Configuration externalisée |
| `workflow/cascade/TxAwareDeferredRunner.java` | Anti-deadlock DIRECT_COPY |
| `workflow/cascade/MergeFileDeferredRunner.java` | Anti-deadlock MERGE_FILE |
| `workflow/cascade/staging/StagingMode.java` | Modes de staging |
| `workflow/cascade/staging/StagingOrphanSweeper.java` | Nettoyage tables orphelines |
| `workflow/cascade/ImportRateLimiter.java` | Rate limit import |
| `workflow/cascade/ExtractionRateLimiter.java` | Rate limit extraction |

---

### T3 — Dashboard workflow en temps réel (issue #62)

**Motivation**

La table `oa_audit.workflow_log` n'est alimentée qu'en fin de workflow (COMPLETED / FAILED /
CANCELLED). Il n'existait aucune visibilité sur les imports en cours.

**Ce qui a été fait**

#### T3.1 — `WorkflowActiveRegistry` (in-memory)

Registry thread-safe (`ConcurrentHashMap`) maintenant l'état live de chaque workflow :
- Démarrage : `registerWorkflowStart` (CascadeImportPipeline, avant envoi au pool).
- Mise à jour : `update(uuid, liveRecords, liveChunks, ...)` à chaque lot de lignes traitées.
- Drill-down chunk : abonnement aux événements cascade (`WorkflowListener`) — snapshots
  `ChunkSnapshot` par workflow, fusionnés à la lecture.
- Fin : `finish(uuid)` dans le `finally` (ou dans les runners deferred en cas de TX externa).

#### T3.2 — API REST Dashboard (`DashboardController`)

Trois endpoints sous `/api/dashboard/workflows` :

| Endpoint | Rôle |
|---|---|
| `GET /in-progress` | Workflows actuellement en cours (depuis le registry in-memory) |
| `GET /history` | Historique paginé depuis `oa_audit.workflow_log` |
| `GET /{correlationId}` | Détail d'un workflow spécifique |

Sécurité : `openAdomAdmin` voit tout ; les autres utilisateurs voient uniquement leurs propres
workflows (row-level authorization).

#### T3.3 — `HeartbeatService`

Émission d'un heartbeat toutes les 30 secondes pendant les phases longues (staging SQL). Stocké
dans `workflow_log.last_heartbeat_at`. Seuil zombie configurable.

#### T3.4 — `WorkflowZombieSweeper`

Tâche planifiée (`@Scheduled`) qui détecte les rows `IN_PROGRESS` dont
`COALESCE(last_heartbeat_at, start_time)` dépasse le seuil (`app.workflow.zombie-threshold-minutes`,
défaut 10 min) et les passe à `CANCELLED` avec `fatal_error = 'presumed dead'`.

Sans ce sweeper, un SIGKILL (OOM, `docker kill`) laisserait éternellement une row `IN_PROGRESS`
dans le dashboard.

#### T3.5 — Dashboard complémentaires

| Contrôleur | Endpoints | Rôle |
|---|---|---|
| `DashboardConfigController` | `/api/dashboard/config` | Lecture / édition à chaud de `ImportProperties` |
| `DashboardIntegrityController` | `/api/dashboard/integrity` | Contrôle de cohérence via `IntegrityService` |
| `DashboardCompensationController` | `/api/dashboard/compensation` | Liste et rejeu des opérations de compensation |

**Apport**

- Visibilité temps réel des imports en cours (progression en %, records/s).
- Drill-down par chunk (phase planifiée E).
- Détection automatique des workflows zombies.
- Administration à chaud du pipeline sans redémarrage.
- API consommée par le micro-frontend `oa-live`.

**Fichiers clés**

| Fichier | Rôle |
|---|---|
| `workflow/cascade/history/WorkflowActiveRegistry.java` | Registry in-memory |
| `workflow/cascade/history/WorkflowLogWriter.java` | Persistance en base |
| `workflow/cascade/history/WorkflowLogRepository.java` | Repository `oa_audit.workflow_log` |
| `workflow/cascade/history/HeartbeatService.java` | Heartbeat 30 s |
| `workflow/cascade/history/WorkflowZombieSweeper.java` | Sweeper zombies |
| `rest/dashboard/DashboardController.java` | API REST in-progress / history |
| `rest/dashboard/DashboardConfigController.java` | API config à chaud |
| `rest/dashboard/DashboardIntegrityController.java` | API intégrité |
| `rest/dashboard/DashboardCompensationController.java` | API compensation |

---

### T4 — Journal de compensation (`CompensationLogService`)

**Motivation**

Si la JVM crashe entre le CREATE d'une table staging et la fin du `FinalizeHook`, la table
reste orpheline en base. Il faut un mécanisme de récupération au redémarrage.

**Ce qui a été fait**

Pattern compensation-log (saga) dans `fr.inra.oresing.monitoring.compensation` :

1. **Avant l'opération risquée** : `CompensationLogService.record(op, ...)` insère une row
   `PENDING` dans une **transaction indépendante** (`REQUIRES_NEW`) — visible immédiatement
   avant que l'opération commence.
2. **Après le succès** : `confirm(id)` DELETE la row.
3. **Sur échec / crash** : `CompensationSweeper` (tâche planifiée) relit les rows `PENDING`
   dont le TTL est expiré et exécute le handler correspondant.

Deux handlers :

| Handler | Op type | Action |
|---|---|---|
| `StagingCleanupHandler` | `STAGING_CLEANUP` | DROP la table/rows staging orphelines. Refuse si le workflow est encore `IN_PROGRESS` légitime (smart-check). |
| `BinaryFileCompensationHandler` | `BINARY_FILE_CLEANUP` | Supprime le fichier binaire orphelin si le binaryFile DB a été rollbacké. |

**Apport**

- Garantie de nettoyage des tables staging même après crash JVM (docker kill, OOM).
- Pas de fuites silencieuses de tables `UNLOGGED` en base.
- UI admin pour visualiser et rejouer les opérations en échec
  (`DashboardCompensationController`).

**Fichiers clés**

| Fichier | Rôle |
|---|---|
| `monitoring/compensation/CompensationLogService.java` | API publique (record / confirm / compensateNow) |
| `monitoring/compensation/CompensationLogRepository.java` | Accès base `oa_audit.compensation_log` |
| `monitoring/compensation/CompensationSweeper.java` | Sweeper automatique |
| `monitoring/compensation/handlers/StagingCleanupHandler.java` | Handler staging |
| `monitoring/compensation/handlers/BinaryFileCompensationHandler.java` | Handler binary file |

---

### T5 — Gestion des sessions utilisateurs

**Motivation**

Aucune visibilité sur les sessions actives, impossible de détecter les sessions expirées ou
de blacklister un JWT sans redémarrage.

**Ce qui a été fait**

Package `fr.inra.oresing.monitoring.session` :

| Composant | Rôle |
|---|---|
| `UserSessionRegistry` | Registry in-memory des sessions (pattern identique à `WorkflowActiveRegistry`) |
| `UserSessionLogWriter` | Persistance en base (login/logout/expiration) |
| `UserSessionLogRepository` | Accès `oa_audit.user_session_log` |
| `JwtBlacklistRegistry` | Liste noire in-memory des JWT révoqués |
| `SessionExpirySweeper` | Tâche planifiée : marque `JWT_EXPIRED` les sessions dont `expiresAt <= now` |

**Apport**

- Visibilité en temps réel des sessions actives.
- Possibilité de révoquer un JWT sans redémarrage.
- Traçabilité complète login / logout / expiration en base.

---

### T6 — Métriques Prometheus / Micrometer (`OpenadomMetrics`)

**Motivation**

Aucune métrique applicative disponible dans Grafana pour les imports.

**Ce qui a été fait**

- `OpenadomMetrics` : compteurs et timers Micrometer par application/datatype :
  - `openadom.import.duration` (histogram)
  - `openadom.import.records` (counter)
  - `openadom.import.errors` (counter)
  - `openadom.import.filesize` (summary)
- `OreSiWebMvcTagsContributor` : tags MVC pour enrichir les métriques HTTP (application, datatype).
- Activation conditionnelle (`cascade.import.enableMetrics=true`) pour ne pas pénaliser les
  imports sur les gros volumes si non nécessaire.

**Apport**

- Dashboards Grafana disponibles pour le suivi des imports en production.
- Activation/désactivation à chaud sans redémarrage.

---

### T7 — Qualité Sonar (SONAR_ROADMAP)

**Motivation**

461 issues Sonar ouvertes et 125 tests en échec bloquaient toute analyse de qualité fiable.

**Ce qui a été fait**

#### T7.1 — Correction des 125 tests en échec (P0 — bloquant)

La cause principale était une régression introduite par le déplacement de l'orchestration
`uploadBundle` sur `normalExecutorService` (commit `33cd8f1`). Les flux d'import asynchrones
ne démarraient plus correctement dans le contexte de test Spring MVC.

**Résultat** : `OreSiResourcesTest` → **0 failure** sur develop.

#### T7.2 — Fiabilité (P1)

| Lot | Correctif |
|---|---|
| 1.1 | Faux positifs NPE `OreSiNg.java` — builder `Info.Builder` extrait en variable locale |
| 1.2 | Méthodes `private` masquant la classe parente dans `OaImportWorkerService` renommées |
| 1.3 | Type `List<Map>` corrigé en `List<Map<String, ?>>` dans `DataRow.java` |
| 1.4 | `equals()` inter-types sans relation corrigé dans `EmailService.java` |

#### T7.3 — Nettoyage dead code (P2)

| Lot | Correctif |
|---|---|
| 2.1 | Champs inutilisés supprimés dans `WorkflowOrchestratorImport` (réduit constructeur > 7 params) |
| 2.2 | Imports inutilisés supprimés (`DataRepository`, `DataRepositoryForBuffer`) |
| 2.3 | Variables locales inutilisées supprimées (5 fichiers) |
| 2.4 | Code commenté supprimé (3 fichiers) |

#### T7.4 — Règles de code appliquées systématiquement

- Injection Spring uniquement par constructeur (`final` fields, jamais `@Autowired` sur champ).
- Logs SLF4J paramétrés : `log.debug("msg {}", val)` — jamais de concaténation.
- Exceptions : toujours propager la cause (`new Ex("msg", cause)`).
- Pas de mots-clés Java restreints (`record`, `var`, `yield`) comme noms de variable.

**Apport**

- Suite complète verte (0 failure) → déblocage des pipelines CI.
- Qualité Sonar améliorée (code mort, NPE, injections).
- Base saine pour les lots Sonar suivants (P3 maintenabilité).

---

### T8 — CI/CD Use Cases

**Motivation**

Les tests use-cases (`@Tag("use-cases")`) étaient exécutés dans la suite complète sans
possibilité de les isoler dans le pipeline CI.

**Ce qui a été fait**

- **Profil Maven `use-cases`** : `mvn test -Puse-cases` exécute uniquement les tests taggés
  `@Tag("use-cases")` (sans Docker requis).
- **Exclusion profil `no-tags`** : le tag `use-cases` est exclu du profil existant pour éviter
  la double exécution.
- **Job GitLab CI** `test_use-cases` dans `.gitlab_test_mvn.yml` : exécution automatique à
  chaque push.

**Apport**

- Feedback rapide (< 2 min) sur la couche use-cases seule.
- Séparation claire des niveaux de test (unit / use-case / intégration Docker).
- 55 tests use-cases : **55/55 ✅**.

---

## Récapitulatif des apports par transformation

| # | Transformation | Apport principal |
|---|---|---|
| T1 | Extraction 46 Use Cases | Testabilité, fin du ServiceContainer, architecture hexagonale |
| T2 | Pipeline Cascade | Import non-bloquant, anti-deadlock, staging résilient, rate limiting |
| T3 | Dashboard workflow | Visibilité temps réel, détection zombies, admin à chaud |
| T4 | Journal de compensation | Résilience crash JVM, nettoyage garanti des tables orphelines |
| T5 | Sessions utilisateurs | Visibilité sessions, revocation JWT, traçabilité |
| T6 | Métriques Prometheus | Grafana dashboards, observabilité imports production |
| T7 | Qualité Sonar | Suite verte (0 failure), code mort éliminé, règles d'injection |
| T8 | CI/CD Use Cases | Feedback rapide CI, isolement use-cases, 55 tests verts |

---

## Points d'attention pour un portage vers une autre branche

1. **Base de données** : T2 (staging tables), T3 (`oa_audit.workflow_log`), T4
   (`oa_audit.compensation_log`), T5 (`oa_audit.user_session_log`) nécessitent des migrations
   Flyway. Vérifier que les scripts de migration sont présents et compatibles avec la branche cible.

2. **Configuration** : T2 nécessite les propriétés `cascade.import.*` dans
   `application.yml` / `application.properties`. Sans elles, les valeurs par défaut s'appliquent
   mais le comportement peut différer (ex. `sinkStrategy=MERGE_FILE` par défaut).

3. **Dépendances Maven** : T2 dépend de la bibliothèque `cascade` (groupId `fr.inrae.ore`). S'assurer
   que la version 3.0.0+ est disponible dans le dépôt Maven du projet (`pom.xml`).

4. **Tests** : le `OreSiResourcesTest` est l'indicateur principal de régression. Un seul test en
   échec dans cette classe signale généralement un problème dans le flux `uploadBundle` /
   `createData` ou dans la configuration Spring MVC asynchrone.

5. **T1 vs branches anciennes** : si la branche cible contient encore le `ServiceContainer`, la
   fusion T1 produira de nombreux conflits dans `OreSiResources.java`. Résoudre conflit par conflit
   en privilégiant toujours l'injection directe du Use Case.

---

## Proposition de récupération depuis `Refactoring_deposit` vers `develop`

> **Contexte** : La branche `Refactoring_deposit` a été développée en parallèle de `develop`
> (`amelioration_sonar`). Elle contient des correctifs de bugs, des optimisations de performances,
> de nouveaux tests unitaires, une documentation d'architecture et des refactorings de qualité.
> Certains de ses apports **sont déjà présents dans `develop`** (use cases, pipeline Cascade,
> dashboard, compensation-log, sessions, métriques). D'autres **méritent d'être portés**.
>
> Les tableaux ci-dessous classent les travaux par priorité et indiquent le verdict pour chacun.

---

### Inventaire des travaux et verdicts

#### Travaux déjà intégrés dans `develop` — à ne **pas** re-porter

| Thème | Ce qui est dans `Refactoring_deposit` | Raison de l'exclusion |
|---|---|---|
| Use Cases (T1) | Extraction 46 use cases, suppression ServiceContainer | **Déjà dans `develop`** — même structure, même organisation de package |
| Pipeline Cascade (T2) | MERGE_FILE / DIRECT_COPY, TxAwareDeferredRunner, MergeFileDeferredRunner, rate limiter | **Déjà dans `develop`** — fonctionnellement équivalent voire plus avancé |
| Dashboard (T3) | WorkflowActiveRegistry, DashboardController, WorkflowZombieSweeper | **Déjà dans `develop`** |
| Compensation-log (T4) | CompensationLogService, CompensationSweeper | **Déjà dans `develop`** |
| Sessions (T5) | UserSessionRegistry, JwtBlacklistRegistry | **Déjà dans `develop`** |
| Métriques Prometheus (T6) | OpenadomMetrics, timers Micrometer | **Déjà dans `develop`** |

---

#### P0 — Correctifs critiques (à porter immédiatement)

| ID | Thème | Fichiers concernés | Description | Risque de conflit |
|---|---|---|---|---|
| **R-P0-1** | **Fix récursivité Cascade** | `CascadeImportPipeline.java` | `effectiveChunkSizeLines()` et `effectiveParallelism()` : pour un référentiel récursif en mode legacy, force `chunkSizeLines=MAX_VALUE` (tout le fichier en 1 seul chunk) et `parallelism=1`. Sans ce correctif, l'algorithme `missingParentLine` échoue silencieusement quand le parent d'une ligne se trouve dans un autre chunk. **Bug potentiellement présent en production.** | Moyen — bloc `execute()` modifié |
| **R-P0-2** | **Jackson Blackbird** | `pom.xml`, `JsonRowMapper.java` | Remplacement de `jackson-module-afterburner` par `jackson-module-blackbird`. Afterburner utilise des APIs de réflexion bloquées en Java 17+ et génère une `InaccessibleObjectException` silencieuse à chaque sérialisation. Blackbird utilise `LambdaMetafactory` (Java 9+) sans `--add-opens`. Gain mesuré : **~39 % de throughput d'import** sur certains profils. | Faible — 1 ligne `pom.xml`, 1 ligne `JsonRowMapper` |
| **R-P0-3** | **Robustesse sécurité (NPE→false)** | `ApplicationPermissionEvaluator.java` | Ajout d'un `try-catch OreSiTechnicalException` dans `hasPermissionForSystem()` et `hasPermissionForApplication()` : si le domaine lève une exception d'autorisation, retourne `false` au lieu de propager en HTTP 500. | Faible — ajout try-catch localisé |

---

#### P1 — Fonctionnalités — à porter dans un premier lot

| ID | Thème | Fichiers concernés | Description | Risque de conflit |
|---|---|---|---|---|
| **R-P1-1** | **Mode récursion ordonnée (`__ORDER_STRICT__`)** | `Tag.java` (nouveau `OrderStrictTag`), `AsynchroneFileImporterContext.java` (méthode `isOrderStrictTaggedOnRecursiveValidation()`), `DataImporter.java`, `CascadeImportPipeline.java` (appel `effectiveChunkSizeLines(props, isRecursive, isStrictOrdered)`), `ImportProperties.java` (champ `orderedRecursionMode`, propriété `cascade.import.ordered-recursion-mode`), `RecursionStrategy.java` (méthode `isOrderedMode()`), `recusivite-strict.yaml` (nouveau exemple YAML) | Activation du mode récursion ordonnée par tag YAML `__ORDER_STRICT__` (en plus de la propriété Spring globale `cascade.import.ordered-recursion-mode=false`). En mode strict-ordonné, la chunk size reste normale (pas MAX_VALUE), ce qui permet le chunking et le parallélisme même sur un référentiel récursif — à condition que le fichier source ait les **parents avant les enfants**. La propriété Spring est le fallback global ; le tag YAML est l'activation par datatype. **Non intégré sur `amelioration_sonar`** : `Tag.java` ne connaît pas `OrderStrictTag`, `ImportProperties` n'a pas le champ `orderedRecursionMode`, `CascadeImportPipeline` n'appelle que la surcharge 2-params. | Moyen — modifications Tag.java + CascadeImportPipeline + AsynchroneFileImporterContext + RecursionStrategy |
| **R-P1-2** | **Sécurisation migration** | `MigrationProperties.java` (nouveau), `MigrationService.java`, `ApplicationService.java`, `application.properties` | Paramètre `openadom.migration.bypass-configuration-check` (défaut `true` = comportement actuel inchangé). En mode sécurisé (`false`), les changements incompatibles avec le schéma bloquent la mise à jour YAML et retournent `REQUIRES_CONFIRMATION` / `FAILED` plutôt que de passer silencieusement. Aucun impact à l'activation (bypass=true par défaut). | Faible — nouveau fichier + injection dans constructeurs existants |
| **R-P1-3** | **Endpoint `/file/{id}/info`** | `FileResources.java` | Branchement de `GetFileUseCase` sur `GET /api/v1/applications/{name}/file/{id}/info` : retourne les métadonnées (id, nom, commentaire, taille, params, fichiers référencés) sans télécharger le binaire. `GetFileUseCase` était créé depuis le commit `f5f8b270` mais n'était branché sur aucun endpoint (orphelin). | Faible — ajout endpoint dans FileResources |
| **R-P1-4** | **Barre de progression ASCII dans les logs** | `ImportProgressReporter.java`, `LoggingImportProgressReporter.java`, `AsynchroneFileImporterContext.java`, `DataImporter.java` | Comptage des lignes totales pendant la 1ère passe CSV (`prepareContextForDataTreatment`), puis affichage `[████████░░░░] 65% (1300 / 2000) Δ+100` pendant l'import. Pas d'I/O supplémentaire (le CSV est déjà lu lors de la préparation). | Faible — ajout dans les interfaces existantes |

---

#### P2 — Performances import CSV — à porter en lot "perf"

Ces optimisations forment un ensemble cohérent portant sur la validation des colonnes référence
(`OA_refs`) et les expressions Groovy. Elles agissent sur les imports avec de nombreuses lignes
répétant les mêmes valeurs (cas typiques : SWC/ACBB 16 colonnes de référence, référentiels volumineux).

> ⚠️ **État partiel sur `amelioration_sonar`** : les propriétés de configuration
> `cascade.import.reference-cache-max-entries` et `cascade.import.groovy-cache-max-entries`
> ont été **pré-provisionnées** dans `application.properties` (avec leurs valeurs par défaut
> `5000` et `1000`), **mais le code Java correspondant n'est pas encore porté** :
> - `ImportProperties.java` ne déclare pas les champs `referenceCacheMaxEntries` / `groovyCacheMaxEntries`
> - `ReferenceType.java` ne contient ni le cache `precomputedResults` ni le `seenOnce`
> - `DataValidator.java` ne contient pas le `groovyTransformationCache`
>
> Spring ignore silencieusement ces propriétés (aucune erreur de binding car les setters
> n'existent pas). Elles n'ont **aucun effet** tant que R-P2-2 et R-P2-4 ne sont pas portés.

| ID | Thème | Fichiers concernés | Description | Gain attendu |
|---|---|---|---|---|
| **R-P2-1** | **Index O(1) dans `ReferenceType`** | `ReferenceType.java` | Remplacement du `stream().filter()` O(n) dans `check()` par un `HashMap<Ltree, LineIdentityColumnName>` (`naturalKeyIndex`) construit une seule fois dans `setReferenceValues()`. | Recherche O(1) au lieu de O(n) |
| **R-P2-2** | **Cache lazy partagé entre copies Cascade** | `ReferenceType.java` | `seenOnce` + `precomputedResults` (`ConcurrentHashMap`) partagés entre l'instance originale et **toutes ses copies** Cascade (workers parallèles). 1ère occurrence calcul normal, 2ème occurrence → mise en cache. `setReferenceValues()` invalide les deux structures. Plafond configurable via `cascade.import.reference-cache-max-entries` (déjà dans `application.properties`, champ `referenceCacheMaxEntries` à ajouter dans `ImportProperties.java`). | Évite les recalculs entre workers parallèles |
| **R-P2-3** | **Pré-warmer sélectif par fréquence** | `DataImporter.java` | Pendant `prepareContextForDataTreatment()`, comptage des occurrences de chaque valeur par colonne référence. Toute valeur vue ≥ `PRECOMPUTE_CACHE_THRESHOLD=2` fois est pré-calculée et injectée dans `precomputedResults` avant le démarrage des workers. | Élimine les cache manqués pour les valeurs fréquentes |
| **R-P2-4** | **Cache Groovy avec analyse AST** | `DataValidator.java`, `GroovyCacheKey.java` (nouveau), `GroovyAstAnalyzer.java` (nouveau), `GroovyExpressionAnalysis.java` (nouveau), `ImportProperties.java` (2 nouveaux champs : `referenceCacheMaxEntries`, `groovyCacheMaxEntries`) | Mémoïsation des résultats de transformation Groovy via `GroovyCacheKey` (clé composite : expression + valeurs des colonnes d'entrée). `GroovyAstAnalyzer` analyse l'AST pour détecter si l'expression utilise `currentRowNumber` (disqualifie la mise en cache partielle). Plafond configurable via `cascade.import.groovy-cache-max-entries=1000` (déjà dans `application.properties`, champ `groovyCacheMaxEntries` à ajouter dans `ImportProperties.java`). | Élimine les exécutions Groovy redondantes pour les transformations sur colonnes fixes |
| **R-P2-5** | **Micro-optimisations Groovy/Script** | `ScriptConstantProvider.java`, `GroovyExpression.java` | `ScriptConstantProvider` : tableau statique `PROVIDERS[]` → **supprime ~21 000 instanciations par réflexion** pour 100 lignes SWC. `GroovyExpression.evaluate()` : `new SimpleBindings(new HashMap<>(context))` au lieu de `putAll` → élimine une allocation `HashMap` + `putAll` par appel (~4 800+ appels pour un import SWC). | Allocation mémoire réduite |

> **Précondition pour P2** : R-P0-1 doit être porté avant, car le pré-warmer et le cache partagé
> supposent une exécution correcte du chunking récursif.

---

#### P3 — Qualité et refactorings — à porter en lot "qualité"

| ID | Thème | Fichiers concernés | Description |
|---|---|---|---|
| **R-P3-1** | **Refactoring arbre hiérarchique (TDD)** | `Node.java`, `HierarchicalDependancesBuilder.java`, `RootBuilder.java`, `ReferenceGraphBuilder.java` (nouveau) | 7 étapes TDD corrigeant : (1) `Node.compareTo` non-antisymétrie, (2) dead code `addRecursivlyDepends`/`collectDependencies` supprimé dans `HierarchicalDependancesBuilder`, (3) `Node.buildNodesRecursively` immuabilité corrigée (plus de mutation des `TreeSet` existants), (5) `RootBuilder` remplace `boolean hasErrors` par `List<ValidationParams>`, (6) `ReferenceGraphBuilder` extrait et testé séparément. Tests : `NodeBuildTest`, `NodeComparatorTest`, `HierarchicalDependancesBuilderTest`, `ReferenceGraphBuilderTest`, `NodeOrderingTest`, `RootBuilderErrorTest` (39+ tests). |
| **R-P3-2** | **95+ nouveaux tests unitaires purs** | `src/test/java/fr/inra/oresing/…` (50+ fichiers) | Tests sans Docker couvrant : domain (exceptions, enums, chart, rightsrequest, additionalfiles, data, groovy), migration (module, actions, executor, plan, rules, changes), sécurité (PrivilegeAssessor, AuthorizationFilter, JWTExtractor, ApplicationPermissionEvaluator), persistence légère (SqlPrimitiveType, FilterList), dashboard DTOs, reactive types, DataRequestBuilder, ImportRateLimiter, WorkflowTempCleanup, ImportProperties, OpenadomMetrics, ImportProgressReporter. Couverture estimée après portage : **76 → 80 %+**. |
| **R-P3-3** | **Auto-calibrage parallélisme** | `AbstractIntegrationTest.java`, `ImportProperties.java`, `AsyncExecutorConfiguration.java` | `@DynamicPropertySource` calcule `testParallelism = clamp(availableProcessors, 2, 10)` au démarrage du contexte Spring. Positionne `cascade.import.parallelism` et `cascade.pool.*` dynamiquement. Élimine la valeur codée en dur `parallelism=2` et adapte les tests à la machine (CI 2 cores → 2 workers, dev 8 threads → 8 workers). |
| **R-P3-4** | **Suppression `Client.java`** | `src/main/java/fr/inra/oresing/client/Client.java` | Suppression de 362 lignes de code mort (classe client REST interne jamais utilisée). Réduit la base de code et améliore Sonar. |
| **R-P3-5** | **`sonar-project.properties`** | `src/main/resources/sonar-project.properties` | Fichier de configuration Sonar local (branches analysées, exclusions). Permet `mvn sonar:sonar` local sur toutes les branches. |

---

#### P4 — Documentation — à porter facilement (copy-only)

| Fichier | Contenu |
|---|---|
| `documentations/features/ARCHITECTURE_DEPOT_FICHIER.md` | Filière d'import complète bout en bout : configuration YAML → phases de validation → parallélisme Cascade → cas récursif |
| `documentations/features/ARCHITECTURE_LECTURE_CONFIGURATION.md` | Chaîne YAML → Configuration → JSONB PostgreSQL. Remplace `REFACTORING_ARBRE_HIERARCHIQUE.md`. Recettes concrètes pour ajouter une section YAML, un tag, un checker. |
| `documentations/features/ERREURS_DEPOT_FICHIER.md` | Catalogue exhaustif des messages d'erreur produits par l'import (types, paramètres, exemples JSON, conditions de déclenchement) |
| `documentations/features/MODE_RECURSION_ORDONNEE.md` | Documentation du tag `__ORDER_STRICT__` : tableau comparatif des modes (chunk size / parallélisme), activation par YAML ou par propriété Spring |
| `documentations/features/PERF_IMPORT_REFERENCE_PRECOMPUTATION.md` | Spécification des 5 niveaux de cache référence : architecture, axes, thread-safety, cas d'usage SWC/ACBB |
| `documentations/features/REORGANISATION_USE_CASES.md` | Historique et anomalies de la réorganisation use cases (dont GetFileUseCase orphelin, résolu par R-P1-3) |
| `documentations/features/TESTCONTAINERS_CONTAINER_MIGRATION.md` | Migration optionnelle `@BeforeAll → @Container` (Ryuk) : analyse coût/bénéfice |
| `.github/copilot-instructions.md` | Instructions Copilot pour le projet (contexte architecture, règles de code) |

---

### Synthèse — ordre de portage recommandé

```
Lot 1 — Correctifs immédiats (R-P0-1, R-P0-2, R-P0-3)
  → Pas de risque de régression, impact en production
  → Durée estimée : 0,5 jour
  → État : R-P0-1 partiellement porté (surcharge 2-params effectiveChunkSizeLines ✅,
    surcharge 3-params + effectiveParallelism ⏳), R-P0-2 ✅, R-P0-3 ✅

Lot 2 — Fonctionnalités (R-P1-1, R-P1-2, R-P1-3, R-P1-4)
  → R-P1-1 : mode récursion ordonnée — Tag.java (OrderStrictTag) ✅, AsynchroneFileImporterContext (isOrderStrictTaggedOnRecursiveValidation) ✅, DataImporter ✅, CascadeImportPipeline (isStrictOrdered + 3-args) ✅
  → R-P1-2 : sécurisation migration ✅ (MigrationProperties.java créé, ApplicationService injecté, application.properties mis à jour)
  → R-P1-3 : endpoint info ✅ (GET /api/v1/applications/{name}/file/{id}/info branché)
  → R-P1-4 : barre de progression ASCII ✅ (ImportProgressReporter.onTotalLinesKnown, LoggingImportProgressReporter barre ASCII, CascadeImportPipeline.buildRegistryAwareReporter forwarde onTotalLinesKnown)
  → Tests P1 ✅ : TagTest +5 (OrderStrictTag), MigrationPropertiesTest +4, LoggingImportProgressReporterTest +10
  → CI sonar ✅ : image maven:3.9.11 déplacée dans le job sonarqube-check (corrige exit 127)
  → .env-default ✅ : cascade.import.*, openadom.migration.*, HikariCP, executors documentés
  → .env ✅ : tuning 8 cœurs / 31 Go (CASCADE_IMPORT_PARALLELISM=6, pool=25, executors calibrés)
  → État : **P1 COMPLET** — 211/211 core.config ✅, 104/104 domain.model ✅ — compilation sans erreur ✅

Lot 3 — Performances import (R-P2-1 à R-P2-5) — après Lot 1
  → Porter en bloc (les caches sont interdépendants)
  → Les propriétés cascade.import.reference-cache-max-entries et
    cascade.import.groovy-cache-max-entries sont déjà dans application.properties
    mais sans effet (champs ImportProperties absents, caches non implémentés)
  → Durée estimée : 1,5 jour

Lot 4 — Qualité / Tests (R-P3-1 à R-P3-5)
  → Peut être fait sur plusieurs sprints
  → Durée estimée : 2 jours

Lot 5 — Documentation (P4)
  → Copy-only, aucun risque
  → Durée estimée : 0,5 jour
```

### Travaux de `Refactoring_deposit` devenus **obsolètes** dans `develop`

Les travaux suivants n'ont **pas** à être portés car leur équivalent est déjà présent dans
`develop` (parfois dans une version plus avancée) :

| Thème | `Refactoring_deposit` | `develop` (amelioration_sonar) |
|---|---|---|
| Extraction use cases | 46 use cases, injection directe | **Identique** — déjà migré |
| Pipeline import Cascade | CascadeImportPipeline MERGE_FILE / DIRECT_COPY | **Identique et plus avancé** — anti-deadlock intégré |
| Dashboard workflows | WorkflowActiveRegistry, DashboardController | **Déjà dans develop** |
| Journal de compensation | CompensationLogService | **Déjà dans develop** |
| Sessions utilisateurs | UserSessionRegistry, JwtBlacklistRegistry | **Déjà dans develop** |
| Métriques Prometheus | OpenadomMetrics, Micrometer | **Déjà dans develop** |
| Rate limiting import/extraction | ImportRateLimiter, ExtractionRateLimiter | **Déjà dans develop** |

---

*Document mis à jour le 2026-05-05 — section portage `Refactoring_deposit` ajoutée.*