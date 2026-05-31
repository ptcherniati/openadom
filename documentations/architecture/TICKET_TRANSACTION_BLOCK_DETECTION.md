# Ticket : Détection automatique des blocages transactionnels en test

## Contexte

Le bug reproduit le 2026-05-22 sur `OreSiResourcesTest#addApplicationMonsoreDynamic`
> "ajout d'un fichier invalide" a montré un **self-deadlock** entre une transaction
`@Transactional` outer et un appel `@Transactional(REQUIRES_NEW)` sur la même
row (`VersioningService.createData` → `compensationLogService.compensateNow`
→ `DELETE FROM <schema>.binaryfile WHERE id = ?` bloqué en attente du lock
de la tx outer , qui elle-meme attendait le retour de `compensateNow`).

Le diagnostic a été pénible : on a dû ajouter une dizaine de logs `log.debug`
en cascade dans 6 fichiers (`checkHeader`, `readHeader`, `prepareCtx`, `addData`,
`createData.catch`, `VersioningService.catch`) avant de localiser la ligne
exacte qui bloquait, puis comprendre le pourquoi. Avec une fixture appropriée,
on aurait eu le diagnostic en **une exécution** au lieu de 5.

## Mesures déjà en place (poste-fix 2026-05-22)

| Filet | Portée | Effet quand déclenché |
|---|---|---|
| `openadom.http.streaming.timeout` (3 min en test, 6h en prod) | `Future.get()` autour de `heavyExecutorService.submit()` dans `OreSiResources.createData()` | `TimeoutException` + `dumpRelevantThreads()` (thread dump des pools `heavy-*`, `normal-*`, `fast-*`, `cache-preload-*`, scheduling) |
| `cascade.import.finalize-statement-timeout-minutes` (180 min) | `StagingFinalizeSql.runFinalize` | Postgres abort `57014 statement_timeout` |
| `cascade.import.finalize-lock-timeout-minutes` (30 min) | Idem | Postgres abort `55P03 lock_timeout` |
| `WorkflowZombieSweeper` (5 min sans heartbeat) | `@Scheduled` | Marque `workflow_log.status = FAILED` |
| **NEW** `spring.datasource.hikari.connection-init-sql` en test : `SET lock_timeout = '15s'; SET idle_in_transaction_session_timeout = '60s'; SET deadlock_timeout = '500ms'` | Toute connexion JDBC en test | Postgres abort avec `PSQLException` claire au lieu de blocage opaque |

→ Avec le 5e point, le bug d'origine produirait désormais en test une
`PSQLException ( SQL state 55P03 lock_timeout , execution time 15s )` au lieu
d'un timeout JUnit ambigu à 3 minutes.

## Ce ticket : aller plus loin avec une fixture JUnit

Quand le `lock_timeout=15s` aborte une requête, on récupère un `SQLState`
mais on n'a **pas** :
- la query bloquée (côté coupable),
- la query bloquante (côté coupable de l'attente),
- le PID des sessions Postgres,
- le graphe des waits.

L'idée est d'ajouter une `@RegisterExtension` JUnit 5 qui :

### A. Avant chaque test ( `@BeforeEach` ou méthode `beforeEach` du extension )
Snapshot du nombre de sessions actives :
```sql
SELECT COUNT(*) FROM pg_stat_activity WHERE state IS NOT NULL AND pid <> pg_backend_pid();
```

### B. Après chaque test ( `afterEach` , même en cas d'échec via `TestWatcher` )
Détection des sessions toujours **idle in transaction** ou **active** > N secondes :
```sql
SELECT pid, state, wait_event_type, wait_event,
       age(clock_timestamp(), xact_start) AS tx_age_s,
       LEFT(query, 500) AS current_query,
       application_name
  FROM pg_stat_activity
 WHERE state IN ('active','idle in transaction')
   AND xact_start IS NOT NULL
   AND xact_start < NOW() - INTERVAL '5 seconds'
   AND pid <> pg_backend_pid();
```

Et le graphe des blockers :
```sql
SELECT blocked.pid AS blocked_pid,
       LEFT(blocked.query, 200) AS blocked_query,
       blocked.wait_event_type, blocked.wait_event,
       array_agg(blocking.pid) AS blocking_pids,
       array_agg(LEFT(blocking.query, 200)) AS blocking_queries
  FROM pg_stat_activity blocked
  JOIN pg_stat_activity blocking
    ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
 WHERE blocked.wait_event_type = 'Lock'
 GROUP BY blocked.pid, blocked.query, blocked.wait_event_type, blocked.wait_event;
```

Si une ligne est trouvée :
- log `WARN` lisible avec query+blocker dans le report JUnit (via
  `TestWatcher.testFailed(context, cause)` ou un `assertNotEquals` dans
  `afterEach`)
- option `-Dtest.fail-on-blocking-tx=true` pour rendre le test FAIL au lieu de WARN
- option `-Dtest.dump-pg-stat-on-failure=true` pour dumper le snapshot complet
  de `pg_stat_activity` quand un test échoue pour autre raison (utile pour
  diagnostiquer un blocage par effet de bord d'un test précédent)

### C. Optionnel : intégration `dumpRelevantThreads()`
Couplé avec le thread dump Java déjà présent dans `OreSiResources.createData()`,
on aurait pour un test bloqué :
- côté Java : stacks des threads `heavy-*` / `cascade-*` qui bloquent
- côté Postgres : qui attend quoi sur quel lock

→ corrélation Java ↔ Postgres immédiate.

## Implémentation

### Localisation
- Nouveau fichier : `src/test/java/fr/inra/oresing/rest/services/PostgresLockMonitorExtension.java`
- Enregistrement : `AbstractIntegrationTest` ajoute `@RegisterExtension`
  static final field → applicable à tous les tests qui en héritent (43 classes).

### Squelette
```java
public class PostgresLockMonitorExtension implements BeforeEachCallback, AfterEachCallback, TestWatcher {

    private static final boolean FAIL_ON_BLOCK = Boolean.getBoolean("test.fail-on-blocking-tx");
    private static final boolean DUMP_ON_FAILURE = Boolean.getBoolean("test.dump-pg-stat-on-failure");
    private static final Duration MIN_TX_AGE = Duration.ofSeconds(5);

    @Override
    public void beforeEach(ExtensionContext context) {
        // snapshot count - rien d'autre
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        List<BlockingTx> stuck = queryStuckTransactions();
        if (!stuck.isEmpty()) {
            String report = formatReport(context, stuck);
            if (FAIL_ON_BLOCK) {
                throw new AssertionError(report);
            } else {
                log.warn("Blocking tx detected after test {}:\n{}",
                        context.getDisplayName(), report);
            }
        }
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        if (DUMP_ON_FAILURE) {
            log.warn("Test {} failed - pg_stat_activity dump:\n{}",
                    context.getDisplayName(), dumpFullActivity());
        }
    }

    private record BlockingTx(long pid, String state, String query, Duration age,
                              List<Long> blockingPids, List<String> blockingQueries) {}

    private List<BlockingTx> queryStuckTransactions() { /* JdbcTemplate ... */ }
    private String dumpFullActivity()                  { /* JdbcTemplate ... */ }
    private String formatReport(...)                   { /* ... */ }
}
```

### Configuration via `application-tests.properties` (optionnel)
```properties
# Fail le test si une transaction bloquante survit au tearDown ( default warn )
test.fail-on-blocking-tx=false
# Dump pg_stat_activity en cas d'echec ( default false , bruyant )
test.dump-pg-stat-on-failure=false
# Age minimum d'une tx pour etre signalee ( default 5s )
test.blocking-tx-min-age=5s
```

## Critères d'acceptation

1. Un test qui laisse délibérément une transaction `idle in transaction` >
   5 secondes (via DB mock ou test dédié) produit un log `WARN` lisible
   ( query + age + blocker ) dans le report JUnit.
2. Avec `-Dtest.fail-on-blocking-tx=true` , le même test fail avec un
   `AssertionError` portant le report.
3. L'extension n'ajoute pas de latence > 50ms par test ( mesure sur la suite
   `OreSiResourcesTest` qui en a 50+ ) — la query `pg_stat_activity` est
   indexée et reste sous la milliseconde sur une base de test.
4. Re-jouer le scenario `compensateNow` self-deadlock ( en désactivant
   temporairement le fix `TransactionSynchronization` dans `VersioningService` )
   reproduit le warn/fail en < 16s ( = `lock_timeout` 15s + post-test query 1s )
   au lieu de 3 min ( = `heavyTaskTimeout` ).

## Hors-scope (autres tickets)

- Métriques Micrometer / Prometheus sur `lock_timeout` / `deadlock_detected`
  côté prod ( pas seulement tests )
- Alerting Grafana sur sessions `idle in transaction` > 1 min en prod
- Documentation du pattern outbox + invariant "jamais d'appel REQUIRES_NEW
  qui agit sur une row touchee par la tx outer" → soit on commit la outer ,
  soit on differe via `TransactionSynchronization.afterCompletion` ( cf
  fix `VersioningService.createData` 2026-05-22 ) .

## Estimation

- Implémentation : 1/2 j ( 1 classe + tests ciblés )
- Intégration à `AbstractIntegrationTest` + ajustement des 3-4 tests qui ont
  des transactions intentionnellement longues : 1/2 j
- Documentation : 1h
- Total : **~1 j**

## Références

- Bug source : commit `d8c14078` ( refactoring sonar ) , détecté 2026-05-22
- Fix appliqué : `VersioningService.createData` catch block → `registerSynchronization`
  ( ne pas appeler `compensateNow` synchronement dans une tx active )
- Filet de sécurité ajouté : `heavyTaskTimeout` + `dumpRelevantThreads()` dans
  `OreSiResources.createData()`
- Config Postgres test : `lock_timeout=15s` + `idle_in_transaction_session_timeout=60s`
  dans `application-tests.properties` ( fixture passive , sans code )