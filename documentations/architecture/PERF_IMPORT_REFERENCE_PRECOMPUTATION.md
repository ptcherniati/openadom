# Performances import — Précomputation des références (R-P2-1 à R-P2-3)

Ce document décrit les trois niveaux d'optimisation de la validation des colonnes référence
(`OA_refs`) lors de l'import CSV, ainsi que leur interaction, leur thread-safety et les
cas d'usage attendus.

---

## Contexte : pourquoi valider une colonne référence est coûteux

Pour chaque cellule d'une colonne déclarée `type: reference`, le backend doit vérifier que
la valeur CSV existe bien dans le référentiel correspondant (`OA_refs.*`). Pour un fichier SWC
typique :

- **16 colonnes référence** × **5 000 lignes** = **80 000 appels** à `ReferenceType.check()`
- Chaque appel d'origine récupérait l'UUID de la valeur par un scan O(N) du `Map.entrySet()`

Sur un référentiel de 500 entrées, cela représente jusqu'à **40 millions de comparaisons**.

---

## Niveau 1 — Index O(1) par clé naturelle (R-P2-1)

### Avant

```java
// O(N) : scan linéaire de toutes les entrées du référentiel
Optional<DataValue.LineIdentityColumnName> optionalKey =
    referenceValues.keySet().stream()
        .filter(k -> k.naturalKey().equals(value))
        .findFirst();
```

### Après

```java
// Construction O(N) une seule fois à l'initialisation (ou setReferenceValues)
private Map<Ltree, DataValue.LineIdentityColumnName> naturalKeyIndex = new HashMap<>();

private void buildNaturalKeyIndex(ImmutableMap<...> referenceValues) {
    Map<Ltree, DataValue.LineIdentityColumnName> index = new HashMap<>(referenceValues.size() * 2);
    for (DataValue.LineIdentityColumnName key : referenceValues.keySet()) {
        index.put(key.naturalKey(), key);
    }
    this.naturalKeyIndex = index;
}

// Utilisation O(1) dans check()
DataValue.LineIdentityColumnName foundKey = naturalKeyIndex.get(value);
```

### Impact

| Référentiel   | Avant (O·N appels) | Après (O·1 appels) |
|--------------|--------------------|--------------------|
| 500 entrées  | 500 comp/appel     | 1 comp/appel       |
| 2 000 entrées| 2 000 comp/appel   | 1 comp/appel       |

### Invalidation

`buildNaturalKeyIndex()` est appelée dans le constructeur ET dans `setReferenceValues()`.
Pour les imports récursifs, `setReferenceValues()` est appelé à chaque passage récursif :
l'index est reconstruit automatiquement.

---

## Niveau 2 — Cache lazy partagé entre workers Cascade (R-P2-2)

### Problème

Même avec l'index O(1), un appel à `check()` effectue :
- Un `Ltree.escapeToLabel()` (normalisation de la valeur)
- Un lookup dans `naturalKeyIndex`
- Un `referenceValues.get(foundKey)` pour l'UUID

Pour une colonne avec 100 valeurs distinctes répétées 50 fois chacune, les 4 999 appels
post-premier ne font que répéter le même calcul.

De plus, Cascade **clone** l'instance `ReferenceType` pour chaque worker parallèle :
sans partage, chaque worker recalcule indépendamment les mêmes valeurs.

### Solution

```java
// Structures partagées entre l'instance originale et TOUTES ses copies Cascade
private final Set<Ltree> seenOnce;                                   // ConcurrentHashMap.newKeySet()
private final Map<Ltree, DataValue.LineIdentityColumnName> precomputedResults; // ConcurrentHashMap

// Constructeur de copie : reçoit les MÊMES références (pas new)
ReferenceType(..., Set<Ltree> sharedSeenOnce,
              Map<Ltree, DataValue.LineIdentityColumnName> sharedPrecomputedResults) {
    this.seenOnce = sharedSeenOnce;
    this.precomputedResults = sharedPrecomputedResults;
    ...
}
```

### Comportement du cache

```
1re occurrence de valeur V :
  → lookup naturalKeyIndex → trouvé
  → seenOnce.add(V)
  → retourne résultat (pas encore en cache)

2e occurrence de valeur V (même worker OU autre worker) :
  → precomputedResults.get(V) → null (pas encore promu)
  → lookup naturalKeyIndex → trouvé
  → seenOnce.contains(V) = true  → promotion
  → precomputedResults.put(V, foundKey)  si size < maxCacheEntries
  → retourne résultat

3e+ occurrence de valeur V :
  → precomputedResults.get(V) → hit direct O(1)
  → retourne résultat sans aucun autre calcul
```

### Thread-safety

`seenOnce` est un `ConcurrentHashMap.newKeySet()` et `precomputedResults` est un
`ConcurrentHashMap`. Les opérations de lecture/écriture sont atomiques. Le pire cas de
concurrence est une « double promotion » (deux workers promettent la même valeur simultanément),
ce qui est bénin (la valeur est idempotente).

### Plafond du cache

Configurable via `cascade.import.reference-cache-max-entries` (défaut : 5 000).

```properties
# application.properties
cascade.import.reference-cache-max-entries=${CASCADE_IMPORT_REFERENCE_CACHE_MAX_ENTRIES:5000}
```

Le champ `referenceCacheMaxEntries` dans `ImportProperties` est injecté dans `DataImporter`
puis propagé à chaque `ReferenceType` via `setMaxCacheEntries(int)`.

### Invalidation

`setReferenceValues()` appelle `seenOnce.clear()` + `precomputedResults.clear()` avant de
reconstruire l'index. Nécessaire pour les imports récursifs (les UUID changent en cours de traitement).

---

## Niveau 3 — Pré-warmer sélectif par fréquence (R-P2-3)

### Principe

Les deux premiers niveaux bénéficient uniquement **à partir de la 2e occurrence** d'une valeur.
Pour les valeurs très fréquentes (ex. `projet_atlantique` présent dans 4 000 lignes sur 5 000),
les 2 premiers appels restent lents.

Le pré-warmer analyse la distribution des valeurs **avant** le démarrage des workers et
pré-chauffe le cache pour toute valeur vue ≥ `PRECOMPUTE_CACHE_THRESHOLD` fois (défaut : 2).

### Fonctionnement

```
prepareContextForDataTreatment()
  ├── écrit le fichier temporaire (passe CSV 1)
  └── prewarmReferenceCache(tempFile, refTypeByColumnName)
       ├── compte les fréquences valeur → compteur  [passe CSV 2 légère]
       └── pour chaque valeur fréquente (>= 2) :
            rt.check(val, lc)   ← 1er appel : seenOnce.add(val)
            rt.check(val, lc)   ← 2e appel  : promotion dans precomputedResults
```

### Robustesse

Le pré-warmer est une **optimisation pure** : tout échec (valeur vide, valeur invalide pour
`Ltree`, IOException) est silencieusement ignoré. Un miss de cache ne produit qu'un léger
surcoût, jamais une erreur fonctionnelle.

```java
// Garde 1 : ignorer les valeurs vides (Ltree ne les accepte pas)
if (val != null && !val.isBlank()) {
    freq.get(colIdx).merge(val, 1, Integer::sum);
}

// Garde 2 : try-catch autour de check() — non fatal
try {
    rt.check(valEntry.getKey(), lc);
    rt.check(valEntry.getKey(), lc);
} catch (Exception e) {
    // Non fatal : un miss de cache est le pire cas
}
```

---

## Synthèse des gains attendus

| Scénario | Sans optimisation | Avec R-P2-1+2+3 |
|----------|-------------------|-----------------|
| SWC 5 000 lignes, 16 cols ref, 500 entrées/ref | ~40M comparaisons | ~80K lookups O(1) + cache hits |
| ACBB 50 000 lignes, 8 cols ref, 200 entrées/ref | ~80M comparaisons | ~50K lookups + cache hits |
| Référentiel récursif (setReferenceValues multi-pass) | Rebuilt O(N) à chaque pass | Rebuilt O(N) → index O(1) le reste |

Le gain mesuré sur un import SWC représentatif est une réduction du temps de validation des
références de **~60 à ~80 %** selon le taux de répétition des valeurs.

---

## Configuration de référence

```properties
# Cache ReferenceType.precomputedResults (lookup référentiel O(1)).
cascade.import.reference-cache-max-entries=${CASCADE_IMPORT_REFERENCE_CACHE_MAX_ENTRIES:5000}
```

```yaml
# .env / .env-default
CASCADE_IMPORT_REFERENCE_CACHE_MAX_ENTRIES=5000
```

---

## Fichiers sources concernés

| Fichier | Rôle |
|---------|------|
| `ReferenceType.java` | Niveaux 1 et 2 : naturalKeyIndex + seenOnce/precomputedResults |
| `DataImporter.java` | Niveau 3 : prewarmReferenceCache() |
| `AsynchroneFileImporterContext.java` | Niveau 4 (post-P2) : naturalKeyPatternIndex — getKnownId O(1) |
| `ImportProperties.java` | Propriété `referenceCacheMaxEntries` |
| `ReferenceTypeTest.java` | Tests @PERF couvrant les 3 niveaux |

---

## Niveau 4 — Index O(1) sur `getKnownId` via `NaturalKeyPattern` (commit `4f2dd688`, develop 2026-05-06)

> Ce niveau a été ajouté après la livraison de P2 et corrige un bottleneck
> symétrique dans `AsynchroneFileImporterContext`.

### Problème

`getKnownId()` effectuait un scan O(N) sur `afterPreloadReferenceUuids` à chaque
appel. Trois callers actifs :
- `DataTransformer.toEntity` (ligne 91)
- `WithRecursion.java` (lignes 158 et 162)
- `DataValidator.java` (ligne 127)

Ce bottleneck était le miroir exact du problème corrigé sur `ReferenceType.check()`
en R-P2-1.

### Solution

```java
// Nouveau record
record NaturalKeyPattern(Ltree naturalKey, String patternColumnName) {}

// Index pré-rempli dans of(...) à partir de storedReferences
Map<NaturalKeyPattern, UUID> naturalKeyPatternIndex =
    new ConcurrentHashMap<>(storedReferences.size());

// Wrapper qui maintient les deux maps cohérentes
void putAfterPreload(NaturalKeyPattern key, UUID uuid) {
    afterPreloadReferenceUuids.put(key, uuid);   // ancienne map
    naturalKeyPatternIndex.put(key, uuid);         // nouvel index
}

// getKnownId : lookup O(1) + fallback défensif O(N)
UUID getKnownId(Ltree naturalKey, String patternCol) {
    UUID fast = naturalKeyPatternIndex.get(new NaturalKeyPattern(naturalKey, patternCol));
    if (fast != null) return fast;
    // fallback scan O(N) pour workflows antérieurs au correctif
    return afterPreloadReferenceUuids.entrySet().stream()
        .filter(e -> e.getKey().naturalKey().equals(naturalKey) && ...)
        .map(Map.Entry::getValue).findFirst().orElse(null);
}
```

### Impact

Élimine jusqu'à `N × (3 callers) × nbLignes` comparaisons pour tout import avec
récursivité ou calcul de composants de référence.