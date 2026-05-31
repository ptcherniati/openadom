# TODO_PERF_OPTIM - Montée en charge OpenADOM (30/05/2026)

## Objectif

Cible : **~100M lignes** atteintes par dépôts successifs de **fichiers ~5-10M lignes**,
**~10 utilisateurs concurrents** faisant dépôt / publi-dépubli / téléchargement / suppression.
Améliorer **performance + résilience**, sans régression (iso-résultat + RLS préservés).

Partitionnement **écarté** pour l'instant (gros chantier migration/RLS/DDL dynamique) -
on cherche les gains équivalents par index / streaming / admission / autovacuum.

Analyse menée point par point sur le code réel (session 30/05/2026). Méthode d'implémentation :
1 point = 1 implémentation iso → **full tests backend** → coche → point suivant.

Commande full tests (depuis `backend/`) :
```
reset ; mvn clean ; mvn --batch-mode test -Pall-tests -Dsurefire.excludedGroups= ; play -n synth 0.7 sine 400 2>/dev/null
```

---

## Acquis (déjà livrés cette série, validés)

- [x] **Heartbeat V17 NOWAIT** : `beat_workflow` 15488ms → ~2ms (contention verrou éliminée). Validé live ×4.
- [x] **Optims transform par-ligne** : `DataValidator.matchingTarget` (split hissé), `InternationalizationDisplay.getDisplays*` (scan→lookup O(1)). Iso, testé.
- [x] **column-extraction** : défaut `false` (casse sur colonne `"authorization"` quotée + type custom) + fix `fetchColumnPgTypes` case-insensitive (dormant).
- [x] **GIN pending list** : testé A/B → aucun gain → reverté (plomberie config dormante, défaut vide).

---

## PHASE 0 - Quick wins (faible risque, gros ROI, iso, indépendants)

### [x] P5-A - Vrai curseur streaming sur l'extract (🔴 anti-OOM download) - FAIT CHIRURGICAL
> v1 ( pool `auto-commit=false` pool-wide ) cassait 195 tests → reverté.
> **v2 chirurgical** : `DataRepository.streamWithServerCursor` - emprunte UNE connexion,
> `auto-commit=false` + `fetchSize=2000` pour CETTE requête ( curseur serveur PG, mémoire
> constante ), libère tout au `close()` du Stream ( RS + stmt + rollback read-only +
> auto-commit restauré + connexion rendue au pool ). `findAllByDataTypeFlux` routé dessus.
> Portée stricte : le pool streaming reste auto-commit=true, charte/LO/additional inchangés.
> **Full-suite : 4342 tests, 0F/0E** ( vs v1 = 115F/80E ) → zéro régression. OOM extract éliminé.
**Problème** : `spring.datasource.streaming.hikari.auto-commit=true` + aucun `fetchSize`
→ le driver PG rapatrie TOUT le resultset en heap avant la 1ère ligne → OOM à 5-10M.
Le `Flux<DataRow>` itère un resultset déjà matérialisé (faux streaming).
**Fix** : curseur serveur réel = `autocommit=false` + `fetchSize` (ex 2000) sur le chemin
streaming (template/pool streaming, ou router le CSV sur le chemin `ConnectionCallback`
curseur existant `DataRepository` L201-319). Mémoire constante quel que soit le volume.
**Fichiers** : `config/StreamingDataSourceConfig.java`, `application.properties` (streaming),
`persistence/DataRepository.findAllByDataTypeFlux`.
**Risque** : sémantique tx des reads streaming (read-only 1 tx) - à valider. iso-données.

### [~] P4-A - Drop GIN plein refvalues #5 (~½ coût GIN write import) - PRÊT, NON APPLIQUÉ
> SQL prêt dans `migration/application/V5` (section 3, commentée) + backfill partiels
> assuré par `MigrateService.updateAuthorizationIndexes`. **Non auto-appliqué** : nécessite
> confirmation `idx_scan≈0` sur #5 en prod (pas de requête refvalues cross-type sans filtre).
> Activer = décommenter le DROP dans V5 (ou nouvelle migration) après sign-off prod.
**Problème** : refvalues indexé GIN 2× par ligne : `referencetype_refvalue_gin_idx` (plein,
statique V1) + `<type>_refvalues_index` (partiel par type, `AuthorizationIndex`). Les partiels
couvrent tous les types scopés.
**Fix** : migration qui (1) backfill les partiels manquants pour tout referencetype existant,
(2) DROP le GIN plein #5. → ~½ maintenance GIN refvalues à l'import.
**Fichiers** : nouvelle migration `application/Vxx`, `AuthorizationIndex.java` (backfill).
**Précondition** : confirmer `idx_scan≈0` sur #5 en **prod** (local reset = pas d'historique).
Réversible. iso-données (perf lecture seulement si une requête cross-type sans filtre existait).

### [x] P4-B - Drop index redondant `referencetype_idx` #6
> FAIT : `DROP INDEX IF EXISTS ...referencetype_idx` dans `migration/application/V5`.
> Prefixe-redondant prouvé (couvert par nk_patternColumnNam_type + referencetype_binaryfile_idx).

### [x] P1-A - Semaphore global imports (anti-meltdown 10 users)
> FAIT : `ImportRateLimiter` + Semaphore global (`cascade.import.max-concurrent-global`, défaut 6)
> par-dessus le per-user ; relâche le slot per-user si le global rejette ; tests unitaires ajoutés
> (GlobalCapTest). Property + env wiring.
**Problème** : `ImportRateLimiter` = quota par-user (3) seulement → 10×3=30 imports simultanés
possibles → saturation CPU/IO/connexions.
**Fix** : semaphore GLOBAL (cap N=4-6 tous users) en plus du per-user, dans `ImportRateLimiter`.
Configurable (`cascade.import.max-concurrent-global`). 429 si dépassé.
**Fichiers** : `ImportRateLimiter.java`, `ConfigFieldRegistry`, `application.properties`, env.

### [ ] P2-A - Autovacuum par-table data (hook + job) + REINDEX GIN
**Problème** : V16 ne tune que `oa_audit.*`. Tables data (`referencevalue`, data, `reference_reference`)
= défaut 20% → à 100M, 20M tuples morts avant vacuum → bloat snowball.
**Fix** (les deux, décision user) :
- hook à la création de table (apply-config) : set `autovacuum_vacuum_scale_factor=0.02`,
  `autovacuum_vacuum_insert_scale_factor`, `cost_limit` ↑ sur chaque table data créée.
- job de maintenance : applique les storage params sur toutes les tables data existantes
  (rattrapage) + REINDEX CONCURRENTLY périodique sur les GIN + monitoring bloat (pgstattuple).
**Fichiers** : service apply-config (création tables), nouveau scheduled job, migration.

---

## PHASE 1 - Infra / résilience (effort moyen)

### [~] P1-C - Pool Hikari cascade dédié (#308) - CONÇU, IMPL DIFFÉRÉE
> **Analyse de conception faite.** Carte des connexions cascade :
> - binaryfile INSERT + COPY→staging ( synchrone , dans la tx requête via
>   `TransactionAwareDataSourceProxy` pour FK-visibility + atomicité ) → **NON déplaçable**.
> - **finalize deferred** ( UPSERT staging→final , en afterCommit , connexion fraîche
>   hors tx caller - `TxAwareDeferredRunner` ) → **déplaçable** ( post-commit , atomique
>   en soi ) . C'est la connexion lourde + longue.
> **Design retenu** : DataSource à routage tx-aware donné à la cascade —
> tx active → connexion requête ( inchangé ) ; hors tx ( finalize deferred ) → pool
> cascade dédié. Isole la connexion lourde SANS toucher l'atomicité. + bean Hikari
> `cascadePool` configurable ( `SPRING_DATASOURCE_CASCADE_*` ) + métriques Micrometer
> + remontée oa-live ( affichage / hot-resize MXBean ) .
> **Impl DIFFÉRÉE** : gain marginal **faible** ( P1-A cap global = 6 imports max →
> ≤6 connexions finalize sur pool main 30 → HTTP pas starvé ) ; **risque atomicité
> réel** sur le wrapper ; **PgBouncer** ( infra ) répond mieux au scaling connexions
> sans toucher le modèle tx. À implémenter SI symptôme réel ( latence HTTP mesurée
> pendant imports ) . Cf. analyse session 30/05.

### [i] Cohérence pools / max_connections - VÉRIFIÉE OK
> Pas de sur-souscription : dev = main 15 + streaming 5 = 20 ≤ max_connections 30
> ( marge 10 ) ; prod = 40 ≤ 150. WAL/checkpoint déjà tunés ( max_wal_size 4GB ,
> checkpoint_completion_target 0.9 ) . Rien à corriger.

### [ ] P1-D - max_connections cohérent
main + streaming + cascade + marge admin ≤ max_connections. Config DB + pools.

### [x] Récursif OOM - cap ( garde-fou résilience )
> FAIT : `RecursiveReferenceImportTooLargeException` ( 413 ) + garde dans
> `DataService.addData` ( compte les lignes du buffer disque pour un référentiel
> récursif , rejette AVANT le transform si > seuil → évite l'OOM-crash JVM qui
> tomberait tous les users ; catch dédié pour ne pas être avalé par le fallback
> legacy ) . Config `cascade.import.recursive-max-rows` ( application.properties +
> cascade.env ) , **défaut 0 = désactivé** ( iso ; poser une vraie valeur par profil
> selon la heap ) . Full-suite 4342 tests , 0F/0E . Le vrai fix ( récursif streamé )
> reste un gros chantier algo séparé.

### [ ] BUG - seuil d'erreurs non respecté sur référentiel récursif (détection doublons)
Constaté en live ( import ticket_507 `t_soil_water_content_swc` , 31/05 ) : `maxErrors=100`
configuré ( `CASCADE_IMPORT_MAX_ERRORS_THRESHOLD` / `ImportProperties.maxErrorsThreshold` ) ,
mais l'UI remonte **2865 erreurs** « ... a le même identifiant ... que les lignes [...] ».
L'import a tourné ~243s ( 4 cores ) avant de jeter `InvalidDatasetContentException`.

> **Cause** : deux chemins d'erreurs distincts.
> - Pipeline cascade ( transform/sink ) : **borné** par `withMaxErrors(100)` ( cf.
>   `CascadeImportPipeline` ~L646 ) → OK.
> - **Détection des doublons de clé naturelle du référentiel RÉCURSIF**
>   ( `WithRecursion` , message « même identifiant que les lignes » ) : chemin
>   séparé qui **n'honore PAS `maxErrorsThreshold`** → accumule TOUTES les erreurs
>   ( 2865 ) au lieu d'aborter à 100.
>
> **Impact** ( aligné scaling 5-10M ) :
> 1. perf : on parcourt + construit la liste d'erreurs complète au lieu de couper tôt ;
> 2. mémoire : 2865 objets erreur , chacun portant la **liste des numéros de lignes
>    dupliquées** → s'ajoute au risque OOM récursif déjà identifié ( la liste d'erreurs
>    elle-même peut gonfler la heap sur un 10M plein de doublons ) ;
> 3. UX/mail : on noie l'utilisateur + on alourdit le mail d'erreurs.
>
> **Fix attendu** : appliquer le même plafond ( `maxErrorsThreshold` ) au collecteur
> d'erreurs du référentiel récursif → couper la collecte à N et signaler « N+ erreurs ,
> tronqué » ( au lieu de tout accumuler ) . Iso-résultat sur le verdict ( import reste
> rejeté ) , seul le **volume d'erreurs remontées** est borné. Lié au chantier
> « récursif streamé » ( même chemin mémoire ) .

### [x] PgBouncer (infra) - IMPLÉMENTÉ derrière toggle, OFF par défaut
Pooler transaction-mode devant PG. La vraie réponse scaling 10+ users : multiplexe
les transactions courtes du backend sur un petit jeu stable de connexions PG, donc
`max_connections` n'est plus le plafond et la churn de connexions disparaît.

> **Livré ( local_deployment, EN )** : toggle unique `PGBOUNCER_ENABLED`
> ( `config/infra/pgbouncer.env`, **défaut false = comportement actuel, zéro changement** ).
> À true, `start.sh` : (1) active le profil compose `pgbouncer` ( service
> `infra/pgbouncer/compose.yml`, image `edoburu/pgbouncer`, mode `transaction`,
> `max_prepared_statements=256` pour le cache prepared JDBC, `scram-sha-256` ),
> (2) repointe le **pool MAIN** Hikari vers `pgbouncer:6432`.
>
> **Le pool STREAMING reste en DIRECT sur Postgres** ( `SPRING_DATASOURCE_STREAMING_URL`,
> backend `application.properties` découplé avec fallback iso quand le toggle est off ) :
> les curseurs serveur 6h des exports ZIP/CSV ne doivent jamais transiter par un pooler
> en mode transaction. Locks advisory xact-level, TEMP ON COMMIT DROP et SET LOCAL
> restent transaction-scoped → sûrs sous transaction pooling.
>
> Validé : `docker compose config` off → main+streaming direct ( iso ) ; on → main
> `pgbouncer:6432`, streaming direct, service présent. **Test live à faire** ( la suite
> backend se connecte en direct aux Testcontainers, ne couvre PAS PgBouncer ).

### [ ] WAL / checkpoint tuning (infra)
max_wal_size, checkpoint_completion_target, disque WAL. **Config DB, aval requis.**

### [ ] Observabilité scale (Grafana)
Métriques bloat / lock waits / saturation pool / WAL rate + alertes. **Étendre dashboards.**

### [i] Heartbeat transform NULL - ANALYSÉ, PAS un bug ( faux soupçon levé )
Constat live ( import 2M , 31/05 ) : `last_heartbeat_at` reste NULL pendant tout le
transform ( ~350s ) , puis bat ( ~30s ) dès le finalize. Soupçonné faux-zombie à 10M.
**Vérification code : ce n'est PAS un bug.** Design délibéré et cohérent :

> La transaction de l'import détient un **verrou de ligne** sur SA row `workflow_log`
> pendant toute la phase synchrone ( transform + COPY ) . `beat_workflow` ( V17 ) sonde
> en `FOR UPDATE NOWAIT` → row verrouillée → skip silencieux ( beat redondant ) . Le
> sweeper `mark_zombie_workflows` ( V4 ) sonde en `FOR UPDATE SKIP LOCKED` → **skip la
> même row verrouillée** → ne peut PAS marquer zombie un import actif. Le verrou EST la
> preuve de vie. Après commit de la tx ( finalize deferred , row libérée ) , le
> `HeartbeatService` ( thread dédié ) bat pour couvrir la phase finalize longue. Aucun
> trou : pas de fenêtre IN_PROGRESS + déverrouillée + sans heartbeat > seuil.

> **Sweeper audité OK** : guard `p_minutes>0` , `WHERE status='IN_PROGRESS' AND
> COALESCE(last_heartbeat_at,start_time) < now()-Nmin` , `FOR UPDATE SKIP LOCKED` ,
> UPDATE atomique. Boot-cleanup ( toutes IN_PROGRESS → CANCELLED , SKIP LOCKED ) correct
> en mono-instance ( cf `APP_WORKFLOW_ZOMBIE_CLEANUP_ON_BOOT` ) .

> **Invariant porteur ( à documenter / tester pour ne pas le casser )** : la sûreté
> dépend du fait que la tx d'import **garde le verrou de la row workflow_log en continu**
> pendant toute la phase synchrone. Si un refacto futur committait par chunk ( libère le
> verrou entre chunks ) ou écrivait la row sur une connexion auto-commit séparée , la
> protection SKIP-LOCKED tomberait → faux-zombie réel. Ajouter un test de non-régression
> sur cet invariant. Reste seulement cosmétique : pas d'indicateur de vivacité en UI
> pendant le transform ( le verrou n'est pas exposé au front ) .

### [ ] Heartbeat watchdog - amélioration observabilité ( pattern standard )
Refacto optionnel pour avoir une **vraie vivacité + progression en UI pendant le transform**
( aujourd'hui figé , cf note ci-dessus ) , et **découpler du verrou + de la tx longue** :

> **Écrire la row IN_PROGRESS HORS de la tx d'import** ( connexion auto-commit séparée ,
> comme `WorkflowLogWriter` ) **et ne JAMAIS l'UPDATE en in-tx** ( phase , parent , etc.
> sur connexion séparée aussi ) . Conséquence : la row n'est plus verrouillée par la tx
> d'import → le `HeartbeatService` ( thread dédié ) bat librement toutes les 30s dès le
> transform → progression temps réel + le sweeper retombe sur l'ancienneté heartbeat
> standard ( plus besoin du `SKIP LOCKED` comme protection ) .
>
> **Trade-off** : on perd la détection-crash gratuite du verrou ( JVM tuée → plus de
> verrou → sweeper détecte ) ; remplacée par l'ancienneté heartbeat ( pattern watchdog
> normal , équivalent ) . Optionnel : doubler avec `pg_advisory_lock(corrId)` dédié comme
> jeton de vie explicite , découplé de la row.
>
> Pas urgent ( l'actuel est correct ) ; à faire si l'UI figée gêne , ou avec le découpage
> de la tx longue.

### [ ] Finalize / UPSERT staging→final lent - levier corrigé
Bench live 2M : finalize ~420s ( 50% du temps total ) , DB-bound , maintenance des index
GIN ( refValues , refsLinkedTo ) dominante sur chaque batch ( `DataFileRead/Extend` ) .

> **drop+rebuild GIN au finalize = ÉCARTÉ.** `referencevalue` est une table partagée
> ( tous dépôts + toutes lectures publi/dépubli/download/filtres ) . Drop d'un index
> partagé sous dépôts parallèles + lectures = race ( 2 dépôts ne peuvent pas drop/rebuild
> chacun ) , et un dépôt qui foire après le drop laisse la base sans index → rampe
> totale. `DROP/CREATE INDEX` ne composent pas avec le finalize atomique ;
> `CONCURRENTLY` ne tient pas dans une tx. C'est une astuce de **load initial one-shot
> sur table privée/vide** , pas pour de l'UPSERT incrémental concurrent.
>
> **Best practice pour ce cas** :
> 1. **Partitionnement par dépôt/version** ( cf PHASE 2 ci-dessous ) : construire la
>    partition + ses index en isolation puis `ATTACH PARTITION` ( méta quasi-instantané ) .
>    Le GIN de la nouvelle partition est bâti une fois sur ses seules données , sans
>    contention avec les autres dépôts ni les lectures. **La vraie réponse.**
> 2. **En attendant ( cheap , testable )** : `CASCADE_IMPORT_FINALIZE_MAINTENANCE_WORK_MEM`
>    est **vide** → construction/maintenance GIN au défaut cluster. Poser 256MB-1GB
>    ( SET LOCAL pendant le finalize , déjà plombé ) accélère la maintenance d'index.
>    À A/B-bencher iso-résultat.
> 3. dup-check WARN scanne les 2M ( ~37s ) même sans agir → si policy OFF , gain net.

---

## PHASE 2 - Structurel (différé par décision)

### [ ] (différé) Export pré-matérialisé au publish (P5-B)
Générer ZIP/CSV au publish, servir le fichier au download → tue latence + coût DB répété.

### [ ] (écarté) Soft-unpublish (visibilité + delete différé)
Unpublish instantané par flag + delete physique batché hors chemin critique.

### [ ] Partitionnement par dépôt/version - RÉPONSE STRUCTURELLE (finalize + volume + delete)
Promu de "écarté" à levier central après le bench live : c'est la best-practice pour
l'UPSERT incrémental concurrent dans une table indexée partagée ( cf finalize/GIN ci-dessus ).
- Build partition + index GIN en isolation puis `ATTACH PARTITION` ( méta quasi-instantané ) :
  GIN bâti une fois sur les seules données du dépôt , aucune contention inter-dépôts.
- Drop-partition au unpublish/supersede/delete = **instantané** ( vs DELETE + vacuum d'une
  table monolithique ) + pruning à la lecture.
- Gros chantier : RLS / role / SECURITY DEFINER à préserver iso. Design d'abord.

---

## Résultats / bench (fin de plan)

### Full tests backend (régression)
- **Baseline** (sans changements) : BUILD SUCCESS, 4340 tests, **0F / 0E**.
- **Phase 0 finale** (P1-A + P4-B + V5 + P5-A reverté) : BUILD SUCCESS, 4342 tests, **0F / 0E**.
- → **zéro régression**. (+2 tests = GlobalCapTest P1-A.)

### Incident P5-A ( capté par le full-test )
1ère version P5-A ( pool streaming auto-commit=false ) = **115F + 80E** en full-suite
( baseline 0/0 le prouve ). Cause : flip auto-commit pool-wide casse la tx des autres
usages streaming + fixtures. **Reverté.** Leçon : `NormalizedServiceTest` isolé passait
( 40/40 ) - seule la full-suite a revélé la pollution inter-tests. Le full-test obligatoire
a évité un ship cassé.

### Livré ( propre, dans la suite )
| Item | Gain attendu | Risque |
|------|-------------|--------|
| Heartbeat V17 NOWAIT ( commité avant ) | 15488ms→2ms beat , plus de contention | nul ( validé ) |
| P1-A semaphore global imports | filet anti-meltdown 10 users | nul |
| P4-B drop index btree #6 | -1 index/ligne à l'import | faible |
| P2-A V5 autovacuum insert_scale_factor + cost_limit | autovacuum suit à 100M ( pas de bloat snowball ) | nul ( storage params ) |

### Non livré
- **P5-A** : à refaire chirurgical ( curseur par-requête ) - le gain anti-OOM extract reste à capturer.
- **P4-A** drop GIN #5 : SQL prêt ( V5 commenté ) , gated sur idx_scan prod.
- **Phase 1** ( pool cascade , cap récursif OOM , PgBouncer , WAL , observabilité ) + **Phase 2** : documentés.

**Bilan** : Phase 0 partielle livrée sans régression ( résilience 10 users + autovacuum 100M + index
write ) . Le plus gros gain perf restant ( P5-A extract streaming + P4-A GIN ) nécessite un travail
ciblé supplémentaire ( chirurgical / validation prod ) plutôt qu'un changement large autonome.

---

## TODO ( plus tard ) - Plafond de workers TRANSFORM par type d'opération ( fairness )

**Statut** : analysé le 31/05/2026, **décidé : à faire plus tard** ( pas maintenant ). Décisions
verrouillées ci-dessous pour reprise directe.

### Besoin
Plafonner le nombre de **workers transform concurrents PAR TYPE d'opération** sur le pool transform
partagé, avec arbitrage inter-jobs. Ex : max dépôt = 2, pool = 4 → un dépôt utilise ≤2 threads, les
2 restants dispo ; un publish qui arrive prend les dispo dans la limite de SON max ; au-delà, les
chunks attendent ( pas de rejet ). But = empêcher qu'un gros dépôt n'affame les publish ( ~10 users,
100M lignes ).

### Faits d'archi ( vérifiés )
- `cascade` ( repo local, branche **develop** ) : `WorkflowPoolRegistry` = singleton, **UN seul**
  `transformPool` partagé par tous les workflows ( `getTransformExecutor()` ). Le « parallelism » par
  workflow ne cappe PAS la part de threads qu'un job prend dans ce pool.
- Tâches transform soumises dans les drivers : `StagedWorkflowDriver` ( CompletableFuture chaîné ) et
  `PipelinedCoordinator` ( producer + `Semaphore inflight`, `transformPool` ). Ce sont les 2 points
  d'insertion.
- **UNPUBLISH / DELETE = SQL direct chunké, SANS stage transform.** PUBLISH FAST = COPY SQL, sans
  transform. → le plafond worker ne concerne QUE **IMPORT + PUBLISH ( LITE/FULL )**.
- Pattern existant réutilisable : `ImportRateLimiter` ( sémaphore par user, hot-resize ),
  `CascadePoolReloader.resize()`, registre hot-edit `ConfigFieldRegistry` + `/api/dashboard/config`
  ( ConfigEditPanel oa-live ).

### Décisions verrouillées
- **Design B** = **BoundedExecutor + `Semaphore` par type** sur le pool transform partagé ( pattern
  Goetz/JCiP ). C'est la SEULE façon JDK propre ( pas de cap natif par sous-tâche dans un
  ThreadPoolExecutor ). `acquire(label)` **sur le thread soumetteur, AVANT** `pool.execute` ;
  `release` en `finally` ( + sur rejet/cancel ). NE PAS acquérir dans la tâche déjà sur un thread du
  pool ( sinon threads bloqués → famine/deadlock ).
- **Périmètre** : IMPORT + PUBLISH uniquement. Unpublish/delete restent gérés par le rate-limit de
  jobs par user existant.
- **Défaut = 0 ( illimité / OFF )** → comportement actuel à l'octet près, **opt-in strict**, zéro
  régression tant que non activé.

### Plan d'exécution
- **C1 cascade** : SPI `TransformAdmissionController` ( interface, impl défaut **no-op** + impl
  `LabeledSemaphoreAdmission` Map<label,Semaphore>, resize à chaud ). Brancher dans les 2 drivers
  ( acquire avant submit, release finally, interruptible sur cancel ). Label porté par le builder
  ( `.transformConcurrencyLabel("IMPORT"|"PUBLISH")` ). Tests : cap respecté, pas de deadlock, release
  sur exception/cancel, fairness 2 types concurrents, ordering STAGED inchangé.
- **C2 cascade** : bump version mineure + CHANGELOG + `mvn install` local + tag/push après validation.
- **O1 openADOM** : bean controller depuis les plafonds, label IMPORT ( CascadeImportPipeline ) /
  PUBLISH ( PublishLifecyclePhase2Handler ), bump dépendance cascade.
- **O2** : props `cascade.import.max-transform-workers` + `openadom.publish.max-transform-workers`
  ( défaut 0=OFF, volatile+setter resize ), enregistrées HOT dans `ConfigFieldRegistry` ( règle
  1 ≤ cap ≤ pool transform, warning si cap > pool ).
- **O3** : env ( cascade.env ) + deployment ( commentaires/commit EN pour local_deployment ).
- **OA1 oa-live** : 2 champs éditables dans **Configuration > Dynamique** ( max workers dépôt / publish )
  via ConfigEditPanel, validation + warning perf.
- **OA2 oa-live** : observabilité **Supervision > Temps-réel** : workers transform in-use / en attente
  par type ( events cascade → endpoint live → bloc UI ).
- **V** : bench iso-résultat AVANT/APRÈS sous rôle applicatif ( mêmes lignes + RLS ), débit
  transform/copy/finalize, scénario 1 gros dépôt + N publish ( fairness + non-famine ), cancel <5s avec
  chunk en attente de permit, full backend tests + tests cascade.

### Risques à surveiller
Famine/deadlock si acquire après occupation d'un thread pool ( → acquire avant submit ) ; effondrement
débit si cap trop bas ; interaction avec `parallelism` ( cap = borne sup, parallelism = cible, cap≥1,
recursive forcé à 1 ) ; iso-résultat ( throttle ne doit que retarder ) ; fuite de permits sur
cancel/exception ( release finally ) ; compat ascendante cascade ( no-op par défaut ).
