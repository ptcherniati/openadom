# Architecture — Lecture du fichier de configuration et construction de l'objet `Configuration`

## Vue d'ensemble

La configuration d'une application OpenADOM est décrite dans un fichier **YAML**. Ce fichier est
lu côté backend, transformé en un objet Java `Configuration`, puis stocké tel quel en base de
données PostgreSQL sous forme de colonne **JSONB** dans la table `application`.

La chaîne complète est la suivante :

```
Fichier YAML (upload HTTP)
    │
    ▼
ApplicationConfigurationService.parseConfigurationBytes()
    │  • garde-fou fichier vide / BOM
    ▼
ConfigurationBuilder.build()            ← record, point d'entrée statique
    │  • YAMLMapper → JsonNode          (Jackson YAML)
    │  • STRICT_DUPLICATE_DETECTION
    ▼
RootBuilder.build()                     ← construit l'objet métier
    │
    ├─ NodeSchemaValidator.testSchema()  (validation structurelle JSON)
    │
    ├─ applicationdescriptionBuilder     → ApplicationDescription
    ├─ tagsBuilder                       → Set<Tag>          (tags applicatifs)
    ├─ dataBuilder (×n datasets)         → Map<String, StandardDataDescription>
    │    ├─ basicComponentBuilder
    │    ├─ computedComponentBuilder
    │    ├─ constantComponentsBuilder
    │    ├─ dynamicComponentsBuilder
    │    ├─ patternComponentsBuilder
    │    ├─ checkerDescriptionBuilder
    │    └─ validationsBuilder
    ├─ rightsRequestBuilder              → RightRequestDescription
    ├─ additionalFilesBuilder            → Map<String, AdditionalFileDescription>
    │
    ├─ HierarchicalDependancesBuilder    → SortedSet<Node>
    │    └─ ReferenceGraphBuilder        (détection des relations)
    │         └─ Node.buildNode()        (construction de l'arbre)
    │
    └─ new Configuration(...)            ← objet immuable final
         │
         ▼
    Application (agrégat JPA)
         │
         ▼
    PostgreSQL – colonne JSONB (JsonRowMapper)
```

---

## Phase 1 — Décodage YAML → `JsonNode`

**Classe :** `ConfigurationBuilder` (record)

```java
// ConfigurationBuilder.build(InputStream, ReactiveEventHelper, String)
YAMLMapper mapper = YAMLMapper.builder().build();
mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION); // clés dupliquées → erreur
JsonNode rootNode = mapper.readTree(inputStream);
DocumentContext documentContext = JsonPath.parse(mapper.writeValueAsString(rootNode));
```

- Le flux d'entrée vient soit d'une requête HTTP multipart, soit d'un `.zip` dézippé par
  `MultiYaml.parseConfigurationBytes`.
- `STRICT_DUPLICATE_DETECTION` garantit qu'une même clé YAML dupliquée lève une
  `JsonParseException` immédiatement reportée au client.
- En cas d'erreur de parsing, un `ValidationError` est poussé dans `ReactiveEventHelper`
  (channel SSE) et la méthode retourne `null`.

---

## Phase 2 — Validation structurelle du schéma

**Classe :** `NodeSchemaValidator`

Avant tout parsing métier, `RootBuilder.build()` appelle :

```java
new NodeSchemaValidator(this).testSchema(RootType.EMPTY_INSTANCE(), rootNode, "").get();
```

### Principe général

`RootType` est un **type racine** qui décrit récursivement la structure attendue du YAML.
Chaque nœud du schéma est une implémentation de `ConfigurationSchemaNodeType` :

| Type de nœud schéma | Rôle |
|---|---|
| `RootType` | Nœud racine — contient toutes les sections de premier niveau |
| `SectionBuilder` | Décrit les clés attendues/optionnelles d'une section Map |
| `CollectionType.MapType` | Section `Map<String, ...>` (ex : `OA_data`) |
| `CollectionType.ArrayType` | Section `List<...>` (ex : `OA_naturalKey`) |
| `FinalType` | Feuille — valeur scalaire ou enum |
| `CheckerFactory` | Section polymorphe `OA_checker` — le nom détermine le sous-schéma |

### `SectionBuilder` — règles sur les clés YAML

```java
SectionBuilder.getInstance()
    .withMandatorySections(...)      // clés obligatoires → erreur si absentes
    .withOptionalSections(...)       // clés autorisées mais non obligatoires
    .withAnyOfMandatorySections(...) // au moins l'une doit être présente
    .withLocalType()                 // clés = codes Locale (fr, en, ...)
```

Les clés inattendues déclenchent une `SiOreConfigurationFormatException`.

---

## Phase 3 — Construction des sections métier

### 3a. Version (`OA_version`)

Validée en premier dans `RootBuilder.getAndTestOpenAdomVersion()`.  
Seule la version `Configuration.OPEN_ADOM_VERSION` est acceptée.  
Toute non-conformité interrompt le parsing (`return null`).

### 3b. Description applicative (`OA_application`)

`ApplicationdescriptionBuilder` lit :

| Clé YAML | Champ Java | Remarque |
|---|---|---|
| `OA_name` | `ApplicationDescription.name` | — |
| `OA_defaultLanguage` | `defaultLanguage` | Locale Java |
| `OA_comment` | `comment` | — |
| `OA_version` | `version` | Version sémantique |

### 3c. Tags applicatifs (`OA_tags`)

`TagsBuilder.buildDomainTagsOfApplication()` lit la liste de tags de premier niveau.
Ces tags constituent la **liste de référence** : tout `OA_tags` utilisé sur un composant
doit en être un sous-ensemble.

### 3d. Données (`OA_data`)

`DataBuilder.build()` est appelé une fois par entry de `OA_data`. Pour chaque dataset :

1. **Tags** (`OA_tags`) — validés contre les tags applicatifs
2. **Paramètres de fichier** : `OA_dataHeaderLine`, `OA_dataFirstLine`, `OA_separator`,
   `OA_allowUnexpectedColumns`
3. **Clé naturelle** (`OA_naturalKey`) — liste ordonnée de noms de composants
4. **Composants** — chaque section composant est déléguée à un builder spécialisé
   (voir tableau ci-dessous)
5. **Validations** (`OA_validations`) — règles inter-composants
6. **Soumission** (`OA_submission`) — stratégie de dépôt
7. **Autorisations** (`OA_authorizations`)
8. **Migrations** (`OA_migrations`)
9. **i18n** (`OA_i18n`, `OA_exportHeader`) — alimentent l'objet `Internationalizations`

#### Builders de composants

| Section YAML | Builder | Type Java produit |
|---|---|---|
| `OA_basicComponents` | `BasicComponentBuilder` | `BasicComponent` |
| `OA_computedComponents` | `ComputedComponentBuilder` | `ComputedComponent` |
| `OA_constantComponents` | `ConstantComponentsBuilder` | `ConstantComponent` |
| `OA_dynamicComponents` | `DynamicComponentsBuilder` | `DynamicComponent` |
| `OA_patternComponents` | `PatternComponentsBuilder` | `PatternComponent` |

Chaque composant peut porter :
- un **checker** (section `OA_checker`) parsé par `CheckerDescriptionBuilder`
- des **tags** (`OA_tags`)
- un en-tête d'export (`OA_exportHeader`)
- une contrainte de présence (`OA_mandatory`)

### 3e. Checkers (`OA_checker`)

`CheckerDescriptionBuilder.build()` lit `OA_name` pour identifier le type via `CheckerEnum`,
puis parses les paramètres dans `OA_params` :

| `OA_name` | Classe Java | Paramètres YAML notables |
|---|---|---|
| `OA_string` | `StringChecker` | `OA_pattern` |
| `OA_integer` | `IntegerChecker` | `OA_min`, `OA_max` |
| `OA_float` | `FloatChecker` | `OA_min`, `OA_max` |
| `OA_date` | `DateChecker` | `OA_pattern`, `OA_min`, `OA_max`, `OA_duration` |
| `OA_boolean` | `BooleanChecker` | `OA_isTrue` |
| `OA_reference` | `ReferenceChecker` | `OA_reference`, `OA_isParent`, `OA_isRecursive` |
| `OA_groovyExpression` | `GroovyExpressionChecker` | `OA_expression`, `OA_references`, `OA_groovyExceptions` |

---

## Phase 4 — Construction de l'arbre hiérarchique

### 4a. Collecte des `ReferenceChecker`

Pendant le parsing des composants (phase 3d), `CheckerDescriptionBuilder` stocke chaque
`ReferenceChecker` dans `RootBuilder.checkers` :

```
checkers[CheckerDescriptionType.ReferenceChecker][dataName][componentKey] = [ReferenceChecker...]
```

### 4b. `HierarchicalDependancesBuilder.of()`

Transforme la map brute des checkers en :
- `checkerdescriptions` — map `dataName → List<ReferenceChecker>`
- `orders` — map `dataName → Integer` (ordre d'affichage IHM)

### 4c. `ReferenceGraphBuilder.detectRelations()`

Pour chaque `ReferenceChecker` détecte le type de relation :

| Condition | `RelationType` | Effet |
|---|---|---|
| `dataName == refType` ou `isRecursive` | `RECURSIVE` | Le nœud se référence lui-même |
| `isParent && !isRecursive` | `PARENT_CHILD` | `refType` devient parent de `dataName` |
| Sinon | `DEPENDS` | `dataName` dépend de `refType` |

### 4d. `Node.buildNode()`

Construit le `SortedSet<Node>` (arbre trié) depuis les `BuilderNode` :

1. **Calcul des dépendances transitives** — `BuilderNode.withAllDepends()` remonte la
   chaîne de dépendances et calcule le `level` (profondeur).
2. **Identification des feuilles** — `BuilderNode.getNodeLeaves()` : nœuds ayant un
   parent OU sans dépendances, sans enfants.
3. **Boucle de reconstruction** — `buildNodesRecursively()` :
   - regroupe les nœuds non-racines par leur parent
   - reconstruit chaque parent avec ses enfants (immuabilité — nouveaux records)
   - l'ancien parent est remplacé dans `rootNodes` par `removeIf` + `add`
   - répète jusqu'à épuisement des non-racines

### 4e. Tri dans `TreeSet<Node>` — `Node.compareTo()`

Ordre de tri strict (ordre total garanti) :

1. `deepLevel` décroissant (nœuds les plus profonds d'abord dans leur sous-arbre)
2. `level` croissant
3. Dépendance directe (le dépendant vient après sa dépendance)
4. `order` croissant (tag `__order__:N`)
5. `nodeName` alphabétique (stabilité)

---

## Phase 5 — Assemblage et stockage

### 5a. `RootBuilder.build()` — retour du `Configuration`

```java
return new Configuration(
    version,
    tags.result(),           // Set<Tag> — tags applicatifs
    internationalizations,   // Internationalizations — i18n agrégée
    applicationDescription,  // ApplicationDescription
    data.result(),           // Map<String, StandardDataDescription>
    rightRequest.result(),   // RightRequestDescription
    additionalFiles.result(),// Map<String, AdditionalFileDescription>
    hierarchicalNodes,       // SortedSet<Node>
    requiredAuthorizationsAttributes
);
```

Si des erreurs ont été collectées (`RootBuilder.hasErrors()`), `null` est retourné et
les erreurs ont été poussées dans `ReactiveEventHelper` (SSE côté client).

### 5b. Stockage PostgreSQL

`ApplicationConfigurationService.getConfigurationParsingResultForSyntacticallyValidYaml()`
crée ou met à jour l'agrégat `Application` dont le champ `configuration` est sérialisé
en **colonne JSONB** par `JsonRowMapper` (ObjectMapper Jackson).

La désérialisation à la lecture utilise les annotations **Jackson** présentes sur
les records/classes du package `domain.application.configuration` (@JsonProperty,
sous-types polymorphes via `@JsonSubTypes` sur `ComponentDescription` et `CheckerDescription`).

---

## Recette — Comment ajouter un élément au système

### A. Ajouter une nouvelle section YAML (clé de 1er ou 2e niveau)

**Exemple :** Ajouter `OA_exportConfig` au niveau d'un dataset.

1. **Déclarer la constante de clé** dans `ConfigurationSchemaNode` :
   ```java
   public static final String OA_EXPORT_CONFIG = "OA_exportConfig";
   ```

2. **Déclarer le schéma YAML** dans le type parent. Pour une section dataset,
   modifier le `SectionBuilder` retourné par `DataType.EMPTY_INSTANCE()` :
   ```java
   .withOptionalSections(new LabelDescription(OA_EXPORT_CONFIG, ExportConfigType.EMPTY_INSTANCE()))
   ```
   Créer `ExportConfigType` en implémentant `ConfigurationSchemaNodeType`.

3. **Créer le record Java cible**, ex. `ExportConfig` dans le package `domain.application.configuration`.

4. **Créer le builder** `ExportConfigBuilder(RootBuilder rootBuilder)` dans le package
   `rest.model.configuration.builder`. Il lit le `JsonNode` et retourne un `Parsing<ExportConfig>`.

5. **Câbler dans `DataBuilder.build()`** :
   ```java
   Parsing<ExportConfig> exportConfig = exportConfigBuilder.build(path, jsonNode.get(OA_EXPORT_CONFIG), i18n);
   ```

6. **Ajouter le champ** dans `StandardDataDescription` (record) et mettre à jour
   le constructeur et les tests builders.

7. **Ajouter un test unitaire** `@Tag("core.config")` dans le package de test,
   sans Docker.

---

### B. Ajouter un tag métier (tag System — pattern `__MON_TAG__`)

Les **tags système réservés** sont des marqueurs fonctionnels posés sur les composants.
Exemples existants : `__DATA__`, `__REFERENCE__`, `__HIDDEN__`, `__FILTER_TEXT__`,
`__FILTER_LIST__`, `__ORDER__:N`.

**Exemple :** Ajouter un tag `__EXPORT_CSV__`.

1. **Déclarer la constante et le prédicat** dans `Tag.java` :
   ```java
   record ExportCsvTag(TagDefinitions tagDefinition) implements DefinedTag {
       public static final String EXPORT_CSV_PATTERN = "__EXPORT_CSV__";
       public static final Predicate<String> EXPORT_CSV_TAG = EXPORT_CSV_PATTERN::equals;

       public ExportCsvTag() { this(TagDefinitions.EXPORT_CSV_TAG); }
       public static ExportCsvTag instance() { return new ExportCsvTag(); }
       public static String getTagPattern() { return EXPORT_CSV_PATTERN; }
   }
   ```

2. **Enregistrer dans `TagDefinitions`** (enum interne de `Tag`) :
   ```java
   EXPORT_CSV_TAG(ExportCsvTag.EXPORT_CSV_TAG, w -> ExportCsvTag.instance(), ExportCsvTag.getTagPattern()),
   ```
   > ⚠ L'ordre dans l'enum compte : `DOMAIN_TAG` doit rester **dernier** car son prédicat
   > est le moins restrictif et servirait de catch-all.

3. **Déclarer le type dans la hiérarchie scellée** (`sealed interface`) :
   ```java
   sealed interface DefinedTag extends Tag permits DataTag, FilterTag, HiddenTag, NoTag, OrderTag, ReferenceTag, ExportCsvTag {}
   ```

4. **Ajouter la logique métier** dans `ComponentDescription` si le tag doit exposer
   une méthode calculée (ex. `isExportedToCsv()`).

5. **Tests unitaires** dans `TagTest` et `ComponentDescriptionTest`.

---

### C. Ajouter un tag métier (tag domaine — pattern `[a-z][a-z_0-9]*[a-z0-9]`)

Les **tags domaine** (`DomainTag`) sont définis **dans le YAML** sous `OA_tags` au niveau
racine de l'application (ex. `data`, `reference_nationale`). Ils ne nécessitent aucune
modification du code Java : le système les lit, les valide contre le pattern
`^[a-z][a-z_0-9]*[a-z0-9]$`, et les stocke dans `Configuration.tags`.

Pour en déclarer un, il suffit d'ajouter dans le YAML :

```yaml
OA_tags:
  - mon_tag_domaine
```

Puis de le référencer sur un composant :

```yaml
OA_tags:
  - mon_tag_domaine
```

> Un tag domaine utilisé sur un composant mais non déclaré à la racine de l'application
> déclenche l'erreur `NOT_EXPECTED_DOMAIN_TAGS`.

---

### D. Ajouter un nouveau checker

**Exemple :** Ajouter un checker `OA_email`.

1. **Déclarer l'entrée de l'enum** dans `CheckerEnum` :
   ```java
   OA_email("OA_email"),
   ```

2. **Créer le record** `EmailChecker` dans `domain.application.configuration.checker` :
   ```java
   public record EmailChecker(
       CheckerDescriptionType type,
       Multiplicity multiplicity,
       boolean required,
       String domainRestriction    // ex: "@inra.fr"
   ) implements CheckerDescription {
       @Override public String comment() { return "Email"; }
       @Override public String buildImportDataExempleForheader() { return "an email address"; }
   }
   ```

3. **Créer le type de schéma YAML** `EmailCheckerType` dans
   `domain.application.configuration.type` en implémentant `CheckerType` :
   ```java
   public record EmailCheckerType(Map<String, ConfigurationSchemaNodeType<?>> children)
       implements CheckerType {
       public static EmailCheckerType EMPTY_INSTANCE() { ... }
       @Override public CheckerEnum getChecker() { return CheckerEnum.OA_email; }
   }
   ```

4. **Enregistrer dans `CheckerFactory.getCheckerTypeForName()`** :
   ```java
   case "OA_email" -> EmailCheckerType.EMPTY_INSTANCE();
   ```

5. **Parser dans `CheckerDescriptionBuilder.build()`** — ajouter le cas dans le
   switch sur `CheckerEnum` :
   ```java
   case OA_email -> {
       String domainRestriction = Optional.ofNullable(paramsNode.get(OA_DOMAIN_RESTRICTION))
           .map(JsonNode::asText).orElse(null);
       yield new EmailChecker(
           CheckerDescriptionType.EmailChecker,
           multiplicity, required, domainRestriction);
   }
   ```

6. **Déclarer le sous-type Jackson** sur `CheckerDescription` :
   ```java
   @JsonSubTypes.Type(value = EmailChecker.class, name = "EmailChecker")
   ```

7. **Tests unitaires** dans `EmailCheckerTest` (`@Tag("core.config")`) et dans
   `CheckerDescriptionTest` pour le builder, `CheckerFactoryTest` pour l'enregistrement.

8. **Implémenter la logique de validation runtime** dans le checker applicatif
   correspondant dans `domain.checker` (si le checker doit aussi valider les données
   à l'import, pas seulement la syntaxe de configuration).

---

## Invariants à respecter lors de toute modification

| Invariant | Test garant |
|---|---|
| `Node.compareTo` est un ordre total strict | `NodeComparatorTest` |
| `Node.buildNode` est idempotent, immuable | `NodeBuildTest` |
| Hiérarchie 3 niveaux sans doublon de racine | `NodeBuildTest.buildNode_threeLevel_rootAppearsExactlyOnce` |
| Cycle détecté → `BadApplicationConfigurationException` | `BuilderNodeTest` + `HierarchicalDependancesBuilderTest` |
| Tri IHM garanti (dépendances avant dépendants) | `NodeOrderingTest` |
| `RootBuilder` sans flag mutable | `RootBuilderErrorTest` |
| Tag système non reconnu → erreur de parsing | `TagTest` |
| Checker inconnu → `SiOreConfigurationFormatException` | `CheckerFactoryTest` |
| Parsing YAML complet sans régression | `ApplicationConfigurationServiceTest` (docker-required) |

---

## Commandes utiles

```bash
# Tests unitaires purs (sans Docker)
mvn test -pl . -Dgroups="core.config" --no-transfer-progress

# Tests d'intégration complets
mvn test -pl . -Dgroups="docker-required" --no-transfer-progress

# Test ciblé (ex: nouveau checker)
mvn test -pl . -Dtest="CheckerFactoryTest,CheckerDescriptionTest" --no-transfer-progress

# Rapport de couverture
mvn jacoco:report --no-transfer-progress
open target/site/jacoco/index.html
```