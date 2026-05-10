# Indépendance de la couche `domain` — Stratégie Ports & Adapters

> **Statut** : ✅ **OBJECTIF ATTEINT** — 2026-05-10  
> **Auteur** : Philippe Tcherniatinsky

---

## ✅ Résultat obtenu (2026-05-10)

| Critère | Résultat |
|---------|----------|
| `import org.springframework.*` dans domain | **0** |
| `import reactor.*` dans domain | **0** |
| `import fr.inra.oresing.persistence.*` dans domain | **0** |
| `import fr.inra.oresing.rest.*` dans domain | **0** |
| Fichiers alias `@Deprecated(forRemoval=true)` | **0** (tous supprimés) |
| Tests `@Tag("domain.model")` sans Spring/Docker | **1 350+ ✅** |

**Validation** :
```bash
grep -r "import fr.inra.oresing.rest\|import fr.inra.oresing.persistence\|import org.springframework\|import reactor" \
  src/main/java/fr/inra/oresing/domain --include="*.java" | wc -l
# → 0
```

---

## 1. Objectif

Rendre le package `fr.inra.oresing.domain` **totalement indépendant** des
couches techniques (`rest`, `persistence`, Spring) afin de :

- tester la logique métier **sans Spring, sans base de données** (JUnit pur)
- pouvoir remplacer l'implémentation technique sans toucher au domaine
- clarifier les responsabilités entre les couches

**Règle absolue après refactoring** :

> `domain` ne doit importer
> ni `fr.inra.oresing.rest.*`
> ni `fr.inra.oresing.persistence.*`
> ni `org.springframework.*` (sauf `@Nullable` si jugé utile)

---

## 2. Architecture cible

```
┌──────────────────────────────────────────────────────┐
│  REST  (fr.inra.oresing.rest)                        │
│  controllers · DTOs · sécurité Spring                │
│  dépend de → domain, services                        │
└──────────────────┬───────────────────────────────────┘
                   │ appelle
┌──────────────────▼───────────────────────────────────┐
│  SERVICES  (fr.inra.oresing.rest.services)            │
│  use cases · orchestration · beans Spring             │
│  dépend de → domain, persistence                     │
└──────────────────┬───────────────────────────────────┘
                   │ implémente les ports
┌──────────────────▼───────────────────────────────────┐
│  DOMAIN  (fr.inra.oresing.domain)                    │
│  entités · value objects · règles métier              │
│  interfaces de PORT (ce que le domaine EXIGE)        │
│  ✦ zéro dépendance extérieure                        │
└──────────────────────────────────────────────────────┘
                   ▲ implémente les ports
┌──────────────────┴───────────────────────────────────┐
│  PERSISTENCE  (fr.inra.oresing.persistence)           │
│  JDBC · PostgreSQL · Spring Data                     │
│  dépend de → domain uniquement                       │
└──────────────────────────────────────────────────────┘
```

---

## 3. Stratégie choisie : Ports & Adapters

### Deux approches possibles

| | Approche 1 — Déplacer le code | Approche 2 — Définir des ports ✦ **retenue** |
|--|-------------------------------|----------------------------------------------|
| Principe | Sortir du domaine les classes qui ont des dépendances illicites | Le domaine exprime ses besoins via des **interfaces** ; les autres couches les implémentent |
| Avantage | Simple | Le domaine reste riche · testable sans Spring |
| Inconvénient | La logique métier quitte le domaine | Nécessite de créer les interfaces |
| ISP | Non | Oui — les interfaces peuvent être **plus petites** que leur implémentation |

### Principe de découpe des interfaces (ISP)

Une seule implémentation (`DataRepository`) peut satisfaire plusieurs ports
domaine fins :

```java
// domaine — exprime ce dont il a besoin (petit, précis)
package fr.inra.oresing.domain.port;

public interface ReferenceReadPort {
    Map<String, String> findNaturalKeys(UUID applicationId, String referenceType);
}

public interface DataWritePort {
    void saveRows(UUID applicationId, List<DataRow> rows);
}

// persistence — implémentation unique, plusieurs ports
@Repository
public class DataRepository implements ReferenceReadPort, DataWritePort, ... {
    // logique JDBC
}
```

Les classes domaine reçoivent les ports **par constructeur** — jamais via
`ServiceContainer` ou `@Autowired` sur champ.

---

## 4. Inventaire des violations et actions

### 4-A — `ExceptionMessage` dans `rest.exceptions` ← déplacement simple

`ExceptionMessage` est une enum de codes d'erreur **métier** mal placée dans
`rest.exceptions`.

**Fichiers domaine contaminés** :

| Fichier | Import illicite |
|---------|-----------------|
| `domain/data/DataDatum.java` | `rest.exceptions.ExceptionMessage` |
| `domain/data/DataColumnMultipleValue.java` | `rest.exceptions.ExceptionMessage` |
| `domain/data/deposit/recursion/WithoutRecursion.java` | `rest.exceptions.ExceptionMessage` |
| `domain/data/deposit/recursion/WithRecursion.java` | `rest.exceptions.ExceptionMessage` |
| `domain/data/deposit/context/DataImporterContext.java` | `rest.exceptions.ExceptionMessage` |
| `domain/application/configuration/Ltree.java` | `rest.exceptions.ExceptionMessage` |

**Action** : déplacer `ExceptionMessage` → `domain.exceptions.DomainErrorCode`.
`rest.exceptions` peut garder un import/alias si la sérialisation JSON le requiert.

---

### 4-B — Types `persistence.*` utilisés dans le domaine

#### B1 — Value objects égarés dans `persistence`

Ces types sont des concepts **métier** qui doivent être dans le domaine :

| Type | Package actuel | Package cible |
|------|---------------|---------------|
| `RefsLinked` | `persistence` | `domain.data` |
| `OperationAdditionalFileType` | `persistence` | `domain.additionalfiles` |
| `AuthenticationFailure` | `persistence` | `domain.exceptions` |

Mettre à jour les imports dans les fichiers domaine et persistence concernés.

#### B2 — `DataRepository` (persistence) utilisé dans le domaine

**Fichiers contaminés** :
`DataImporterContext`, `ComponentOrderBy`, `ComponentPatternOrderBy`,
`ComponentPatternValueOrderBy`, `ComponentOrderByForExport`,
`DynamicComponentOrderBy`

**Action** : créer des interfaces de port dans `domain.port` :

```java
// domain/port/ReferenceReadPort.java
public interface ReferenceReadPort {
    Map<String, String> findNaturalKeys(UUID applicationId, String referenceType);
    Optional<UUID> findIdByNaturalKey(UUID applicationId, String referenceType, String naturalKey);
}

// domain/port/DataQueryPort.java
public interface DataQueryPort {
    List<Map<String, Object>> query(DataQueryCriteria criteria);
    Stream<Map<String, Object>> stream(DataQueryCriteria criteria);
}
```

`persistence.DataRepository` implémente ces interfaces.
Les classes domaine reçoivent les ports par constructeur.

#### B3 — `JsonRowMapper` : un adaptateur DTO ↔ métier à responsabilités multiples

`JsonRowMapper` n'est **pas** un objet domaine, mais il n'est pas non plus
purement une classe de persistence. Il remplit trois rôles distincts :

| Rôle | Responsabilité | Couche |
|------|---------------|--------|
| 1 — lecteur JDBC | `implements RowMapper<T>` — lit le JSONB de PostgreSQL | **persistence** |
| 2 — adaptateur DTO ↔ métier | Sérialiseurs/désérialiseurs Jackson pour `Ltree`, `DataDatum`, `Tag`, `FieldType`, `AuthorizationForScope`… | **application** (anti-corruption layer) |
| 3 — logique applicative | `getBinaryFileDatasetJsonSerializer` accède à `OreSiApiRequestContext` et `ServiceContainer` pour convertir les dates selon la configuration de l'application | **service** dans le mauvais endroit |

Ce mélange explique pourquoi il est utilisé aussi bien dans `persistence` que
dans `services` — il sert de **pont JSON entre toutes les couches**.

**L'interface `domain.Mapper` est la bonne abstraction** : elle existe déjà
dans le domaine, et `JsonRowMapper` l'implémente déjà. Le domaine doit
référencer `Mapper`, jamais l'implémentation concrète.

```java
// Avant (violation — le domaine connaît l'implémentation concrète)
import fr.inra.oresing.persistence.JsonRowMapper;
public class AsynchroneFileImporterContext {
    private final JsonRowMapper<?> mapper;
}

// Après — le domaine déclare le port qu'il exige
import fr.inra.oresing.domain.Mapper;
public class AsynchroneFileImporterContext {
    private final Mapper mapper;  // injecté par constructeur (JsonRowMapper côté services)
}
```

**Refactoring à terme de `JsonRowMapper` lui-même** : ses trois rôles méritent
d'être séparés en deux ou trois classes distinctes —

- `persistence.JsonRowMapper` réduit au seul rôle JDBC (`RowMapper<T>`)
- `rest.services.DomainObjectMapper` (ou `application.JsonDomainAdapter`) pour
  la conversion DTO ↔ métier, implémentant `domain.Mapper`
- La logique de `getBinaryFileDatasetJsonSerializer` (dates contextuelles)
  déplacée dans le service applicatif concerné

Ce découpage est **hors scope de la phase 5** — à planifier séparément car
l'impact est transverse à toutes les couches.

---

### 4-C — Beans Spring dans le domaine ← déplacement vers `services`

Ces classes sont des **services applicatifs Spring**, pas des objets domaine.

| Fichier | Annotation abusive | Package cible |
|---------|-------------------|---------------|
| `domain/application/configuration/migration/execution/MigrationExecutor.java` | `@Component` | `rest.services` |
| `domain/application/configuration/migration/MigrationProperties.java` | `@Configuration`, `@ConfigurationProperties` | `rest.config` |

**Cas particulier — `MigrationContext` dépend de `ServiceContainer`** :

```java
// Avant (violation grave — le domaine connaît le conteneur Spring)
public class MigrationContext {
    private final ServiceContainer serviceContainer;
}

// Après — injection des ports par constructeur
public class MigrationContext {
    private final ReferenceReadPort referencePort;
    private final DataWritePort     dataPort;
    private final ConfigReadPort    configPort;

    public MigrationContext(ReferenceReadPort referencePort,
                            DataWritePort dataPort,
                            ConfigReadPort configPort) { ... }
}
```

---

### 4-D — Classes REST dans le domaine

#### D1 — `ReactiveResult` / `ReactiveTypeProgress` dans le domaine

`RegisterReactiveResult` et `BundleReport` utilisent des types réactifs REST.

**Action** : définir une interface d'événement dans le domaine :

```java
// domain/event/ImportProgressEvent.java
public interface ImportProgressEvent {
    double getProgress();
    boolean isError();
    String getMessage();
    boolean isComplete();
}
```

`rest.reactive.ReactiveResult` implémente `ImportProgressEvent`.
Le domaine émet des `ImportProgressEvent`, sans connaître `ReactiveResult`.

#### D2 — `BuildColumns` et `DownloadDatasetQuery`

| Fichier domaine | Import illicite | Action |
|-----------------|-----------------|--------|
| `DataHeaderReader`, `AsynchroneFileImporterContext` | `rest.data.BuildColumns` | déplacer `BuildColumns` → `domain.data.deposit` |
| `DownloadDatasetQuery` | `rest.OreSiResources` | extraire la constante dans le domaine, supprimer l'import REST |

#### D3 — `AuthorizationService` dépend de `rest.model.authorization.AuthorizationsResult`

Évaluer la nature de `AuthorizationsResult` :
- Si valeur **métier** → déplacer dans `domain.authorization`
- Si **DTO REST** → créer un type domaine `AuthorizationSummary`, mapper côté REST

#### D4 — `SectionBuilder` dépend de `NodeSchemaValidator`

Évaluer si `NodeSchemaValidator` est une règle métier :
- Oui → déplacer dans `domain.application.configuration`
- Non → extraire l'interface `SchemaValidator` dans le domaine

#### D5 — `FileSenderInternationalisationForBuildBundleReport`

Importe `rest.filesenderclient.BuildBundleReport`.

**Action** : déplacer `BuildBundleReport` → `domain.filesenderclient`
(le sous-package existe déjà dans le domaine).

---

### 4-E — Spring Security dans les rôles domaine

`NotConnectedUser` et `NotConnectedUnauthentifiedUserForCreate` implémentent
`UserDetails` / `GrantedAuthority` de Spring Security.

**Action** : définir des interfaces domaine :

```java
// domain/authorization/AuthenticatedUser.java
public interface AuthenticatedUser {
    String getUsername();
    boolean isActive();
    Set<String> getRoles();
}
```

Les adaptateurs Spring Security (`OreSiUserDetails`…) dans `rest.security`
implémentent à la fois les interfaces domaine et `UserDetails`.
Le domaine n'importe jamais `spring.security.*`.

---

### 4-F — `domain.repository` : repositories dans le domaine

`domain.repository.*` contient des implémentations JDBC
(`@Transactional`, `MultiValueMap`…) — ce sont des classes `persistence` égarées.

**Action** :
1. Créer les interfaces de port dans `domain.port.*`
2. Déplacer les implémentations vers `persistence.*`
3. Supprimer `domain.repository.*`

L'interface marqueur vide `domain/repository/repository.java` peut être
supprimée — les ports suffisent.

---

## 5. Plan d'action

Les phases sont ordonnées du plus simple (risque faible, valeur immédiate)
au plus structurant.

| Phase | Catégorie | Actions | Effort | Risque |
|-------|-----------|---------|--------|--------|
| **1** | A + B1 | Déplacer `ExceptionMessage` → `domain.exceptions` · `RefsLinked`, `OperationAdditionalFileType`, `AuthenticationFailure` → `domain.*` | faible | faible |
| **2** | C | Déplacer `MigrationExecutor`, `MigrationProperties` hors du domaine · supprimer `ServiceContainer` dans `MigrationContext` par injection de ports | moyen | moyen |
| **3** | D2 + D5 | Déplacer `BuildColumns` → `domain` · nettoyer `DownloadDatasetQuery` · déplacer `BuildBundleReport` → `domain.filesenderclient` | faible | faible |
| **4** | D1 | Interface `domain.event.ImportProgressEvent` · faire implémenter par `ReactiveResult` | moyen | faible |
| **5** | B2 + B3 + F | Créer les ports `domain.port.*` · faire implémenter par `DataRepository` · **remplacer `JsonRowMapper` par `domain.Mapper`** dans les classes domaine (quick win — `domain.Mapper` existe déjà) · déplacer `domain.repository.*` → `persistence` | fort | moyen |
| **5b** | B3 avancé | Séparer `JsonRowMapper` en trois classes : lecteur JDBC, adaptateur DTO↔métier, logique service | fort | fort |
| **6** | D3 + D4 | Clarifier `AuthorizationsResult` et `NodeSchemaValidator` | moyen | moyen |
| **7** | E | Interfaces `AuthenticatedUser` · adaptateurs Spring Security dans `rest.security` | fort | moyen |

### Critère de validation par phase

Après chaque phase, la commande suivante doit retourner **zéro résultat**
pour les fichiers traités :

```bash
grep -r "import fr.inra.oresing.rest\|import fr.inra.oresing.persistence\|import org.springframework" \
  src/main/java/fr/inra/oresing/domain \
  --include="*.java" -l
```

---

## 6. Résultat attendu

Après les 7 phases :

- `domain` ne dépend plus que de `java.*` et bibliothèques pures
  (`guava`, `commons-lang3`, `slf4j-api`)
- Tous les tests `@Tag("domain.model")` s'exécutent **sans Spring, sans Docker,
  sans base de données** en quelques secondes
- Chaque port peut être **mocké indépendamment** dans les tests unitaires
- L'implémentation JDBC peut être remplacée sans modifier une ligne du domaine