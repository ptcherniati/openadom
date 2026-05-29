# Vérification de la migration de configuration — Bilan, analyse et règles d'acceptation

> **Statut** : 🟡 Étude en cours — bilan de l'amorce + analyse des cas + approfondissement ancré dans le code (§7)
> **Périmètre** : projet `openadom`, branche `develop`
> **Module concerné** : `fr.inra.oresing.domain.application.configuration.migration` (+ `rest.services.MigrationService`)

---

## 1. Contexte et objectif

OpenADOM génère un Système d'Information (SI) à partir d'un **fichier de configuration YAML**
décrivant tout un domaine (types de données, composants, checkers, autorisations, soumissions,
i18n…). Au **premier dépôt**, un schéma PostgreSQL dédié est créé pour l'application. Par la suite,
l'utilisateur peut **mettre à jour** le SI en redéposant une nouvelle version du fichier.

Le problème : une mise à jour peut rendre **incohérentes** les données déjà insérées, voire
empêcher l'application de fonctionner. On met donc en place un **vérificateur de migration** qui,
*partant d'un fichier déjà validé syntaxiquement et structurellement*, **explore les changements**
entre l'ancienne et la nouvelle configuration et **décide** de la conduite à tenir.

Les conduites possibles, selon la nature du changement et l'existence (ou non) de données :

1. **Interdire** la migration (changement destructeur incompatible avec les données).
2. **Autoriser** la migration sans contrainte (changement sûr).
3. **Demander à l'utilisateur d'agir sur la base avant** (purge, correction de données).
4. **Réaliser des modifications dans la base** (avant / pendant / après la migration).
5. **Émettre un avertissement** (données correctes mais fichier non redéposable tel quel).
6. **Demander des précisions** à l'utilisateur (valeur par défaut, confirmation, mapping…).

Ce document fait d'abord le **bilan** de ce qui a été amorcé (tâche 1), puis **analyse** comment
chaque section du fichier agit sur la base et la cohérence des données (tâche 2), enfin propose les
**modifications acceptables et leurs conditions** avec justification (tâche 3).

---

## 2. Bilan de la mise en place existante

### 2.1 Vue d'ensemble du pipeline

```
Dépôt d'une nouvelle configuration (mise à jour)
        │
        ▼
ApplicationService.createOrUpdateApplication()
        │  (cas mise à jour : oldApplication != null)
        ▼
MigrationService.executeMigration(old, new, acceptedWarnings, mode)
        │
        ├─ 1. Diff des datatypes   (CollectionUtils.subtract sur getData())
        │        → DataAdded pour chaque datatype ajouté
        │
        ├─ 2. detectChanges(old, new)        ← Javers.compare(...) → MapChange
        │        → toConfigurationChange(...) → liste de ConfigurationChange
        │            (DataAdded / IgnorableChange / UnresolvableChange / …)
        │
        ├─ 3. Pour chaque change : evaluateChange()
        │        → rulesEngine.fire(migrationRules, facts)   (jeasy-rules)
        │        → chaque règle alimente le MigrationPlan (pre/core/post actions, warnings, statut)
        │
        ├─ 4. Décision selon le statut du plan + mode bypass
        │        → MigrationResult.onError / blocked / noChanges
        │
        └─ 5. MigrationExecutor.execute(plan, context)
                 → exécute les actions PRE → CORE → POST (si mode EXECUTE)
                 → MigrationResult (EXECUTED / FAILED / APPROVED dry-run)
```

Le **moteur de diff** est [Javers](https://javers.org/) ; le **moteur de règles** est
[jeasy-rules](https://github.com/j-easy/easy-rules). Tous deux sont câblés dans
`rest/data/migration/MigrationConfiguration.java` (beans `javers`, `ruleEngine`, `rules`,
`executor`, `migrationRepositories`).

### 2.2 Modèle de changements — `ConfigurationChange`

Une hiérarchie de **types scellés (`sealed interface`) + records** modélise les changements
détectables. C'est la pièce maîtresse de la conception : elle force une **taxonomie exhaustive**
et un traitement *pattern-matché* de chaque cas.

```
ConfigurationChange (sealed)
├── DataChange (sealed)
│    ├── DataAdded(dataName, dataDescription)          ← ajout d'un datatype
│    ├── DataRemoved(dataName, dataDescription)        ← suppression d'un datatype
│    └── ComponentChange (sealed)
│         ├── ComponentAdded()                         ← ajout d'un composant
│         ├── ComponenRemoved()                        ← suppression d'un composant
│         └── ComponentChanged (sealed)
│              ├── NaturalKeyChanged()                 ← clé naturelle modifiée
│              ├── AuthorizationChanged()              ← scope d'autorisation modifié
│              ├── HierarchieChanged()                 ← hiérarchie / référence modifiée
│              ├── SubmissionChanged()                 ← stratégie de soumission modifiée
│              ├── I18nDisplayPattenChanged()          ← motif d'affichage i18n
│              ├── I18nImportHeaderChange()            ← en-tête d'import i18n
│              └── CheckerChange (sealed)
│                   ├── CheckerAdded()
│                   ├── CheckerRemoved()
│                   └── CheckerModified (sealed)
│                        ├── CheckerTypeChanged()      ← type de checker changé
│                        └── CheckerDefinitionChanged()← paramètres du checker changés
├── I18nChange (sealed)
│    ├── I18nSimpleChange()                            ← libellé i18n sans DDL
│    ├── I18nDisplayPattenChanged()
│    └── I18nImportHeaderChange()
├── IgnorableChange()                                  ← changement automatiquement géré, sans effet
└── UnresolvableChange(propertyPath, changeType, left, right)  ← changement non encore classé
```

> **Constat** : la **taxonomie est riche et largement complète** (elle couvre déjà tous les grands
> axes : datatype, composant, clé naturelle, autorisation, hiérarchie, soumission, checker, i18n).
> En revanche, **seuls `DataAdded`, `IgnorableChange` et `UnresolvableChange` sont effectivement
> produits** par `MigrationService.toConfigurationChange()` aujourd'hui ; les autres records sont
> déclarés mais pas encore *détectés* par le diff.

### 2.3 Plan, statut, mode, avertissements

- **`MigrationPlan`** (record mutable) : porte trois listes d'actions ordonnancées
  (`preActions`, `coreActions`, `postActions`), une liste de `warnings`, un `status`
  (`AtomicReference`) et un `Set<String> actionIds` qui **déduplique** les actions par identifiant.
- **`MigrationStatus`** : `PENDING`, `APPROVED`, `REQUIRES_CONFIRMATION`, `FORBIDDEN`,
  `EXECUTED`, `FAILED`, `NO_CHANGES`. C'est le vocabulaire des 6 conduites du §1.
- **`MigrationMode`** : `DRY_RUN` (simulation : aucune action exécutée) ou `EXECUTE`.
- **`MigrationWarning`** : `id`, `message`, `impact`, `severity` (`INFO`/`WARNING`/`CRITICAL`),
  `blocking`. Un warning **bloquant non acquitté** bascule le plan en `REQUIRES_CONFIRMATION`.
  Les avertissements **acquittés** (`acceptedWarnings`) ne bloquent plus → c'est le mécanisme de
  *« l'utilisateur accepte des modifications automatiques »*.

> **Constat / point d'attention** : `MigrationPlan.setStatus(...)` est aujourd'hui **un no-op**
> (le statut n'est réellement modifié que par `addWarning()`). `DataAddedRule` appelle pourtant
> `plan.setStatus(APPROVED)` : cet appel n'a donc pas d'effet. La transition `PENDING → APPROVED`
> n'est pas matérialisée. À solidifier lors de l'industrialisation.

### 2.4 Actions de migration — `MigrationAction`

`sealed interface MigrationAction` avec 4 implémentations, chacune typée par sa **phase** :

| Action | Phase | Rôle | Confirmation |
|---|---|---|---|
| `SaveConfigurationAction` | CORE | Persiste la nouvelle configuration (`storeApplication`) ; *singleton* | non |
| `AddAuthorizationScopeAttributeAction` | POST | `ALTER TYPE … ADD ATTRIBUTE` sur le type composite `requiredAuthorizations` | non |
| `CreateIndexesForDataAction` | POST | Reconstruit les index d'autorisation (`updateAuthorizationIndexes`) | non |
| `UpdateAuthorizationScopeAction` | CORE | Réservé (non implémenté) | non |

L'ordonnancement **PRE → CORE → POST** est le levier qui permettra de réaliser des modifications
*avant / pendant / après* la migration (conduite n°4).

### 2.5 Contexte et ports (architecture hexagonale)

Le domaine reste **pur** (cf. `INDEPENDENCE_DOMAINE.md`) : aucune dépendance Spring/persistence.

- **`MigrationContext`** : `applicationName`, `oldApplication`, `newApplication`, `schemaInfo`,
  `dataInfo`, et deux ports : `MigrationApplicationPort` (persistance) + `AuthenticationPort`.
- **`MigrationApplicationPort`** : `storeApplication`, `addReferenceToAuthorizationScope`,
  `updateAuthorizationIndexes`. Implémenté *inline* dans `MigrationService.buildContext()`.
- **`SchemaInfo`** : présence d'index/policies **par referenceType**.
- **`DataInfo`** : **nombre de lignes par referenceType** (`rowCountByReferenceType`) et présence
  de **valeurs nulles par composant** (`hasNullValuesByComponent`).

> **Constat clé** : `DataInfo` et `SchemaInfo` fournissent **exactement l'information nécessaire**
> pour décider *« le changement est-il possible selon qu'il existe ou non des données ? »* — c'est
> la brique sur laquelle reposera la majorité des règles conditionnelles (cf. §4). Aujourd'hui ces
> structures sont peuplées mais **peu exploitées** par les règles existantes.

### 2.6 Intégration dans le flux de dépôt

Dans `ApplicationService` :

- **Mise à jour réelle** : `executeMigration(old, new, Set.of(), EXECUTE)` (le rapport de migration
  est poussé dans l'event-stream via le message `MIGRATION_REPORT`).
- **Simulation** : un appel en `DRY_RUN` existe (ligne ~538) → socle pour un futur **rapport de
  migration restitué à l'utilisateur avant resoumission**.

### 2.7 Mode `bypass` (compatibilité ascendante)

`MigrationProperties` (`openadom.migration.bypass-configuration-check`, **défaut `true`**) :

- `true` (**permissif, historique**) : tout changement est accepté ; les `UnresolvableChange` et
  les confirmations requises sont **seulement journalisés**, jamais bloquants.
- `false` (**sécurisé**) : un `UnresolvableChange` → `MigrationResult.onError` (échec) ; un statut
  `REQUIRES_CONFIRMATION` → `MigrationResult.blocked`.

> **Constat** : le vérificateur est donc **désactivé par défaut** en production — choix prudent qui
> garantit la non-régression pendant que la couverture des règles se complète.

### 2.8 Couverture de tests

Tests **unitaires purs** (sans Spring/BDD, `@Tag("core.config")`/`@Tag("domain.model")`) :
`MigrationModuleTest`, `MigrationRulesTest`, `DataAddedRuleTest`, `MigrationPlanTest`,
`MigrationActionTest(s)`, `MigrationExecutorTest`, `MigrationChangeRecords(Extra)Test`,
`ComparingConfiguration`. Tests d'intégration : `MigrationTest`, `MigrationServiceTest`,
`MigrationServiceBypassTest`.

### 2.9 Synthèse du bilan — acquis et manques

**Acquis**
- Taxonomie de changements **exhaustive et fermée** (sealed) → base solide et évolutive.
- Pipeline complet **diff (Javers) → règles (jeasy) → plan → exécuteur** opérationnel de bout en bout.
- Ordonnancement **PRE/CORE/POST** et notion de **warning acquittable** → couvrent conceptuellement
  les 6 conduites du §1.
- Domaine **découplé** (ports) et **testable** sans infrastructure.
- Contexte riche (`DataInfo`/`SchemaInfo`) permettant des **décisions conditionnées aux données**.
- Mode **bypass** pour un déploiement progressif sans régression.

**Manques / dette à résorber (à traiter dans les tâches suivantes)**
1. **Détection partielle** : `toConfigurationChange()` ne produit que `DataAdded`/`Ignorable`/
   `Unresolvable`. La majorité des records (`DataRemoved`, `Component*`, `NaturalKeyChanged`,
   `Checker*`, `Authorization/Hierarchie/SubmissionChanged`) **ne sont pas encore mappés** depuis
   le diff Javers.
2. **Règles non enregistrées** : les classes `DataAddedRule` et `IgnorableRule` **existent bien**
   (`domain/application/configuration/migration/rules/`) et sont couvertes par des tests unitaires,
   mais le bean `rules()` retourne `new Rules()` **vide** (`MigrationConfiguration.java:40-42`) :
   elles ne sont donc *jamais ajoutées* au moteur en production (testées uniquement en isolation).
   En l'état, `rulesEngine.fire(...)` n'ajoute aucune action : le datatype ajouté est traité par le
   **pré-traitement inline** de `executeMigration`, pas par la règle.
3. **`setStatus` no-op** : la transition explicite vers `APPROVED`/`FORBIDDEN` n'est pas matérialisée.
4. **Conditionnement aux données peu exploité** : `DataInfo`/`SchemaInfo` sont disponibles mais les
   règles ne s'en servent pas encore pour distinguer *« avec données »* / *« sans données »*.
5. **Pas de boucle de resoumission** : le `DRY_RUN` produit un rapport, mais l'API de resoumission
   utilisateur (accepter des warnings, fournir des valeurs par défaut) n'est pas encore exposée.

Ces manques **n'invalident pas la conception** : ils délimitent le travail des tâches 2 et 3.

---

## 3. Comment le fichier de configuration agit sur la base et la cohérence des données

Comprendre l'impact d'un changement suppose de savoir **ce que chaque section du fichier matérialise**
réellement en base. Point essentiel : OpenADOM **n'a pas une table par datatype**. Toutes les
données vivent dans **une table générique `referenceValue`** par schéma applicatif, avec des colonnes
**JSONB** et **ltree**.

### 3.1 Du dépôt au schéma

- Au **premier dépôt**, un **schéma PostgreSQL dédié** (= nom de l'application) est créé via Flyway
  (`MigrateService`, `SchemaFlywayCallback`, `migration/application/V*.sql`).
- La configuration elle-même est **stockée en JSONB** dans la table `application` (cf.
  `ARCHITECTURE_LECTURE_CONFIGURATION.md`).
- Les **données** sont écrites dans `referenceValue` : `referencetype` (= nom du datatype),
  `refValues` (jsonb des composants), `naturalKey` (ltree), `hierarchicalKey` (ltree),
  `refsLinkedTo` (jsonb des liens), `authorization` (type composite), `binaryFile` (FK).

### 3.2 Tableau d'impact — section de config → structure en base

| Section du fichier | Ce qui est matérialisé en base | Couplage aux **données existantes** |
|---|---|---|
| `OA_data.<datatype>` (ajout) | Nouvelles lignes possibles `referencetype=<datatype>` ; index GIN `refvalues` ; attribut ajouté au type composite `requiredAuthorizations` | **Faible** : aucune donnée existante pour ce type |
| `OA_data.<datatype>` (suppression) | Les lignes `referencetype=<datatype>` deviennent **orphelines** ; liens `refsLinkedTo` cassés | **Fort** si des lignes existent |
| `OA_basicComponents.<comp>` | Clé dans le JSONB `refValues` (pas de colonne dédiée) | Variable : ajout ⇒ clé absente sur l'existant ; suppression ⇒ donnée stockée mais non lue |
| `OA_naturalKey: [...]` | Valeur **ltree** `naturalKey` + contrainte d'**unicité** `(referenceType, hierarchicalKey, patternColumnName)` + index | **Fort** : recalcul de clé + risque de **collisions d'unicité** sur l'existant |
| `OA_checker` (type/paramètres) | Aucune DDL : contrôle **applicatif** à l'import. Mais conditionne la **validité** des valeurs déjà stockées | **Fort** : les valeurs existantes peuvent ne plus respecter le checker |
| `OA_authorizations.authorizationScope` | Attribut `ltree[]` du type composite `requiredAuthorizations` + index GIN ; politiques RLS | **Fort** : les lignes existantes ont une valeur d'autorisation calculée selon l'ancien scope |
| `OA_authorizations.timeScope` | Champ `tsrange` de l'autorisation + index GiST | **Fort** : recalcul du timescope des lignes existantes |
| `OA_submission` (stratégie/scope) | Pas de DDL directe, mais pilote l'écriture (versioning vs insertion) et le parsing des fichiers | **Moyen/Fort** : change la sémantique des dépôts ultérieurs |
| `OA_hierarchicalNodes` / références (`OA_reference`, `isParent`, `isRecursive`) | `hierarchicalKey` (ltree), table `Reference_Reference`, `refsLinkedTo` (jsonb) | **Fort** : recalcul de hiérarchie et d'intégrité référentielle |
| `OA_i18n`, `i18nDisplayPattern`, `exportHeader`, libellés | **Aucune DDL** ; éventuellement clés d'affichage `__display_*` recalculées à l'import | **Faible** : libellés, pas de contrainte d'intégrité |
| `OA_tags`, `OA_application.comment`, `OA_defaultLanguage` | Métadonnées en JSONB de `application` | **Nul** : aucun effet sur `referenceValue` |

### 3.3 Lecture transversale

Trois familles se dégagent, qui structureront les décisions du §4 :

1. **Sans impact sur l'intégrité des données** (libellés/i18n, tags, commentaires, en-têtes
   d'affichage) → migration **toujours autorisable**.
2. **Impact applicatif différé** (checkers, soumission) : la DDL ne bouge pas, mais la
   **cohérence sémantique** des données déjà présentes peut être rompue → **avertissement** voire
   **confirmation/correction** selon présence de données.
3. **Impact structurel** (datatype, clé naturelle, autorisation, hiérarchie, références) : touche
   des **contraintes**, **index** ou **types composites** → **conditionné à l'absence de données**
   ou à une **action de réparation** (avant/pendant/après).

---

## 4. Modifications acceptables et conditions — analyse cas par cas

Pour chaque type de `ConfigurationChange`, on indique : l'**impact**, la **conduite proposée**
(parmi les 6 du §1), la **condition** (souvent : *existe-t-il des données ?* via
`DataInfo.rowCountByReferenceType`), et la **justification**. La colonne *Statut cible* reprend le
vocabulaire de `MigrationStatus`/`MigrationWarning`.

### 4.1 Changements de datatype (`DataChange`)

| Cas | Données existantes ? | Conduite | Statut cible | Justification |
|---|---|---|---|---|
| **`DataAdded`** | sans objet (type neuf) | **Autoriser** + actions auto : `SaveConfiguration` (CORE), `AddAuthorizationScopeAttribute` + `CreateIndexesForData` (POST) | `APPROVED` | Aucun datum existant ne peut être incohérent ; il faut seulement créer les structures dérivées (type composite, index). **Déjà implémenté.** |
| **`DataRemoved`** | **non** (0 ligne) | **Autoriser** (éventuel POST de nettoyage des structures) | `APPROVED` | Rien à perdre, aucune donnée orpheline. |
| **`DataRemoved`** | **oui** | **Interdire** *ou* **demander une action préalable** (purge/export) | `FORBIDDEN` / `REQUIRES_CONFIRMATION` | Supprimer un type avec données crée des lignes orphelines et casse `refsLinkedTo`. On exige une purge explicite (conduite n°3) ou une confirmation de perte (conduite n°6). |

### 4.2 Changements de composant (`ComponentChange`)

| Cas | Données existantes ? | Conduite | Statut cible | Justification |
|---|---|---|---|---|
| **`ComponentAdded`** (non requis) | quelconque | **Autoriser** | `APPROVED` | Nouvelle clé JSONB absente de l'existant = `null` implicite, sans rupture. |
| **`ComponentAdded`** (requis, `OA_required`) | **oui** | **Demander une valeur par défaut** *ou* **avertir** | `REQUIRES_CONFIRMATION` | Les lignes existantes n'ont pas la valeur ⇒ violation de l'obligation. Conduite n°6 : demander un défaut (upsert) ou accepter un warning. |
| **`ComponentAdded`** (requis) | **non** | **Autoriser** | `APPROVED` | Aucune ligne à compléter. |
| **`ComponenRemoved`** | **non** | **Autoriser** | `APPROVED` | Aucune donnée concernée. |
| **`ComponenRemoved`** | **oui** | **Avertir** (non bloquant) | `WARNING` | La clé reste dans `refValues` mais n'est plus lue : pas de rupture d'intégrité, mais donnée « morte » ⇒ warning (conduite n°5). Bloquant seulement si le composant est utilisé par une clé/échelle. |

### 4.3 Clé naturelle (`NaturalKeyChanged`)

| Cas | Données existantes ? | Conduite | Statut cible | Justification |
|---|---|---|---|---|
| `NaturalKeyChanged` | **non** | **Autoriser** | `APPROVED` | La clé est recalculée au prochain import, aucune ligne à reclé. |
| `NaturalKeyChanged` | **oui**, nouvelle clé **garantie unique** | **Réaliser une action** de recalcul (CORE/POST) | `APPROVED` (auto) ou `REQUIRES_CONFIRMATION` | Le `naturalKey` (ltree) et la contrainte d'unicité doivent être recalculés. Faisable automatiquement si l'unicité est préservée. |
| `NaturalKeyChanged` | **oui**, **collisions possibles** | **Interdire / corriger d'abord** | `FORBIDDEN` / `REQUIRES_CONFIRMATION` | Une nouvelle clé non injective viole `hierarchicalKey_uniqueness`. On demande une correction préalable (conduite n°3) ou un re-dépôt des fichiers sources. |

### 4.4 Autorisations (`AuthorizationChanged`)

| Cas | Données existantes ? | Conduite | Statut cible | Justification |
|---|---|---|---|---|
| Ajout d'un scope d'autorisation | quelconque | **Réaliser** : `AddAuthorizationScopeAttribute` (POST) + reconstruction d'index | `APPROVED` (auto) | `ALTER TYPE … ADD ATTRIBUTE` est non destructeur ; les lignes prennent `null` pour le nouveau scope (à recalculer). |
| Suppression / changement de scope | **oui** | **Avertir / confirmer** + recalcul des autorisations | `REQUIRES_CONFIRMATION` | Les valeurs `authorization` des lignes existantes ont été calculées selon l'ancien scope ⇒ risque de **fuite ou perte d'accès**. Confirmation + recalcul requis. |
| Changement de `timeScope` | **oui** | **Confirmer** + recalcul `tsrange` | `REQUIRES_CONFIRMATION` | Modifie la fenêtre temporelle d'accès des lignes existantes. |

### 4.5 Checkers (`CheckerChange`)

| Cas | Données existantes ? | Conduite | Statut cible | Justification |
|---|---|---|---|---|
| **`CheckerAdded`** sur composant existant | **oui** | **Avertir / vérifier** | `WARNING`/`REQUIRES_CONFIRMATION` | Les valeurs déjà stockées n'ont jamais été contrôlées ⇒ peuvent être invalides au regard du nouveau checker. |
| **`CheckerRemoved`** | quelconque | **Autoriser** | `APPROVED` | Relâcher une contrainte ne peut pas rendre l'existant invalide. |
| **`CheckerTypeChanged`** | **oui** | **Interdire / confirmer** | `FORBIDDEN`/`REQUIRES_CONFIRMATION` | Changer p.ex. `string → integer` peut rendre les valeurs existantes inconvertibles (et casser les requêtes/affichages). |
| **`CheckerTypeChanged`** | **non** | **Autoriser** | `APPROVED` | Aucune valeur à reconvertir. |
| **`CheckerDefinitionChanged`** (resserrement : min/max, pattern plus strict) | **oui** | **Avertir / vérifier** | `WARNING`/`REQUIRES_CONFIRMATION` | Des valeurs jadis valides peuvent sortir des nouvelles bornes. Vérification ou acceptation explicite. |
| **`CheckerDefinitionChanged`** (élargissement) | quelconque | **Autoriser** | `APPROVED` | Élargir le domaine de validité n'invalide rien. |

> *Sous-distinction utile à industrialiser* : « contrainte resserrée » vs « contrainte élargie ».
> Seul le resserrement nécessite une vérification des données (via `DataInfo`).

### 4.6 Hiérarchie / références (`HierarchieChanged`)

| Cas | Données existantes ? | Conduite | Statut cible | Justification |
|---|---|---|---|---|
| Changement de hiérarchie/référence | **non** | **Autoriser** | `APPROVED` | `hierarchicalKey`/`refsLinkedTo` recalculés au prochain import. |
| Changement de hiérarchie/référence | **oui** | **Confirmer** + recalcul, ou **re-dépôt** des fichiers | `REQUIRES_CONFIRMATION` | Modifie `hierarchicalKey` (ltree) et l'intégrité référentielle (`Reference_Reference`). Risque de liens cassés ⇒ recalcul contrôlé. |

### 4.7 Soumission (`SubmissionChanged`)

| Cas | Conduite | Statut cible | Justification |
|---|---|---|---|
| Changement de stratégie (`OA_VERSIONING` ↔ `OA_INSERTION`) ou de scope/fileName | **Avertir** | `WARNING` | Pas de DDL ni d'incohérence sur l'existant, mais les **fichiers déjà déposés peuvent ne plus être redéposables** tels quels (conduite n°5). |

### 4.8 Internationalisation (`I18nChange`) et métadonnées

| Cas | Conduite | Statut cible | Justification |
|---|---|---|---|
| `I18nSimpleChange`, `I18nDisplayPattenChanged`, `I18nImportHeaderChange`, titres/descriptions, tags, commentaire, `defaultLanguage` | **Autoriser** | `APPROVED` / `IgnorableChange` | Purement libellés/affichage : **aucune contrainte d'intégrité**. `I18nImportHeaderChange` ne touche que le mapping d'import des **prochains** dépôts. **Déjà géré** comme `IgnorableChange` pour la branche `data` de `Internationalizations`. |

### 4.9 Changements non classés (`UnresolvableChange`)

| Cas | Conduite (mode sécurisé) | Conduite (bypass) | Justification |
|---|---|---|---|
| `UnresolvableChange` | **Interdire** (`MigrationResult.onError`) | **Avertir** (log) puis continuer | Tant qu'un changement n'est pas explicitement classé dans la taxonomie, on **ne peut pas garantir** la cohérence ⇒ refus prudent par défaut. Au fil de l'industrialisation, ces cas migrent vers des records dédiés. |

### 4.10 Interactions entre changements (non-atomicité)

Le problème souligne que des changements peuvent **interagir**. Le modèle actuel les traite
**indépendamment** (une règle par change). Cas à industrialiser :

- **Renommage** = `…Removed` + `…Added` corrélés (datatype ou composant). À reconnaître comme un
  couple pour proposer un **upsert/rename** plutôt qu'une suppression + recréation destructrice.
- **Ajout de datatype + référence depuis un type existant** : l'ordre PRE/CORE/POST doit garantir
  que la cible existe avant la création du lien.
- **Changement de clé naturelle + de composant la composant** : à évaluer **ensemble** pour le
  calcul d'unicité.

La déduplication d'actions par `actionIds` et l'ordonnancement par phase sont les **leviers** déjà
présents pour orchestrer ces dépendances ; il manque une étape de **corrélation des changements**
en amont des règles.

---

## 5. Justification des choix de conception

1. **Types scellés + records pour la taxonomie** : garantissent l'**exhaustivité** (le compilateur
   force le traitement de chaque cas dans un `switch` pattern-matché) et l'**immuabilité**, ce qui
   colle à la nature « décrire puis décider » du problème. Choix retenu plutôt qu'une hiérarchie de
   classes ouverte, plus risquée pour la complétude.
2. **Moteur de règles (jeasy) + diff (Javers)** : sépare la **détection** (mécanique, générique) de
   la **décision** (métier, déclarative). On peut ajouter une règle par cas sans toucher au moteur,
   et conditionner la décision au contexte (`DataInfo`/`SchemaInfo`).
3. **Phases PRE/CORE/POST** : matérialisent directement la conduite n°4 (« réaliser des changements
   avant/pendant/après ») et permettent d'ordonner des actions interdépendantes.
4. **Warnings acquittables + `acceptedWarnings`** : couvrent les conduites n°5 (avertir) et n°6
   (demander précisions/confirmation) et préparent la **boucle de resoumission** utilisateur.
5. **`DRY_RUN` vs `EXECUTE`** : permet de produire un **rapport de migration** sans rien modifier,
   préalable indispensable à une décision utilisateur éclairée.
6. **Conditionnement aux données via `DataInfo`** : la quasi-totalité des décisions du §4 bascule
   selon *« existe-t-il des données pour ce type ? »* — d'où le choix d'exposer `rowCountByReferenceType`
   et `hasNullValuesByComponent` dès la construction du contexte.
7. **Mode `bypass` par défaut** : déploiement **progressif** sans régression tant que la couverture
   des règles est incomplète ; bascule en mode sécurisé une fois les règles complétées.
8. **Domaine découplé (ports)** : conforme à `INDEPENDENCE_DOMAINE.md`, rend la logique de décision
   **testable sans base ni Spring**.

---

## 6. Prochaines étapes recommandées

1. **Compléter `toConfigurationChange()`** pour mapper les `MapChange`/`ValueChange` Javers vers les
   records existants (`DataRemoved`, `Component*`, `NaturalKeyChanged`, `Checker*`,
   `Authorization/Hierarchie/SubmissionChanged`).
2. **Enregistrer les règles** dans le bean `rules()` et en **ajouter une par cas** du §4, en
   consommant `DataInfo`/`SchemaInfo` pour les décisions conditionnelles.
3. **Rendre `MigrationStatus` effectif** (corriger le no-op de `setStatus`, gérer `FORBIDDEN`).
4. **Corréler les changements** (détection de renommages, dépendances) avant l'évaluation des règles.
5. **Exposer la boucle de resoumission** : restituer le rapport `DRY_RUN`, permettre l'acquittement
   de warnings et la fourniture de valeurs par défaut, puis rejouer en `EXECUTE`.
6. **Distinguer resserrement/élargissement** des checkers pour n'imposer une vérification que quand
   c'est nécessaire.
7. **Basculer le défaut `bypass` à `false`** une fois la couverture jugée suffisante.

---

## 7. Approfondissement de l'analyse — ancrage dans le code réel

Cette section pousse plus loin les 8 axes du cahier des charges, en **s'appuyant sur le code
existant** (et non plus seulement sur la conception). Chaque constat est référencé par fichier et
ligne pour être vérifiable et actionnable.

### 7.1 Bypass piloté par une simple propriété (portée globale)

**État réel.** C'est déjà le cas : `MigrationProperties.bypassConfigurationCheck` (préfixe
`openadom.migration`, défaut `true`) est un **booléen unique** lu une seule fois dans
`executeMigration()` (`final boolean bypass = migrationProperties.isBypassConfigurationCheck();`,
`MigrationService.java:192`). Il s'applique donc à **toute l'instance** OpenADOM, tous
tenants/applications confondus.

**Conséquences à acter.**
- Interrupteur « tout ou rien » de déploiement : il sécurise *ou* déverrouille **l'ensemble du SI**
  d'un coup. Aucun grain fin possible.
- Surcharge runtime : aujourd'hui c'est `@ConfigurationProperties` **non** `@RefreshScope`
  (`MigrationProperties.java`) → un changement nécessite un redéploiement. Pour le basculer à chaud
  (utile pendant une fenêtre de portage), il faut le rendre rafraîchissable ou l'exposer via un
  endpoint admin.
- **Recommandation** : conserver ce flag comme **filet de sécurité global (kill-switch)**, en
  complément (et non en remplacement) d'un réglage plus fin (§7.2).

### 7.2 Bypass réglé au niveau applicatif — risque de contagion inter-applications

**Faisabilité.** Le pipeline est déjà **par application** : `buildContext(applicationName, …)` cible
un schéma PostgreSQL dédié via `getRepository(applicationName)` (`MigrationService.java:263-299`).
Déplacer le réglage dans la config (`ApplicationDescription` / métadonnées JSONB de `application`)
est cohérent avec l'isolation par schéma.

**La vraie question : une incohérence des données d'une application peut-elle bloquer les autres ?**
Analyse des points de partage :

- **Transaction / schéma** : chaque migration agit sur le schéma de l'app. Une erreur bloquante
  (`FORBIDDEN`, exception dans `MigrationExecutor`) doit rester **confinée** à la transaction de
  cette app. ⇒ *Pas de contagion* **si** le rollback est strictement scoping-app et qu'aucune action
  ne touche `public` ou des structures partagées.
- **Beans partagés** : `Javers`, `RulesEngine`, `Rules migrationRules`, `MigrationExecutor`,
  `JsonRowMapper` sont des **singletons Spring** (constructeur `MigrationService.java:57-70`). Ils
  doivent rester **sans état mutable par app**. *Point de vigilance réel* : `executeMigration()`
  **mute** `oldApplication.getConfiguration().dataDescription()` et `.i18n()` en place
  (`MigrationService.java:208-209`). Si l'objet `Application` provenait d'un cache partagé, cette
  mutation pourrait fuiter vers d'autres requêtes — à auditer / cloner défensivement (cf. dette E).
- **Event-stream `MIGRATION_REPORT`** : canal commun ; un volume d'erreurs d'une app ne doit pas
  saturer/bloquer le flux des autres.
- **Type composite `requiredAuthorizations`, index, RLS** : `AddAuthorizationScopeAttributeAction`
  fait `ALTER TYPE … ADD ATTRIBUTE`. Vérifier que ce type est **par schéma applicatif** et non
  global, sinon un `ALTER` d'une app prend un **lock DDL** qui peut gêner les autres.

**Garde-fous à mettre en place.**
1. Réglage par app **autorisé**, mais une incohérence d'une app **ne doit jamais** rendre le SI
   global indisponible : encapsuler chaque migration dans sa propre transaction + try/catch,
   journaliser, et ne jamais propager l'échec au boot applicatif global.
2. **Précédence** : `global bypass=false` (kill-switch sécurisé) doit pouvoir **forcer** la
   vérification même si une app demande `bypass=true`. Règle de combinaison explicite recommandée :
   **ET logique** — vérification active si *au moins un* des deux niveaux l'exige.
3. **Cloner la config** avant la mutation in-place (`MigrationService.java:208-209`) pour éliminer
   tout risque de fuite inter-requêtes.

### 7.3 Couverture dépendant vs indépendant — un changement autorisé peut-il masquer un interdit ? **(critique)**

**Réponse franche, preuves dans le code : OUI, aujourd'hui c'est possible.** Trois failles
concrètes :

1. **Seuls les `MapChange` sont collectés.** `detectChanges()` fait
   `javersCompare.getChangesByType(MapChange.class)` (`MigrationService.java:303`). Les `ValueChange`
   (ex. paramètre de checker, stratégie de soumission, booléen `required`), `ReferenceChange`,
   `ListChange` (**la clé naturelle est une `List` → sa modification est un `ListChange`**) et
   `SetChange` sont **purement ignorés**. ⇒ Une modification de clé naturelle ou d'un paramètre de
   checker peut passer **totalement inaperçue**.
2. **`DataRemoved` n'est jamais produit.** `removedData = CollectionUtils.subtract(oldData, newData)`
   est calculé (`MigrationService.java:204`) puis **jamais utilisé**. La suppression d'un datatype
   est donc **silencieuse**.
3. **Masquage actif par mutation.** Pour chaque datatype ajouté, on **injecte** la description dans
   `oldConfig` *avant* le diff (`MigrationService.java:208`). C'est nécessaire pour ne pas
   re-signaler l'ajout, mais cela illustre que le diff travaille sur une base modifiée — fragile si
   d'autres corrélations en dépendent.

**Le scénario du cahier des charges est donc réel** : un dépôt qui *ajoute un datatype* (autorisé,
`DataAdded`) **et** *supprime un composant / change une clé* (interdit) passera : l'ajout est traité,
la suppression/clé est invisible (pas un `MapChange` mappé) → `changes` ne contient que le
`DataAdded` → migration acceptée.

**Principe de correction (exhaustivité garantie).**
- **Catch-all sur TOUS les types Javers** : itérer `javersCompare.getChanges()` (pas seulement
  `MapChange`) et garantir que **tout** atome de diff non explicitement classé retombe en
  `UnresolvableChange`. C'est l'**invariant de sûreté** : *aucun changement ne doit pouvoir
  « disparaître »*.
- **Test de non-régression dédié** : pour un couple `(old, new)`, *nombre d'atomes Javers ==
  nombre de `ConfigurationChange` produits* (somme), afin qu'aucun atome ne soit avalé.
- Mapper enfin `DataRemoved` (utiliser `removedData`), `NaturalKeyChanged` (`ListChange` sur
  `naturalKey`), `Checker*`, `Component*`.
- En mode sécurisé, **un seul** `UnresolvableChange`/`FORBIDDEN` dans le lot doit bloquer **tout** le
  dépôt (**atomicité**) — sinon un changement sûr « porte » un changement risqué.

### 7.4 Cas complexes : groovy affichés/masqués, transformations, dépendances directes/indirectes

Ces cas vivent dans des **value objects imbriqués** ; le diff Javers les voit, mais leur
**sémantique** n'est pas modélisée.

- **Groovy « affichés ou masqués »** : composants calculés (`computedComponents`), validations
  (`OA_checker` de type expression), `i18nDisplayPattern`. Un script qui devient *visible* (nouvelle
  colonne calculée) vs *masqué* (retiré de l'affichage) n'a pas le même impact : retrait d'affichage
  ≈ libellé (sûr) ; ajout/modif d'un calcul **change les valeurs dérivées stockées** → relève du
  recalcul (§7.5).
- **Transformations** : recalcul potentiellement long → indisponibilité (§7.7). Une transformation
  *modifiée* doit être traitée comme « les données dérivées sont périmées » ⇒ action de recalcul,
  pas simple `SaveConfiguration`.
- **Dépendances indirectes** : un datatype A référence B (`refsLinkedTo`, `hierarchicalKey` en
  ltree, table `Reference_Reference`). Modifier B (clé, suppression, hiérarchie) **invalide A** alors
  qu'aucun changement n'apparaît sur A. Le modèle actuel évalue **chaque change indépendamment**
  (une règle par change ; `FactKeys.buildFacts` ne porte qu'**un** change) → **aucune propagation**.

**Recommandations.**
1. Introduire une **étape de corrélation / graphe de dépendances** *avant* le moteur de règles
   (déjà identifiée au §4.10) : construire le DAG des datatypes (références, hiérarchies) et
   **propager l'invalidation** d'un nœud modifié vers ses dépendants.
2. Distinguer pour les checkers/groovy **resserrement vs élargissement** (cf. §4.5) : seul le
   resserrement exige une vérification des données.
3. Faire porter au moteur non pas un change isolé mais le **lot corrélé** (renommage =
   `Removed` + `Added` ; clé + composant ensemble) pour éviter des décisions localement correctes
   mais globalement fausses.

### 7.5 Précalcul des `__display_*` qui change : recalcul (y compris dépendants) ou interdit ?

**État réel.** Les affichages sont **précalculés et stockés** dans `refvalues`
(`DataColumn.DISPLAY = "__display_"`, calculés par `InternationalizationDisplay` à l'import depuis
`i18nDisplayPattern`). Ils sont **recopiés** dans les lignes dépendantes via `refsLinkedTo` /
`RefsLinked.__display_*` (cf. `SelectRequest`, `ComponentOrderByForExport`). Un changement de pattern
rend donc **périmés** les displays de la donnée **et de tous ses référents**.

**Ce n'est pas un changement interdit — c'est un recalcul** (conduite n°4, phase POST). Deux options :
1. **Recalcul en base (recommandé)** : `UPDATE referencevalue SET refvalues = jsonb_set(…)` pour
   recomposer `__display_*` selon le nouveau pattern, puis **propager aux dépendants** (mise à jour
   des `__display_*` recopiés dans `refsLinkedTo`). Ordre POST, en parcourant le graphe du §7.4.
   Précédent technique : `SchemaBuilder` sait déjà composer le display en SQL
   (`NULLIF(refvalues ->> '__display_fr', ''), refvalues ->> '__display_default'`).
2. **Re-import** : marquer la donnée « display périmé » et exiger un redépôt — plus simple mais
   coûteux côté utilisateur.

**Garde-fou** : un pattern référençant un composant **supprimé** ⇒ recalcul impossible ⇒
`FORBIDDEN` / `REQUIRES_CONFIRMATION`. Le recalcul doit être **transactionnel** et idéalement
déclenché sous l'état de maintenance (§7.7) car il réécrit des lignes.

### 7.6 Vider un type / des autorisations / nettoyer ; backup ciblé → suppression → restauration ; vérifier la cohérence

**Les primitives existent déjà** dans la couche workflow/cascade et peuvent être réutilisées comme
`MigrationAction` :

- **Backup → delete → restore** : le mode `PublishMode.CACHED_ROTATION` (`PublishProperties.java`)
  fait exactement cela — snapshot SQL de `referencevalue` vers `binaryfile.processed_data` **avant**
  le delete, puis `COPY processed_data → referencevalue` pour restaurer. C'est le patron idéal pour
  un **backup ciblé** par `referencetype` avant une transformation risquée.
- **Vider un type** : `DELETE FROM referencevalue WHERE referencetype = ?` (action PRE/CORE dédiée,
  précédée du snapshot).
- **Nettoyer un champ obsolète** (`ComponenRemoved`) : `UPDATE … SET refvalues = refvalues - 'comp'`
  (POST, non destructeur de ligne).
- **Vider certaines autorisations** : recalcul/reset du type composite `requiredAuthorizations` +
  reconstruction d'index (`CreateIndexesForDataAction` existe déjà).

**Vérifier la cohérence directement.** C'est précisément le rôle de `DataInfo`/`SchemaInfo` — **mais
ils retournent `null`** (`OreSiRepository.getSchemaInfo()`/`getDataInfo()`, lignes 77-83). Tant
qu'ils ne sont pas câblés, **aucune règle ne peut décider selon les données** (et toute règle qui les
lirait ferait un NPE). Étapes :
1. Implémenter réellement `getDataInfo` (`COUNT(*) GROUP BY referencetype`, présence de NULL par
   composant) et `getSchemaInfo` (présence index/policies).
2. Ajouter une **passe de validation read-only** rejouant les checkers sur les valeurs existantes
   (réutiliser le moteur de checkers d'import) pour confirmer/infirmer une alerte *avant* d'autoriser
   une migration (utile pour `CheckerDefinitionChanged` resserré).

**Proposition d'API** : nouvelles `MigrationAction` typées par phase — `BackupReferenceTypeAction`
(PRE), `PurgeReferenceTypeAction` (CORE), `RestoreReferenceTypeAction` (POST/compensation),
`CleanObsoleteComponentAction` (POST), `VerifyDataConsistencyAction` (PRE, read-only). **Réutiliser**
le code SQL de `CACHED_ROTATION` plutôt que réinventer.

### 7.7 État applicatif de maintenance pendant les transformations longues

**Constat.** Aujourd'hui la mise à jour est rapide (recalcul du JSON applicatif + `storeApplication`).
Mais les transformations des §7.5 / §7.6 (recalcul displays, purge/restore, recalcul de
clés/hiérarchies) peuvent rendre les données **temporairement indisponibles ou incohérentes**. Il
faut un **état de maintenance par application**.

**Proposition.**
1. **Drapeau de maintenance** par app : champ dans les métadonnées JSONB de `application`
   (ex. `maintenanceState: NONE | MAINTENANCE`), positionné en **PRE**, levé en **POST** (avec
   compensation si échec).
2. **Filtrage des accès** : un intercepteur/filtre de sécurité qui, en état `MAINTENANCE`, **bloque
   les utilisateurs non-admin** (lecture/écriture) et **restreint l'admin aux seules actions de
   portage** (migration, backup/restore, validation). Réutiliser le `AuthenticationPort` déjà présent
   dans `MigrationContext`.
3. **Cohérence avec l'existant** : la fenêtre « unpublished » de `CACHED_ROTATION` est déjà un état
   où la donnée est absente — aligner la sémantique (un utilisateur ne doit pas voir des données à
   moitié recalculées).
4. **Granularité** : maintenance **par application** (cohérent avec l'isolation par schéma),
   idéalement par datatype si la transformation est ciblée, pour minimiser l'indisponibilité.
5. **Idempotence/reprise** : si le process meurt en cours, l'état de maintenance + le snapshot (§7.6)
   permettent une **reprise ou un rollback** propre.

### 7.8 Stocker un hash de la configuration pour éviter le travail redondant

**Précédent existant à réutiliser.** Le `configHash` est **déjà** utilisé dans la cascade
(`PublishProperties`, `OreSiWorkflowType`, `ConfigFieldRegistry`) pour décider LITE/FULL et armer un
FAST path. Le même principe s'applique à la migration.

**Idée clé : séparer ce qui dépend de la config seule de ce qui dépend des données.**
- Le **plan d'actions préalables** (diff + corrélation + règles) ne dépend que du couple
  `(oldConfig, newConfig)` → **hashable et cacheable**. Hash = empreinte stable de
  `(hash(oldConfig), hash(newConfig))`.
- La **vérification des données** (alertes : collisions de clé, valeurs hors bornes, lignes
  orphelines) dépend des données et **doit toujours être rejouée**.

**Mise en œuvre proposée.**
1. Calculer `migrationPlanHash = H(oldConfigHash, newConfigHash)`. Si un plan a déjà été
   calculé/validé pour ce hash (table de cache), **ne pas relancer Javers + règles** ; ne (re)faire
   que la **passe data** (`VerifyDataConsistencyAction`) pour confirmer que les alertes sont bien
   levées (ou plus d'actualité).
2. Stocker le hash de la config **effectivement appliquée** (comme un point de portage), pour
   distinguer « config inchangée » (court-circuit total) de « config changée ».
3. Bénéfice : un même portage rejoué sur plusieurs environnements/snapshots de données réutilise le
   plan et ne paie que la vérification data.

**Précaution** : invalider le cache de plan si la **version du code** des règles/taxonomie change
(inclure une version du moteur dans le hash), sinon un plan obsolète serait réutilisé après évolution
des règles.

### 7.9 Synthèse des dettes bloquantes révélées par le code (à traiter en priorité)

| # | Dette concrète | Fichier | Impact |
|---|---|---|---|
| A | Diff limité à `MapChange` | `MigrationService.detectChanges` l.303 | **Changements interdits invisibles** (clé, checker, soumission) — §7.3 |
| B | `removedData` jamais utilisé | `MigrationService.executeMigration` l.204 | Suppression de datatype silencieuse — §7.3 |
| C | `getDataInfo`/`getSchemaInfo` → `null` | `OreSiRepository` l.77-83 | Décisions conditionnées aux données impossibles / NPE — §7.3, §7.4, §7.6. *Masqué en test* : `MigrationServiceTest` (l.155-156) **mocke** ces deux méthodes, donc la suite passe alors que le code de production renverrait `null`. |
| D1 | `rules()` vide (règles non enregistrées) | `MigrationConfiguration.rules()` l.40-42 | **Aucune règle ne s'exécute en prod** : `DataAddedRule`/`IgnorableRule` existent mais ne sont pas câblées au moteur |
| D2 | `setStatus()` no-op | `MigrationPlan.setStatus` l.75-77 | Transitions `APPROVED`/`FORBIDDEN` non matérialisées (statut modifié uniquement par `addWarning()`) |
| E | Mutation in-place de `oldApplication` config | `MigrationService` l.208-209 | Fuite potentielle inter-requêtes/inter-apps — §7.2 |
| F | Évaluation change par change, sans corrélation | `FactKeys.buildFacts` (1 change) | Pas de dépendances directes/indirectes — §7.4 |

**Ordre recommandé** : (A, B) garantir l'exhaustivité du diff + atomicité du refus → (C) câbler
`DataInfo`/`SchemaInfo` → (D1, D2) activer/compléter les règles et matérialiser le statut →
(F) corrélation / graphe de dépendances → (§7.5, §7.6) actions de recalcul / backup-restore
réutilisant le code `CACHED_ROTATION` → (§7.7) état de maintenance → (§7.8) cache de plan par
`configHash` → (§7.2) réglage par app avec précédence du kill-switch global (§7.1).

---

## 8. Plan de travail priorisé

Cette section transforme les constats des §6 et §7.9 en **backlog actionnable**. La priorisation suit
trois principes, dans l'ordre :

1. **Stabiliser l'existant d'abord** — corriger les points potentiellement buggants (dettes A→F)
   *avant* d'ajouter quoi que ce soit, pour ne pas bâtir sur des fondations qui « avalent » des
   changements.
2. **Poser le socle ensuite** — câbler ce qui servira à toutes les règles futures
   (`DataInfo`/`SchemaInfo`, garde-fous d'exhaustivité, vérification read-only).
3. **Ordonner les cas de traitement par faisabilité × simplicité** — traiter d'abord les
   `ConfigurationChange` triviaux et sûrs, finir par les cas complexes et corrélés.

> **Légende.**
> **Effort** : `S` ≤ 1 j · `M` ≈ 2-4 j · `L` ≥ 5 j.
> **Risque** : *faible* (aucun impact sur les données existantes) · *moyen* (impact applicatif
> différé, réversible) · *élevé* (impact structurel / destructeur potentiel sur l'existant).

### 8.0 Vue d'ensemble des phases

| Phase | Objectif | Dettes / sections couvertes |
|---|---|---|
| **0** | Stabiliser l'anti-bug | A, B, D1, D2, E |
| **1** | Poser le socle | C, atomicité du refus, garde-fous d'exhaustivité |
| **2** | Cas de traitement ordonnés (faisabilité × simplicité) | §4.1→§4.9, F |
| **3** | Industrialisation avancée | §7.5, §7.6, §7.7, §7.8, §7.1-7.2 |
| **4** | Bascule sécurisée | §6.7, §2.7 |

### 8.1 Phase 0 — Stabiliser l'existant (anti-bug)

Aucune nouvelle fonctionnalité : on rend **fiable** le pipeline déjà en place. C'est le prérequis
absolu, car tant que le diff peut perdre des changements (A, B), toute règle ajoutée raisonnera sur
un lot incomplet.

| Action | Dette | Effort | Risque | Dépendances |
|---|---|---|---|---|
| Catch-all sur `javersCompare.getChanges()` (plus seulement `MapChange`) ; tout atome non classé ⇒ `UnresolvableChange` | A | M | élevé | — |
| Mapper `DataRemoved` à partir de `removedData` (aujourd'hui calculé puis ignoré) | B | S | moyen | A |
| Enregistrer `DataAddedRule` + `IgnorableRule` dans le bean `rules()` | D1 | S | faible | — |
| Rendre `MigrationPlan.setStatus()` effectif (transition `PENDING→APPROVED→FORBIDDEN` matérialisée) | D2 | S | faible | — |
| Cloner défensivement la config avant la mutation in-place (`MigrationService` l.208-209) | E | S | moyen | — |

- [ ] **(A)** Itérer sur **tous** les types de changement Javers, garantir l'invariant *« aucun atome avalé »*
- [ ] **(B)** Produire `DataRemoved` depuis `removedData`
- [ ] **(D1)** Câbler `DataAddedRule` et `IgnorableRule` au moteur de règles
- [ ] **(D2)** Implémenter réellement `setStatus()` et gérer `FORBIDDEN`
- [ ] **(E)** Clonage défensif de `oldApplication.getConfiguration()` avant mutation

### 8.2 Phase 1 — Poser le socle pour la suite

On ajoute les briques **réutilisées par toutes les règles conditionnelles** des phases suivantes.

| Action | Dette / § | Effort | Risque | Dépendances |
|---|---|---|---|---|
| Implémenter `getDataInfo` (`COUNT(*) GROUP BY referencetype`, NULL par composant) et `getSchemaInfo` (index/policies) | C / §7.6 | M | moyen | — |
| Test de non-régression : `nb atomes Javers == Σ ConfigurationChange produits` | A / §7.3 | S | faible | Phase 0 (A) |
| Atomicité du refus : un seul `UnresolvableChange`/`FORBIDDEN` bloque tout le dépôt (mode sécurisé) | §7.3 | S | moyen | Phase 0 (A, D2) |
| `VerifyDataConsistencyAction` (PRE, read-only) rejouant les checkers sur l'existant | §7.6 | M | moyen | C |

- [ ] **(C)** Câbler réellement `DataInfo`/`SchemaInfo` (remplacer les `return null`)
- [ ] Test « aucun atome Javers avalé »
- [ ] Atomicité du refus en mode sécurisé
- [ ] `VerifyDataConsistencyAction` read-only

### 8.3 Phase 2 — Cas de traitement ordonnés (faisabilité × simplicité)

On implémente la détection (mapping Javers → record) **et** la règle de décision (§4) de chaque
`ConfigurationChange`, **du plus simple/sûr au plus complexe/risqué**. Chaque palier ne démarre
qu'une fois le précédent vert.

| Palier | Cas (`ConfigurationChange`) | Conduite (§4) | Effort | Risque |
|---|---|---|---|---|
| **2.a — Trivial / sans intégrité** | `I18nSimpleChange`, `I18nDisplayPattenChanged`, `I18nImportHeaderChange`, tags, commentaire, `defaultLanguage`, `CheckerRemoved`, `CheckerDefinitionChanged` (élargissement) | Autoriser | S | faible |
| **2.b — Simple, conditionné aux données** | `DataRemoved`, `ComponentAdded` (non requis), `ComponenRemoved` | Autoriser / Avertir selon `DataInfo` | S→M | moyen |
| **2.c — Modéré** | `ComponentAdded` (requis), `CheckerAdded`, `CheckerDefinitionChanged` (resserrement), `SubmissionChanged` | Avertir / demander valeur par défaut | M | moyen |
| **2.d — Complexe / structurel** | `NaturalKeyChanged`, `AuthorizationChanged`, `HierarchieChanged`, `CheckerTypeChanged` | Confirmer / interdire + recalcul | L | élevé |
| **2.e — Corrélation** | Renommage (`Removed`+`Added`), clé+composant ensemble, dépendances A→B | Lot corrélé / graphe DAG | L | élevé |

- [ ] **2.a** Détection + règles des cas triviaux (i18n, métadonnées, checker relâché)
- [ ] **2.b** `DataRemoved` / `Component*` conditionnés à `DataInfo`
- [ ] **2.c** Composant requis, checker resserré, soumission (warnings/valeurs par défaut)
- [ ] **2.d** Clé naturelle, autorisations, hiérarchie, type de checker (recalcul/interdiction)
- [ ] **2.e (F)** Étape de corrélation + graphe de dépendances *avant* le moteur de règles

### 8.4 Phase 3 — Industrialisation avancée

Réutilise au maximum le code existant (`CACHED_ROTATION`, `configHash`) plutôt que de réinventer.

| Action | § | Effort | Risque | Dépendances |
|---|---|---|---|---|
| Actions backup/purge/restore réutilisant `CACHED_ROTATION` (`BackupReferenceTypeAction`…) | §7.6 | L | élevé | C, 2.d |
| Recalcul transactionnel des `__display_*` + propagation aux dépendants | §7.5 | L | élevé | F, §7.6 |
| État de maintenance par application (drapeau JSONB + filtre d'accès) | §7.7 | M | moyen | — |
| Cache de plan par `migrationPlanHash = H(oldConfigHash, newConfigHash)` (+ version du moteur) | §7.8 | M | faible | Phase 2 |
| Réglage `bypass` par application + précédence du kill-switch global (ET logique) | §7.1-7.2 | M | moyen | — |

- [ ] Backup → purge → restore (réutiliser `CACHED_ROTATION`)
- [ ] Recalcul `__display_*` transactionnel + dépendants
- [ ] État de maintenance par app
- [ ] Cache de plan par `configHash` (invalidé par version du moteur)
- [ ] Bypass par app avec précédence du kill-switch global

### 8.5 Phase 4 — Bascule sécurisée

| Action | § | Effort | Risque | Dépendances |
|---|---|---|---|---|
| Passer `openadom.migration.bypass-configuration-check` à `false` par défaut | §6.7, §2.7 | S | élevé | Phases 0→3 |

- [ ] Validation de couverture (tous les cas §4 mappés + testés)
- [ ] Bascule du défaut `bypass` à `false`

### 8.6 Matrice backlog consolidée

| Phase | Action | Dette / § | Effort | Risque | Dépendances |
|---|---|---|---|---|---|
| 0 | Diff exhaustif (catch-all `getChanges()`) | A | M | élevé | — |
| 0 | Mapper `DataRemoved` | B | S | moyen | A |
| 0 | Enregistrer les règles | D1 | S | faible | — |
| 0 | `setStatus()` effectif | D2 | S | faible | — |
| 0 | Clonage défensif config | E | S | moyen | — |
| 1 | Câbler `DataInfo`/`SchemaInfo` | C | M | moyen | — |
| 1 | Test « aucun atome avalé » | §7.3 | S | faible | A |
| 1 | Atomicité du refus | §7.3 | S | moyen | A, D2 |
| 1 | `VerifyDataConsistencyAction` | §7.6 | M | moyen | C |
| 2.a | Cas triviaux (i18n, métadonnées, checker relâché) | §4.5/4.8 | S | faible | Phase 1 |
| 2.b | `DataRemoved`/`Component*` conditionnés | §4.1/4.2 | S→M | moyen | C |
| 2.c | Composant requis, checker resserré, soumission | §4.2/4.5/4.7 | M | moyen | C |
| 2.d | Clé/autorisation/hiérarchie/type checker | §4.3/4.4/4.6 | L | élevé | C, §7.6 |
| 2.e | Corrélation / graphe de dépendances | F / §7.4 | L | élevé | 2.d |
| 3 | Backup/purge/restore | §7.6 | L | élevé | C, 2.d |
| 3 | Recalcul `__display_*` | §7.5 | L | élevé | F |
| 3 | État de maintenance | §7.7 | M | moyen | — |
| 3 | Cache de plan `configHash` | §7.8 | M | faible | Phase 2 |
| 3 | Bypass par app + kill-switch | §7.1-7.2 | M | moyen | — |
| 4 | Bascule `bypass=false` | §6.7 | S | élevé | Phases 0→3 |

---

> **Références code** (branche `develop`) :
> `domain/application/configuration/migration/**`,
> `rest/services/MigrationService.java`,
> `rest/data/migration/MigrationConfiguration.java`,
> `rest/config/MigrationProperties.java`,
> `domain/port/MigrationApplicationPort.java`,
> `persistence/flyway/**`, `persistence/index/AuthorizationIndex.java`,
> `persistence/ApplicationRepository.java`.
> Documents liés : `ARCHITECTURE_LECTURE_CONFIGURATION.md`, `INDEPENDENCE_DOMAINE.md`,
> `ARCHITECTURE_DEPOT_FICHIER.md`.