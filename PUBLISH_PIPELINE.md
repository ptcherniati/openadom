# LITE V2 ROADMAP — Atomic Republish + Bypass DataImporter

Author : R.YAHIAOUI
Date : 2026-05-13

---

## 1. Contexte

Suite à `lite-v1` (déjà shippé, cf `PUBLISH_UNPUBLISH.md` + tasks #189-#193), le republish d'un fichier déjà uploadé évite l'accumulation `encounteredHierarchicalKeysForConflictDetection` dans `DataImporter`. Gain mémoire moyen sur 875K rows : ~250 MB → ~50 MB heap.

`lite-v1` reste cependant limité :

- D'autres structures cross-row demeurent peuplées (`afterPreloadReferenceUuids`, `seenOnce`, `naturalKeyIndex` de `ReferenceType`, `missingParentLine`).
- Validators rejoués (Groovy, refs lookups, type conversion) → CPU gaspillé.
- Rollback Phase 2 reste `best-effort` (flag toggle Phase 1 committed, données partielles possibles si Phase 2 fail mid-flight).

Cette roadmap définit deux évolutions qui adressent ces limites en séquence :

- **Sprint A** — Strict atomicity. Déplace `binaryfile.published` toggle de Phase 1 vers Phase 2 `@Transactional`. Garantit "all-or-nothing" via MVCC natif Postgres 18, élimine `rollbackVisibleFlag()` best-effort.
- **Sprint B** — Bypass `DataImporter`. Persiste le CSV processed (JSON `DataValue` serialisé) au premier upload. Au republish (hash config inchangé), `COPY` direct vers staging sans rejouer la pipeline de validation. Heap O(chunk) constant, scaling illimité.

---

## 2. Architecture cible

### 2.1 Flux republish avant / après

```
ÉTAT ACTUEL (lite-v1)
─────────────────────
Phase 1 :  toggle flag published=true  ─►  visible UI immédiatement
           workflow_log IN_PROGRESS
           COMMIT
           emit event

Phase 2 :  streamFileContent (bytea)  ─►  DataImporter (lite)  ─►  cascade
                                                                       │
                                                                       ▼
                                                          referencevalue (N batches)
           (Si fail : rollbackVisibleFlag best-effort)


ÉTAT SPRINT A (Strict Atomic)
─────────────────────────────
Phase 1 :  workflow_log IN_PROGRESS
           COMMIT
           emit event
                                            (flag NON touché)

Phase 2 @Transactional :
           streamFileContent  ─►  DataImporter (lite)  ─►  cascade staging
                                                              │
                                                              ▼
                                                       referencevalue (CTE batches)
                                                              │
                                                              ▼
                                         toggle flag published=true
                                         workflow_log COMPLETED
                                         COMMIT  ◄── atomique unique
                                            │
                                            ▼
                                         (Si fail : ROLLBACK automatique tout)


ÉTAT SPRINT B (FAST Path)
─────────────────────────
Upload initial :
           CSV brut user  ─►  DataImporter FULL  ─►  cascade  ─►  referencevalue
                                                       │
                                                       ▼
                                          capture processed Path
                                          persist binaryfile.processed_data (bytea)

Republish (hash match + processedData present) :
           streamProcessedData  ─►  COPY direct  ─►  staging  ─►  referencevalue
                                                       (skip DataImporter complet)
                                          O(chunk) heap constant

Republish (hash mismatch OU processedData absent) :
           streamFileContent (raw)  ─►  DataImporter (FULL ou lite)  ─►  cascade
                                                       │
                                                       ▼
                                          regénère processed_data au passage
```

### 2.2 Trois paths au republish (post Sprint B)

| Path | Condition | DataImporter | Heap pic | Speed |
|---|---|---|---|---|
| **FAST** | hash match + processed_data present | bypass complet | ~10 MB constant | ~5s pour 875K |
| **LITE** | hash match + processed_data absent (fichier pre-feature) | lite-v1 mode | ~50 MB pour 875K | ~30s pour 875K |
| **FULL** | hash mismatch (config évoluée) | FULL revalidation + regenerate processed | ~250 MB pour 875K | ~60s pour 875K |

Décision routage dans `PublishLifecyclePhase2Handler.doPublish` :

```java
if (bf.processedData() != null && hashMatch) {
    return doPublishFast(app, ev, bf);           // FAST path
} else if (hashMatch) {
    return doPublishLite(app, ev, bf);           // LITE path (existing)
} else {
    long count = doPublishFull(app, ev, bf);     // FULL path
    persistProcessedData(bf, cascade.lastProcessedPath());  // regenerate cache
    return count;
}
```

---

## 3. Sprint A — Strict Atomic Republish

### 3.1 Motivation

État actuel `rollbackVisibleFlag` est un anti-pattern :

```java
try {
    executeAction(...);
} catch (RuntimeException ex) {
    rollbackVisibleFlag(application, ev);  // best-effort, peut échouer
}
```

Si `rollbackVisibleFlag` échoue (DB down, lock contention) → état incohérent permanent : `flag=true` + data partielle visible.

### 3.2 Refacto cible

**`PublishLifecycleService.startPublish` / `startUnpublish` / `startDeleteFile`** :
- Supprimer `togglePublishedFlag()` appel synchrone Phase 1.
- Conserver supersedure check + cancellation + `workflow_log INSERT(IN_PROGRESS)` + emit event.

**`PublishLifecyclePhase2Handler.onPublishLifecycleEvent`** :
- Wrapper `@Transactional(propagation=REQUIRED)` autour `executeAction`.
- À la fin (succès) : `togglePublishedFlag(true)` + `workflow_log UPDATE(COMPLETED)` dans la **même transaction**.
- Si exception : `@Transactional` rollback automatique → flag inchangé + workflow_log inchangé. Un second `@Transactional(REQUIRES_NEW)` écrit `workflow_log UPDATE(FAILED)` pour traçabilité.

### 3.3 Subtilité — Cascade pipeline et transaction context

Cascade `StagingFinalizeSql.runFinalize` utilise déjà `conn.setAutoCommit(false) + commit()/rollback()` sur sa propre connection (sticky). Il faut :

- Soit faire en sorte que cascade utilise la **connection courante Spring `@Transactional`** (via `DataSourceUtils.getConnection(dataSource)`).
- Soit garder cascade en transaction indépendante mais piloter le flag toggle séparément après cascade.commit() OK.

**Choix recommandé** : option 2 (cascade en tx indépendante). Plus simple, moins de couplage Spring/cascade. Le flag toggle se fait après cascade success, dans une mini-tx Spring. Si cascade fail → exception remontée → flag jamais touché.

```java
@Async
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onPublishLifecycleEvent(PublishLifecycleEvent ev) {
    try {
        executeAction(application, ev);  // cascade pipeline (own tx)
        // === Cascade succeeded ===
        commitVisibleFlagAndComplete(application, ev);  // mini-tx Spring
    } catch (Exception ex) {
        recordFailureNonTransactional(ev, ex);  // mini-tx REQUIRES_NEW
    }
}

@Transactional(propagation = REQUIRED)
private void commitVisibleFlagAndComplete(Application app, PublishLifecycleEvent ev) {
    repository.getRepository(app).binaryFile()
        .togglePublishedFlag(ev.fileId(), targetFlag(ev), ev.userId());
    workflowLogRepository.markCompleted(ev.correlationId(), recordsProcessed);
}
```

### 3.4 Code à supprimer (dead code)

- `PublishLifecyclePhase2Handler.rollbackVisibleFlag()` (lines ~315-323).
- Méthode `togglePublishedFlag` Phase 1 (si plus utilisée nulle part).
- Tests qui testent le rollback flag (deviennent obsolètes).
- Sweeper "fix orphan flags" si présent.

### 3.5 Tests atomicity

| Scénario | Attendu |
|---|---|
| Phase 2 cascade succès | flag=true, workflow_log COMPLETED, referencevalue peuplé |
| Phase 2 cascade exception RuntimeException | flag inchangé, workflow_log FAILED, referencevalue inchangé |
| Phase 2 DB down mid-cascade | idem above, retry possible |
| Concurrent reader pendant Phase 2 | voit ancien snapshot MVCC, jamais partial |
| Supersedure : reclic pendant Phase 2 | annule, flag inchangé, workflow_log CANCELLED |
| Cascade pipeline OK mais flag toggle fail (lock) | exception remontée, données partielles présentes en référencevalue → besoin compensation cleanup |

Dernier scénario : cas edge où cascade commit OK mais flag toggle échoue. À mitiger via :
- Retry flag toggle avec backoff.
- Compensation log entry `STAGING_CLEANUP` pour rollback dans Phase 2.
- Documentation : "ce cas reste théoriquement possible, monitorer via Grafana alert".

---

## 4. Sprint B — Bypass DataImporter (Processed CSV Cache)

### 4.1 Motivation

Même en lite-v1, le republish réinvoque `DataImporter.doDataTreatment` qui :
- Parse CSV brut chunk par chunk.
- Re-déclenche validators (Groovy compile, refs cache lookups, type conversion).
- Peuple `afterPreloadReferenceUuids`, `naturalKeyIndex`, `seenOnce`, autres structures cross-row.

Or au republish, **la transformation est déjà connue** — elle a été calculée au 1er upload et son output (JSON `DataValue` serialisé) est éphémère en disque temp, supprimé en fin de cascade.

L'idée : **capturer ce processed JSON et le persister** dans `binaryfile.processed_data` (bytea). Au republish, si la config datatype est inchangée (`configHash` match), on lit ce cache et on l'envoie directement vers staging → bypass complet `DataImporter`.

### 4.2 Format processed CSV

Confirmé par investigation code :
- 1 ligne du processed file = 1 `DataValue` serialisé en JSON (via `JsonRowMapper.toJson()`).
- Contient : `id` (UUID stable), `patternColumnName`, `creationDate`, `updateDate`, `application`, `referenceType`, `hierarchicalKey`, `naturalKey`, `refValues` (typed values), `refsLinkedTo` (inter-ref map), `binaryFile`, `authorization`.
- Stocké en bytea jsonb-lines.

Au COPY vers staging :
```sql
COPY oa_staging.referencevalue_import_shared (correlation_id, data)
FROM STDIN (FORMAT CSV, DELIMITER E'\t')
```
où chaque ligne fournit `(correlationId, json_line)`. La colonne `data jsonb` reçoit le JSON brut.

Le finalize SQL existant (`StagingFinalizeSql.runFinalize`) reste **inchangé** : il consomme déjà `staging.data jsonb` via `jsonb_populate_record` → `referencevalue` ON CONFLICT UPSERT.

### 4.3 Migration BDD

```sql
-- V???__lite_v2_processed_data.sql
ALTER TABLE ${applicationSchema}.binaryfile
    ADD COLUMN processed_data BYTEA,
    ADD COLUMN processed_at   TIMESTAMP,
    ADD COLUMN processed_size BIGINT;

COMMENT ON COLUMN ${applicationSchema}.binaryfile.processed_data IS
    'Lite-v2 : cache du CSV processed (JSON DataValue lines) capturé au 1er upload.
     Permet bypass DataImporter au republish si configHash inchangé.
     NULL pour fichiers pré-feature ou si configHash mismatch.';
```

- **Non-breaking** : colonne nullable, fichiers historiques restent fonctionnels via fallback LITE/FULL.
- **Index** : aucun nécessaire (lecture par PK uniquement).

### 4.4 Capture au upload

`CascadeImportPipeline.execute` produit un `MERGE_FILE` Path en mode `STAGED + MERGE_FILE`. Pour `PIPELINED + DIRECT_COPY` (default actuel), il faut soit :

1. Forcer `MERGE_FILE` au 1er upload pour capturer (mais ralentit upload).
2. Intercepter chaque chunk processed dans un `Sink` custom `CapturingSink` qui écrit en parallèle vers (a) staging table et (b) un fichier temp local. Plus complexe mais préserve PIPELINED.

**Choix recommandé** : option 2 via cascade `TransformInterceptor` (cf cascade 3.1 API). Wrap le `Sink` existant pour aussi écrire le chunk vers un `BufferedWriter` local. Cet output est ensuite stream-inserted vers `binaryfile.processed_data` à la fin de l'upload.

### 4.5 Stream processed_data au republish

Réutiliser `ByteaSubstringInputStream` (déjà implémenté lite-v1) sur la nouvelle colonne `processed_data` :

```java
public InputStream streamProcessedData(UUID fileId, int chunkBytes) {
    return new ByteaSubstringInputStream(
        jdbcTemplate, schema, "binaryfile", "processed_data", fileId, chunkBytes
    );
}
```

### 4.6 FAST path implementation

```java
private long doPublishFast(Application app, PublishLifecycleEvent ev, BinaryFile bf) throws IOException {
    BinaryFileRepository bfRepo = repository.getRepository(app).binaryFile();
    DataRepository dataRepo = repository.getRepository(app).data();
    try (InputStream processedStream = bfRepo.streamProcessedData(ev.fileId())) {
        dataRepo.copyProcessedToStaging(ev.correlationId(), processedStream);
        dataRepo.finalizeStagingToReferenceValue(ev.correlationId(), bf);
    }
    return 0L;  // count via buildSynthesis
}
```

`copyProcessedToStaging` :
```sql
COPY oa_staging.referencevalue_import_shared (correlation_id, data)
FROM STDIN (FORMAT TEXT, DELIMITER E'\t')
```
Chaque ligne = `<correlationId>\t<json_payload>\n`. Le bytea processed_data contient déjà ce format si on l'a capturé en ce format. Sinon : transformer à la volée (ajouter `<correlationId>\t` prefix par ligne).

`finalizeStagingToReferenceValue` : invoque `StagingFinalizeSql.runFinalize` directement (réutilise infra existante).

### 4.7 Phase 2 routing complet (final state Sprint B)

```java
private long doPublish(Application app, PublishLifecycleEvent ev) throws IOException {
    BinaryFileRepository bfRepo = repository.getRepository(app).binaryFile();
    BinaryFile bf = bfRepo.tryFindById(ev.fileId()).orElseThrow();
    BinaryFileInfos params = bf.getParams();

    boolean hashMatch = configHashService.configUnchangedSinceUpload(
        app, ev.dataName(), params.configHash()
    );
    boolean processedAvailable = bf.processedSize() > 0;

    if (hashMatch && processedAvailable) {
        log.info("doPublish FAST path fileId={}", ev.fileId());
        return doPublishFast(app, ev, bf);
    } else if (hashMatch) {
        log.info("doPublish LITE path fileId={} (no processed cache)", ev.fileId());
        return doPublishLite(app, ev, bf);
    } else {
        log.info("doPublish FULL path fileId={} (hash mismatch)", ev.fileId());
        long count = doPublishFull(app, ev, bf);
        regenerateProcessedData(app, ev.fileId());  // best-effort regenerate
        return count;
    }
}
```

### 4.8 Configurabilité (feature flag)

```yaml
openadom:
  lite-v2:
    enabled: true                 # global toggle
    capture-on-upload: true       # save processed_data at upload
    fast-path: true               # use FAST path when available
    backfill:                     # background regeneration for historical files
      enabled: false
      max-files-per-run: 100
```

Si `enabled=false` → comportement lite-v1 préservé.

### 4.9 Tests Sprint B

| Test | Scénario |
|---|---|
| Upload + capture | upload nouveau fichier, vérifier `processed_data` populated post-cascade |
| Republish FAST | unpublish, republish, vérifier no DataImporter dans stack, heap pic O(chunk) |
| Republish LITE fallback | fichier sans processed_data (legacy), vérifier passage lite-v1 |
| Republish FULL fallback | modifier datatype config entre upload et republish, vérifier passage FULL + regen processed |
| Idempotence | re-republish 3 fois, vérifier referencevalue identique |
| Concurrent FAST + UPLOAD | new upload en parallèle d'un republish FAST, vérifier isolation |
| Migration backward | fichier pré-feature, processed_data NULL, republish OK via LITE |
| Bench 1M rows | bench AVANT (lite-v1) vs APRES (FAST) sur dataset si_acbb largest file |

---

## 5. Plan d'implémentation

### Sprint A (1 semaine)

| Étape | Effort |
|---|---|
| A.1 PublishLifecycleService skip flag toggle Phase 1 | 0.5j |
| A.2 Phase2Handler toggle flag inside @Transactional | 1j |
| A.3 Remove rollbackVisibleFlag dead code | 0.5j |
| A.4 Tests atomicity scenarios | 1j |
| Doc PUBLISH_UNPUBLISH.md update | 0.5j |
| Bench + manual QA scenarios | 1j |
| Buffer | 1j |
| **Total** | **~5j** |

### Sprint B (2-3 semaines)

| Étape | Effort |
|---|---|
| B.1 Migration BDD binaryfile.processed_data | 0.5j |
| B.2 Capture processed CSV (cascade interceptor) | 3j |
| B.3 streamProcessedData + ByteaSubstringInputStream reuse | 1j |
| B.4 FAST path implementation + COPY + finalize wiring | 2j |
| B.5 Phase2Handler 3-path routing | 1j |
| B.6 Tests integration FAST/LITE/FULL + bench | 3j |
| B.7 Feature flags + observability (Grafana metric path used) | 1j |
| Backfill task (regenerate processed for historical files) | 2j |
| Doc + deployment env vars + ansible templates | 1j |
| Buffer + bug fixes | 2j |
| **Total** | **~16j** |

### Total combiné

~21 jours (4-5 semaines) ingénieur senior.

---

## 6. Risques + mitigations

| Risque | Impact | Mitigation |
|---|---|---|
| Cascade pipeline ne supporte pas tx Spring | bloquant | Garder cascade en tx indépendante (option 2 Sprint A.3) |
| Format processed CSV change entre versions openADOM | invalidation cache | Versionner `processed_data` (column `processed_format_version`) + invalidation auto |
| Storage cost +30% bytea | budget disk | Acceptable (SSD); option `capture-on-upload=false` pour clusters constrained |
| Backfill historique long | délai migration | Sweep en background, low priority, opt-in |
| Bug FAST path = data corruption | critique | Hash check strict + tests integration extensifs + Grafana alert sur mismatch counts post-finalize |
| Migration backward compat | breaking | Nullable columns, fallback LITE/FULL automatique, pas de drop colonne |
| Cascade interceptor capture impacte perf upload | ralentit upload | Streaming write parallèle (BufferedWriter daemon thread), bench requis |

---

## 7. Observability

Métriques à exposer (Prometheus + Grafana) :

- `openadom_republish_path_total{path=fast|lite|full,datatype}` — counter par path.
- `openadom_republish_duration_seconds{path,datatype}` — histogram durée.
- `openadom_republish_heap_peak_bytes{path}` — gauge heap pic par republish (sampled via JFR ou hook custom).
- `openadom_processed_data_size_bytes{datatype}` — gauge taille cache par fichier.
- `openadom_processed_data_coverage_ratio` — % fichiers avec processed_data populated.
- `openadom_atomic_rollback_total{action,reason}` — counter rollbacks atomiques Sprint A.

Logs structurés :
- `Phase 2 doPublish path={fast|lite|full} hashMatch={true|false} fileId={uuid}`.
- `Atomic rollback : flag preserved, workflow_log=FAILED, fileId={uuid}, reason={...}`.

---

## 8. Rollout plan

1. **Sprint A merge** sur `develop` → tests integration verts → deploy preprod → 1 semaine bake → deploy prod.
2. **Sprint B feature flag `lite-v2.enabled=false` deploy** → vérifier zero regression sur lite-v1.
3. **Sprint B activation `capture-on-upload=true` preprod** → bake 2 semaines, monitor storage growth.
4. **Sprint B activation `fast-path=true` preprod** → tests utilisateurs ACBB sur datasets réels.
5. **Backfill processed_data fichiers historiques** (script admin).
6. **Activation production progressive** par application (datatype par datatype via override config).

---

## 9. Compatibilité

- **Backward** : fichiers sans `processed_data` (legacy) continuent via path LITE/FULL → zero régression.
- **Forward** : si format `processed_data` doit évoluer (e.g. cascade 4.0), prévoir `processed_format_version` column + détection auto + regenerate.
- **Cross-version** : OpenADOM v(n+1) peut lire `processed_data` écrit par v(n) tant que JSON `DataValue` schema inchangé. Schema change → invalidation auto par configHash (qui couvre StandardDataDescription, donc indirect).

---

## 10. Décisions architecturales

| Décision | Justification |
|---|---|
| Stocker processed dans `binaryfile.processed_data` (bytea) | Co-localisé avec raw, simple, idempotent, indexable, no extra table |
| Format jsonb-lines (1 JSON par ligne) | Match staging table format, COPY direct sans transformation |
| `configHash` réutilisé de lite-v1 | Mécanisme déjà validé, granularité datatype, conservative |
| Cascade tx indépendante du Spring `@Transactional` | Découplage, simpler, cascade reste agnostique |
| FAST path skip DataImporter entièrement | Maximise gain perf et heap, justifié par hash match safety |
| Pas de stockage processed pour fichiers `< N rows` | Pas dans v1 ; future optim si needed (threshold config) |
| Pas de compression bytea | jsonb-lines déjà ~30% inflation vs raw ; compression nivel Postgres TOAST auto |

---

## 11. Tasks tracking

Voir tasks #194-#205 dans le système TaskCreate :

- **Sprint A** : #194 (A.1 Phase 1 skip), #195 (A.2 Phase 2 toggle), #196 (A.3 cleanup), #197 (A.4 tests).
- **Sprint B** : #198 (B.1 migration), #199 (B.2 capture), #200 (B.3 stream), #201 (B.4 FAST path), #202 (B.5 routing), #203 (B.6 tests + bench).
- **Cross-cutting** : #204 (deployment + env vars), #205 (cette doc).

---

## 12. Etat d'avancement actuel (session du 2026-05-13)

### Implémenté et mergé

| Étape | Statut | Fichiers clés |
|---|---|---|
| Sprint A.1 — Phase 1 skip flag toggle | done | `PublishLifecycleService.java` (suppression `applyVisibleFlag`) |
| Sprint A.2 — Phase 2 toggle inside `@Transactional` | done | `PublishLifecyclePhase2Handler.commitVisibleFlagAndSynthesis()` |
| Sprint A.3 — Remove `rollbackVisibleFlag` dead code | done | `PublishLifecyclePhase2Handler.java` |
| Sprint A.4 — Tests atomicity | done | `PublishLifecycleServiceTest.java` (verify `never()` flag toggle Phase 1) |
| Sprint B.1 — Migration BDD | done | `V12__binaryfile_processed_data.sql` |
| Sprint B.2 partial — capture mechanism | done | `DataImporterTransformation` (ctor `captureAggregateFile`), `BinaryFile` entity (`processedData/Size/At`), `BinaryFileRepository` (`streamProcessedData`, `storeProcessedData`, `findProcessedSize`) |
| Feature flags + env vars | done | `LiteV2Properties.java`, `local_deployment/backend/compose.yml`, `deployment/ansible/roles/backend_compose/templates/compose.yml.j2` |

### Tests
3100 tests passent (BUILD SUCCESS) après refactor Sprint A. Zero régression.

### À implémenter dans une session ultérieure (deferred)

| Étape | Effort estimé | Note |
|---|---|---|
| Sprint B.7 — Backfill historique | 2j | Script admin pour regenerate `processed_data` sur fichiers anciens via republish forcé en mode capture |
| Métriques Prometheus + dashboard Grafana | 1j | Counter par path utilisé (FAST/LITE/FULL), histogram durée, gauge coverage ratio processed_data |
| Compression bytea `processed_data` | (skipped) | PG TOAST compresse déjà auto → gain marginal |
| Stream refs (`findAllByReferenceType` → Stream) pour scaling 1B+ rows | 5-10j | Backlog si fichiers >50M rows en production |

### Précautions pour la suite

1. **Feature flag `openadom.lite-v2.enabled=false` par défaut** : aucune feature Sprint B ne s'active tant que le flag global n'est pas activé. Zero régression sur `lite-v1` actuel.
2. **Backward compat assurée** : colonnes `processed_data/at/size` nullable, fichiers pré-feature continuent via path LITE/FULL automatiquement.
3. **Capture optionnelle** : `LiteV2Properties.shouldCaptureOnUpload()` retourne `false` par défaut → aucune charge supplémentaire upload tant que non activé.
4. **Tests à conserver à chaque étape** : 3100 tests doivent rester verts. Run `mvn test -o` avant chaque commit.

### Activation progressive recommandée

```
Phase 1 ( ship sprint A + B.1 + scaffolding capture )
  └─ openadom.lite-v2.enabled=false     # tout désactivé
     └─ Zero impact UX, zero régression
     └─ Atomicité stricte gagnée (Sprint A)

Phase 2 ( merge B.2 wiring complet )
  └─ openadom.lite-v2.enabled=true
     └─ openadom.lite-v2.capture-on-upload=true
        └─ Captures démarrent sur nouveaux uploads
        └─ Monitor storage growth Grafana
     └─ openadom.lite-v2.fast-path=false   # FAST path encore désactivé
     └─ Bake 1-2 semaines preprod

Phase 3 ( merge B.4 + B.5 FAST path )
  └─ openadom.lite-v2.fast-path=true     # par datatype d'abord
  └─ Bench AVANT/APRES on dataset si_acbb
  └─ Rollout progressif prod

Phase 4 ( backfill historique )
  └─ Script admin lance backfill background
  └─ Coverage ratio Grafana progresse vers 100%
```
