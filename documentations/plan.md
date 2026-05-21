==========================================================================================
PLAN DE CORRECTION EXHAUSTIF -- 566 issues en nouvelle periode de code
==========================================================================================

######################################################################
### SEVERITE : BLOCKER
######################################################################

  - [x] java:S2699  (15 occurrences) --
    L   71  TEST/fr/inra/oresing/workflow/cascade/preparation/PreparationPhaseListenerInterceptorTest.java
           -> Add at least one assertion to this test case.
    L   77  TEST/fr/inra/oresing/workflow/cascade/ImportRateLimiterTest.java
           -> Add at least one assertion to this test case.
    L   85  TEST/fr/inra/oresing/workflow/cascade/ImportRateLimiterTest.java
           -> Add at least one assertion to this test case.
    L   95  TEST/fr/inra/oresing/workflow/cascade/ImportRateLimiterTest.java
           -> Add at least one assertion to this test case.
    L  170  TEST/fr/inra/oresing/workflow/cascade/ImportRateLimiterTest.java
           -> Add at least one assertion to this test case.
    L  182  TEST/fr/inra/oresing/workflow/cascade/ImportRateLimiterTest.java
           -> Add at least one assertion to this test case.
    L   95  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Add at least one assertion to this test case.
    L   78  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowZombieSweeperTest.java
           -> Add at least one assertion to this test case.
    L   37  TEST/fr/inra/oresing/rest/security/SpaCsrfTokenRequestHandlerTest.java
           -> Add at least one assertion to this test case.
    L   50  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogWriterTest.java
           -> Add at least one assertion to this test case.
    L   67  TEST/fr/inra/oresing/workflow/cascade/progress/LoggingImportProgressReporterTest.java
           -> Add at least one assertion to this test case.
    L   74  TEST/fr/inra/oresing/workflow/cascade/progress/LoggingImportProgressReporterTest.java
           -> Add at least one assertion to this test case.
    L   84  TEST/fr/inra/oresing/workflow/cascade/progress/LoggingImportProgressReporterTest.java
           -> Add at least one assertion to this test case.
    L  111  TEST/fr/inra/oresing/workflow/cascade/progress/LoggingImportProgressReporterTest.java
           -> Add at least one assertion to this test case.
    L  144  TEST/fr/inra/oresing/workflow/cascade/progress/LoggingImportProgressReporterTest.java
           -> Add at least one assertion to this test case.

  - [x] java:S2229  (1 occurrences) --
    L   63  fr/inra/oresing/monitoring/compensation/CompensationLogService.java
           -> "record's" @Transactional requirement is incompatible with the one for this method.

  - [x] java:S2095  (1 occurrences) --
    L  498  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Use try-with-resources or close this "LargeObject" in a "finally" clause.

  - [x] java:S2695  (1 occurrences) --
    L  123  fr/inra/oresing/persistence/refref/RefrefRebuildSql.java
           -> This "PreparedStatement" has no parameters.

  - [x] java:S1845  (1 occurrences) --
    L   46  fr/inra/oresing/workflow/cascade/config/CascadeRuntimeOverride.java
           -> Rename method "empty" to prevent any misunderstanding/clash with field "EMPTY".

######################################################################
### SEVERITE : CRITICAL
######################################################################

  -- java:S1192  (50 occurrences) --
    L  605  fr/inra/oresing/workflow/cascade/history/WorkflowActiveRegistry.java
           -> Define a constant instead of duplicating this literal "RUNNING" 6 times.
    L  131  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal "application_name" 3 times.
    L  133  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal "status" 4 times.
    L  138  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal "metadata" 5 times.
    L  200  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal "IN_PROGRESS" 3 times.
    L  213  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal "CANCELLED" 4 times.
    L  213  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal "FAILED" 4 times.
    L  258  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal "\".referencevalue WHERE binaryfile = ?" 3 times.
    L  437  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Define a constant instead of duplicating this literal " WHERE correlation_id = ?" 3 times.
    L  100  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Define a constant instead of duplicating this literal "datatype" 3 times.
    L  472  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Define a constant instead of duplicating this literal "SELECT processed_data FROM %s WHERE id = ?::uuid" 3 tim
    L   60  fr/inra/oresing/persistence/DataRepository.java
           -> Define a constant instead of duplicating this literal "binaryFile" 3 times.
    L  782  fr/inra/oresing/persistence/DataRepository.java
           -> Define a constant instead of duplicating this literal "naturalkey" 3 times.
    L  783  fr/inra/oresing/persistence/DataRepository.java
           -> Define a constant instead of duplicating this literal "hierarchicalkey" 3 times.
    L 1015  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Define a constant instead of duplicating this literal "Workflow not found : " 3 times.
    L   81  fr/inra/oresing/rest/exceptions/OreExceptionHandler.java
           -> Define a constant instead of duplicating this literal "message" 3 times.
    L  109  fr/inra/oresing/workflow/cascade/pipeline/PipelineRegistry.java
           -> Define a constant instead of duplicating this literal "RUNNING" 4 times.
    L  129  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal "applicationName" 5 times.
    L  140  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal ".referencevalue_count_stats" 4 times.
    L  140  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal "SELECT count(*) FROM " 7 times.
    L  146  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal "lastUpdate" 3 times.
    L  150  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal "error" 4 times.
    L  159  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal ".data_versioning_scope_cache" 4 times.
    L  182  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal ".oresisynthesis" 4 times.
    L  232  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal " AND " 6 times.
    L  232  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal " WHERE " 6 times.
    L  430  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal ".\"binaryfile\"" 3 times.
    L  476  fr/inra/oresing/rest/AdminSystemResources.java
           -> Define a constant instead of duplicating this literal "fileId" 3 times.
    L  112  fr/inra/oresing/rest/CacheAdminResources.java
           -> Define a constant instead of duplicating this literal "ttlMinutes" 4 times.
    L  140  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Define a constant instead of duplicating this literal "dataName" 3 times.
    L  339  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Define a constant instead of duplicating this literal "wasPublished" 3 times.
    L  339  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Define a constant instead of duplicating this literal "fileId" 3 times.
    L   66  fr/inra/oresing/rest/users/UserRolesService.java
           -> Define a constant instead of duplicating this literal "applicationManager" 5 times.
    L   66  fr/inra/oresing/rest/users/UserRolesService.java
           -> Define a constant instead of duplicating this literal "reader" 5 times.
    L   66  fr/inra/oresing/rest/users/UserRolesService.java
           -> Define a constant instead of duplicating this literal "userManager" 7 times.
    L   66  fr/inra/oresing/rest/users/UserRolesService.java
           -> Define a constant instead of duplicating this literal "writer" 5 times.
    L   70  fr/inra/oresing/rest/users/UserRolesService.java
           -> Define a constant instead of duplicating this literal "openAdomAdmin" 3 times.
    L  317  fr/inra/oresing/rest/users/UserRolesService.java
           -> Define a constant instead of duplicating this literal "Unknown global role: " 3 times.
    L  322  fr/inra/oresing/rest/users/UserRolesService.java
           -> Define a constant instead of duplicating this literal "Unknown application role: " 3 times.
    L   64  fr/inra/oresing/persistence/DataVersioningScopeCacheRepository.java
           -> Define a constant instead of duplicating this literal "application" 5 times.
    L   65  fr/inra/oresing/persistence/DataVersioningScopeCacheRepository.java
           -> Define a constant instead of duplicating this literal "referenceType" 3 times.
    L   67  fr/inra/oresing/persistence/DataVersioningScopeCacheRepository.java
           -> Define a constant instead of duplicating this literal "userId" 3 times.
    L   70  fr/inra/oresing/workflow/cascade/metrics/OpenadomCacheMetrics.java
           -> Define a constant instead of duplicating this literal "_size" 3 times.
    L   79  fr/inra/oresing/workflow/cascade/metrics/OpenadomCacheMetrics.java
           -> Define a constant instead of duplicating this literal "_max_entries" 3 times.
    L   87  fr/inra/oresing/workflow/cascade/metrics/OpenadomCacheMetrics.java
           -> Define a constant instead of duplicating this literal "_hit_total" 3 times.
    L   89  fr/inra/oresing/workflow/cascade/metrics/OpenadomCacheMetrics.java
           -> Define a constant instead of duplicating this literal "_miss_total" 3 times.
    L   91  fr/inra/oresing/workflow/cascade/metrics/OpenadomCacheMetrics.java
           -> Define a constant instead of duplicating this literal "_invalidate_total" 3 times.
    L  246  fr/inra/oresing/rest/CacheAdminResources.java
           -> Define a constant instead of duplicating this literal "enabled" 3 times.
    L  247  fr/inra/oresing/rest/CacheAdminResources.java
           -> Define a constant instead of duplicating this literal "maxEntries" 4 times.
    L  249  fr/inra/oresing/rest/CacheAdminResources.java
           -> Define a constant instead of duplicating this literal "entries" 3 times.

  -- java:S3776  (17 occurrences) --
    L  282  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Refactor this method to reduce its Cognitive Complexity from 19 to the 15 allowed.
    L   27  fr/inra/oresing/domain/application/configuration/Submission.java
           -> Refactor this method to reduce its Cognitive Complexity from 28 to the 15 allowed.
    L  199  fr/inra/oresing/domain/data/deposit/DataImporter.java
           -> Refactor this method to reduce its Cognitive Complexity from 33 to the 15 allowed.
    L  353  fr/inra/oresing/domain/data/deposit/context/AsynchroneFileImporterContext.java
           -> Refactor this method to reduce its Cognitive Complexity from 17 to the 15 allowed.
    L  133  fr/inra/oresing/rest/BundleResources.java
           -> Refactor this method to reduce its Cognitive Complexity from 21 to the 15 allowed.
    L  992  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Refactor this method to reduce its Cognitive Complexity from 16 to the 15 allowed.
    L  328  fr/inra/oresing/rest/data/DataService.java
           -> Refactor this method to reduce its Cognitive Complexity from 26 to the 15 allowed.
    L  533  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Refactor this method to reduce its Cognitive Complexity from 20 to the 15 allowed.
    L  142  fr/inra/oresing/workflow/cascade/history/WorkflowLogRepository.java
           -> Refactor this method to reduce its Cognitive Complexity from 24 to the 15 allowed.
    L  227  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Refactor this method to reduce its Cognitive Complexity from 22 to the 15 allowed.
    L   81  fr/inra/oresing/cache/CachePreloader.java
           -> Refactor this method to reduce its Cognitive Complexity from 19 to the 15 allowed.
    L  176  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Refactor this method to reduce its Cognitive Complexity from 16 to the 15 allowed.
    L  485  fr/inra/oresing/domain/data/deposit/DataImporter.java
           -> Refactor this method to reduce its Cognitive Complexity from 33 to the 15 allowed.
    L  137  fr/inra/oresing/domain/groovy/GroovyExpression.java
           -> Refactor this method to reduce its Cognitive Complexity from 18 to the 15 allowed.
    L  108  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Refactor this method to reduce its Cognitive Complexity from 42 to the 15 allowed.
    L  184  fr/inra/oresing/persistence/DataRepository.java
           -> Refactor this method to reduce its Cognitive Complexity from 25 to the 15 allowed.
    L  104  fr/inra/oresing/workflow/cascade/CascadeSinkFactory.java
           -> Refactor this method to reduce its Cognitive Complexity from 29 to the 15 allowed.

  -- java:S1452  (7 occurrences) --
    L  178  fr/inra/oresing/domain/data/DataDatum.java
           -> Remove usage of generic wildcard type.
    L  204  fr/inra/oresing/domain/data/deposit/context/DataImporterContext.java
           -> Remove usage of generic wildcard type.
    L  211  fr/inra/oresing/domain/data/deposit/context/DataImporterContext.java
           -> Remove usage of generic wildcard type.
    L  133  fr/inra/oresing/rest/BundleResources.java
           -> Remove usage of generic wildcard type.
    L  369  fr/inra/oresing/workflow/cascade/config/ConfigFieldRegistry.java
           -> Remove usage of generic wildcard type.
    L  376  fr/inra/oresing/workflow/cascade/config/ConfigFieldRegistry.java
           -> Remove usage of generic wildcard type.
    L   18  fr/inra/oresing/domain/authorization/DomainUserDetails.java
           -> Remove usage of generic wildcard type.

  - [x] java:S1948  (3 occurrences) --
    L   26  fr/inra/oresing/domain/BinaryFile.java
           -> Make "processedData" transient or serializable.
    L   21  fr/inra/oresing/domain/rightsrequest/RightsRequest.java
           -> Make "rightsRequestForm" private or transient.
    L   44  fr/inra/oresing/domain/rightsrequest/RightsRequest.java
           -> Make "linkedAuthorizationIds" private or transient.

  - [x] java:S1186  (2 occurrences) --
    L   71  TEST/fr/inra/oresing/workflow/cascade/config/CascadePoolReloaderTest.java
           -> Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete t
    L   83  TEST/fr/inra/oresing/workflow/cascade/config/CascadePoolReloaderTest.java
           -> Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete t

  - [x] java:S2479  (1 occurrences) --
    L  385  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Remove the non-escaped \uFEFF character from this literal.

  - [x] java:S2235  (1 occurrences) --
    L  278  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Refactor this piece of code to not catch IllegalMonitorStateException

  - [x] java:S5845  (1 occurrences) --
    L  229  TEST/fr/inra/oresing/rest/model/data/DownloadDatasetQueryAdvancedSearchTest.java
           -> Change the assertion arguments to not compare dissimilar types.

######################################################################
### SEVERITE : MAJOR
######################################################################

  -- java:S125  (25 occurrences) --
    L  373  TEST/fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanServiceTest.java
           -> This block of commented-out lines of code should be removed.
    L  361  fr/inra/oresing/persistence/DataRepository.java
           -> This block of commented-out lines of code should be removed.
    L  619  fr/inra/oresing/persistence/DataRepository.java
           -> This block of commented-out lines of code should be removed.
    L  242  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> This block of commented-out lines of code should be removed.
    L  995  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> This block of commented-out lines of code should be removed.
    L 1383  fr/inra/oresing/rest/data/DataService.java
           -> This block of commented-out lines of code should be removed.
    L  181  fr/inra/oresing/rest/services/RightsRequestService.java
           -> This block of commented-out lines of code should be removed.
    L  479  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> This block of commented-out lines of code should be removed.
    L  281  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> This block of commented-out lines of code should be removed.
    L  410  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> This block of commented-out lines of code should be removed.
    L  147  fr/inra/oresing/workflow/cascade/history/WorkflowLogRepository.java
           -> This block of commented-out lines of code should be removed.
    L  702  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> This block of commented-out lines of code should be removed.
    L  384  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> This block of commented-out lines of code should be removed.
    L  158  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> This block of commented-out lines of code should be removed.
    L  200  fr/inra/oresing/rest/usecases/storage/versioning/ConfigHashService.java
           -> This block of commented-out lines of code should be removed.
    L  431  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> This block of commented-out lines of code should be removed.
    L  144  fr/inra/oresing/workflow/cascade/StagingUpsertSqlBuilder.java
           -> This block of commented-out lines of code should be removed.
    L  608  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> This block of commented-out lines of code should be removed.
    L  616  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> This block of commented-out lines of code should be removed.
    L   91  fr/inra/oresing/rest/services/AuthorizationService.java
           -> This block of commented-out lines of code should be removed.
    L  850  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> This block of commented-out lines of code should be removed.
    L  245  fr/inra/oresing/workflow/extraction/ExtractionLifecycle.java
           -> This block of commented-out lines of code should be removed.
    L  256  fr/inra/oresing/rest/services/ApplicationService.java
           -> This block of commented-out lines of code should be removed.
    L  948  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> This block of commented-out lines of code should be removed.
    L  136  fr/inra/oresing/workflow/cascade/history/WorkflowLogEntry.java
           -> This block of commented-out lines of code should be removed.

  -- java:S6813  (21 occurrences) --
    L  189  fr/inra/oresing/rest/data/DataService.java
           -> Remove this field injection and use constructor injection instead.
    L  103  fr/inra/oresing/rest/binaryFile/BinaryFileService.java
           -> Remove this field injection and use constructor injection instead.
    L   91  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Remove this field injection and use constructor injection instead.
    L  102  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Remove this field injection and use constructor injection instead.
    L  110  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Remove this field injection and use constructor injection instead.
    L  154  fr/inra/oresing/rest/data/DataService.java
           -> Remove this field injection and use constructor injection instead.
    L  170  fr/inra/oresing/rest/data/DataService.java
           -> Remove this field injection and use constructor injection instead.
    L  180  fr/inra/oresing/rest/data/DataService.java
           -> Remove this field injection and use constructor injection instead.
    L 1411  fr/inra/oresing/rest/data/DataService.java
           -> Remove this field injection and use constructor injection instead.
    L   81  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Remove this field injection and use constructor injection instead.
    L   92  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Remove this field injection and use constructor injection instead.
    L  105  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Remove this field injection and use constructor injection instead.
    L  131  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Remove this field injection and use constructor injection instead.
    L  144  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Remove this field injection and use constructor injection instead.
    L  154  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Remove this field injection and use constructor injection instead.
    L  167  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Remove this field injection and use constructor injection instead.
    L  104  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Remove this field injection and use constructor injection instead.
    L  106  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Remove this field injection and use constructor injection instead.
    L  118  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Remove this field injection and use constructor injection instead.
    L  132  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Remove this field injection and use constructor injection instead.
    L  490  fr/inra/oresing/rest/services/AuthorizationService.java
           -> Remove this field injection and use constructor injection instead.

  -- java:S5778  (21 occurrences) --
    L  196  TEST/fr/inra/oresing/domain/data/deposit/prescan/PrescanAxeBFlowRegressionTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  110  TEST/fr/inra/oresing/workflow/cascade/preparation/DeferredFileChunkSourceTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  126  TEST/fr/inra/oresing/workflow/cascade/preparation/DeferredFileChunkSourceTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  128  TEST/fr/inra/oresing/workflow/cascade/preparation/DeferredFileChunkSourceTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L   91  TEST/fr/inra/oresing/workflow/phase/PhaseScopeTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  137  TEST/fr/inra/oresing/rest/data/LazyDisplayNamesMapTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  266  TEST/fr/inra/oresing/domain/checker/type/AbstractMapTypeExtendedTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  142  TEST/fr/inra/oresing/cache/MemoryCacheTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L   68  TEST/fr/inra/oresing/monitoring/compensation/handlers/BinaryFileCompensationHandlerTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L   78  TEST/fr/inra/oresing/monitoring/compensation/handlers/BinaryFileCompensationHandlerTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  139  TEST/fr/inra/oresing/monitoring/compensation/handlers/BinaryFileCompensationHandlerTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L   39  TEST/fr/inra/oresing/rest/data/publication/DataVersioningResultTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L   64  TEST/fr/inra/oresing/monitoring/compensation/CompensationHandlerRegistryTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  157  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  175  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  194  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  258  TEST/fr/inra/oresing/workflow/cascade/config/ConfigFieldTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  268  TEST/fr/inra/oresing/workflow/cascade/config/ConfigFieldTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  252  TEST/fr/inra/oresing/domain/application/configuration/NodeBuildTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L  156  TEST/fr/inra/oresing/workflow/cascade/MergedFileChunkCollectorTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.
    L   97  TEST/fr/inra/oresing/workflow/cascade/StoreAllPathSinkTest.java
           -> Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception.

  -- java:S107  (17 occurrences) --
    L   89  fr/inra/oresing/domain/checker/type/ReferenceType.java
           -> Constructor has 8 parameters, which is greater than 7 authorized.
    L  209  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Method has 8 parameters, which is greater than 7 authorized.
    L  233  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Method has 9 parameters, which is greater than 7 authorized.
    L  176  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Method has 8 parameters, which is greater than 7 authorized.
    L  284  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Method has 8 parameters, which is greater than 7 authorized.
    L  296  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Method has 13 parameters, which is greater than 7 authorized.
    L  310  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Method has 9 parameters, which is greater than 7 authorized.
    L  313  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Method has 12 parameters, which is greater than 7 authorized.
    L  421  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Method has 10 parameters, which is greater than 7 authorized.
    L  299  fr/inra/oresing/mail/rightsrequest/RightsRequestNotificationService.java
           -> Method has 8 parameters, which is greater than 7 authorized.
    L  190  fr/inra/oresing/workflow/extraction/ExtractionLifecycle.java
           -> Constructor has 11 parameters, which is greater than 7 authorized.
    L 1473  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Method has 17 parameters, which is greater than 7 authorized.
    L 1403  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Method has 15 parameters, which is greater than 7 authorized.
    L  220  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Method has 8 parameters, which is greater than 7 authorized.
    L  249  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Method has 9 parameters, which is greater than 7 authorized.
    L  203  fr/inra/oresing/workflow/cascade/metrics/OpenadomMetrics.java
           -> Method has 8 parameters, which is greater than 7 authorized.
    L   47  fr/inra/oresing/workflow/cascade/MergeFileDeferredRunner.java
           -> Constructor has 10 parameters, which is greater than 7 authorized.

  -- java:S2259  (13 occurrences) --
    L  531  fr/inra/oresing/rest/OreSiResources.java
           -> A "NullPointerException" could be thrown; "binaryFileDataset" is nullable here.
    L  177  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  178  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  179  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  180  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  181  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  182  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  183  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  184  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L  185  fr/inra/oresing/OreSiNg.java
           -> A "NullPointerException" could be thrown; "ofNullable()" can return null.
    L   91  fr/inra/oresing/domain/application/configuration/date/DatePattern.java
           -> A "NullPointerException" could be thrown; "date" is nullable here.
    L   96  fr/inra/oresing/domain/application/configuration/date/DatePattern.java
           -> A "NullPointerException" could be thrown; "date" is nullable here.
    L  302  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> A "NullPointerException" could be thrown; "binaryFileDataset" is nullable here.

  -- java:S1141  (13 occurrences) --
    L  590  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Extract this nested try block into a separate method.
    L  607  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Extract this nested try block into a separate method.
    L  298  fr/inra/oresing/persistence/DataRepository.java
           -> Extract this nested try block into a separate method.
    L  554  fr/inra/oresing/rest/data/DataService.java
           -> Extract this nested try block into a separate method.
    L  117  fr/inra/oresing/workflow/cascade/DataImporterTransformation.java
           -> Extract this nested try block into a separate method.
    L  229  fr/inra/oresing/workflow/cascade/history/WorkflowZombieSweeper.java
           -> Extract this nested try block into a separate method.
    L  102  fr/inra/oresing/rest/usecases/storage/versioning/CacheCaptureService.java
           -> Extract this nested try block into a separate method.
    L  227  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Extract this nested try block into a separate method.
    L  281  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Extract this nested try block into a separate method.
    L  328  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Extract this nested try block into a separate method.
    L  215  fr/inra/oresing/persistence/DataRepository.java
           -> Extract this nested try block into a separate method.
    L  271  fr/inra/oresing/persistence/DataRepository.java
           -> Extract this nested try block into a separate method.
    L  283  fr/inra/oresing/persistence/DataRepository.java
           -> Extract this nested try block into a separate method.

  -- java:S112  (9 occurrences) --
    L  511  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L  615  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L 1664  fr/inra/oresing/rest/data/DataService.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L  304  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L  291  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L  143  fr/inra/oresing/workflow/phase/PhaseScope.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L  148  fr/inra/oresing/workflow/phase/PhaseScope.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L  264  fr/inra/oresing/rest/usecases/storage/versioning/PublishFastPathDirectExecutor.java
           -> Define and throw a dedicated exception instead of using a generic one.
    L  284  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Define and throw a dedicated exception instead of using a generic one.

  -- java:S4738  (8 occurrences) --
    L   30  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use "java.util.List.of()" instead.
    L   40  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use "java.util.List.of()" instead.
    L   50  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use "java.util.List.of()" instead.
    L   58  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use "java.util.List.of()" instead.
    L   66  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use "java.util.List.of()" instead.
    L   74  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use "java.util.List.of()" instead.
    L   84  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use "java.util.List.of()" instead.
    L   71  TEST/fr/inra/oresing/domain/data/deposit/PipelineContextRecordsTest.java
           -> Use "java.util.List.of()" instead.

  -- java:S1068  (7 occurrences) --
    L   89  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleCoordinator.java
           -> Remove this unused "workflowLogRepository" private field.
    L  193  fr/inra/oresing/rest/OreSiResources.java
           -> Remove this unused "getReferencedBinaryFilesUseCase" private field.
    L  172  TEST/fr/inra/oresing/domain/groovy/GroovyAndMailEnumsTest.java
           -> Remove this unused "ctx" private field.
    L   46  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Remove this unused "tpe" private field.
    L   96  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleService.java
           -> Remove this unused "coordinator" private field.
    L   63  fr/inra/oresing/workflow/extraction/ExtractionLifecycle.java
           -> Remove this unused "repository" private field.
    L  184  fr/inra/oresing/workflow/extraction/ExtractionLifecycle.java
           -> Remove this unused "bytesTotal" private field.

  -- java:S6213  (6 occurrences) --
    L  334  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Rename this variable to not match a restricted identifier.
    L  515  fr/inra/oresing/domain/data/deposit/DataImporter.java
           -> Rename this variable to not match a restricted identifier.
    L  119  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Rename this variable to not match a restricted identifier.
    L  155  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Rename this variable to not match a restricted identifier.
    L   42  TEST/fr/inra/oresing/domain/data/deposit/PipelineContextRecordsTest.java
           -> Rename this variable to not match a restricted identifier.
    L   70  TEST/fr/inra/oresing/domain/data/deposit/PipelineContextRecordsTest.java
           -> Rename this variable to not match a restricted identifier.

  -- java:S6126  (6 occurrences) --
    L  351  TEST/fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanServiceTest.java
           -> Replace this String concatenation with Text block.
    L  389  TEST/fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanServiceTest.java
           -> Replace this String concatenation with Text block.
    L  408  TEST/fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanServiceTest.java
           -> Replace this String concatenation with Text block.
    L  483  TEST/fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanServiceTest.java
           -> Replace this String concatenation with Text block.
    L   84  TEST/fr/inra/oresing/domain/data/deposit/prescan/PrescanAxeBFlowRegressionTest.java
           -> Replace this String concatenation with Text block.
    L  228  fr/inra/oresing/rest/CacheAdminResources.java
           -> Replace this String concatenation with Text block.

  -- java:S3740  (6 occurrences) --
    L   63  fr/inra/oresing/domain/checker/type/PatternType.java
           -> Provide the parametrized type for this generic.
    L  514  fr/inra/oresing/persistence/DataRepository.java
           -> Provide the parametrized type for this generic.
    L 1178  fr/inra/oresing/persistence/DataRepository.java
           -> Provide the parametrized type for this generic.
    L  402  fr/inra/oresing/rest/services/ApplicationService.java
           -> Provide the parametrized type for this generic.
    L  452  fr/inra/oresing/rest/services/ApplicationService.java
           -> Provide the parametrized type for this generic.
    L  471  fr/inra/oresing/rest/services/ApplicationService.java
           -> Provide the parametrized type for this generic.

  -- java:S1144  (6 occurrences) --
    L  319  fr/inra/oresing/rest/data/DataService.java
           -> Remove this unused private "addData" method.
    L  621  fr/inra/oresing/workflow/cascade/history/WorkflowLogRepository.java
           -> Remove this unused private "extractEntryArgs" method.
    L  747  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Remove this unused private "preCountAndPublishRecordsTotal" method.
    L  128  fr/inra/oresing/persistence/LargeObjectOrphanSweeper.java
           -> Remove this unused private "quoteIdent" method.
    L  361  fr/inra/oresing/rest/OreSiResources.java
           -> Remove this unused private "logExtractionEvent" method.
    L 1403  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Remove this unused private "logImportEvent" method.

  -- java:S6485  (5 occurrences) --
    L  232  fr/inra/oresing/domain/checker/LineChecker.java
           -> Replace this call to the constructor with the better suited static method HashMap.newHashMap(int numMappings)
    L  141  fr/inra/oresing/domain/checker/type/ReferenceType.java
           -> Replace this call to the constructor with the better suited static method HashMap.newHashMap(int numMappings)
    L  253  fr/inra/oresing/domain/data/deposit/context/AsynchroneFileImporterContext.java
           -> Replace this call to the constructor with the better suited static method HashMap.newHashMap(int numMappings)
    L   60  fr/inra/oresing/domain/groovy/GroovyExpression.java
           -> Replace this call to the constructor with the better suited static method HashMap.newHashMap(int numMappings)
    L  869  fr/inra/oresing/persistence/DataRepository.java
           -> Replace this call to the constructor with the better suited static method HashMap.newHashMap(int numMappings)

  -- java:S1168  (5 occurrences) --
    L  847  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Return an empty map instead of null.
    L  851  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Return an empty map instead of null.
    L  854  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> Return an empty map instead of null.
    L   95  fr/inra/oresing/rest/data/LazyDisplayNamesMap.java
           -> Return an empty map instead of null.
    L  206  fr/inra/oresing/rest/FileResources.java
           -> Return an empty collection instead of null.

  -- java:S2925  (5 occurrences) --
    L   63  TEST/fr/inra/oresing/cache/MemoryCacheTest.java
           -> Remove this use of "Thread.sleep()".
    L  147  TEST/fr/inra/oresing/cache/MemoryCacheTest.java
           -> Remove this use of "Thread.sleep()".
    L   86  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this use of "Thread.sleep()".
    L   89  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this use of "Thread.sleep()".
    L  120  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this use of "Thread.sleep()".

  -- java:S1172  (4 occurrences) --
    L  393  fr/inra/oresing/rest/users/UserRolesService.java
           -> Remove this unused method parameter "roleKeyName".
    L  256  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Remove this unused method parameter "idJsonPath".
    L  890  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Remove these unused method parameters "endTime", "duration", "recordsProcessed".
    L  826  fr/inra/oresing/rest/services/AuthorizationService.java
           -> Remove these unused method parameters "publicAuthorizations", "authorizationsForUser".

  -- java:S4449  (3 occurrences) --
    L  495  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Annotate the parameter with @javax.annotation.Nullable in method 'getConnection' declaration, or make sure tha
    L  576  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Annotate the parameter with @javax.annotation.Nullable in constructor declaration, or make sure that null can
    L  693  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Annotate the parameter with @javax.annotation.Nullable in constructor declaration, or make sure that null can

  -- java:S6916  (3 occurrences) --
    L  111  fr/inra/oresing/rest/model/configuration/builder/ReferenceGraphBuilder.java
           -> Replace this "if" statement with a pattern match guard.
    L  117  fr/inra/oresing/rest/model/configuration/builder/ReferenceGraphBuilder.java
           -> Replace this "if" statement with a pattern match guard.
    L  124  fr/inra/oresing/rest/model/configuration/builder/ReferenceGraphBuilder.java
           -> Replace this "if" statement with a pattern match guard.

  -- java:S6355  (2 occurrences) --
    L  488  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Add 'since' and/or 'forRemoval' arguments to the @Deprecated annotation.
    L   60  fr/inra/oresing/workflow/cascade/history/FastPathSnapshot.java
           -> Add 'since' and/or 'forRemoval' arguments to the @Deprecated annotation.

  -- java:S2583  (2 occurrences) --
    L  322  fr/inra/oresing/persistence/DataRepository.java
           -> Change this condition so that it does not always evaluate to "false"
    L  142  fr/inra/oresing/rest/usecases/admin/BuildCacheService.java
           -> Change this condition so that it does not always evaluate to "false"

  -- java:S2159  (2 occurrences) --
    L  353  fr/inra/oresing/rest/AuthorizationResources.java
           -> Remove this call to "equals"; comparisons between unrelated types always return false.
    L  358  fr/inra/oresing/rest/AuthorizationResources.java
           -> Remove this call to "equals"; comparisons between unrelated types always return false.

  -- java:S6885  (2 occurrences) --
    L  113  fr/inra/oresing/cache/CachePreloader.java
           -> Use "Math.clamp" instead of "Math.min" or "Math.max".
    L  158  fr/inra/oresing/cache/CachePreloader.java
           -> Use "Math.clamp" instead of "Math.min" or "Math.max".

  -- java:S5976  (2 occurrences) --
    L  131  TEST/fr/inra/oresing/rest/usecases/storage/versioning/ConfigHashServiceTest.java
           -> Replace these 3 tests with a single Parameterized one.
    L  153  TEST/fr/inra/oresing/domain/authorization/request/AuthorizationInputDomainTest.java
           -> Replace these 3 tests with a single Parameterized one.

  -- java:S5738  (2 occurrences) --
    L  229  TEST/fr/inra/oresing/rest/model/data/DownloadDatasetQueryAdvancedSearchTest.java
           -> Remove this call to a deprecated class, it has been marked for removal.
    L  229  TEST/fr/inra/oresing/rest/model/data/DownloadDatasetQueryAdvancedSearchTest.java
           -> Remove this call to a deprecated class, it has been marked for removal.

  -- java:S2629  (1 occurrences) --
    L  110  fr/inra/oresing/domain/application/configuration/Ltree.java
           -> Invoke method(s) only conditionally.

  -- java:S5164  (1 occurrences) --
    L   59  fr/inra/oresing/domain/groovy/GroovyExpression.java
           -> Call "remove()" on "EVAL_SCRATCH".

  -- java:S6809  (1 occurrences) --
    L   63  fr/inra/oresing/monitoring/compensation/CompensationLogService.java
           -> Call transactional methods via an injected dependency instead of directly via 'this'.

  -- java:S3358  (1 occurrences) --
    L  199  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Extract this nested ternary operation into an independent statement.

  -- java:S4274  (1 occurrences) --
    L  323  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Replace this assert with a proper check.

  -- java:S1123  (1 occurrences) --
    L 1517  fr/inra/oresing/persistence/DataRepository.java
           -> Add the missing @deprecated Javadoc tag.

  -- java:S6856  (1 occurrences) --
    L  434  fr/inra/oresing/rest/AuthorizationResources.java
           -> Bind path variable "nameOrId" to a method parameter.

  -- java:S1117  (1 occurrences) --
    L  231  fr/inra/oresing/rest/BundleResources.java
           -> Rename "mapper" which hides the field declared at line 98.

  -- java:S1066  (1 occurrences) --
    L  115  fr/inra/oresing/persistence/ByteaSubstringInputStream.java
           -> Merge this if statement with the enclosing one.

  -- java:S6207  (1 occurrences) --
    L   57  fr/inra/oresing/domain/data/rapport/BundleReport.java
           -> Remove this redundant constructor which is the same as a default one.

  -- java:S2201  (1 occurrences) --
    L   86  fr/inra/oresing/domain/application/SqlIdentifierUtils.java
           -> The return value of "orElseThrow" must be used.

  -- java:S1854  (1 occurrences) --
    L  180  TEST/fr/inra/oresing/rest/model/authorization/AuthorizationModelTest.java
           -> Remove this useless assignment to local variable "uid".

  -- java:S3415  (1 occurrences) --
    L   53  TEST/fr/inra/oresing/domain/application/configuration/type/CheckerEnumTest.java
           -> Swap these 2 arguments so they are in the correct order: actual value, expected value.

######################################################################
### SEVERITE : MINOR
######################################################################

  -- java:S5838  (58 occurrences) --
    L   52  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use isZero() instead.
    L   59  TEST/fr/inra/oresing/workflow/cascade/MergedFileChunkCollectorTest.java
           -> Use isEmpty() instead.
    L  120  TEST/fr/inra/oresing/workflow/cascade/MergedFileChunkCollectorTest.java
           -> Use isZero() instead.
    L  197  TEST/fr/inra/oresing/workflow/cascade/MergedFileChunkCollectorTest.java
           -> Use isEmpty() instead.
    L  198  TEST/fr/inra/oresing/domain/data/deposit/prescan/LazyParentLoaderTest.java
           -> Use isPositive() instead.
    L  107  TEST/fr/inra/oresing/rest/data/LazyDisplayNamesMapTest.java
           -> Use assertThat(actual).containsKey(expected) instead.
    L  108  TEST/fr/inra/oresing/rest/data/LazyDisplayNamesMapTest.java
           -> Use assertThat(actual).containsKey(expected) instead.
    L  119  TEST/fr/inra/oresing/rest/data/LazyDisplayNamesMapTest.java
           -> Use assertThat(actual).isEmpty() instead.
    L  120  TEST/fr/inra/oresing/rest/data/LazyDisplayNamesMapTest.java
           -> Use assertThat(actual).isNotEmpty() instead.
    L   36  TEST/fr/inra/oresing/domain/exceptions/AuthenticationFailureTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L   62  TEST/fr/inra/oresing/domain/exceptions/AuthenticationFailureTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L   94  TEST/fr/inra/oresing/domain/exceptions/AuthenticationFailureTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L   95  TEST/fr/inra/oresing/domain/exceptions/AuthenticationFailureTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L  111  TEST/fr/inra/oresing/domain/exceptions/AuthenticationFailureTest.java
           -> Use isEmpty() instead.
    L  112  TEST/fr/inra/oresing/domain/exceptions/AuthenticationFailureTest.java
           -> Use isEmpty() instead.
    L  113  TEST/fr/inra/oresing/domain/exceptions/AuthenticationFailureTest.java
           -> Use isEmpty() instead.
    L   94  TEST/fr/inra/oresing/domain/sql/DomainSqlTypesTest.java
           -> Use isEmpty() instead.
    L  247  TEST/fr/inra/oresing/rest/model/rightsrequest/RightsRequestDTOsTest.java
           -> Use assertThat(actual).hasSameHashCodeAs(expected) instead.
    L   62  TEST/fr/inra/oresing/domain/data/deposit/validation/transformer/LineElementTransformerTest.java
           -> Use assertThat(actual).hasToString(expectedString) instead.
    L   77  TEST/fr/inra/oresing/domain/data/deposit/validation/transformer/LineElementTransformerTest.java
           -> Use assertThat(actual).hasToString(expectedString) instead.
    L  110  TEST/fr/inra/oresing/domain/data/deposit/validation/transformer/LineElementTransformerTest.java
           -> Use assertThat(actual).hasToString(expectedString) instead.
    L  127  TEST/fr/inra/oresing/domain/data/deposit/validation/transformer/LineElementTransformerTest.java
           -> Use assertThat(actual).hasToString(expectedString) instead.
    L  129  TEST/fr/inra/oresing/domain/data/deposit/validation/transformer/LineElementTransformerTest.java
           -> Use assertThat(actual).hasToString(expectedString) instead.
    L  248  TEST/fr/inra/oresing/domain/data/deposit/validation/CriticalValidationPipelineTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L  249  TEST/fr/inra/oresing/domain/data/deposit/validation/CriticalValidationPipelineTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L  250  TEST/fr/inra/oresing/domain/data/deposit/validation/CriticalValidationPipelineTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L  261  TEST/fr/inra/oresing/domain/data/deposit/validation/CriticalValidationPipelineTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L  106  TEST/fr/inra/oresing/domain/exceptions/DomainExceptionsExtendedTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L   69  TEST/fr/inra/oresing/domain/data/deposit/validation/ValidationCheckResultTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L   70  TEST/fr/inra/oresing/domain/data/deposit/validation/ValidationCheckResultTest.java
           -> Use assertThat(actual).containsEntry(key, value) instead.
    L   44  TEST/fr/inra/oresing/domain/application/configuration/type/CheckerEnumTest.java
           -> Use assertThat(actual).hasToString(expectedString) instead.
    L   66  TEST/fr/inra/oresing/domain/application/configuration/type/CheckerEnumTest.java
           -> Use assertThat(actual).isLessThan(expected) instead.
    L   67  TEST/fr/inra/oresing/domain/application/configuration/type/CheckerEnumTest.java
           -> Use assertThat(actual).isGreaterThan(expected) instead.
    L   68  TEST/fr/inra/oresing/domain/application/configuration/type/CheckerEnumTest.java
           -> Use assertThat(actual).isEqualByComparingTo(expected) instead.
    L   78  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use assertThat(actual).hasSameHashCodeAs(expected) instead.
    L  118  TEST/fr/inra/oresing/domain/data/deposit/context/column/ColumnContextTest.java
           -> Use assertThat(actual).hasSameHashCodeAs(expected) instead.
    L   90  TEST/fr/inra/oresing/rest/HierarchicalReferenceAsTreeAndZipUtilsTest.java
           -> Use isPositive() instead.
    L   24  TEST/fr/inra/oresing/rest/dashboard/PipelinePoolsDtoTest.java
           -> Use isZero() instead.
    L   25  TEST/fr/inra/oresing/rest/dashboard/PipelinePoolsDtoTest.java
           -> Use isZero() instead.
    L   26  TEST/fr/inra/oresing/rest/dashboard/PipelinePoolsDtoTest.java
           -> Use isZero() instead.
    L   27  TEST/fr/inra/oresing/rest/dashboard/PipelinePoolsDtoTest.java
           -> Use isZero() instead.
    L   78  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowSnapshotWithMethodsTest.java
           -> Use isZero() instead.
    L   79  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowSnapshotWithMethodsTest.java
           -> Use isZero() instead.
    L   80  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowSnapshotWithMethodsTest.java
           -> Use isZero() instead.
    L   48  TEST/fr/inra/oresing/rest/dashboard/RemainingDashboardDtosTest.java
           -> Use isPositive() instead.
    L   93  TEST/fr/inra/oresing/rest/dashboard/RemainingDashboardDtosTest.java
           -> Use isZero() instead.
    L  128  TEST/fr/inra/oresing/rest/dashboard/RemainingDashboardDtosTest.java
           -> Use isZero() instead.
    L   50  TEST/fr/inra/oresing/workflow/cascade/history/FinalizePhaseSnapshotTest.java
           -> Use isZero() instead.
    L   52  TEST/fr/inra/oresing/rest/dashboard/DashboardConfigDtoTest.java
           -> Use isZero() instead.
    L  100  TEST/fr/inra/oresing/rest/dashboard/DashboardWorkflowDtoTest.java
           -> Use isZero() instead.
    L  156  TEST/fr/inra/oresing/rest/dashboard/DashboardWorkflowDtoTest.java
           -> Use isZero() instead.
    L  277  TEST/fr/inra/oresing/rest/dashboard/DashboardWorkflowDtoTest.java
           -> Use isZero() instead.
    L   55  TEST/fr/inra/oresing/monitoring/compensation/CompensationHandlerRegistryTest.java
           -> Use isZero() instead.
    L   47  TEST/fr/inra/oresing/monitoring/compensation/CompensationSweeperTest.java
           -> Use isZero() instead.
    L   48  TEST/fr/inra/oresing/monitoring/compensation/CompensationSweeperTest.java
           -> Use isZero() instead.
    L   49  TEST/fr/inra/oresing/monitoring/compensation/CompensationSweeperTest.java
           -> Use isZero() instead.
    L   69  TEST/fr/inra/oresing/monitoring/compensation/CompensationSweeperTest.java
           -> Use isNotNegative() instead.
    L  108  TEST/fr/inra/oresing/monitoring/compensation/CompensationSweeperTest.java
           -> Use assertThat(actual).hasSameHashCodeAs(expected) instead.

  -- java:S5853  (48 occurrences) --
    L   41  TEST/fr/inra/oresing/domain/authorization/SimpleDomainGrantedAuthorityTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   48  TEST/fr/inra/oresing/domain/event/DomainProgressEventTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   92  TEST/fr/inra/oresing/domain/repository/authorization/role/OreSiRightOnApplicationRoleTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   30  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   39  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   47  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   55  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   63  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   71  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   80  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   89  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   98  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  107  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  116  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  125  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  134  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  153  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   60  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryHasRecentFailedTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   45  TEST/fr/inra/oresing/persistence/refref/RefrefRebuildSqlTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   64  TEST/fr/inra/oresing/persistence/refref/RefrefRebuildSqlTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   83  TEST/fr/inra/oresing/persistence/refref/RefrefRebuildSqlTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   97  TEST/fr/inra/oresing/persistence/refref/RefrefRebuildSqlTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  112  TEST/fr/inra/oresing/persistence/refref/RefrefRebuildSqlTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  126  TEST/fr/inra/oresing/persistence/refref/RefrefRebuildSqlTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  150  TEST/fr/inra/oresing/persistence/refref/RefrefRebuildSqlTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  250  TEST/fr/inra/oresing/domain/application/SqlIdentifierUtilsTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   40  TEST/fr/inra/oresing/persistence/PgIdentifierTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   50  TEST/fr/inra/oresing/persistence/PgIdentifierTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   41  TEST/fr/inra/oresing/domain/authorization/privilegeassessor/PrivilegeAssessorStateHierarchyTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   21  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsExtraTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   30  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsExtraTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   43  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsExtraTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   51  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsExtraTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   60  TEST/fr/inra/oresing/domain/application/configuration/migration/change/MigrationChangeRecordsExtraTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   41  TEST/fr/inra/oresing/domain/groovy/predefined/script/ScriptProvidersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   91  TEST/fr/inra/oresing/domain/groovy/predefined/script/ScriptProvidersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   37  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L   80  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  122  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  143  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  169  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  205  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  223  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  233  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  305  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  340  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  358  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.
    L  399  TEST/fr/inra/oresing/persistence/normalized/PersistenceNormalizedBuildersTest.java
           -> Join these multiple assertions subject to one assertion chain.

  -- java:S1128  (38 occurrences) --
    L   10  fr/inra/oresing/rest/users/RoleGrantAuditRepository.java
           -> Remove this unused import 'java.sql.SQLException'.
    L   23  fr/inra/oresing/domain/checker/LineChecker.java
           -> Remove this unused import 'java.util.stream.Collectors'.
    L   24  fr/inra/oresing/domain/checker/LineChecker.java
           -> Remove this unused import 'java.util.stream.Stream'.
    L    9  fr/inra/oresing/domain/groovy/GroovyExpression.java
           -> Remove this unused import 'java.util.HashMap'.
    L   34  fr/inra/oresing/rest/data/DataService.java
           -> Remove this duplicated import.
    L    9  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Remove this unused import 'java.sql.ResultSet'.
    L    6  fr/inra/oresing/workflow/cascade/StagingUpsertSqlBuilder.java
           -> Remove this unused import 'java.util.ArrayList'.
    L    7  fr/inra/oresing/workflow/cascade/StagingUpsertSqlBuilder.java
           -> Remove this unused import 'java.util.List'.
    L    6  fr/inra/oresing/domain/filesenderclient/FileSenderInternationalisationForBuildBundleReport.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    2  fr/inra/oresing/domain/BinaryFile.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    4  fr/inra/oresing/domain/authorization/AuthenticationServiceImpl.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    6  fr/inra/oresing/domain/authorization/request/AuthorizationForScope.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    3  fr/inra/oresing/domain/data/UUIDsfromData.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    3  fr/inra/oresing/persistence/requestbuilder/data/SelectRequest.java
           -> Remove this unused import 'fr.inra.oresing.domain.data.DataRows'.
    L   24  fr/inra/oresing/rest/AuthorizationResources.java
           -> Remove this unused import 'fr.inra.oresing.domain.authorization.ApplicationUserResult'.
    L   25  fr/inra/oresing/rest/AuthorizationResources.java
           -> Remove this unused import 'fr.inra.oresing.domain.authorization.AuthorizationParsed'.
    L   26  fr/inra/oresing/rest/AuthorizationResources.java
           -> Remove this unused import 'fr.inra.oresing.domain.authorization.AuthorizationsForUserResult'.
    L   30  fr/inra/oresing/rest/AuthorizationResources.java
           -> Remove this unused import 'fr.inra.oresing.domain.authorization.CurrentUserRolesResult'.
    L   31  fr/inra/oresing/rest/AuthorizationResources.java
           -> Remove this unused import 'fr.inra.oresing.domain.authorization.request.AuthorizationInput'.
    L    2  TEST/fr/inra/oresing/domain/additionalfiles/AdditionalFilesDomainTest.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    5  TEST/fr/inra/oresing/domain/authorization/request/AuthorizationRequestDomainTest.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    8  TEST/fr/inra/oresing/domain/filesenderclient/FileSenderInternationalisationForBuildBundleReportTest.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    9  fr/inra/oresing/domain/authorization/GetGrantableResult.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    4  fr/inra/oresing/domain/BinaryFileInfos.java
           -> Remove this unnecessary import: same package classes are always implicitly imported.
    L    6  TEST/fr/inra/oresing/rest/services/AuthorizationServiceCacheTest.java
           -> Remove this unused import 'fr.inra.oresing.domain.data.menu.ReferenceScope'.
    L   15  TEST/fr/inra/oresing/domain/data/deposit/validation/transformer/LineElementTransformerTest.java
           -> Remove this unused import 'java.util.Map'.
    L   14  TEST/fr/inra/oresing/domain/application/configuration/ConfigurationRecordsTest.java
           -> Remove this unused import 'java.util.Set'.
    L    8  TEST/fr/inra/oresing/domain/data/DataSimpleRecordsTest.java
           -> Remove this unused import 'java.util.List'.
    L   15  TEST/fr/inra/oresing/rest/monitoring/OreSiWebMvcTagsContributorTest.java
           -> Remove this unused import 'org.mockito.Mockito'.
    L    6  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogWriterTest.java
           -> Remove this unused import 'org.mockito.Mockito'.
    L    7  TEST/fr/inra/oresing/workflow/cascade/TxAwareDeferredRunnerTest.java
           -> Remove this unused import 'org.mockito.Mockito'.
    L   16  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Remove this unused import 'java.util.EnumMap'.
    L   20  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Remove this unused import 'java.util.concurrent.ExecutorService'.
    L   21  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Remove this unused import 'java.util.concurrent.LinkedBlockingQueue'.
    L   23  TEST/fr/inra/oresing/workflow/cascade/config/ConfigEditServiceTest.java
           -> Remove this unused import 'java.util.concurrent.TimeUnit'.
    L    8  TEST/fr/inra/oresing/workflow/cascade/config/StrategyOptionsResolverTest.java
           -> Remove this unused import 'java.util.ArrayList'.
    L   10  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this unused import 'java.util.concurrent.TimeUnit'.
    L   16  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this unused import 'org.mockito.Mockito.atLeast'.

  -- java:S6068  (28 occurrences) --
    L  127  TEST/fr/inra/oresing/rest/users/UserRolesAuditLoggerTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L  140  TEST/fr/inra/oresing/domain/data/deposit/prescan/LazyParentLoaderTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L   88  TEST/fr/inra/oresing/workflow/cascade/preparation/PreparationPhaseListenerInterceptorTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L  263  TEST/fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleServiceTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L   54  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L   62  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L   68  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L   73  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L   89  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L   94  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L   99  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  104  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  109  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowLogRepositoryDeleteUnitTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  157  TEST/fr/inra/oresing/rest/CacheAdminResourcesUnitTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  143  TEST/fr/inra/oresing/rest/model/data/query/DownloadDatasetQueryTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  166  TEST/fr/inra/oresing/rest/model/data/query/DownloadDatasetQueryTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  190  TEST/fr/inra/oresing/rest/model/data/query/DownloadDatasetQueryTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  215  TEST/fr/inra/oresing/rest/model/data/query/DownloadDatasetQueryTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  226  TEST/fr/inra/oresing/mail/rightsrequest/RightsRequestNotificationServiceTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  227  TEST/fr/inra/oresing/mail/rightsrequest/RightsRequestNotificationServiceTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L   87  TEST/fr/inra/oresing/monitoring/compensation/handlers/StagingCleanupHandlerTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L  104  TEST/fr/inra/oresing/monitoring/compensation/handlers/StagingCleanupHandlerTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  149  TEST/fr/inra/oresing/monitoring/compensation/handlers/StagingCleanupHandlerTest.java
           -> Remove this and every subsequent useless "eq(...)" invocation; pass the values directly.
    L   53  TEST/fr/inra/oresing/workflow/cascade/history/CascadeHeartbeatBridgeTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L   70  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L   73  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  117  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.
    L  121  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove this useless "eq(...)" invocation; pass the values directly.

  -- java:S1874  (15 occurrences) --
    L   66  fr/inra/oresing/rest/usecases/storage/versioning/CacheCaptureService.java
           -> Remove this use of "Nullable"; it is deprecated.
    L   92  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecycleCoordinator.java
           -> Remove this use of "Nullable"; it is deprecated.
    L   78  fr/inra/oresing/workflow/cascade/preparation/PreparationPhaseListenerInterceptor.java
           -> Remove this use of "Nullable"; it is deprecated.
    L   87  fr/inra/oresing/persistence/DataRepository.java
           -> Remove this use of "equalsAnyIgnoreCase"; it is deprecated.
    L   96  fr/inra/oresing/persistence/DataRepository.java
           -> Remove this use of "equalsAnyIgnoreCase"; it is deprecated.
    L  109  fr/inra/oresing/persistence/DataRepository.java
           -> Remove this use of "equalsAnyIgnoreCase"; it is deprecated.
    L  244  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "queryForObject"; it is deprecated.
    L  250  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "query"; it is deprecated.
    L  314  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "queryForObject"; it is deprecated.
    L  320  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "query"; it is deprecated.
    L  368  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "queryForObject"; it is deprecated.
    L  374  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "query"; it is deprecated.
    L  452  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "queryForObject"; it is deprecated.
    L  458  fr/inra/oresing/rest/AdminSystemResources.java
           -> Remove this use of "query"; it is deprecated.
    L    9  fr/inra/oresing/domain/sql/WithSqlIdentifier.java
           -> Remove this use of "containsAny"; it is deprecated.

  -- java:S3077  (10 occurrences) --
    L   49  fr/inra/oresing/domain/checker/type/ReferenceType.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L   70  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L   71  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L   72  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L   73  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L  119  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L  133  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L   63  fr/inra/oresing/workflow/cascade/preparation/DeferredFileChunkSource.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L   49  fr/inra/oresing/cache/CacheSizeEstimator.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.
    L   72  fr/inra/oresing/workflow/cascade/StoreAllPathSink.java
           -> Use a thread-safe type; adding "volatile" is not enough to make this field thread-safe.

  -- java:S1481  (7 occurrences) --
    L  106  fr/inra/oresing/domain/application/configuration/date/DatePattern.java
           -> Remove this unused "date" local variable.
    L  297  TEST/fr/inra/oresing/workflow/cascade/StagingFinalizeSqlTest.java
           -> Remove this unused "saved" local variable.
    L   40  fr/inra/oresing/domain/data/deposit/BuildColumns.java
           -> Remove this unused "tags" local variable.
    L   43  fr/inra/oresing/domain/data/deposit/BuildColumns.java
           -> Remove this unused "checker" local variable.
    L  110  fr/inra/oresing/domain/data/deposit/BuildColumns.java
           -> Remove this unused "multiplicity" local variable.
    L  180  TEST/fr/inra/oresing/rest/model/authorization/AuthorizationModelTest.java
           -> Remove this unused "uid" local variable.
    L  571  TEST/fr/inra/oresing/domain/authorization/privilegeassessor/PrivilegeAccessorDomainTest.java
           -> Remove this unused "result" local variable.

  -- java:S1612  (6 occurrences) --
    L   21  TEST/fr/inra/oresing/domain/cancel/CancellationTokenTest.java
           -> Replace this lambda with method reference 'CancellationToken.NONE::throwIfCancelled'.
    L   80  TEST/fr/inra/oresing/workflow/cascade/TxAwareDeferredRunnerTest.java
           -> Replace this lambda with method reference 'runner::afterCommit'.
    L   92  TEST/fr/inra/oresing/workflow/cascade/TxAwareDeferredRunnerTest.java
           -> Replace this lambda with method reference 'runner::afterCommit'.
    L  105  TEST/fr/inra/oresing/workflow/cascade/TxAwareDeferredRunnerTest.java
           -> Replace this lambda with method reference 'runner::afterCommit'.
    L  177  TEST/fr/inra/oresing/rest/model/data/query/DownloadDatasetQueryTest.java
           -> Replace this lambda with method reference 'DataRowIds::id'.
    L   50  TEST/fr/inra/oresing/rest/services/NormalizedServiceTest.java
           -> Replace this lambda with method reference 'monSoereFixture::testPublic'.

  -- java:S135  (6 occurrences) --
    L  333  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Reduce the total number of break and continue statements in this loop to use at most one.
    L  124  fr/inra/oresing/persistence/flyway/SchemaFlywayCallback.java
           -> Reduce the total number of break and continue statements in this loop to use at most one.
    L  433  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Reduce the total number of break and continue statements in this loop to use at most one.
    L  121  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Reduce the total number of break and continue statements in this loop to use at most one.
    L  185  fr/inra/oresing/cache/MemoryCache.java
           -> Reduce the total number of break and continue statements in this loop to use at most one.
    L   93  fr/inra/oresing/rest/services/RightsRequestAuthorizationGranter.java
           -> Reduce the total number of break and continue statements in this loop to use at most one.

  -- java:S6353  (4 occurrences) --
    L  108  fr/inra/oresing/persistence/LargeObjectOrphanSweeper.java
           -> Use concise character class syntax '\\w' instead of '[a-zA-Z0-9_]'.
    L  129  fr/inra/oresing/persistence/LargeObjectOrphanSweeper.java
           -> Use concise character class syntax '\\w' instead of '[a-zA-Z0-9_]'.
    L  609  fr/inra/oresing/rest/AdminSystemResources.java
           -> Use concise character class syntax '\\w' instead of '[a-zA-Z0-9_]'.
    L  579  fr/inra/oresing/rest/users/UserRolesService.java
           -> Use concise character class syntax '\\w' instead of '[a-zA-Z0-9_]'.

  -- java:S1130  (4 occurrences) --
    L   98  TEST/fr/inra/oresing/workflow/cascade/TxAwareDeferredRunnerTest.java
           -> Remove the declaration of thrown exception 'java.sql.SQLException', as it cannot be thrown from method's body.
    L  114  TEST/fr/inra/oresing/monitoring/session/JwtBlacklistRegistryTest.java
           -> Remove the declaration of thrown exception 'java.lang.InterruptedException', as it cannot be thrown from metho
    L   84  TEST/fr/inra/oresing/workflow/cascade/history/WorkflowActiveRegistryTest.java
           -> Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body.
    L   65  TEST/fr/inra/oresing/workflow/cascade/history/HeartbeatServiceTest.java
           -> Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body.

  -- java:S4276  (3 occurrences) --
    L   32  fr/inra/oresing/domain/data/DataColumnPatternValue.java
           -> Refactor this code to use the more specialised Functional Interface 'UnaryOperator<FieldType>'
    L  111  fr/inra/oresing/cache/CacheSizeEstimator.java
           -> Refactor this code to use the more specialised Functional Interface 'ToLongFunction<ObjectMapper>'
    L   99  fr/inra/oresing/domain/authorization/request/AuthorizationInput.java
           -> Refactor this code to use the more specialised Functional Interface 'Predicate<String>'

  -- java:S1220  (2 occurrences) --
    L    ?  TEST/fr/inra/oresing/domain/BinaryFileDatasetTest.java
           -> Move this file to a named package.
    L    ?  TEST/fr/inra/oresing/domain/data/DataColumnMultipleValueTest.java
           -> Move this file to a named package.

  -- java:S117  (2 occurrences) --
    L   45  fr/inra/oresing/domain/application/configuration/date/DatePattern.java
           -> Rename this local variable to match the regular expression '^[a-z][a-zA-Z0-9]*$'.
    L  177  TEST/fr/inra/oresing/domain/application/SqlIdentifierUtilsTest.java
           -> Rename this local variable to match the regular expression '^[a-z][a-zA-Z0-9]*$'.

  -- java:S1488  (2 occurrences) --
    L   63  fr/inra/oresing/domain/checker/type/PatternType.java
           -> Immediately return this expression instead of assigning it to the temporary variable "mapType".
    L  445  fr/inra/oresing/domain/data/deposit/DataImporter.java
           -> Immediately return this expression instead of assigning it to the temporary variable "fixed".

  -- java:S1905  (2 occurrences) --
    L  284  fr/inra/oresing/persistence/DataRepository.java
           -> Remove this unnecessary cast to "long".
    L  448  fr/inra/oresing/workflow/cascade/StagingFinalizeSql.java
           -> Remove this unnecessary cast to "long".

  -- java:S1710  (2 occurrences) --
    L  112  fr/inra/oresing/rest/dashboard/DashboardController.java
           -> Remove the 'ApiResponses' wrapper from this annotation group
    L  135  fr/inra/oresing/rest/dashboard/DashboardController.java
           -> Remove the 'ApiResponses' wrapper from this annotation group

  -- java:S2386  (2 occurrences) --
    L   78  fr/inra/oresing/workflow/cascade/cache/ReferencevalueCacheFormat.java
           -> Make this member "protected".
    L   19  fr/inra/oresing/domain/groovy/predefined/script/ScriptConstantProvider.java
           -> Move "PROVIDERS" to a class and lower its visibility

  -- java:S6201  (1 occurrences) --
    L  303  fr/inra/oresing/domain/data/deposit/prescan/NaturalKeyPreScanService.java
           -> Replace this instanceof check and cast with 'instanceof BufferedReader bufferedreader'

  -- java:S1185  (1 occurrences) --
    L   32  fr/inra/oresing/domain/checker/type/PatternType.java
           -> Remove this method to simply inherit it.

  -- java:S2184  (1 occurrences) --
    L  201  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> Cast one of the operands of this multiplication operation to a "long".

  -- java:S6203  (1 occurrences) --
    L  711  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Move this text block out of the lambda body and refactor it to a local variable or a static final field.

  -- java:S1602  (1 occurrences) --
    L  825  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Remove useless curly braces around statement

  -- java:S2160  (1 occurrences) --
    L   70  fr/inra/oresing/rest/data/LazyDisplayNamesMap.java
           -> Override the "equals" method in this class.

  -- java:S6205  (1 occurrences) --
    L  431  fr/inra/oresing/rest/users/UserRolesService.java
           -> Remove this redundant block.

  -- java:S1301  (1 occurrences) --
    L  910  fr/inra/oresing/rest/usecases/storage/versioning/PublishLifecyclePhase2Handler.java
           -> Replace this "switch" statement by "if" statements to increase readability.

  -- java:S6878  (1 occurrences) --
    L  263  fr/inra/oresing/rest/BundleResources.java
           -> Use the record pattern instead of this pattern match variable.

  -- java:S1640  (1 occurrences) --
    L   99  fr/inra/oresing/domain/authorization/privilegeassessor/PrivilegeAssessorDomainForApplication.java
           -> Convert this Map to an EnumMap.

######################################################################
### SEVERITE : INFO
######################################################################

  -- java:S1133  (3 occurrences) --
    L  489  fr/inra/oresing/persistence/BinaryFileRepository.java
           -> Do not forget to remove this deprecated code someday.
    L   61  fr/inra/oresing/workflow/cascade/history/FastPathSnapshot.java
           -> Do not forget to remove this deprecated code someday.
    L 1517  fr/inra/oresing/persistence/DataRepository.java
           -> Do not forget to remove this deprecated code someday.

  -- java:S1135  (2 occurrences) --
    L   66  fr/inra/oresing/rest/services/RightsRequestService.java
           -> Complete the task associated to this TODO comment.
    L  736  fr/inra/oresing/workflow/cascade/CascadeImportPipeline.java
           -> Complete the task associated to this TODO comment.

  -- java:S6541  (2 occurrences) --
    L  601  fr/inra/oresing/rest/dashboard/DashboardService.java
           -> A "Brain Method" was detected. Refactor it to reduce at least one of the following metrics: LOC from 123 to 64
    L  108  fr/inra/oresing/monitoring/integrity/IntegrityService.java
           -> A "Brain Method" was detected. Refactor it to reduce at least one of the following metrics: LOC from 94 to 64,

  -- java:S6208  (1 occurrences) --
    L  103  fr/inra/oresing/monitoring/compensation/handlers/StagingCleanupHandler.java
           -> Merge the previous cases into this one using comma-separated label.
