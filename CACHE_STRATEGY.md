# openADOM cache strategy

Décisions documentées ( 2026-05-10 ) sur où mettre quoi : cache mémoire JVM
vs précompute table SQL.

## Matrice de décision

| Critère                     | Cache JVM ( `MemoryCache` )           | Précompute table ( SQL )              |
|-----------------------------|---------------------------------------|---------------------------------------|
| TTL                         | court ( minutes - heures )            | long ( jusqu'à invalidation )         |
| Capacité                    | borné petit ( ~100 MB max )           | borné grand ( GB possible )           |
| Survit restart backend      | non                                   | oui                                   |
| Partagé multi-instance      | non ( un cache par JVM )              | oui ( partagé naturellement )         |
| Coût miss                   | recompute query                       | recompute query + UPSERT              |
| Coût hit                    | sub-ms ( HashMap lookup )             | qq ms ( SELECT + I/O )                |
| Invalidation                | TTL ou hook applicatif                | trigger SQL ou hook applicatif        |
| Complexité ops              | nulle                                 | migration Flyway + trigger            |

## Critères de choix

Choisir **cache JVM mémoire** quand :

- compute < 1 s ET résultat petit ( < 1 MB ) ;
- données spécifiques session / user / request ( les autres instances n'ont
  pas besoin du même résultat ) ;
- staleness de quelques minutes acceptable ;
- recompute après restart acceptable.

Choisir **précompute table** quand :

- compute > 1 s ET résultat partagé entre tous les utilisateurs ;
- résultat doit être servi rapidement même au premier hit après restart ;
- taille empêche un cache mémoire ( > 100 MB ou cardinalité explosive ) ;
- la donnée mute peu fréquemment ( import / delete - pas sur chaque
  toggle ).

Si les deux critères s'appliquent : **précompute table** wins .

## État courant ( 2026-05-10 )

### Cache JVM ( `fr.inra.oresing.cache.MemoryCache` )

| Cache                          | Clé                                  | Valeur                              | Capacity / TTL  | Justification                                                                                              |
|--------------------------------|--------------------------------------|-------------------------------------|-----------------|------------------------------------------------------------------------------------------------------------|
| `authorizationScopes`          | userId :: app :: scope               | `ScopesValue`                       | configurable    | par-user , recompute < 100 ms , staleness OK                                                               |
| `checkedFormatComponents`      | app :: dataType                      | format components                   | configurable    | pas de mutation runtime , recompute coûteux ( CheckerFactory )                                             |
| `filterListCache`              | app :: dataType                      | `FilterListValue`                   | configurable    | recompute moyen , staleness OK                                                                             |
| `referencedFilesCache`         | app :: dataType :: **single** fileId | `List<ReferencedBinaryFiles>`       | 200 / 30 min    | clé fine ( pas le set ) sinon miss systématique au toggle ; cf. fix `72fac03` -> granularité revue         |

### Précompute SQL ( table dédiée + trigger )

| Table                                | Trigger source                        | Justification                                                                                              |
|--------------------------------------|---------------------------------------|------------------------------------------------------------------------------------------------------------|
| `<app>.oresisynthesis`               | `buildSynthesis` post-mutation        | compute lourd ( CTE multi-niveau sur 9.9 M rows ) , partagé , doit survivre restart                        |
| `<app>.referencevalue_count_stats`   | trigger `AFTER INSERT / DELETE`       | compteur agrégé , exact via trigger , évite seq scan COUNT(*) à chaque page-load                           |
| `<app>.data_versioning_scope_cache`  | trigger `AFTER INSERT / DELETE` statement-level + hooks Java grant/revoke + YAML edit | dropdowns de scope ( ecran DataVersioningView ) , clé ( app , ref_type , column , user_id ) ; configurable enabled/max-entries-per-app |

## Anti-patterns à éviter

1. **Clé de cache liée à un ensemble qui mute à chaque action** ( cf. ancien
   `referencedFilesCache` qui hashait tout le set `publishedIds` -
   chaque toggle invalidait le cache ) → toujours préférer la clé la plus
   fine indépendante de l'action utilisateur .
2. **Cache mémoire sur résultat > 1 MB et cardinalité haute** → prendre une
   table .
3. **Précompute table sans trigger d'invalidation** → drift garanti , ne pas
   créer .
4. **Double cache** ( mémoire en frontal + table en backing ) sans politique
   write-through claire → choisir un seul niveau , documenter ici .

## Process pour ajouter un nouveau cache

1. Mesurer le coût compute ( logs timing ou EXPLAIN ANALYZE ) .
2. Quantifier le hit ratio attendu ( répétition de la même clé entre
   utilisateurs / sessions ) .
3. Appliquer la matrice ci-dessus .
4. Ajouter une ligne dans le tableau "État courant" du présent fichier .
5. Documenter la stratégie d'invalidation ( hook applicatif , trigger SQL ,
   ou TTL ) .
