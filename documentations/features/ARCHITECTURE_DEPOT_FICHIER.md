# Architecture — Filière de dépôt de fichier

Ce document décrit la chaîne complète de traitement lors du dépôt d'un fichier de données
ou de référentiel : de la requête HTTP jusqu'à la persistance en base.

---

## Vue d'ensemble

```
Client HTTP
    │ POST /api/v1/applications/{name}/data/{datatype}
    ▼
OreSiResources  (contrôleur REST pur)
    │ délègue à
    ▼
CreateDataUseCase / WriteUploadBundleUseCase
    │ appelle
    ▼
VersioningService.createData()
    │ @Transactional
    ▼
DataService.addData()
    │
    ├── 1. Validation des droits (ApplicationPermissionEvaluator)
    ├── 2. Rate limiting (ImportRateLimiter — HTTP 429 si quota dépassé)
    └── 3. CascadeImportPipeline.execute()
              │
              ├── Phase A : Préparation contexte (DataImporter.prepareContextForDataTreatment)
              │     ├── Lecture des en-têtes CSV
              │     ├── Construction des checkers (ReferenceType, GroovyChecker, …)
              │     ├── Pré-warmer sélectif ReferenceType (R-P2-3)
              │     └── Initialisation du cache ReferenceType (R-P2-1/R-P2-2)
              │
              ├── Phase B : Découpage CSV en chunks
              │     ├── chunkSizeLines = effectiveChunkSizeLines(props, isRecursive, isStrictOrdered)
              │     └── Écriture des chunks dans /tmp/openadom-import/chunks/
              │
              ├── Phase C : Transformation parallèle (pool « transform »)
              │     └── Pour chaque chunk → DataImporter.doDataTreatment()
              │           ├── Pour chaque ligne CSV :
              │           │     ├── DataTransformer.computeComputedColumns() : expressions Groovy
              │           │     ├── DataValidator.check()  : checkers de validation
              │           │     └── Accumulation des erreurs (ReactiveProgression.pushError)
              │           └── Sérialisation en JSON (JsonRowMapper)
              │
              ├── Phase D : Sink (écriture en base)
              │     ├── MERGE_FILE : chunks → merged.csv → COPY PostgreSQL via storeAll()
              │     └── DIRECT_COPY : UPSERT chunk par chunk via staging table
              │
              └── Phase E : Finalisation
                    ├── InvalidDatasetContentException si errors > maxErrorsThreshold
                    ├── WorkflowLogWriter.markCompleted() / markFailed()
                    └── CompensationLogService.confirm() (nettoyage staging)
```

---

## Phase A — Préparation du contexte (`prepareContextForDataTreatment`)

```
DataImporter.prepareContextForDataTreatment(tempFile)
  │
  ├── Lecture ligne d'en-tête → publishContextBuilder.headerRow
  ├── Validation en-têtes vs schéma YAML
  │     └── InvalidDatasetContentException si colonne obligatoire manquante
  ├── Construction AsynchroneFileImporterContext
  │     ├── transformedLineCheckers()  : checkers de transformation
  │     └── validatedLineCheckers()   : checkers de validation
  │           ├── ReferenceType   (lookup référentiel)
  │           ├── IntegerType / FloatType / DateType  (types primitifs)
  │           ├── GroovyChecker   (règles d'expression)
  │           └── StringType      (regex)
  │
  ├── [R-P2-1] buildNaturalKeyIndex() dans chaque ReferenceType
  ├── [R-P2-2] Initialisation seenOnce + precomputedResults (partagés entre copies)
  └── [R-P2-3] prewarmReferenceCache(tempFile, refTypeByColumnName)
```

### Détection du mode récursif

```
isRecursive = AsynchroneFileImporterContext.isRecursive()
  → true si le schéma YAML déclare au moins un checker Reference avec recursive: true

isStrictOrdered = isOrderStrictTaggedOnRecursiveValidation()
                  || importProperties.isOrderedRecursionMode()
```

---

## Phase B — Chunking (découpage CSV)

Le pipeline Cascade lit le fichier temporaire et le découpe en tranches de `chunkSizeLines`
lignes. Chaque chunk est écrit dans un fichier séparé sous `chunksTempDir`.

| Mode              | `chunkSizeLines`      | `parallelism` |
|-------------------|-----------------------|---------------|
| Non récursif      | configuré (déf. 1000) | configuré (déf. 4) |
| Récursif legacy   | `Integer.MAX_VALUE`   | 1             |
| Récursif ordonné  | configuré (déf. 1000) | 1             |

---

## Phase C — Transformation (`DataImporter.doDataTreatment`)

Pour chaque ligne de chaque chunk (dans le pool `transform`) :

```
1. Lecture CSV → RowWithReferenceDatum
2. DataTransformer.computeComputedColumns()
   └── Pour chaque composant calculé déclaré dans YAML :
         → Groovy transformation expression (GroovyExpression.evaluate)
         → [R-P2-4] Cache per-expression si !context.containsKey("currentRow")
             ⚠️  "currentRow" est présent dans TOUS les contextes d'import :
             le cache Groovy est actif uniquement hors contexte d'import
             (appels unitaires, tests). Pour les imports, bypass systématique.
3. DataValidator.check()
   └── Pour chaque checker :
         ├── ReferenceType.check()    → [R-P2-1/R-P2-2] index O(1) + cache
         ├── IntegerType.check()      → parsing + intervalle
         ├── FloatType.check()        → parsing + intervalle
         ├── DateType.check()         → format + intervalle
         ├── GroovyChecker.check()    → évaluation expression booléenne
         └── StringType.check()       → regex
4. Si erreur → ReactiveProgression.pushError(ValidationError)
   (sérialisée dans le flux SSE + accumulée dans allErrors)
5. Si succès → DataTransformer.computeKeys()
             → AsynchroneFileImporterContext.getKnownId()
               [R-P2-1b] NaturalKeyPattern O(1) via naturalKeyPatternIndex
             → sérialisation JsonRowMapper → ligne JSON
```

### Cas récursif

En mode récursif, les lignes dont le parent n'est pas encore connu sont collectées dans
`missingParentLine` pour un second passage. `DataImporter.treatErrors()` produit en fin
de traitement l'erreur synthèse `missingrecursiveParentReference` si des parents manquent toujours.

---

## Phase D — Sink (persistance)

### Stratégie `MERGE_FILE` (legacy / défaut)

```
chunks transformés → MergeFileDeferredRunner
  → Consolidation en merged.csv
  → DataService.storeAll(merged.csv)
      → COPY PostgreSQL (bulk insert)
  [Si @Transactional externe : exécuté en afterCommit]
```

### Stratégie `DIRECT_COPY` (production)

```
chunks transformés → TxAwareDeferredRunner
  → UPSERT batch dans table staging (SHARED_UNLOGGED / PER_WORKFLOW_TABLE)
  → FinalizeHook : SELECT … INSERT INTO table_finale FROM staging
  [Si @Transactional externe : exécuté en afterCommit]
  → CompensationLogService.confirm() : suppression de la row PENDING
```

---

## Gestion des erreurs

### Erreurs de validation (ligne par ligne)

Chaque erreur est propagée via `ReactiveProgression.pushError()` :
- **Stream SSE** (`REACTIVE_ERROR`) : notification temps réel au client
- **Accumulation** dans `allErrors` pour le rapport final

Si `allErrors.size() > maxErrorsThreshold` : le workflow est avorté et
`InvalidDatasetContentException` est levée avec la liste complète des erreurs.

### Erreurs techniques

| Erreur | Comportement |
|--------|-------------|
| `IOException` lors du chunking | Workflow avorté, `WorkflowLogWriter.markFailed()` |
| `nullLabel` (Ltree invalide) dans pré-warmer | Ignoré (non fatal, simple miss de cache) |
| `NullPointerException` dans DataTransformer | Propagé comme `OreSiTechnicalException` |
| Rollback Spring `@Transactional` | `afterCompletion` → cleanup staging + `markFailed()` |

---

## Anti-deadlock (`TxAwareDeferredRunner` / `MergeFileDeferredRunner`)

Si `DataService.addData()` est appelé dans une transaction Spring active (`VersioningService`),
le thread sink de Cascade ne peut pas effectuer un `UPSERT` pendant que le thread HTTP
tient des row-locks → **deadlock**.

Solution : le sink est configuré en mode `DEFERRED_TO_CALLER`. L'opération finale est
enregistrée comme `TransactionSynchronization.afterCommit` et s'exécute **après** que tous
les row-locks Spring ont été libérés.

---

## Progression en temps réel

Pendant l'import, le dashboard `oa-live` consomme two flux :

1. **`WorkflowActiveRegistry`** : registry in-memory mise à jour à chaque chunk
   (records traités, chunks, records/s, ETA)
2. **`ReactiveProgression` SSE** : notifications push ligne par ligne (erreurs, pourcentage)

```http
GET /api/v1/applications/{name}/data/{datatype}/upload
Accept: text/event-stream

data: {"type":"REACTIVE_PROGRESS","result":0.42}
data: {"type":"REACTIVE_INFO","result":"import.processing.line","params":{"lineNumber":420}}
data: {"type":"REACTIVE_ERROR","errorType":"InvalidDatasetContentException","result":{...}}
data: {"type":"REACTIVE_RESULT","result":"<uuid-workflow>"}
```

---

## Fichiers sources clés

| Fichier | Rôle |
|---------|------|
| `OreSiResources.java` | Contrôleur REST — délégation au Use Case |
| `CreateDataUseCase.java` | Use Case dépôt de données |
| `DataService.java` | Service métier — orchestration @Transactional |
| `CascadeImportPipeline.java` | Pipeline Cascade — chunking, transform, sink |
| `DataImporter.java` | Transformation + validation d'une tranche CSV |
| `DataValidator.java` | Exécution des checkers de validation |
| `DataTransformer.java` | Évaluation des expressions Groovy (computeComputedColumns, computeKeys) |
| `AsynchroneFileImporterContext.java` | Contexte d'import — naturalKeyPatternIndex (getKnownId O(1)) |
| `ReferenceType.java` | Validation colonne référence (O(1) + cache) |
| `GroovyExpression.java` | Cache per-expression Groovy |
| `TxAwareDeferredRunner.java` | Anti-deadlock DIRECT_COPY |
| `MergeFileDeferredRunner.java` | Anti-deadlock MERGE_FILE |
| `ReactiveProgression.java` | Flux SSE de progression |
| `WorkflowActiveRegistry.java` | Registry in-memory dashboard |

---

## Optimisations de performance apportées après P2 (develop 2026-05-06)

### Index O(1) sur `getKnownId` via `NaturalKeyPattern` (commit `4f2dd688`)

`getKnownId()` dans `AsynchroneFileImporterContext` effectuait un scan O(N) sur
`afterPreloadReferenceUuids` à chaque appel (callers : `DataTransformer.toEntity`,
`WithRecursion`, `DataValidator`). Correction symétrique au `naturalKeyIndex` de
`ReferenceType` :

- Nouveau record `NaturalKeyPattern(naturalKey, patternColumnName)`.
- `naturalKeyPatternIndex` (`ConcurrentHashMap<NaturalKeyPattern, UUID>`) pré-rempli
  dans `of(...)` à partir de `storedReferences`.
- `putAfterPreload(key, uuid)` maintient les deux maps cohérentes (`WithRecursion`
  utilise ce wrapper au lieu de `.put` direct).
- Fallback scan O(N) défensif si miss (workflows antérieurs).

### Capacité queue pipeline configurable (commit `d509efd8`)

`ImportProperties.pipelineQueueCapacity` (volatile, défaut 50) permet d'éviter
le backpressure inutile quand le pool transform est bien plus lent que le sink.

```properties
cascade.import.pipeline-queue-capacity=${CASCADE_IMPORT_PIPELINE_QUEUE_CAPACITY:50}
```

### Timeout HTTP streaming configurable (commit `6c98698d`)

`OreSiNg.configureAsyncSupport` pilote désormais son timeout via la propriété
`openadom.http.streaming.timeout` (type `Duration`, défaut **6 h**) pour éviter
les téléchargements ZIP/CSV tronqués sur les gros volumes.

```properties
openadom.http.streaming.timeout=${OPENADOM_HTTP_STREAMING_TIMEOUT:6h}
```

### Skip MigrateService sur indexes déjà OK (commit `b043a111`)

`MigrateService.updateAuthorizationIndexes` comparait la liste attendue avec
`pg_indexes` avant de faire un DROP+CREATE. Si les sets sont égaux, le rebuild
coûteux (~8 min sur si_acbb) est totalement sauté — bénéfice sur 99 % des boots.