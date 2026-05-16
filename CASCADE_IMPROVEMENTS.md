# Cascade improvements - spec upstream

Spec d'évolutions cascade demandées par openadom, identifiées via le
chantier publish/unpublish ( cf `PUBLISH_UNPUBLISH.md` ) . Les patterns
re-codés à la main dans openadom suggèrent qu'ils méritent d'être
remontés dans cascade pour bénéficier à tous les consommateurs cascade.

Auteur : R.YAHIAOUI - 2026-05-13
Cible cascade : >= 3.2.0
Format : 6 propositions , chacune avec contexte / API proposée / impact /
effort estimé.

---

## A — `Sources.bytea(...)` : streaming d'un blob Postgres bytea via cursor

### Contexte

OpenADOM stocke les fichiers utilisateurs dans une colonne `bytea`
( `binaryfile.fileData` ) , typique 1-200 MB. Quand on republie un
fichier , `BinaryFileRepository.retrieveFileContentAsInputStream` lit
ce bytea via JDBC `DefaultLobHandler.getBlobAsBinaryStream` qui **charge
l'intégralité des octets en heap** ( ByteArrayInputStream wrappé ) avant
de retourner le stream. Pour un fichier 200 MB on consomme 200 MB de heap
juste pour démarrer cascade.

Postgres a 2 modes pour stocker des binaires :
- `bytea` ( colonne scalaire ) : pas de cursor server-side natif
- Large Objects ( OID ) : API `lo_open` / `lo_read` server-side streaming

OpenADOM utilise `bytea` ; la migration vers LO est coûteuse ( change le
modèle, nécessite DBA ).

**Workaround possible côté client** : pagination via SQL
`SELECT SUBSTRING ( filedata FROM offset FOR len ) FROM binaryfile WHERE id = ?`
en boucle. Postgres lit uniquement la slice demandée , aucune
matérialisation totale.

### API proposée

```java
package fr.inrae.ore.cascade.api.sources;

public final class Sources {

    /**
     * Source qui stream un blob Postgres bytea via paginations
     * SUBSTRING server-side . Aucun chargement integral en heap .
     *
     * @param dataSource    pool JDBC ( typiquement isole du pool principal )
     * @param schema        schema applicatif ( ex. " si_acbb " )
     * @param table         nom de la table ( ex. " binaryfile " )
     * @param byteaColumn   colonne bytea ( ex. " filedata " )
     * @param pkColumn      colonne PK ( ex. " id " )
     * @param pkValue       valeur PK
     * @param chunkBytes    taille de chaque SUBSTRING ( recommande 1 MB )
     * @return Source<InputStream> qui produit un seul chunk InputStream
     *         delegant la lecture aux SUBSTRING successifs ; ferme la
     *         derniere connection / cursor a end-of-stream .
     */
    public static Source<InputStream> bytea(
            DataSource dataSource,
            String schema,
            String table,
            String byteaColumn,
            String pkColumn,
            UUID pkValue,
            int chunkBytes
    );
}
```

### Impact

- **Mémoire** : heap consommé par blob = max ( chunkBytes ) au lieu de
  totalSize . Pour blob 200 MB + chunkBytes 1 MB → **200x reduction**.
- **OpenADOM publish** : élimine la cause critique de l'OOM observée
  sur ACBB ( 45 MB blob × N concurrent workflows ) .
- **API existante préservée** : addition pure, pas de breaking change.

### Effort

- Implementation : ~300 lignes Java cascade-core + 100 lignes tests
- Documentation : 1 page
- Bench iso-result vs `Sources.file(...)` : 2-3h
- **Total : 2 jours dev + 1 jour bench**

---

## B — `Interceptors.springSecurityContextPropagator()` : propagation auth

### Contexte

OpenADOM utilise Spring Security ( `SecurityContextHolder` thread-local )
+ un `OreSiApiRequestContext` ( request-scoped ) pour porter
l'identité du user authentifié. Quand cascade pipeline tourne sur son
thread pool dédié ( source / transform / sink workers ) , ces
thread-locals **ne sont pas propagés** . Symptôme : `setRoleForClient()`
appelé depuis un transform worker pose un rôle SQL `NULL` → query échoue
avec " application inconnue " ou refuse via RLS.

OpenADOM contourne déjà ce problème pour Spring `@Async` via un
`ContextPropagatingTaskDecorator` ( cf `AsyncExecutorConfiguration` ) .
Le même pattern manque pour cascade.

### API proposée

```java
package fr.inrae.ore.cascade.api.interceptors;

public final class Interceptors {

    /**
     * Interceptor qui copie le SecurityContext + RequestAttributes +
     * MDC du thread caller vers chaque worker thread cascade
     * ( source / transform / sink ) , les restaure avant chaque
     * invocation et les nettoie apres .
     *
     * <p>Reflexion sur Spring Security et Spring Web pour eviter une
     * dependance dure ; no-op gracieux si Spring n'est pas au classpath.
     *
     * <p>Utilise :
     * <pre>{@code
     * WorkflowBuilder.create()
     *     .withInterceptor(Interceptors.springSecurityContextPropagator())
     *     .from(...).to(...).build();
     * }</pre>
     */
    public static Interceptor springSecurityContextPropagator();
}
```

### Impact

- **OpenADOM** : permet de wrapper publish phase 2 dans cascade Action
  Pattern proprement ( commit f4de3335 intent restauré ) . Bug observé
  ( "application inconnue 'si_acbb'" en thread cascade ) fixé natif.
- **Autres consommateurs cascade Spring** : pattern réutilisable
  partout , évite la duplication par projet.

### Effort

- Implementation : ~150 lignes Java + reflection helpers
- Tests : 100 lignes ( SecurityContext / RequestAttributes / MDC
  validation across thread switch )
- **Total : 1 jour**

---

## D — `ConcurrencyLimiter.byKey(...)` : semaphore générique par clé

### Contexte

Cascade a déjà `CASCADE_IMPORT_MAX_CONCURRENT_PER_USER` ( rate limit
par user ) . OpenADOM a besoin d'un rate-limit **par ( application ,
datatype )** : 2 workflows publish sur la même datatype = contention DB
+ buildSynthesis race + risque OOM.

OpenADOM a recodé ce pattern dans `PublishLifecycleCoordinator.synthesisLock`
( ReentrantLock par key ) , mais c'est par-process et non observable.
Cascade pourrait offrir une version générique , observable via
`WorkflowEventBus` + intégrée au rate-limit existant.

### API proposée

```java
package fr.inrae.ore.cascade.api.concurrency;

public interface ConcurrencyLimiter {

    /**
     * Renvoie un limiter avec quota {@code maxConcurrent} pour chaque
     * cle distincte . Quand un workflow demande un permit avec une
     * cle deja saturee , il attend ( bloque ) ou rejette selon
     * {@link AcquirePolicy} .
     *
     * <p>Observable via WorkflowEventBus : emit
     * {@code ConcurrencyAcquireBlockedEvent} quand un workflow attend ,
     * {@code ConcurrencyReleaseEvent} quand un permit est libere .
     * Permet a un dashboard ( ex. oa-live ) d'afficher les blocages .
     *
     * @param limiterId nom de l'instance ( pour metrics tag )
     * @param maxConcurrent permits par cle ( typiquement 1 ou 2 )
     */
    static ConcurrencyLimiter byKey(String limiterId, int maxConcurrent);

    /**
     * Acquiert un permit pour la cle donnee . Bloque jusqu'a
     * disponibilite ou timeout ( WAIT_WITH_TIMEOUT ) , ou rejette
     * immediat ( REJECT_IMMEDIATELY ) .
     */
    Permit acquire(String key, AcquirePolicy policy);

    interface Permit extends AutoCloseable {
        @Override void close();
    }

    enum AcquirePolicy { WAIT_INFINITE, WAIT_WITH_TIMEOUT_SECONDS, REJECT_IMMEDIATELY }
}
```

### Impact

- **OpenADOM** : remplace `PublishLifecycleCoordinator.synthesisLock`
  par cet API ; même fonctionnellement , mais observable + rate-limited.
- **Generic** : tout consommateur cascade peut limiter par (app, datatype),
  par (user, datatype), par tenant, etc.

### Effort

- Implementation : ~200 lignes ( Semaphore + ConcurrentHashMap ) +
  events WorkflowEventBus
- Tests : 100 lignes ( contention , timeout , release )
- **Total : 1-2 jours**

---

## E — `WorkflowConfig.skipValidation` : mode " data déjà validée "

### Contexte

Quand openadom republie un fichier déjà uploadé , la data a déjà passé :
- Parsing CSV
- Check types ( int / float / date / enum )
- Validation referencevalues ( hierarchical keys , refs links )
- Groovy expressions

Lors du republish , cascade refait tous ces checks. Coûteux et inutile :
les data sont stockées telles quelles dans bytea , elles sont déjà valides
par construction ( elles ont passé le 1er upload ) . **~50 % du heap
cascade en republish est consommé par validators / parser**.

### API proposée

```java
package fr.inrae.ore.cascade.api.workflow.builder;

public interface WorkflowPipelineConfig {

    /**
     * Skip toutes les validations / transformations applicatives au
     * niveau Transform : le pipeline lit la source brute , parse en
     * Chunk<Record> ( strictement structural CSV ) , et pousse vers
     * Sink sans appeler les checkers .
     *
     * <p>Le caller est responsable de garantir que la source est deja
     * valide ( cf. cas republish d'un fichier deja stocke ) . Cascade
     * loggue un warning au demarrage si ce flag est actif et que le
     * pipeline contient des Validators.
     *
     * <p>Gain memoire mesure sur openadom republish ACBB :
     * ~50 % heap reduction ( validators sont les pires consommateurs ) .
     */
    WorkflowPipelineConfig withSkipValidation(boolean skip);
}
```

### Impact

- **OpenADOM publish** : republish d'un fichier 200 MB consomme
  ~100 MB heap au lieu de ~200 MB.
- **Generic** : tout consommateur qui sait que sa data est pre-validee
  ( ETL incremental , data warehouse refresh ) en bénéficie.

### Effort

- Implementation : ~50 lignes ( flag propagé , DataValidator bypass conditionnel )
- Tests : 50 lignes ( with vs without validation , iso-result CSV propre )
- **Total : 0.5 jour**

---

## F — `WorkflowResult.phaseTimings()` : métriques granulaires post-execute

### Contexte

Aujourd'hui `WorkflowResult` expose `duration` ( total ) , `recordsProcessed` ,
`recordsFailed` . Pour diagnostiquer un OOM ou une lenteur , openadom
( + tout consommateur ) doit instrumenter manuellement chaque phase
( load source , validate , staging copy , finalize ) avec ses propres
timers / heap snapshots.

Cascade a déjà les hooks internes ( `WorkflowEventBus` events ) mais
ne consolide pas les timings phase-par-phase dans le résultat final.

### API proposée

```java
package fr.inrae.ore.cascade.model.workflow;

public record PhaseTimings(
        Duration sourceLoadDuration,         // total wall clock Source.fetch
        Duration transformDuration,          // total wall clock Transform
        Duration collectorDuration,          // 0 si pas de Collector
        Duration sinkDuration,               // total Sink ( inclut COPY )
        Duration finalizeDuration            // post-sink ( UPSERT staging -> target )
) {}

public record WorkflowResult(
        ...
        PhaseTimings phaseTimings            // <-- ajout
) {}
```

### Impact

- **OpenADOM** : pinpoint exactement quelle phase consomme heap ou
  temps sur les workflows lents , directement dans workflow_log
  ( admin dashboard ) .
- **Generic** : monitoring + alerting plus precis ( prometheus tags
  par phase , Grafana dashboards par-phase ) .

### Effort

- Implementation : ~100 lignes ( timers + assembly post-execute )
- Tests : 50 lignes
- **Total : 0.5 jour**

---

## G — `Sinks.directCopyCompressed(...)` : COPY avec compression

### Contexte

`Sinks.directCopy` actuel envoie le data en COPY FROM stdin raw bytes.
Pour un gros chunk ( 1 MB compressible , typiquement texte CSV ) , le
réseau JDBC <-> Postgres transporte tous les octets. Postgres driver
JDBC supporte natively la compression mais cascade ne l'expose pas.

### API proposée

```java
public static Sink<Chunk<Record>> directCopyCompressed(
        DataSource ds,
        String stagingTable,
        ColumnMapping mapping,
        CompressionAlgo algo  // GZIP | LZ4 | NONE
);

public enum CompressionAlgo { NONE, GZIP, LZ4 }
```

### Impact

- **Réseau** : 3-10x réduction du trafic pour CSV texte.
- **Heap** : marginal ( compression in-flight )
- **CPU** : +10-20 % côté client , compensé par moins de I/O.

### Effort

- Implementation : ~150 lignes
- Tests : 100 lignes
- **Total : 1 jour**

---

## Priorisation pour openadom

Si je devais ordonner par valeur immédiate pour openadom :

| Rang | Item | Pourquoi |
|------|------|----------|
| **1** | A ( bytea streaming ) | Fix critique OOM publish ACBB |
| **2** | B ( SecurityContext propagator ) | Débloque cascade Action Pattern pour publish |
| **3** | E ( skipValidation ) | Gain memoire 50 % republish , easy fix |
| 4 | D ( ConcurrencyLimiter byKey ) | Resilience cross-datatype contention |
| 5 | F ( PhaseTimings ) | Observability / monitoring |
| 6 | G ( COPY compression ) | Optim reseau , pas critique |

**Effort total A + B + E** ≈ 4 jours dev cascade upstream + 1 jour
bench/test + 1 jour bump version openadom.

---

## Plan de soumission upstream

1. Forker cascade
2. Branche par item ( cascade-bytea-source , cascade-spring-interceptor , etc. )
3. PR séparées pour faciliter review
4. Bench openadom AVANT / APRES par item sur ACBB ( datasets representatifs )
5. Merge cascade → release 3.2.0
6. Bump openadom `pom.xml` cascade.version + remove workarounds locaux
