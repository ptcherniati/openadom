# Catalogue des erreurs de dépôt de fichier

Ce document liste **tous les codes d'erreur** que le backend peut renvoyer lors d'un dépôt de
fichier (référentiel ou donnée). Il permet au frontend de préparer les traductions et
l'affichage adapté pour chaque cas.

---

## Structure d'une erreur

Chaque erreur est sérialisée en JSON dans le flux réactif (`REACTIVE_ERROR`) et dans le rapport
final `InvalidDatasetContentException.errors`. Structure type :

```json
{
  "type": "REACTIVE_ERROR",
  "errorType": "InvalidDatasetContentException",
  "result": {
    "lineNumber": 42,
    "message":    "<code-erreur>",
    "params": {
      "param1": "valeur1",
      "…": "…"
    }
  }
}
```

> **`message`** est une clé i18n calculée par `CheckerTarget.getInternationalizedKey(code)`.
> Elle prend la forme `<prefixe>.<code-camel-case>` selon le type de vérificateur
> (ex. `fr.component.invalidReference` pour un composant de variable, 
> `fr.column.invalidReference` pour une colonne de référentiel).

---

## Codes d'erreur — entêtes / structure du fichier

Ces erreurs sont levées avant le traitement des données. Le numéro de ligne correspond
à la ligne d'en-tête.

| Code                    | Paramètres                                                     | Condition de déclenchement |
|-------------------------|----------------------------------------------------------------|---------------------------|
| `invalidHeaders`        | `expectedColumns`, `mandatoryHeaders`, `actualColumns`         | Les colonnes du CSV ne correspondent pas au schéma. |
| `missingMandatoryColumns` | `missingMandatoryColumns`                                    | Des colonnes obligatoires sont absentes. |
| `duplicatedHeaders`     | `duplicatedHeaders`                                            | Deux en-têtes identiques dans le CSV. |
| `emptyHeader`           | `headerLine`                                                   | Une colonne n'a pas de nom (cellule vide). |

---

## Codes d'erreur — validation ligne par ligne

Ces erreurs sont collectées pour chaque ligne. Le numéro de ligne est celui du CSV source (1-indexé avec l'en-tête).

### Valeur obligatoire manquante

| Code            | Paramètres   | Condition |
|-----------------|--------------|-----------|
| `requiredValue` | `component`  | Le champ est déclaré `required: true` et la valeur est vide ou nulle. |

### Type de données

| Code                 | Paramètres                         | Condition |
|----------------------|------------------------------------|-----------|
| `invalidInteger`     | `target`, `value`                  | La valeur n'est pas un entier valide. |
| `badIntervalInteger` | `target`, `value`, `min`, `max`, `bound` | La valeur entière est en dehors de l'intervalle `[min, max]`. |
| `invalidFloat`       | `target`, `value`                  | La valeur n'est pas un nombre décimal valide. |
| `badIntervalFloat`   | `target`, `value`, `bound`         | La valeur décimale est inférieure au min ou supérieure au max. |
| `invalidDate`        | `target`, `value`, `pattern`       | La valeur ne respecte pas le format de date attendu (composant de variable). |
| `invalidDateWithColumn` | `target`, `value`, `pattern`    | Idem pour une colonne de référentiel. |
| `badIntervalDate`    | `target`, `value`, `min`, `max`    | La date est en dehors de l'intervalle autorisé. |
| `invalidFormat`      | `lineNumber`, `columnNumber`, `value`, `authorizedValues` | Format générique invalide (ex. valeur non incluse dans une liste fixe). |

### Référentiel

| Code                       | Paramètres                                            | Condition |
|----------------------------|-------------------------------------------------------|-----------|
| `invalidReference`         | `target`, `value`, `refType`, `referenceValues`       | La valeur ne correspond à aucune entrée du référentiel (composant de variable). |
| `invalidReferenceWithColumn` | `target`, `value`, `refType`, `referenceValues`     | Idem pour une colonne de référentiel. |

> **Note R-P2-1** : depuis le lot performances, la recherche dans le référentiel utilise
> un index O(1) (`naturalKeyIndex`). Le code d'erreur et ses paramètres sont identiques.
> Un cache par valeur (`precomputedResults`) améliore les performances : après la 2e occurrence
> d'une valeur identique, le résultat est retourné depuis le cache sans recalcul.

### Règle d'expression (Groovy)

| Code                          | Paramètres                        | Condition |
|-------------------------------|-----------------------------------|-----------|
| `checkerExpressionReturnedFalse` | `expression`                   | Une règle de validation Groovy (`GroovyChecker`) a retourné `false`. |

### Expression régulière

| Code                | Paramètres             | Condition |
|---------------------|------------------------|-----------|
| `patternNotMatched` | `target`, `value`, `pattern` | La valeur ne correspond pas à l'expression régulière configurée. |

### Doublons de ligne

| Code                       | Paramètres                                         | Condition |
|----------------------------|----------------------------------------------------|-----------|
| `duplicatedLineInDatatype` | `file`, `lineNumber`, `duplicateKey`, `duplicatedRows` | Deux lignes du fichier de données ont la même clé naturelle. |
| `duplicatedLineInReference` | `file`, `lineNumber`, `duplicateKey`, `otherLines` | Deux lignes du fichier de référentiel ont la même clé naturelle. |

---

## Codes d'erreur — récursivité

Ces erreurs s'appliquent aux référentiels récursifs (ex. arbre hiérarchique).

| Code                              | Paramètres                                               | Condition |
|-----------------------------------|----------------------------------------------------------|-----------|
| `missingParentLineInRecursiveReference` | `reference`, `missingReferencesKey`, `knownReferences` | La valeur identifiant le parent n'existe pas parmi les entrées connues du référentiel. |
| `missingrecursiveParentReference` | `target`, `referenceValues`, `refType`, `values`        | Après traitement des lignes récursives, des parents manquants subsistent. Erreur de synthèse levée en fin de traitement. |

> **`missingrecursiveParentReference`** est produit par `DataImporter.treatErrors()` et non par
> un checker ligne par ligne. Il apparaît une seule fois par import, agrège toutes les valeurs
> manquantes, et est ajouté à `allErrors` avant le lancement de `InvalidDatasetContentException`.

---

## Codes d'erreur — configuration (levés à la validation du YAML)

Ces erreurs n'apparaissent pas lors du dépôt de données mais lors de la validation ou du rechargement de la configuration YAML.

| Code                                       | Déclenchement |
|--------------------------------------------|---------------|
| `illegalGroovyExpressionForValidationRuleInDataType` | Expression Groovy non compilable dans un type de données. |
| `illegalGroovyExpressionForValidationRuleInReference` | Expression Groovy non compilable dans un référentiel. |
| `badGroovyExpressionChecker`               | Expression Groovy invalide détectée par `GroovyExpression.forExpression()`. Params : `expression`, `message`, `lineNumber`, `columnNumber`. |
| `missingParentLineInRecursiveReference`    | Voir section récursivité. |

---

## Format complet d'un rapport d'erreurs

Extrait d'un rapport JSON typique renvoyé par `InvalidDatasetContentException` :

```json
[
  {
    "lineNumber": 15,
    "message": "fr.component.invalidReference",
    "params": {
      "target":          "variables.localisation.component.site",
      "value":           "Site Inconnu",
      "refType":         "tr_site_sit",
      "referenceValues": ["site_experimental_1", "site_experimental_2"]
    }
  },
  {
    "lineNumber": -1,
    "message": "fr.component.missingrecursiveParentReference",
    "params": {
      "target":          "variables.localisation.component.agroecosystem",
      "refType":         "tr_agroecosystem_agr",
      "referenceValues": ["agroecosysteme_1", "agroecosysteme_2"],
      "values":          ["agroecosysteme_inconnu"]
    }
  }
]
```

> `lineNumber = -1` est utilisé par convention pour les erreurs globales (non liées à une ligne
> spécifique du CSV).

---

## Conseils d'intégration frontend

1. **Mapper `message` → clé i18n** : le `message` est déjà la clé i18n. Il suffit d'appeler
   `t(error.message, error.params)` dans votre bibliothèque de traduction.
2. **Numéro de ligne** : afficher `lineNumber + 1` si vous voulez le numéro "humain" (le CSV commence à 1 pour l'en-tête, les données à 2).
3. **`referenceValues` peut être volumineux** : pour `invalidReference`, `referenceValues` contient toutes les valeurs connues. Appliquez une troncature si nécessaire (ex. afficher les 10 premières + "… N autres").
4. **Distinction composant / colonne** : les codes `invalidReference` (variable de type de données) et `invalidReferenceWithColumn` (colonne de référentiel) sont distincts. Les traductions peuvent être différentes.
5. **`missingrecursiveParentReference` est global** : il résume toutes les lignes manquantes en une seule erreur. Le paramètre `values` liste toutes les clés parentes manquantes.

---

## Mapping des codes → source Java

| Code d'erreur                     | Classe source                                             |
|-----------------------------------|-----------------------------------------------------------|
| `invalidHeaders`, `duplicatedHeaders`, `emptyHeader`, `missingMandatoryColumns` | `InvalidDatasetContentException` |
| `requiredValue`                   | `LineChecker`                                             |
| `invalidInteger`, `badIntervalInteger` | `IntegerType`                                        |
| `invalidFloat`, `badIntervalFloat` | `FloatType`                                              |
| `invalidDate`, `invalidDateWithColumn`, `badIntervalDate` | `DateType`             |
| `invalidReference`, `invalidReferenceWithColumn` | `ReferenceType`                  |
| `checkerExpressionReturnedFalse`  | `GroovyChecker`                                           |
| `patternNotMatched`               | `StringType`                                              |
| `duplicatedLineInDatatype`, `duplicatedLineInReference` | `DataImporter`           |
| `missingrecursiveParentReference` | `DataImporter.treatErrors()`                              |