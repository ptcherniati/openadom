# Feuille de route qualité Sonar — openADOM backend

> Document committable — tenu à jour à chaque lot traité.
> État de session WIP : voir `.avancement_sonar_wip.md` (ne pas committer).

## Métriques de référence (branche test2, 2026-04-13)

| Indicateur | Valeur |
|---|---|
| Issues Sonar ouvertes | **461** |
| Couverture nouveau code | **41,4 %** |
| Tests run | 1 125 |
| Tests KO (Failures + Errors) | **111 + 14 = 125** |
| Temps total suite complète | ~23 min |

---

## Règles à respecter dans tous les lots

### Git
```bash
# Toujours utiliser --no-pager
git --no-pager log --oneline -10
git --no-pager diff
git --no-pager status

# Commits atomiques par lot (un thème = un commit)
git commit -m "refactor(sonar): <description courte et précise>"

# Ne jamais committer .avancement_sonar_wip.md
# (il est déjà dans .gitignore ou à y ajouter)
```

### Commandes de test (validation ciblée)
```bash
# Tester une ou plusieurs classes sans lancer toute la suite
mvn --batch-mode -Dtest=fr.inra.oresing.rest.exceptions.OreExceptionHandlerTest test

# Tester plusieurs classes
mvn --batch-mode -Dtest=ClasseA,ClasseB test

# Suite non-docker complète
mvn --batch-mode test -Dsurefire.excludedGroups=docker-required

# Suite complète avec Docker (long, ~23 min)
mvn --batch-mode test
```

### Sonar — ne PAS relancer les tests à chaque analyse
```bash
# Analyse rapide sans tests (réutilise jacoco.xml existant)
./sonar-local.sh branch test2 --skip-tests

# Analyse avec Quality Gate bloquant
./sonar-local.sh branch test2 --skip-tests --enforce-gate

# Analyse complète avec tests (lente, avant MR uniquement)
./sonar-local.sh branch test2 --with-docker-tests
```

### Principes de code
- Pas de cast inutile, pas de wildcard `<?>` si évitable par type borné
- Exceptions : toujours propager la cause (`new Ex("msg", cause)`)
- Injection Spring : toujours par constructeur (`final` fields), jamais `@Autowired` sur champ
- Logs conditionnels SLF4J : `log.debug("msg {}", val)` pas `log.debug("msg " + val)`
- Identifiants Java : ne pas utiliser de mot-clé restreint (`record`, `var`, `yield`, etc.) comme nom de variable

---

## Priorités et lots de travail

### P0 — Régression tests (BLOQUANT — traiter en premier)

**Symptôme** : 111 failures + 14 errors dans `OreSiResourcesTest` et `NormalizedServiceTest`

**Erreur caractéristique** :
```
BadFileOrUUIDQuery application inconnue 'acbb_openadom_v2'
Status expected:<201> but was:<400>
Async not started
```

**Hypothèse principale** : l'orchestration `uploadBundle` déplacée sur `normalExecutorService`
(commit `33cd8f1`) introduit une régression dans le flux d'import (`addApplication → 400`).

**Démarche d'investigation** :
```bash
# Isoler le premier test qui échoue
mvn --batch-mode -Dtest=fr.inra.oresing.rest.OreSiResourcesTest#addApplicationAcbb test 2>&1 | tail -40

# Vérifier les logs Spring autour du 400
# Chercher la stack trace complète dans target/surefire-reports/
grep -A 20 "addApplicationAcbb" target/surefire-reports/fr.inra.oresing.rest.OreSiResourcesTest.txt
```

**Fichiers à inspecter** :
- `src/main/java/fr/inra/oresing/rest/OreSiResources.java` (uploadBundle, createData)
- `src/main/java/fr/inra/oresing/rest/services/ApplicationService.java`
- `src/main/java/fr/inra/oresing/OreSiNg.java` (configureAsyncSupport)

**Validation** : `mvn --batch-mode -Dtest=OreSiResourcesTest test` → 0 failure

---

### P1 — Fiabilité (Reliability)

#### Lot 1.1 — Faux positifs NPE dans OreSiNg.java `[~WONTFIX]`

| Fichier | Lignes | Problème |
|---|---|---|
| `src/main/java/fr/inra/oresing/OreSiNg.java` | L145–L153 | Sonar croit que `Info.Builder.withDetail()` peut retourner null |

**Contexte** : Chaînage Spring Boot `Info.Builder` — jamais null en pratique.
**Décision** : marquer "false positive" dans Sonar OU extraire le builder dans une variable locale.

**Fix minimal** (si marquage Sonar non disponible) :
```java
// Avant (chaînage — Sonar se plaint)
return new Info.Builder()
    .withDetail("branch", Optional.ofNullable(gitProperties.get("git.branch")).orElse(""))
    // ...
    .build();

// Après (variable locale — Sonar satisfait)
Info.Builder infoBuilder = new Info.Builder();
infoBuilder.withDetail("branch", Optional.ofNullable(gitProperties.get("git.branch")).orElse(""));
// ...
return infoBuilder.build();
```

**Validation** : `mvn --batch-mode -Dtest=fr.inra.oresing.ApplicationTest test`

---

#### Lot 1.2 — Conflits de noms de méthodes dans OaImportWorkerService

| Fichier | Lignes | Problème |
|---|---|---|
| `src/.../domain/fileprocessor/OaImportWorkerService.java` | L329, L413, L471 | Méthode `private` masque méthode de la classe parente |

**Règle Sonar** : pitfall — une méthode `private` dans la sous-classe ne peut pas `@Override`
la méthode parente, mais si les deux ont le même nom, c'est source de confusion.

**Fix** : renommer les méthodes `private` de la sous-classe (ex. `doProcessHeader` → `processHeaderInternal`).

**Validation** : `mvn --batch-mode -Dtest=*ImportWorker* test`

---

#### Lot 1.3 — Erreur de type dans DataRow

| Fichier | Ligne | Problème |
|---|---|---|
| `src/.../persistence/DataRow.java` | L56 | `List<Map>` ne peut pas contenir un `int` |

**Fix** : corriger le type générique (`List<Map<String, ?>>` ou le type réel attendu).

**Validation** : `mvn --batch-mode -Dtest=*DataRow* test`

---

#### Lot 1.4 — Comparaison inter-types dans EmailService

| Fichier | Ligne | Problème |
|---|---|---|
| `src/main/java/fr/inra/oresing/mail/EmailService.java` | L206 | `equals()` entre types sans relation → toujours `false` |

**Fix** : supprimer la comparaison ou corriger les types comparés.

**Validation** : `mvn --batch-mode -Dtest=*EmailService* test` ou vérification à l'œil

---

### P2 — Nettoyage rapide (Unused / Dead code)

> Ces corrections sont mécaniques et peu risquées. Regrouper en 1–2 commits.

#### Lot 2.1 — Champs inutilisés dans WorkflowOrchestratorImport

| Fichier | Lignes | Champs à supprimer |
|---|---|---|
| `src/.../domain/fileprocessor/WorkflowOrchestratorImport.java` | L33, L36, L37, L38 | `loaderService`, `referenceValueRepository`, `userId`, `path` |

> ⚠️ Ces 4 champs inutilisés gonflent aussi le constructeur à 10 paramètres (> 7 autorisés, L40).
> Supprimer les champs → réduire les paramètres du constructeur en cascade.
> Vérifier aussi le conflit de nom `userId` (L78) qui masque le champ L37.

**Validation** : `mvn --batch-mode -Dtest=*WorkflowOrchestrator* test`

---

#### Lot 2.2 — Imports inutilisés

| Fichier | Lignes | Imports à supprimer |
|---|---|---|
| `src/.../domain/repository/data/DataRepository.java` | L6, L7 | `DataInfo`, `SchemaInfo` |
| `src/.../domain/repository/data/DataRepositoryForBuffer.java` | L4, L9 | `DataValue`, `Stream` |
| `src/.../persistence/DataRepository.java` | — | via IDE (Organize Imports) |

---

#### Lot 2.3 — Variables locales inutilisées

| Fichier | Ligne | Variable |
|---|---|---|
| `src/.../domain/fileprocessor/WorkflowOrchestratorImportBuilder.java` | L179 | `response` |
| `src/.../domain/internationalization/InternationalizationDisplay.java` | L90 | `refType` |
| `src/.../domain/data/read/DataHeaderReader.java` | L69 | `firstRowLine` |
| `src/.../persistence/JsonRowMapper.java` | L251 | `applicationName` |
| `src/.../persistence/DataRepository.java` | L205, L206 | `referenceid`, `referencesby` |

---

#### Lot 2.4 — Code commenté à supprimer

| Fichier | Lignes |
|---|---|
| `src/.../domain/fileprocessor/WorkflowOrchestratorImportBuilder.java` | L200, L211 |
| `src/main/java/fr/inra/oresing/mail/Email.java` | L12 |
| `src/main/java/fr/inra/oresing/mail/EmailService.java` | L124 |

---

### P3 — Maintenabilité (Maintainability)

#### Lot 3.1 — Wildcards génériques à typer

| Fichier | Lignes | Action |
|---|---|---|
| `src/.../domain/checker/CheckerFactory.java` | L29 | Remplacer `<?>` par type borné |
| `src/.../domain/checker/LineChecker.java` | L82, L88, L92, L102, L117 | Idem + supprimer cast inutile L106 |
| `src/.../domain/data/deposit/context/DataImporterContext.java` | L204, L211, L370 | Idem |
| `src/.../domain/data/deposit/csvreader/CsvReader.java` | L35 | Idem |
| `src/.../data/deposit/transformation/DataValidator.java` | L29, L47, L220 | Idem + paramètre F inutilisé L209 |
| `src/.../domain/data/deposit/recursion/WithRecursion.java` | L159 | Fournir le type paramétré |
| `src/.../domain/data/rapport/BundleReport.java` | L18, L19, L50, L61 | Fournir le type paramétré |
| `src/.../domain/data/read/DataHeaderReader.java` | L49 | Idem |
| `src/.../persistence/DataRepository.java` | L687 | Idem |

> 💡 Travailler fichier par fichier — chaque fichier peut être un sous-commit ou regroupé par package.

---

#### Lot 3.2 — Complexité cognitive

| Fichier | Ligne | Complexité actuelle → cible |
|---|---|---|
| `src/.../application/configuration/Submission.java` | L27 | 28 → 15 (extraction de méthodes privées) |
| `src/.../data/deposit/transformation/DataValidator.java` | L29 | 8 paramètres → 7 max (objet de contexte) |

---

#### Lot 3.3 — Injection champs → constructeur

| Fichier | Ligne | Action |
|---|---|---|
| `src/.../executor/AsyncExecutorConfiguration.java` | L33 | Remplacer `@Autowired` champ par injection constructeur |
| `src/.../persistence/JsonRowMapper.java` | L57 | Idem |

---

#### Lot 3.4 — Deprecations et bonnes pratiques

| Fichier | Lignes | Problème | Fix |
|---|---|---|---|
| `src/.../executor/AsyncExecutorConfiguration.java` | L38, L40 | `@NonNull` déprécié | Remplacer par `jakarta.annotation.NonNull` ou `Objects.requireNonNull` |
| `src/.../executor/AsyncExecutorConfiguration.java` | L89 | Littéral dupliqué 4× | Extraire en constante `private static final String LOG_UNCAUGHT = "..."` |
| `src/.../executor/AsyncExecutorConfiguration.java` | L259 | `@Deprecated` sans `since`/`forRemoval` | Ajouter `@Deprecated(since = "x.y", forRemoval = true)` |
| `src/.../persistence/DataRepository.java` | L67, L76, L89 | `equalsAnyIgnoreCase` déprécié | Remplacer par `StringUtils.equalsAnyIgnoreCase(s, vals)` ou `Set.of(vals).contains(s.toLowerCase())` |
| `src/.../executor/AsyncExecutorConfiguration.java` | L282 | Log non conditionnel | `if (log.isDebugEnabled()) { log.debug(...) }` |

---

#### Lot 3.5 — Petites corrections ciblées

| Fichier | Ligne | Problème | Fix |
|---|---|---|---|
| `src/.../application/configuration/Ltree.java` | L108–L110 | Invoke method conditionally | Conditionner les appels de méthode coûteux |
| `src/.../domain/application/normalized/Component.java` | L174, L248, L254 | Text block Java 14 | Remplacer `"..."` par simple littéral sans `\n` superflu |
| `src/.../data/deposit/context/column/ManyValuesStaticColumn.java` | L15 | Constructeur `public` → `protected` + param inutilisé | Changer visibilité + supprimer `headerForColumnn` |
| `src/.../data/deposit/context/column/OneValueStaticColumn.java` | L13 | Idem | Idem |
| `src/.../data/deposit/context/column/PatternColumnFactory.java` | L42 | `Arrays.asList()` → `List.of()` | Remplacer |
| `src/.../domain/data/rapport/BundleReport.java` | L50 | Constructeur redondant | Supprimer |
| `src/.../domain/data/rapport/Manifest.java` | L66 | Exception générique | Créer/utiliser exception dédiée |
| `src/.../domain/fileprocessor/OaImportWorkerService.java` | L203 | `@Override` manquant | Ajouter l'annotation |
| `src/.../domain/fileprocessor/OaImportWorkerService.java` | L252 | `instanceof` + cast → pattern matching | `instanceof ErrorThresholdExceededException errorEx` |
| `src/.../domain/fileprocessor/OaImportWorkerService.java` | L415 | Paramètre `headerContext` inutilisé | Supprimer |
| `src/.../domain/fileprocessor/WorkflowOrchestratorImportBuilder.java` | L93 | Délimiteur de chemin hardcodé | Utiliser `File.separator` ou `Path` |
| `src/.../domain/fileprocessor/WorkflowOrchestratorImportBuilder.java` | L136 | Variable nommée comme identifiant restreint | Renommer |
| `src/.../persistence/AuthenticationService.java` | L442, L445 | Variable temp inutile + lambda → méthode ref | `return userRepository::getRolesForCurrentUser` |
| `src/.../persistence/DataRepository.java` | L55 | Membre `package-private` → `protected` | Changer visibilité |
| `src/.../persistence/DataRepository.java` | L187, L193, L228 | try imbriqué + exception générique | Extraire méthode + exception dédiée |

---

### P4 — Couverture de code (Coverage)

**Objectif** : remonter la couverture du nouveau code au-dessus de **60 %** (Quality Gate cible).

#### Fichiers prioritaires (0% et volume élevé)

| Fichier | Lignes non couvertes | Conditions | Stratégie |
|---|---|---|---|
| `OreSiResources.java` | 535 | 61 | Dépend de P0 (tests intégration) |
| `Column.java` | 90 | 22 | Tests unitaires dépôt |
| `DataImporter.java` | 89 | 30 | Tests unitaires import |
| `Manifest.java` | 43 | 8 | Tests unitaires rapport |
| `WithRecursion.java` | 54 | 16 | Tests unitaires récursion |
| `DataRepository.java` (persistence) | 61 | 4 | Tests intégration |
| `MigrationExecutor.java` | 37 | 10 | Tests use-cases migration |

#### Fichiers avec couverture partielle à améliorer

| Fichier | Couverture actuelle | Lignes restantes |
|---|---|---|
| `AsyncExecutorConfiguration.java` | 11,8 % | 123 |
| `DataService.java` | 21,1 % | 100 |
| `ApplicationService.java` | 22,2 % | 66 |
| `MigrationService.java` | 6,6 % | 98 |
| `OaImportWorkerService.java` | 55,5 % | 43 |

**Stratégie recommandée** :
1. Corriger P0 en premier → `OreSiResources.java` retrouve sa couverture d'intégration
2. Ajouter tests use-cases (`@Tag("use-cases")`) pour les services applicatifs
3. Ajouter tests unitaires purs (sans Spring) pour les classes domaine

---

## Workflow de reprise à tout moment

```
1. Lire .avancement_sonar_wip.md → état de la session précédente
2. Choisir le lot le plus prioritaire non terminé (P0 > P1 > P2 > P3 > P4)
3. Lire les fichiers concernés (git --no-pager diff HEAD~1 -- fichier)
4. Implémenter le lot
5. Valider avec tests ciblés : mvn --batch-mode -Dtest=ClassesConcernées test
6. Committer : git commit -m "refactor(sonar): ..."
7. Mettre à jour .avancement_sonar_wip.md (lot fait, métriques)
8. Lancer Sonar sans tests : ./sonar-local.sh branch test2 --skip-tests
9. Vérifier le dashboard, noter la nouvelle valeur des issues
10. Recommencer en 2
```

---

## Suivi des lots

| Lot | Description | Fichiers | Issues fermées | Statut |
|---|---|---|---|---|
| P0 | Régression tests (addApplication 400) | OreSiResources, ApplicationService | — | 🔴 À faire |
| 1.1 | NPE faux positifs OreSiNg | OreSiNg.java | 9 | 🟡 Wontfix à évaluer |
| 1.2 | Conflits méthodes OaImportWorkerService | OaImportWorkerService | 3 | 🟡 Fichier absent de main source |
| 1.3 | Type error DataRow | DataRow.java | 1 | ✅ 14473fe |
| 1.4 | Equals inter-types EmailService | EmailService.java | 1 | ✅ 6462d83 |
| 2.1 | Champs inutilisés WorkflowOrchestratorImport | WorkflowOrchestratorImport | 5 | ✅ 6462d83 |
| 2.2 | Imports inutilisés | DataRepository×2, DataRepositoryForBuffer | 4 | ✅ 6462d83 |
| 2.3 | Variables locales inutilisées | 5 fichiers | 10 | ✅ 6462d83 |
| 2.4 | Code commenté + délimiteur + var restreinte | WFOImportBuilder, Email, EmailService | 7 | ✅ 6462d83 |
| 3.1 | Wildcards génériques | 9 fichiers | ~20 | ✅ 14473fe + 1054d57 + 2f0ffcb |
| 3.2 | Complexité cognitive | Submission, DataValidator | 2 | ✅ 1054d57 + 4111f2f |
| 3.3 | Injection constructeur AsyncExecutorConf | AsyncExecutorConf | 1 | ✅ e7557b0 |
| 3.4 | Deprecations + constante dupliquée + @Override | AsyncExecutorConf, OaImportWorker | ~5 | ✅ e7557b0 + 4111f2f |
| 3.5 | Petites corrections restantes | BundleReport, ManyValuesStatic, OneValueStatic, Manifest, AuthenticationService, Ltree, DataRepository | ~12 | ✅ 14473fe + 1054d57 |
| P4 | Couverture (nouvelles tests) | OreSiResources++ | — | 🔴 Après P0 |

