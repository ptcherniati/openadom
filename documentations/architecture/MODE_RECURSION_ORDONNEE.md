# Mode récursion ordonnée (`__ORDER_STRICT__`)

Ce document décrit le mode de traitement des référentiels récursifs en ordre strict :
activation, comportement, différences avec le mode legacy, et configuration.

---

## Pourquoi un mode récursion ordonnée ?

### Problème du mode legacy

Un référentiel récursif (ex. hiérarchie de sites : Watershed → Platform → Point) doit être
importé en respectant la règle : **chaque ligne parent doit exister avant ses enfants**.

Le pipeline Cascade **découpe le CSV en chunks** pour traiter plusieurs lignes en parallèle.
En mode legacy, si un parent se trouve dans le chunk 2 et son enfant dans le chunk 1 — traité
en premier — la validation échoue silencieusement avec `missingParentLine`.

**Contournement legacy** : forcer `chunkSizeLines = Integer.MAX_VALUE` (tout le fichier en un
seul chunk) + `parallelism = 1`. C'est fiable mais lent sur les gros fichiers.

### Solution : mode ordonné

Si le fichier CSV garantit que **les parents apparaissent avant leurs enfants**, le pipeline
peut traiter le fichier normalement (chunks de taille normale, parallélisme activé). Toute
référence à un parent non encore vu génère une **erreur immédiate** plutôt qu'une attente.

---

## Tableau comparatif des modes

| Critère                    | Legacy (défaut)               | Ordonné (`__ORDER_STRICT__`) |
|----------------------------|-------------------------------|------------------------------|
| Prérequis sur le CSV       | Aucun                         | Parents **avant** enfants    |
| `chunkSizeLines`           | `Integer.MAX_VALUE`           | Valeur configurée (ex. 1000) |
| Parallélisme               | Forcé à **1**                 | Forcé à **1** (race condition évitée) |
| Comportement parent absent | Différé dans `missingParentLine` | Erreur immédiate             |
| Performance                | Faible (1 chunk = tout le fichier) | Bonne (chunks normaux)    |

> **Note** : même en mode ordonné, le parallélisme est forcé à 1 pour éviter les race
> conditions sur la liste `missingParentLine` accumulée entre workers. Le gain de performance
> vient des **petits chunks** (moins de mémoire peak, meilleure progression live) et non du
> parallélisme.

---

## Activation

### Option 1 — Tag YAML par datatype (recommandé)

```yaml
# Dans la configuration YAML du référentiel récursif
validations:
  validation_hierarchie:
    tags:
      - __ORDER_STRICT__
    checkers:
      reference_recursive:
        checker: Reference
        params:
          refType: tr_site_sit
          recursive: true
```

Le tag `__ORDER_STRICT__` est détecté par
`AsynchroneFileImporterContext.isOrderStrictTaggedOnRecursiveValidation()` qui parcourt
toutes les validations portant un checker récursif et cherche une instance de `Tag.OrderStrictTag`.

### Option 2 — Propriété Spring globale (fallback pour tous les datatypes)

```properties
# application.properties
cascade.import.ordered-recursion-mode=true
```

```yaml
# .env / .env-default
CASCADE_IMPORT_ORDERED_RECURSION_MODE=false   # défaut : mode legacy
```

Cette propriété active le mode ordonné pour **tous** les datatypes récursifs, sans distinction.
Utile si tous les CSV de l'application garantissent l'ordre parent-avant-enfants.

### Priorité

```
isStrictOrdered = isOrderStrictTaggedOnRecursiveValidation()   // tag YAML
                  || importProperties.isOrderedRecursionMode() // propriété Spring
```

Les deux sources se combinent en `OR` : l'une ou l'autre suffit à activer le mode.

---

## Implémentation

### `CascadeImportPipeline.effectiveChunkSizeLines()`

```java
static int effectiveChunkSizeLines(ImportProperties props, boolean isRecursive, boolean isStrictOrdered) {
    if (!isRecursive) return props.getChunkSizeLines();
    return isStrictOrdered ? props.getChunkSizeLines() : Integer.MAX_VALUE;
}
```

### `CascadeImportPipeline.effectiveParallelism()`

```java
static int effectiveParallelism(ImportProperties props, boolean isRecursive) {
    return isRecursive ? 1 : props.getParallelism();
}
```

### Appel dans `execute()`

```java
final boolean isRecursive     = dataImporter.getDataImporterContext().isRecursive();
final boolean isStrictOrdered = dataImporter.getDataImporterContext().isOrderStrictTaggedOnRecursiveValidation()
        || importProperties.isOrderedRecursionMode();
final int chunkSizeLines = effectiveChunkSizeLines(importProperties, isRecursive, isStrictOrdered);
final int parallelism    = effectiveParallelism(importProperties, isRecursive);
```

---

## Exemple de fichier CSV valide en mode strict

```csv
tze_type_nom;zet_chemin_parent;zet_nom_key
Watershed;;oir
Watershed;;nivelle
Platform;- Oir;p1
Platform;- Oir;p2
Platform;NULL_KEY__oir - P1;a   ← enfant de p1 qui est déjà défini ci-dessus
```

Tout `zet_chemin_parent` référençant une ligne **non encore vue** provoquera une erreur
`missingParentLineInRecursiveReference` signalée immédiatement.

---

## Tests

La classe `CascadeImportPipelineRecursionConfigTest` couvre les cas :

| Test | `isRecursive` | `isStrictOrdered` | `chunkSizeLines` attendu |
|------|--------------|-------------------|--------------------------|
| Non récursif                           | false | false | configuredValue |
| Récursif legacy (non ordonné)          | true  | false | `MAX_VALUE`     |
| Récursif ordonné (tag ou propriété)    | true  | true  | configuredValue |

---

## Fichiers sources concernés

| Fichier | Rôle |
|---------|------|
| `Tag.java` → `OrderStrictTag` | Définition du tag `__ORDER_STRICT__` |
| `AsynchroneFileImporterContext.java` | `isOrderStrictTaggedOnRecursiveValidation()` |
| `ImportProperties.java` | Champ `orderedRecursionMode` |
| `CascadeImportPipeline.java` | `effectiveChunkSizeLines()` + `effectiveParallelism()` |
| `CascadeImportPipelineRecursionConfigTest.java` | Tests unitaires |