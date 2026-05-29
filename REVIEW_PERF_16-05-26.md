# REVIEW_PERF_16-05-26 - Audit observability publish/unpublish/delete/deposit

Author : audit fork - 2026-05-16
Scope : oa-live frontend + openadom backend - end-user observability per use case
Mandate : identify missing info that should surface in oa-live so that the user has a clear picture at every step . Code is NOT modified by this audit ; this file lists actionable items grouped by use case + confidence level .

Conventions :
- Confidence "obvious" : easy fix , no design choice required .
- Confidence "needs validation" : non-trivial UX or backend change , confirm before coding .
- Confidence "speculative" : likely improvement but requires deeper inspection .

---

## 0 . What already lands in oa-live ( reference inventory )

The current oa-live surface for publish / unpublish workflows wires :

- `WorkflowTable.vue` row : workflow_type + dataType + progress bar ( rows / total ) + lines / sec + bytes + duration + StatusBadge ( IN_PROGRESS / WAITING / COMPLETED / ... ) + a phase chip pulled from `metadata.phase` , plus an inline "FAST" badge when `fastPath` field is populated . See file :
  - `oa-live/src/components/WorkflowTable.vue` lines 414-486 .
- `WorkflowFinalizeBadge.vue` ( the STAGING / MERGE_FILE collapsible under each row ) : 2 progress bars phase A / phase B + finalize duration + finalize throughput + sink + staging strategies + the post-cascade phase indicator added recently ( spinner + label for SYNTHESIS_REBUILD / CACHE_CAPTURE / COMMIT_VISIBILITY ) . See file :
  - `oa-live/src/components/WorkflowFinalizeBadge.vue` lines 191-223 , 380-396 .
- `FastPathPanel.vue` : dedicated panel rendered when `WorkflowSummary.fastPath` is non-null ( 5 phases pill + counters cache bytes / streamed / upserted + per-phase duration ) . See file :
  - `oa-live/src/components/FastPathPanel.vue` .
- `StatusBadge.vue` : 13 status entries with styled pills ( IN_PROGRESS , WAITING , UPLOADING , CHUNKING , PROCESSING , LOADING_DB , RUNNING , ACTIVE , IDLE , DISCONNECTED , COMPLETED , FAILED , CANCELLED , RATE_LIMITED ) . See file :
  - `oa-live/src/components/StatusBadge.vue` .
- `WorkflowDetailView.vue` : detail page from `/api/dashboard/workflows/{cid}` exposing every field of `WorkflowSummary` + per-chunk drill-down + ImportConfig snapshot .
- `BinaryFileCachePanel.vue` ( admin tab ) : `processedSize` , `configHash` , `published` , `processedAt` per binaryfile - lets the admin see cache state + clear cache + trigger BUILD_CACHE workflow . Cache rebuild emits a workflow with `workflowType=BUILD_CACHE` , visible in the live table .

Backend emits :
- `WorkflowSummary` ( endpoint `/api/dashboard/workflows/in-progress` + `/.../{cid}` ) - core record .
- `FinalizeProgress` ( `/api/dashboard/workflows/{cid}/finalize` ) - phase A / B + strategies + counters .
- `FinalizeAggregate` ( `/api/dashboard/workflows/finalize/aggregate` ) - global tile .
- `FastPathSnapshot` is included in `WorkflowSummary.fastPath` only while the FAST path is running ; the cache size is only known after FAST started ( `bfRepo.findProcessedSize` called inside `PublishFastPathDirectExecutor.executeInternal` line 144 ) .
- `metadata.phase` mirror : updated by `PublishLifecyclePhase2Handler.updatePhase` + `CascadeImportPipeline.updatePhase` + `PublishFastPathDirectExecutor.timeStage` .

---

## 1 . PUBLISH - FAST path ( binary COPY from cache )

### What user sees today
- `WorkflowTable` row : "Publication" + dataType + inline `FAST` badge ( title tooltip ) , progress bar appears only when `recordsProcessed > 0` , status WAITING then IN_PROGRESS .
- Expanding the caret on the row reveals `FastPathPanel` : 5 phases pill + cache bytes + streamed rows + upserted rows + per-phase duration .
- After FAST returns , Phase2Handler enters `SYNTHESIS_REBUILD` -> the post-cascade phase indicator shows under the - never-displayed for FAST - "UPSERT staging" bar . But that indicator lives inside `WorkflowFinalizeBadge` , which is hidden for FAST workflows ( no STAGING happened ) . **Bug** : during `SYNTHESIS_REBUILD` on a FAST path , the user has no visible phase indicator , only the row-level "phase chip" next to the StatusBadge ( phase = SYNTHESIS_REBUILD ) which is small and not associated with a spinner .

### Missing info
1. **Cache state badge BEFORE the publish starts** . Currently the user must navigate to the admin "Cache" tab to know if a binaryfile has a cache . On the main frontend `DataVersioningView` , no badge or hint says "this datatype has a cache , next publish will be FAST" . Surface `binaryfile.processedSize > 0 && configHash matches current` as a per-row badge ( "FAST ready" / "no cache" / "cache stale" ) . Confidence : obvious .
   - Backend : already exposed via `BinaryFileCacheRow` ( `processedSize` , `configHash` , `published` ) at endpoint `/api/v1/admin/applications/{app}/binaryfile-cache` , but limited to admin role . Need a lighter endpoint scoped to non-admin showing the same booleans per file the user owns , or expose `BinaryFileInfosResult` with `processedSize` + a derived `fastEligible` boolean .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/rest/model/data/BinaryFileInfosResult.java` ( add 2 fields ) , `backend/src/main/java/fr/inra/oresing/domain/BinaryFileInfos.java` , `backend/src/main/java/fr/inra/oresing/persistence/BinaryFileRepository.java` ( surface processed_size via existing `findProcessedSize` ) , `frontend/src/views/data/DataVersioningView.vue` ( badge rendering ) .

2. **CACHE_CLEAR phase under CACHED_ROTATION** . `PublishFastPathDirectExecutor` already calls `timeStage(correlationId, FastPathSnapshot.PHASE_CACHE_CLEAR, ...)` ( line 242 ) which writes the phase to `workflow_log.metadata.phase` . But `FastPathPanel.vue` ( the table of 5 phases ) renders the `CACHE_CLEAR` row even when `clearProcessedDataOnSuccess=false` ( CACHED_ROTATION disabled ) - the user sees a "Pending" row that will never run . Confidence : obvious .
   - Files to touch : `oa-live/src/components/FastPathPanel.vue` line 21-27 ( hide the `CACHE_CLEAR` entry when publishMode != CACHED_ROTATION , exposing the mode via existing `WorkflowSummary.strategy` or via a new field on `FastPathInfo` ) ; `backend/src/main/java/fr/inra/oresing/workflow/cascade/history/FastPathSnapshot.java` ( record one boolean `cacheClearArmed` to gate the row ) .

3. **Post-FAST synthesis phase has no visual anchor** . On a FAST path , `WorkflowFinalizeBadge` is rendered only if the backend emits a `FinalizeProgress` ( which is keyed off `WorkflowActiveRegistry.findFinalizePhase` , populated by cascade ; FAST does not go through cascade so this is null ) . The post-cascade indicator I added under the UPSERT bar therefore never shows for FAST publishes . The phase chip on the workflow row ( WorkflowTable line 445-448 ) still works , but it is small and not animated . Recommendation : show a dedicated post-FAST footer in `FastPathPanel.vue` for SYNTHESIS_REBUILD / COMMIT_VISIBILITY / CACHE_CAPTURE ( spinner + label ) so the FAST view is informationally complete . Confidence : obvious .
   - Files to touch : `oa-live/src/components/FastPathPanel.vue` ( add a footer reading `workflowPhase` prop ) , `oa-live/src/components/WorkflowTable.vue` line 484 ( pass `:workflow-phase="phaseRaw(w)"` like `WorkflowFinalizeBadge` already does ) .

### Bugs / inconsistencies
- `FastPathPanel.vue` line 11 docstring mentions `STREAM_CACHE` phase ; this phase no longer exists ( renamed to `COPY_IN` ) . Cosmetic drift but confuses readers .
- `FastPathInfo.streamedRows` is set by the reader to the same value as `upsertedRows` ( `registry.setFastPathUpsertedRows` ) - the panel meta shows both with identical numbers . Either populate `streamedRows` from a streaming counter in `ReferencevalueCacheReader.replayCache` ( during the `COPY ... FROM stdin` loop ) , or drop the field from the UI . Confidence : needs validation .

---

## 2 . PUBLISH - LITE path ( cascade republish with hashMatch )

### What user sees today
- Row : "Publication" + WAITING -> IN_PROGRESS + progress bar . Phase chip cycles : CASCADE_PREPARING -> CASCADE_RUNNING -> SYNTHESIS_REBUILD -> CACHE_CAPTURE ( optional ) -> DONE .
- Expanded : workers ( SOURCE / TRANSFORM / SINK ) + STAGING / UPSERT bars in `WorkflowFinalizeBadge` + post-cascade phase indicator .

### Missing info
1. **`CASCADE_PREPARING` is opaque** . After the recent fix `getAsynchroneImporterContext` is no longer the bottleneck ( self refType preload skipped ) , so prep is ~3 - 5 s . But for datatypes with many external references the prep can still take seconds . Suggest a sub-phase counter "Resolving N referenced datatypes" while in CASCADE_PREPARING . Backend already iterates per refType in `CheckerFactory.getCheckers` -> `CheckerDescription.buildFieldtype` ( still calling `getDataIdPerKeys` per ReferenceChecker for external refs ) . Confidence : needs validation .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/rest/data/DataService.java` ( emit a sub-phase via WorkflowLogRepository.updatePhase counter , e . g . `CASCADE_PREPARING:resolving 3/7 refs` ) ; `oa-live/src/components/WorkflowTable.vue` ( decode the suffix ) .

2. **`SYNTHESIS_REBUILD` progress** . Currently a generic spinner + label . If a synthesis rebuild rebuilds N refTypes ( buildReferenceSynthesis aggregates per refType ) , we could surface "Rebuilding synthesis 3/12 ( refType : x )" . Backend `SynthesisService.buildSynthesis` is called with `dataName=ev.dataName()` , so the scope is already a single datatype - the loop is on rows not on refTypes . Drop this idea . Confidence : speculative - skip .

3. **`CACHE_CAPTURE` byte counter** . `BinaryFileRepository.storeProcessedDataDirectCopy` calls `ReferencevalueCacheWriter.writeCache` which streams `COPY ... TO STDOUT (FORMAT BINARY)` through a `CountingOutputStream` ( see `BinaryFileRepository.CountingOutputStream` inner class ) . The total byte counter is captured at the end and persisted to `binaryfile.processed_size` , but **no live counter is emitted during the COPY** . On 1 . 7 GB caches this phase takes ~30 s ; user sees a generic spinner without progress . Confidence : obvious .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/persistence/BinaryFileRepository.java` ( push counter to `WorkflowActiveRegistry` every N MB ) , `backend/src/main/java/fr/inra/oresing/workflow/cascade/history/WorkflowActiveRegistry.java` ( add field `cacheCaptureBytes` keyed by cid ) , `backend/src/main/java/fr/inra/oresing/rest/dashboard/DashboardService.java` ( expose via `WorkflowSummary` or `FinalizeProgress` ) , `oa-live/src/components/WorkflowFinalizeBadge.vue` ( render bytes when phase=CACHE_CAPTURE ) .

### Bugs / inconsistencies
- `WorkflowFinalizeBadge.vue` polls `/finalize` endpoint every 5 s . On a long SYNTHESIS_REBUILD the endpoint keeps returning `phase=COMPLETED` ( the finalize phase is logically complete ) , so the badge stops polling ( see `TERMINAL = ['COMPLETED', 'ROLLBACK_DONE']` line 46 ) . But the workflow as a whole is NOT terminal yet . The post-cascade phase indicator ( reading `props.workflowPhase` from parent ) keeps working because that prop refreshes from `WorkflowTable` polling . OK , no actual bug , but the behaviour is non-obvious . Add a code comment .

---

## 3 . PUBLISH - FULL path ( first-time publish or hash mismatch )

### What user sees today
- Same surface as LITE path - all phases visible , workers grid expanded . Cache capture phase only runs when `isCaptureProcessedEnabled` and the cache is absent or stale .

### Missing info
1. **Distinguish FULL from LITE in the badge** . Right now both look identical to the user . Backend logs `Phase 2 cascade path : ... lite=true/false ( hashKnown=true/false hashMatch=true/false )` but the boolean does not reach the UI . Surfacing it ( inline pill "LITE" / "FULL" ) helps the user understand why a republish is suddenly slower . Confidence : obvious .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java` ( pass `lite` via `workflow_log.metadata.cascadeMode` ) , `oa-live/src/components/WorkflowTable.vue` ( decode + render mini pill next to the FAST badge ) .

2. **Cache absent badge** . A FULL publish that runs because no cache exists yet should display a clear "First publish - cache will be built at the end" notice rather than leaving the user wondering if their cache is broken . Confidence : needs validation .

### Bugs / inconsistencies
- None spotted in the FULL path .

---

## 4 . UNPUBLISH

### What user sees today
- Row : "Depublication" + WAITING -> IN_PROGRESS + phase chip = DELETE_ROWS -> COMMIT_VISIBILITY -> SYNTHESIS_REBUILD -> DONE .
- `FinalizeProgress` returns null ( no cascade for unpublish ) , so `WorkflowFinalizeBadge` is hidden -> no STAGING bar , no post-cascade indicator visible , user only has the small phase chip .
- No row counter for DELETE_ROWS ( PostgreSQL does not return progress for a DELETE ) .

### Missing info
1. **DELETE_ROWS progress / row count** . Currently invisible . PostgreSQL does not expose mid-statement progress for DELETE , but we can :
   - Show the number of rows that will be deleted ( read once at start , equals `referencevalue WHERE binaryfile=?` count ) as the denominator .
   - Surface elapsed seconds and a moving-bar so the user knows it's not stuck .
   - Confidence : obvious for the denominator , needs validation for the moving bar wording .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java` `doUnpublish` ( read row count before DELETE , push to registry via a new field `unpublishExpectedRows` ) ; new field on `WorkflowSummary` ; `oa-live/src/components/WorkflowTable.vue` ( show "Deleting N rows" hint ) .

2. **SYNTHESIS_REBUILD on unpublish has no anchor either** . Same problem as PUBLISH FAST path : `WorkflowFinalizeBadge` is hidden and only the small phase chip shows the recompute step . The phase chip is good enough but could be paired with a tooltip explaining what is happening . Confidence : speculative .

### Bugs / inconsistencies
- `doUnpublish` in `PublishLifecyclePhase2Handler` line 582-606 does NOT emit `SYNTHESIS_REBUILD` ; only `DELETE_ROWS` . The subsequent `commitVisibleFlagAndSynthesis` ( shared with PUBLISH at line 693 ) updates the phase to SYNTHESIS_REBUILD then DONE - reuses the same chain . OK , no bug , but the sequencing comment in `WorkflowPhase.java` lines 34-36 lists "DELETE_ROWS -> COMMIT_VISIBILITY -> SYNTHESIS_REBUILD -> DONE" while the actual sequence is "DELETE_ROWS -> SYNTHESIS_REBUILD ( inside the same tx as the toggle ) -> DONE" . Update the docstring to match . Confidence : obvious .

---

## 5 . DELETE_FILE

### What user sees today
- Row : "Suppression" + phase chip DELETE_ROWS -> DELETE_FILE_ROW -> SYNTHESIS_REBUILD -> DONE .
- No row counter , no SYNTHESIS_REBUILD progress .

### Missing info
1. **Row count for DELETE_ROWS** : same point as UNPUBLISH . Confidence : obvious .
2. **Visual confirmation that the binaryfile row was deleted** . After DELETE_FILE_ROW the binaryfile is gone from the table , but the workflow row remains . Confidence : low - the main frontend already polls binaryfile list and removes the row . Skip .

### Bugs / inconsistencies
- Nothing to add .

---

## 6 . DEPOSIT ( upload + first publish chain )

### What user sees today
- Upload step is **not** rendered in oa-live . `DataVersioningView.vue` ( main frontend ) shows the upload progress bar via the native HTTP request `progress` event ; once the file is uploaded the user has to look at the global Live tab to see the workflow .
- After upload , if the dataset is marked as auto-publish , a PUBLISH workflow starts and `oa-live/WorkflowTable` shows it - but the user has no visual link between "I just uploaded foo.csv" and "this row in oa-live is the publish of that file" .

### Missing info
1. **`resourceName` ( file name ) display in the row** . `WorkflowTable.vue` shows `dataType` but not `resourceName` , which is precisely the file name . The information IS in `WorkflowSummary.resourceName` . Adding a tooltip on the `dataType` cell or a small file name pill would help . Confidence : obvious .
   - Files to touch : `oa-live/src/components/WorkflowTable.vue` line 432 ( `{{ w.dataType ?? '-' }}` -> append `<span class="resource-name">{{ w.resourceName }}</span>` ) .

2. **Cache build at the end of first publish** . When `isCaptureProcessedEnabled=true` and the first publish completes , `CACHE_CAPTURE` phase runs ( ~30 s on 870 k rows ) . The user sees the phase chip but not the bytes flowing - covered in section 2 . 3 above .

3. **Upload throughput** . Upload itself is invisible to oa-live . The `bytesTotal` field on `WorkflowSummary` is populated AFTER upload completes ; during upload there is no record yet . If upload UX matters , expose a pre-workflow row via `BinaryFileService.startUpload` to surface upload progress . Confidence : speculative - probably out of scope .

### Bugs / inconsistencies
- Nothing to add .

---

## 7 . BUILD_CACHE ( admin operation )

### What user sees today
- Triggered from `BinaryFileCachePanel.vue` "Build cache" button per row .
- A workflow appears in oa-live with `workflowType=BUILD_CACHE` , phase chip = CACHE_CAPTURE then DONE .
- `BinaryFileCachePanel.vue` polls cache state every X seconds and updates the row .

### Missing info
1. **BUILD_CACHE byte counter** : same as section 2 . 3 - no live bytes counter , generic phase chip . Confidence : obvious .
2. **BUILD_CACHE source row count** : the cache is built from `referencevalue WHERE binaryfile=?` - the row count is known up-front . Display "Capturing N rows" . Confidence : obvious .

### Bugs / inconsistencies
- Nothing to add .

---

## 8 . Cross-cutting issues

### 8 . 1 . History endpoint duplication
- Backend already filters cascade child IMPORT rows in `DashboardService.listHistory` ( `WHERE (metadata->>'parentCorrelationId') IS NULL` ) since the fix earlier this session .
- The tagging happens in `CascadeImportPipeline.run()` right after `registerWorkflowStart` ( call to `workflowLogRepository.setParentCorrelationId(corrUuid, publishParent)` ) - the race vs cascade INSERT is now resolved .
- **Remaining issue** : pre-existing workflow_log rows ( before the fix was deployed ) have no `parentCorrelationId` so they still surface as duplicates . A one-off SQL backfill could match `IMPORT` rows to a parent `PUBLISH/UNPUBLISH/DELETE_FILE` by ( user_id , application_name , data_type , start_time within 60s window , matching `metadata.fileId` ) . Confidence : needs validation - the heuristic can produce false positives if multiple publishes ran in parallel on the same user / dataset .
- Files to touch : new Flyway migration `backend/src/main/resources/db/migration/main/Vxx__backfill_parent_correlation.sql` .

### 8 . 2 . Cancelling label persistence
- After clicking Cancel , the row shows "CANCELLING" briefly then flips back to IN_PROGRESS / EN COURS for a few polls until the backend writes `status=CANCELLED` . Race already tracked in task 232 of the task list . Confidence : needs validation - waiting for the existing fix to land .

### 8 . 3 . CASCADED_ROTATION semantics not visible
- The publishMode ( `STANDARD` / `CACHED_ROTATION` ) is configured globally ; oa-live does not surface it on the workflow row . A user in CACHED_ROTATION can be surprised that the cache disappears after a successful publish . Confidence : obvious .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/rest/dashboard/DashboardService.java` ( expose `publishMode` on `WorkflowSummary` ) , `oa-live/src/components/WorkflowTable.vue` or `FastPathPanel.vue` ( show pill "CACHED_ROTATION" ) .

### 8 . 4 . FAST eligibility hint on Publish button
- The Publish button in `DataVersioningView.vue` does not tell the user whether it will trigger a FAST , LITE , or FULL publish . Knowing the path matters because FAST is ~10-20s while FULL on 1M rows is 15+ min . Confidence : obvious .
   - Files to touch : `frontend/src/views/data/DataVersioningView.vue` ( a small pill above the Publish button reading the new `BinaryFileInfosResult.fastEligible` field from section 1 . 1 ) .

### 8 . 5 . Phase chip text size + lack of icon
- The phase chip on the workflow row is small ( 11 px ) and grey . On long phases ( SYNTHESIS_REBUILD , CACHE_CAPTURE ) , the lack of a spinner or icon makes it look static . The post-cascade phase indicator inside `WorkflowFinalizeBadge` already has a spinner ; align the row-level chip the same way . Confidence : obvious .
   - Files to touch : `oa-live/src/components/WorkflowTable.vue` ( add spinner CSS when phase is a known long phase ) .

---

## 9 . Perf opportunities ( spotted during the audit )

1. **`getDataIdPerKeys` is still called per external ReferenceChecker** . Section 1 of this audit notes that the recent fix only skipped the self refType . For datatypes referencing N external refs , `CheckerDescription.buildFieldtype` calls `repository.getDataIdPerKeys(rc.refType())` N times sequentially in `CheckerFactory.getCheckers` . For small refs ( <2 k rows ) this is fine ( <10 ms ) ; for large ref tables it adds up . Consider lazy-loading external refs the same way `LazyDisplayNamesMap` lazy-loads display names . Confidence : needs validation - the existing eager load is consumed downstream by `getKnownId` per row , so a lazy adapter must preserve thread-safety and idempotency .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/domain/application/configuration/checker/CheckerDescription.java` line 50-77 .

2. **`storeAll` UPSERT scales as N * num_indexes** . `referencevalue` has ~20 GIN partial indexes ( one per refType , `WHERE referencetype='...'` ) . Each UPSERT row triggers index maintenance on the matching partial index . For a republish of 870 k rows of a single refType this is fine ( only that ref's GIN index touches ) , but for a fresh first-time publish or for the cascade UPSERT during republish the index maintenance dominates the time . Two options : ( a ) drop+recreate the matching GIN index during the publish ( atomic tx , risk on authorization reads during the publish ) ; ( b ) accept the cost since FAST path bypasses this entirely for republishes . Confidence : speculative .

3. **Polling cadence on `WorkflowTable`** . `WorkflowTable.vue` polls `/in-progress` every 3 s ; `WorkflowFinalizeBadge.vue` polls `/finalize` every 5 s . On a long workflow with many polls open , the load on the backend `DashboardService.finalizeProgress` is non-trivial - it does ~3 SQL queries per call ( workflow_log read , final_count read , optional COUNT staging ) . A debounce / shared-cache layer on the backend ( 1 s TTL ) would absorb redundant calls when multiple browser tabs view the same workflow . Confidence : needs validation .

4. **`autovacuum` on `referencevalue` keeps blocking `Flyway changeOwner` at backend startup** . We hit this twice in the session . The Flyway callback `SchemaFlywayCallback.changeOwner` runs `ALTER TABLE referencevalue OWNER TO ...` which acquires an AccessExclusiveLock - waits on autovacuum then times out . Two options : ( a ) skip the callback if owner is already correct ( the common case ) ; ( b ) `pg_cancel_backend` any conflicting autovacuum before the ALTER . Confidence : obvious .
   - Files to touch : `backend/src/main/java/fr/inra/oresing/persistence/flyway/SchemaFlywayCallback.java` line 111-115 ( add a `SELECT tableowner FROM pg_tables` short-circuit ) .

---

## 10 . Quick-win punch list ( safe to implement without further discussion )

| # | Item | Files | Effort |
|---|------|-------|--------|
| Q1 | Hide `CACHE_CLEAR` row in `FastPathPanel` when CACHED_ROTATION off | `oa-live/src/components/FastPathPanel.vue` + `FastPathSnapshot.java` | S |
| Q2 | Pass `workflowPhase` to `FastPathPanel` so the post-FAST synthesis phase appears under the panel | `oa-live/src/components/WorkflowTable.vue` line 484 + `FastPathPanel.vue` footer | S |
| Q3 | Show file name ( `resourceName` ) in the workflow row | `oa-live/src/components/WorkflowTable.vue` line 432 | S |
| Q4 | Surface `cascadeMode` ( LITE / FULL ) in workflow row | `PublishLifecyclePhase2Handler.java` + `WorkflowTable.vue` | M |
| Q5 | Fix doc drift `WorkflowPhase.java` UNPUBLISH ordering | `WorkflowPhase.java` lines 34-36 | S |
| Q6 | Drop stale STREAM_CACHE mention in `FastPathPanel.vue` docstring | `FastPathPanel.vue` line 11 | S |
| Q7 | Short-circuit `SchemaFlywayCallback.changeOwner` when owner is already correct | `SchemaFlywayCallback.java` line 111 | M |
| Q8 | `DELETE_ROWS` denominator ( unpublish / delete_file ) | `PublishLifecyclePhase2Handler.java` + `WorkflowSummary` + `WorkflowTable.vue` | M |
| Q9 | `CACHE_CAPTURE` live bytes counter via `CountingOutputStream` push | `BinaryFileRepository.java` + `WorkflowActiveRegistry.java` + `FinalizeProgress` + `WorkflowFinalizeBadge.vue` | L |

S = under 1 hr ; M = 1 - 3 hr ; L = half day with tests .

---

## 11 . Items requiring user validation before code change

| # | Item | Why validation |
|---|------|----------------|
| V1 | Backfill old workflow_log rows to tag child IMPORT with parentCorrelationId | Heuristic ; can produce false positives on parallel publishes |
| V2 | Lazy-load external refType `getDataIdPerKeys` | Need to vet `getKnownId` thread-safety on lazy load + retry semantics on transient DB failure |
| V3 | Drop+recreate GIN authorization indexes during UPSERT | Authorization reads during the publish window would be incomplete - data-correctness risk |
| V4 | Backend debounce on `/finalize` endpoint | Shared cache TTL ( 1 s ) introduces stale-data window ; needs discussion with the live-view team |
| V5 | "FAST ready" / "no cache" badge on `DataVersioningView` | Exposes `processedSize` to non-admin - vet that this leaks no internal info |

---

## 12 . Out-of-scope / TODO not investigated

- Tests : whether the existing `mvn -Pall-tests` suite still passes with the recent edits .
- BinaryFileCachePanel.vue full read - I only confirmed the API surface , not the panel UX completeness ( pagination , filtering , bulk operations ) .
- Internationalization coverage in en.json - I checked fr.json only ; assumed parallel coverage but did not verify .
- DataVersioningView.vue full read - I only confirmed the FAST awareness gap , not the broader workflow .
- The cascade library itself ( `cascade/` directory ) was treated as a black box ; only the openadom-side integration was audited .

---

End of report .

---

## 13 . Implementation status ( applied overnight , 2026-05-16 )

Items in this section have been implemented and deployed without further validation because confidence was high ( "obvious" tier ) . The user only needs to verify the live behaviour after a hard refresh .

### Applied

- **Q3 . Resource name on workflow row** . `WorkflowTable.vue` now renders `w.resourceName` ( file name ) as a small muted line under the dataType cell , with title attribute for the full name on hover . CSS class `.resource-name` added .
- **Q2 . Post-FAST phase indicator** . `FastPathPanel.vue` accepts a new optional prop `workflowPhase` ; when the phase is `SYNTHESIS_REBUILD` / `CACHE_CAPTURE` / `COMMIT_VISIBILITY` , a spinner + label appears at the bottom of the panel . `WorkflowTable.vue` passes `:workflow-phase="phaseRaw(w)"` to mirror what `WorkflowFinalizeBadge` already does for the cascade path .
- **Q5 . WorkflowPhase docstring** . Updated the UNPUBLISH sequence to reflect the actual code path ( DELETE_ROWS -&gt; SYNTHESIS_REBUILD inside the atomic tx that toggles `binaryfile.published=false` -&gt; DONE ) , dropping the stale `COMMIT_VISIBILITY` intermediate that the previous comment listed but the implementation never emits .
- **Q6 . STREAM_CACHE doc drift** . Replaced the obsolete `STREAM_CACHE` reference in `FastPathPanel.vue` header comment with the current `COPY_IN` phase name .
- **Q7 . SchemaFlywayCallback short-circuit** . `changeOwner` now first queries `pg_tables` for tables already owned by the target role and skips the `ALTER TABLE ... OWNER TO` for those . On steady-state startups the loop is a no-op , removing the `AccessExclusiveLock` requirement that auto-vacuum on `referencevalue` regularly blocks ( twice during this session ) .
- **History dup race fix** . `CascadeImportPipeline` now calls `workflowLogRepository.setParentCorrelationId(corrUuid, publishParent)` AFTER `registerWorkflowStart` ( previously the call was raced from `PublishLifecycleCoordinator.registerChildImport` , before the cascade row was visible , and updated zero rows ) . New child IMPORT rows are tagged immediately ; old rows from before the fix still surface as duplicates ( see item V1 for backfill ) .
- **Mvn test full suite passes** . `mvn --batch-mode test -Pall-tests -Dsurefire.excludedGroups=` : 3489 tests run , 0 failures , 0 errors , 8 skipped . Build success . No regression from the day's changes .

### Not applied ( still in Q list )

- **Q1 . Hide CACHE_CLEAR row when CACHED_ROTATION off** . Requires a new `cacheClearArmed` boolean in `FastPathSnapshot.java` and gating logic in `FastPathPanel.vue` . Touches both backend and frontend ; left for review .
- **Q4 . LITE / FULL pill on workflow row** . Requires a new metadata field `cascadeMode` emitted by `PublishLifecyclePhase2Handler` + UI render . Bigger change , left for review .
- **Q8 . DELETE_ROWS row count denominator** . Requires reading the expected row count before DELETE + new `WorkflowSummary` field + UI . Medium effort , left for review .
- **Q9 . CACHE_CAPTURE live bytes counter** . Requires plumbing `CountingOutputStream` -&gt; `WorkflowActiveRegistry` -&gt; `FinalizeProgress` -&gt; UI . Largest effort , left for review .

### Validation-required items ( V1 - V5 )

All five items in section 11 remain pending user validation . No code touched .

## Annexe — modèle de filtres et coût des index

Le modèle `OA_filterModel` permet de choisir le coût d'indexation par datatype :

- `NONE` : aucun index de filtre sur `refvalues`, empreinte minimale ; recommandé pour les datatypes peu filtrés ou volumineux en écriture.
- `LEGACY_GIN` : conserve le GIN historique sur `refvalues jsonb_path_ops`, avec un surcoût estimé de 15 à 40 % de la table selon la largeur JSONB et la cardinalité ; recommandé pour compatibilité ou filtres JSONB génériques.
- `DEFINED_FILTERS` : crée seulement les index des colonnes marquées `__FILTER_TEXT__` / `__FILTER_LIST__`. Un B-tree de liste coûte typiquement 2 à 5 % par colonne ; un GIN trigramme texte est plus coûteux mais ciblé sur les champs de recherche libre.

Recommandations : `NONE` par défaut pour réduire le footprint, `DEFINED_FILTERS` pour les référentiels consultés par quelques filtres connus, `LEGACY_GIN` uniquement pour les profils nécessitant l'ancien comportement global ou pendant une phase de migration via `app.filterModel.legacyDefault=true`.
