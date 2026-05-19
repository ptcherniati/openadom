# Publication / Dépublication / Suppression : refonte 2-phases async

Plan de refonte de la publication/dépublication d'un fichier binaire, avec
exécution asynchrone, notifications mail début/fin, supersedure des
workflows concurrents, et logs visibles dans oa-live.

Auteur : R.YAHIAOUI - 2026-05-12 ( v1 ) , 2026-05-13 ( v2 : tuning per-flux )

---

## 0. Tuning per-flux ( v2 , 2026-05-13 )

### Problème observé
Cascade pipeline était partagé entre upload initial et republication, avec
**1 seul jeu de paramètres** ( `cascade.import.*` ) :
- Upload veut throughput max ( STAGED + parallelism 4 + chunk 1000 )
- Republish veut mémoire basse ( PIPELINED + parallelism 2 + chunk 200 )

Incompatible avec 1 seul jeu -> OOM heap 2.8GB observé sur datasets ACBB
lors d'un publish toggle qui passait par les défauts upload.

### Solution architecturale

**2 beans de configuration distincts**, le pipeline applique l'un ou l'autre
selon le flux appelant :

| Bean | Prefix | Defauts | Cas d'usage |
|------|--------|---------|-------------|
| `ImportProperties` | `cascade.import.*` | STAGED + parallelism 4 + chunk 1000 + MERGE_FILE | Upload initial ( createData via REST POST data ) |
| `PublishProperties` | `openadom.publish.*` | PIPELINED + parallelism 2 + chunk 200 + MERGE_FILE | Republish toggle ( PublishLifecyclePhase2Handler.doPublish ) |

**Mécanisme de surcharge :** record immuable
`CascadeRuntimeOverride( pipelineMode , sinkStrategy , stagingStrategy , parallelism , chunkSizeLines , maxErrorsThreshold )`
passé en argument à `DataService.addData( ... , override )` et
`CascadeImportPipeline.execute( ... , override )`.

- Si override field == null → utilise `ImportProperties` ( backward compat upload )
- Si override field != null → surcharge pour ce workflow uniquement
- `CascadeRuntimeOverride.EMPTY` = pas de surcharge ( upload )
- `publishProperties.toRuntimeOverride()` = override complet ( republish )

**Pool cascade reste partagé** entre upload et republish ( source / transform / sink threads ) :
seuls les params strategiques sont surcharges. Backpressure / rate limiting cascade
( `CASCADE_IMPORT_MAX_CONCURRENT_PER_USER` ) s'applique uniformément.

### Hot-edit ( oa-live admin )

Les 6 champs de `PublishProperties` sont exposés dans `ConfigFieldRegistry`
avec préfixe `publish.*` ( ex. `publish.pipelineMode` , `publish.chunkSizeLines` ) .
La section "Publication / Republication" du panel `ConfigEditPanel.vue`
permet l'édition à chaud sans redémarrage backend.

### Env vars deployment

```bash
# local_deployment/.env ou ansible group_vars/*.yml :
OPENADOM_PUBLISH_PIPELINE_MODE=PIPELINED          # STAGED si atomicite critique
OPENADOM_PUBLISH_SINK_STRATEGY=MERGE_FILE         # ou DIRECT_COPY si pool DB large
OPENADOM_PUBLISH_STAGING_STRATEGY=SHARED_UNLOGGED # ignored si sink=MERGE_FILE
OPENADOM_PUBLISH_PARALLELISM=2                    # 1-64
OPENADOM_PUBLISH_CHUNK_SIZE_LINES=200             # 1-1000000
OPENADOM_PUBLISH_MAX_ERRORS_THRESHOLD=100         # 0-1000000
```

### Trade-offs documentés

| Axe | Valeur | Mémoire | Throughput | Atomicité |
|-----|--------|---------|------------|-----------|
| `pipelineMode=STAGED` | tous chunks transform avant sink | élevée ( N chunks bufferisés ) | meilleur | atomique |
| `pipelineMode=PIPELINED` | chunks pipent via queue bornée | basse ( queue capacity ) | bon | commits partiels possibles |
| `sinkStrategy=MERGE_FILE` | temp files locaux + 1 COPY final | basse ( disque ) | bon | atomique ( 1 staging cohérent ) |
| `sinkStrategy=DIRECT_COPY` | N workers COPY parallel | moyenne ( N buffers réseau ) | élevé | partielle ( N writers ) |

---

## 1. État actuel

### Flux toggle (bouton publier/dépublier dans le frontend)
- Endpoint : `POST /applications/{name}/data/{dataName}/files/{fileId}/publish?published=...`
- Code : `FileResources.togglePublished` -> `PublishToggleUseCase.execute`
- Action : `binaryFileRepository.togglePublishedFlag` (un seul `UPDATE` sur `binaryfile.params.published`)
- workflow_log : 1 entry type `PUBLISH_TOGGLE` IN_PROGRESS -> SUCCESS
- **Divergence** : ne touche pas aux données `referencevalue`, pas de mail envoyé.

### Flux upload avec `topublish=true`
- Code : `VersioningService.createData` -> `unPublishVersions` puis `publishData`
- `unPublishVersions` : `dataRepository.removeByFileId` + `markAsPublished(false)` + `store` sur les fichiers chevauchants
- `publishData` : `dataService.addData` -> `cascadeImportPipeline.execute` (parse CSV + STAGING + INSERT)
- Mail envoyé : `UPLOAD_STATE.PUBLISHED` via `safeSendUploadSuccessMail`
- workflow_log : 1 entry type `IMPORT` via cascade pipeline

### Flux delete fichier (publié)
- Endpoint : `DELETE /applications/{name}/file/{id}`
- Code : `OreSiResources.removeFile` :
  1. Si publié : `unPublishVersionBeforeDeleteUseCase` -> `versioningService.unPublishVersionBeforeDelete` -> `createData(... null, true, withEmail=false)` -> DELETE rows + mail DELETED en post-commit
  2. `removeFileUseCase.execute` -> DELETE binaryfile
- workflow_log : 1 entry type `IMPORT` (cascade pipeline de createData)

### Conflit / supersedure
- `BinaryFileService.findPublishedVersion` détecte l'overlap de versions (erreur `overlappingpublishedversion`)
- `unPublishVersions` dépublie auto les versions non-overlap remplacées
- **Aucun mécanisme** pour annuler un workflow PUBLISH/UNPUBLISH IN_PROGRESS sur le même `fileId` quand l'utilisateur reclique.

### Asynchrone
- Spring `@Async` configuré via `AsyncExecutorConfiguration` (`@EnableAsync`)
- `WorkflowLogWriter` async pour observabilité
- Cascade pipeline déjà async par design

---

## 2. Conception cible

### Sémantique stricte (rappel utilisateur)

| Action | Binaryfile | Données referencevalue |
|--------|------------|------------------------|
| Toggle PUBLISH | flag `published=true` | INSERT depuis blob via cascade |
| Toggle UNPUBLISH | flag `published=false` | DELETE rows `WHERE fileId=X` |
| Delete fichier (publié) | DELETE row binaryfile | DELETE rows referencevalue |
| Delete fichier (non publié) | DELETE row binaryfile | rien |

### Modèle 2-phases pour toutes les actions toggle/delete

**Phase 1 - Synchrone (REST, ~10 ms)**

1. Acquisition supersedure : check `workflow_log` actifs sur `fileId`, annule via `WorkflowEventBus.cancel(corrId)`, attend `CANCELLED` confirmé
2. UPDATE `binaryfile.params.published` (toggle flag, visuel instant côté UI)
3. Insert `workflow_log` IN_PROGRESS avec type adapté
4. COMMIT
5. Envoi mail **START** ("Publication en cours", "Dépublication en cours", "Suppression en cours")
6. Renvoie HTTP 202 Accepted + correlationId au client
7. Trigger phase 2 async via `@TransactionalEventListener(AFTER_COMMIT) @Async`

**Phase 2 - Asynchrone (thread Spring @Async)**

Selon action :
- **PUBLISH** : `dataService.addData(application, dataName, new DataFile(fou, binaryFile.getFileData()))` -> cascade pipeline complet (parse + STAGING + INSERT)
- **UNPUBLISH** : `dataRepository.removeByFileId(fileId)` (1 DELETE SQL)
- **DELETE_FILE** : si flag published was true -> `dataRepository.removeByFileId(fileId)` puis `binaryFileRepository.delete(fileId)`. Sinon juste `binaryFileRepository.delete(fileId)`.

Puis (commun) :
1. `synthesisService.buildSynthesis(applicationName, dataName, null)` (recompute compteurs dashboard)
2. `binaryFileService.invalidateReferencedFilesCache(applicationName)`
3. UPDATE `workflow_log` -> SUCCESS (+ duration, recordsProcessed, finalCount)
4. Envoi mail **END** (`UPLOAD_STATE.PUBLISHED / UNPUBLISHED / DELETED`)

**Rollback en cas d'erreur Phase 2** :
1. Reset flag `published` à sa valeur précédente
2. UPDATE workflow_log -> FAILED + fatalError
3. Mail erreur (réutiliser templates `ERROR_SUBJECTS` / `ERROR_BODIES` existants)

### Supersedure

Algorithme dans Phase 1 :

```sql
SELECT correlation_id FROM oa_audit.workflow_log
WHERE application_name = ? AND resource_name = ? 
  AND workflow_type IN ('PUBLISH', 'UNPUBLISH', 'DELETE_FILE')
  AND status = 'IN_PROGRESS'
ORDER BY start_time DESC LIMIT 1
```

Si trouvé :
1. `WorkflowEventBus.getInstance().cancel(corrId)` -> notify cascade pipeline
2. UPDATE workflow_log SET status='CANCELLED', end_time=NOW, fatal_error='Superseded by user request' WHERE correlation_id=?
3. Mail "Opération annulée" (nouveau template) - optionnel V1, recommandé V2
4. Attendre que cascade reporte CANCELLED (timeout ~3s, sinon abort dur)
5. Procéder à la nouvelle opération

### Types workflow_log

Extend `OreSiWorkflowType` :

```java
PUBLISH         // toggle ON
UNPUBLISH       // toggle OFF
DELETE_FILE     // delete file (avec ou sans data)
// Conserver PUBLISH_TOGGLE deprecated pour backward compat lecture historique
```

`workflow_log` stocke des `String` -> pas de migration DB.

### Templates mail

États existants : `PUBLISHED`, `UNPUBLISHED`, `UPLOADED`, `DELETED` (pour Phase 2 END)

Nouveaux états à ajouter (pour Phase 1 START) :
- `PUBLISH_STARTED` ("Votre fichier va être publié...")
- `UNPUBLISH_STARTED` ("Votre fichier va être dépublié...")
- `DELETE_STARTED` ("Votre fichier va être supprimé...")

État erreur (Phase 2 FAILED) : utiliser `ERROR_SUBJECTS` / `ERROR_BODIES` (#477)

---

## 3. Architecture

### Composants

```
FileResources (toggle endpoint)
OreSiResources.removeFile (delete endpoint)
        |
        v
PublishLifecycleService (NEW, orchestrator unique)
    - startPublish(...)
    - startUnpublish(...)
    - startDeleteFile(...)
        |
        v - Phase 1 (sync, @Transactional)
        |   - supersedure check + cancel
        |   - flag toggle
        |   - workflow_log.recordStart(IN_PROGRESS)
        |   - mail START
        |   - emit ApplicationEvent (AFTER_COMMIT trigger)
        |
        v - Phase 2 (@Async @TransactionalEventListener)
        |   - PublishExecutor.executePublish(...)
        |   - UnpublishExecutor.executeUnpublish(...)
        |   - DeleteFileExecutor.executeDelete(...)
        |
        |   chaque executor :
        |   - try { do work } catch { rollback flag + FAILED + mail }
        |   - workflow_log.recordEnd(SUCCESS / FAILED)
        |   - mail END
```

### Diagramme séquence PUBLISH

```
User --click Publish-> Front
Front -POST /publish?published=true-> FileResources
FileResources --> PublishLifecycleService.startPublish
PublishLifecycleService :
  - supersedure check / cancel
  - togglePublishedFlag(true)
  - workflow_log IN_PROGRESS type=PUBLISH
  - mail PUBLISH_STARTED
  - COMMIT
  - publish ApplicationEvent
PublishLifecycleService --HTTP 202 + correlationId--> Front
Front affiche pastille "en cours" + correlationId
(spring AFTER_COMMIT trigger fires)
PublishExecutor (@Async) :
  - load binaryFile blob
  - dataService.addData() -> cascade pipeline
    - cascade emet ses propres workflow_log type=IMPORT (sub)
  - synthesisService.buildSynthesis()
  - invalidateReferencedFilesCache
  - workflow_log SUCCESS type=PUBLISH
  - mail PUBLISHED
oa-live affiche workflow type=PUBLISH (parent) + type=IMPORT (cascade sub) groupés
Front polling correlationId voit SUCCESS, met à jour pastille
```

### Concurrence

- Un fichier (`fileId`) = au plus **1** workflow PUBLISH/UNPUBLISH/DELETE_FILE actif à un instant donné
- Plusieurs fichiers distincts = N workflows parallèles (image utilisateur, périodes différentes)
- Supersedure = reclick sur le même `fileId` annule l'actif en cours

### Tableau perfomance

| Action | Phase 1 sync | Phase 2 async (P50) | Phase 2 async (P95, gros CSV) |
|--------|--------------|---------------------|-------------------------------|
| PUBLISH | ~10 ms | 2 s | 90+ s (cascade pipeline) |
| UNPUBLISH | ~10 ms | 200 ms | 2 s |
| DELETE_FILE | ~10 ms | 300 ms | 3 s |

---

## 4. Modifications par fichier

### Backend - nouveau fichier

**`backend/src/main/java/fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java`** (nouveau)
- Orchestrateur unique
- 3 méthodes publiques : `startPublish`, `startUnpublish`, `startDeleteFile`
- Phase 1 transactionnelle + emission `ApplicationEvent`
- Phase 2 `@Async @TransactionalEventListener(AFTER_COMMIT)`
- Supersedure logique

**`backend/src/main/java/fr/inra/oresing/rest/usecases/storage/versioning/events/PublishLifecycleEvent.java`** (nouveau)
- Record `PublishLifecycleEvent(correlationId, applicationName, fileId, dataName, fileName, action, locale, userId, userLogin)`
- `action` enum : `PUBLISH | UNPUBLISH | DELETE_FILE`

### Backend - modifications

**`OreSiWorkflowType.java`** :
- Ajout `PUBLISH`, `UNPUBLISH`, `DELETE_FILE`
- Retire `PUBLISH_TOGGLE` si aucune contrainte historique (commit f4de3335 récent, peu de données existantes -> safe)
- Sinon : marquer `PUBLISH_TOGGLE` `@Deprecated` + non utilisé en écriture

**`EmailService.java`** :
- Enum `UPLOAD_STATE` : ajouter `PUBLISH_STARTED`, `UNPUBLISH_STARTED`, `DELETE_STARTED`
- Maps `SUCCESS_UPLOAD_SUBJECTS` + `SUCCESS_UPLOAD_TEXTS` : ajouter entrées localisées FR/EN pour les 3 nouveaux états

**`FileResources.java`** :
- `togglePublished(...)` délègue à `publishLifecycleService.startPublish` ou `startUnpublish` selon `published` param
- Ajout `HttpServletRequest` + `LocaleResolver` pour extraire locale
- HTTP 202 + JSON `{correlationId, fileId, dataName, action, status: "IN_PROGRESS"}`

**`OreSiResources.removeFile(...)`** :
- Délègue à `publishLifecycleService.startDeleteFile`
- Suppression des blocs `unPublishVersionBeforeDeleteUseCase` + `removeFileUseCase` + `finalizePostCommit` (déplacés dans le service)

### Backend - suppression / code mort

**`PublishToggleUseCase.java`** : SUPPRIMÉ (remplacé par PublishLifecycleService)

**`UnPublishVersionBeforeDeleteUseCase.java`** : SUPPRIMÉ (intégré dans DELETE_FILE phase 2)

**`PublishTogglePayload.java`** : SUPPRIMÉ (remplacé par PublishLifecycleEvent)

**`RemoveFileUseCase.java`** : KEEP (toujours utilisé par PublishLifecycleService pour DELETE_FILE phase 2)

**`VersioningService.unPublishVersionBeforeDelete(...)`** : SUPPRIMÉ
- Logique extraite dans `PublishLifecycleService.executeDeleteFile`

**Tests des fichiers supprimés** : à mettre à jour ou supprimer si exclusifs.

### WorkflowLogRepository

Ajout d'une méthode `findActiveByResource(applicationName, resourceName, List<OreSiWorkflowType> types) -> Optional<UUID>` pour la supersedure.

### Frontend (oa-live) - workflow_log rendering

**Aucune modification structurelle** : workflow_log type=PUBLISH / UNPUBLISH / DELETE_FILE est rendered comme les autres types (IMPORT, EXTRACT). Le dashboard live actuel filtre par status (IN_PROGRESS) et affiche par type.

**i18n minor** : `workflow.type.PUBLISH`, `workflow.type.UNPUBLISH`, `workflow.type.DELETE_FILE` à ajouter dans `fr.json` / `en.json`.

### Frontend (principal) - polling correlationId

**Optionnel V1** : le HTTP 202 retourne correlationId. Le frontend peut poller `GET /workflow-log/{correlationId}` pour suivre status.

**V1 minimal** : afficher pastille "en cours" basé sur état flag + invitation à recharger après quelques secondes.

---

## 5. Migration et code mort

### Code supprimé

| Fichier | Action | Raison |
|---------|--------|--------|
| `PublishToggleUseCase.java` | DELETE | Remplacé par `PublishLifecycleService.startPublish/Unpublish` |
| `PublishTogglePayload.java` | DELETE | Remplacé par `PublishLifecycleEvent` |
| `UnPublishVersionBeforeDeleteUseCase.java` | DELETE | Intégré dans `PublishLifecycleService.startDeleteFile` |
| `VersioningService.unPublishVersionBeforeDelete(...)` | DELETE | Logique dans `PublishLifecycleService` |
| `OreSiWorkflowType.PUBLISH_TOGGLE` | DELETE si pas de blocage | Remplacé par `PUBLISH` / `UNPUBLISH` |

### Code conservé / refactor mineur

- `BinaryFileRepository.togglePublishedFlag` : conservé (appelé par phase 1)
- `BinaryFileService.removeFile` + `RemoveFileUseCase` : conservés (phase 2 DELETE_FILE)
- `DataService.addData` : conservé (phase 2 PUBLISH)
- `DataRepository.removeByFileId` : conservé (phase 2 UNPUBLISH / DELETE_FILE publié)
- `SynthesisService.buildSynthesis` : conservé (phase 2 commun)
- `EmailService.sendUpoadSuccessMail` : conservé + étendu (nouveaux états)
- `VersioningService.publishData` + `unPublishVersions` + `safeSendUploadSuccessMail` : conservés (utilisés par flux upload `createData`)
- `BinaryFileService.findPublishedVersion` + `unPublishVersions` (autre méthode) : conservés (overlap check)

### Migration de données

**Aucune** : pas de schéma DB modifié, workflow_log accepte tout `String` comme `workflow_type`.

---

## 6. Plan de tests

### Unitaires (JUnit)

- `PublishLifecycleServiceTest`
  - `startPublish_phase1_setsFlagAndEmitsEvent`
  - `startUnpublish_phase1_setsFlagAndEmitsEvent`
  - `startDeleteFile_phase1_setsFlagAndEmitsEvent`
  - `supersedure_cancelsActiveOnSameFileId`
  - `supersedure_doesNotTouchDifferentFileId`
  - `phase2_publish_callsAddData`
  - `phase2_unpublish_callsRemoveByFileId`
  - `phase2_deletePublished_callsRemoveByFileIdThenDelete`
  - `phase2_deleteUnpublished_callsDeleteOnly`
  - `phase2_failure_rollsBackFlagAndSendsErrorMail`
  - `phase2_success_sendsEndMail`

- `EmailServiceTest`
  - `sendStartMail_PUBLISH_STARTED`
  - `sendStartMail_UNPUBLISH_STARTED`
  - `sendStartMail_DELETE_STARTED`

- `WorkflowLogRepositoryTest`
  - `findActiveByResource_returnsActive`
  - `findActiveByResource_returnsEmptyIfNone`

### Intégration (Spring Boot test)

- `PublishLifecycleIntegrationTest`
  - End-to-end : POST /publish?published=true -> HTTP 202 -> attend phase 2 -> verifie rows referencevalue INSERTED + workflow_log SUCCESS
  - End-to-end UNPUBLISH : verifie rows DELETED
  - End-to-end DELETE publié : verifie rows + binaryfile DELETED
  - Supersedure : 2 toggles concurrents même fileId -> 1er CANCELLED, 2eme SUCCESS

### Tests existants à adapter

- `CacheAdminResourcesUnitTest` : déjà adapté précédemment
- Tests référençant `PublishToggleUseCase` : à supprimer ou redirigér vers `PublishLifecycleService`
- Tests `UnPublishVersionBeforeDeleteUseCase` : à supprimer (use case retiré)

---

## 7. Phases d'implémentation

### Phase A - Plumberie (sans casser l'existant)
1. Ajout `OreSiWorkflowType.PUBLISH`, `UNPUBLISH`, `DELETE_FILE`
2. Ajout `UPLOAD_STATE.PUBLISH_STARTED`, `UNPUBLISH_STARTED`, `DELETE_STARTED` + templates FR/EN
3. Ajout `WorkflowLogRepository.findActiveByResource`
4. Création `PublishLifecycleEvent` (record)

### Phase B - Service central
5. Création `PublishLifecycleService` avec phase 1 + phase 2
6. Tests unitaires `PublishLifecycleServiceTest`

### Phase C - Wiring REST
7. `FileResources.togglePublished` -> délègue à `PublishLifecycleService`
8. `OreSiResources.removeFile` -> délègue à `PublishLifecycleService`
9. Tests d'intégration end-to-end

### Phase D - Nettoyage
10. Suppression `PublishToggleUseCase` + `PublishTogglePayload`
11. Suppression `UnPublishVersionBeforeDeleteUseCase`
12. Suppression `VersioningService.unPublishVersionBeforeDelete`
13. Suppression `OreSiWorkflowType.PUBLISH_TOGGLE` (si safe)
14. Mise à jour / suppression des tests obsolètes

### Phase E - Frontend
15. i18n `workflow.type.PUBLISH / UNPUBLISH / DELETE_FILE` dans oa-live
16. Front principal : polling correlationId + indication "en cours" (V1 minimal : recharger)

---

## 8. Risques et mitigations

| Risque | Impact | Mitigation |
|--------|--------|-----------|
| Phase 2 fail, flag toggled mais data non-sync | UI affiche publié mais base incohérente | Rollback flag dans catch phase 2 + mail erreur |
| Supersedure pas atomique (race) | 2 workflows actifs sur même fileId | Lock pessimiste sur binaryfile row pendant phase 1 (`SELECT FOR UPDATE`) |
| Mail SMTP down | Notif perdue | `safeSendUploadSuccessMail` déjà fail-safe (log warn, pas d'exception) |
| Cascade pipeline timeout (gros CSV) | workflow_log reste IN_PROGRESS | `WorkflowZombieSweeper` existant détecte zombie après threshold |
| Utilisateur déconnecté pendant phase 2 | Mail destinataire incorrect ? | userId capturé en phase 1 + propagé dans event, indépendant de session |
| Lecture data pendant phase 2 (eventual consistency) | Réponse incomplète | Accepté en V1 ; V2 : verrouiller lectures sur fileId IN_PROGRESS |

---

## 9. Décisions assumées

- **Workflow type sémantique** : 3 types distincts (PUBLISH / UNPUBLISH / DELETE_FILE) plutôt que 1 type (PUBLISH_TOGGLE) avec metadata. Plus lisible dans dashboard oa-live + plus simple à filtrer.
- **HTTP 202 + correlationId** : meilleur pattern REST pour async vs HTTP 200 + polling implicite.
- **Spring @Async + AFTER_COMMIT TransactionalEventListener** : pattern Spring standard, observable via Micrometer si besoin, simple à tester.
- **Pas de nouveau type d'event cascade** : on réutilise `WorkflowEventBus.cancel` existant.
- **Mail START best-effort** : si SMTP fail, on log et on continue (déjà comportement de `safeSendUploadSuccessMail`).
- **Cascade IMPORT en sous-workflow** : visible séparément dans oa-live (workflow_log distinct par correlationId), pas de hiérarchie parent/child explicite en V1.

---

## 10. Métriques de succès

- Toggle publish/unpublish : HTTP response time < 50 ms (vs ~10 s actuel via toute la pipeline)
- 100% des actions notifiées par mail (start + end)
- Visibilité 100% dans oa-live workflow dashboard live (status IN_PROGRESS visible immédiatement)
- 0 workflow zombie après supersedure (workflow_log CANCELLED correctement persistée)
- 0 code mort restant après nettoyage (vérifié via grep)
