# Instructions pour GitHub Copilot — openADOM backend

## Conventions générales

- **Commandes git** : toujours les exécuter sans pagination (`git --no-pager …`).
- **Langage** : Java 25, Spring Boot 3.x, PostgreSQL (schéma `oa_audit`), Maven.
- **Tests** : JUnit 5 + Testcontainers (PostgreSQL). Profils Maven : `use-cases`, `no-tags`,
  `docker-required`, `sonar-aggregate`.

## Architecture

Le backend suit une architecture hexagonale :

| Couche | Package principal |
|--------|------------------|
| REST | `fr.inra.oresing.rest` + `rest/usecases/**` (46 Use Cases) |
| Services métier | `fr.inra.oresing.rest.services` |
| Domaine | `fr.inra.oresing.domain` |
| Persistance | `fr.inra.oresing.persistence` |
| Pipeline import | `fr.inra.oresing.workflow.cascade` |
| Monitoring | `fr.inra.oresing.monitoring` |

## Règles de code à respecter

1. **Injection Spring uniquement par constructeur** : champs `final`, jamais `@Autowired` sur champ.
2. **Logs SLF4J paramétrés** : `log.debug("msg {}", val)` — jamais de concaténation de chaînes.
3. **Exceptions** : toujours propager la cause (`new Ex("msg", cause)`).
4. **Pas de mots-clés Java restreints** (`record`, `var`, `yield`) comme noms de variable.
5. **Dead code** : supprimer immédiatement tout champ ou méthode inutilisée signalé par Sonar.

## Pipeline d'import CSV (Cascade)

Voir `documentations/architecture/ARCHITECTURE_DEPOT_FICHIER.md` pour la description complète.
Points clés :
- Ordre de traitement par ligne : `computeComputedColumns()` → `check()` → `computeKeys()`
- Mode récursif legacy : `chunkSizeLines = MAX_VALUE`, `parallelism = 1`.
- Mode récursif ordonné (`__ORDER_STRICT__`) : chunk size normale, `parallelism = 1`.
- Cache ReferenceType : `naturalKeyIndex` O(1) + `seenOnce`/`precomputedResults` partagés.
- `getKnownId` : `naturalKeyPatternIndex` O(1) (commit `4f2dd688`).

## Propriétés de configuration importantes

```properties
cascade.import.chunk-size-lines=1000
cascade.import.parallelism=4
cascade.import.pipeline-queue-capacity=50
cascade.import.sink-strategy=MERGE_FILE
cascade.import.reference-cache-max-entries=5000
openadom.migration.bypass-configuration-check=true
openadom.http.streaming.timeout=6h
```

## Fichiers de documentation

### `documentations/architecture/` — fonctionnalités implémentées (référence)

| Document | Contenu |
|----------|---------|
| `ARCHITECTURE_DEPOT_FICHIER.md` | Filière d'import complète bout en bout |
| `ARCHITECTURE_LECTURE_CONFIGURATION.md` | YAML → Configuration → JSONB PostgreSQL |
| `PERF_IMPORT_REFERENCE_PRECOMPUTATION.md` | Optimisations cache référence (niveaux 1-4) |
| `ERREURS_DEPOT_FICHIER.md` | Catalogue des codes d'erreur d'import |
| `MODE_RECURSION_ORDONNEE.md` | Mode `__ORDER_STRICT__` |
| `REORGANISATION_USE_CASES.md` | Structure des 46 Use Cases |
| `INDEPENDENCE_DOMAINE.md` | Découplage de la couche `domain` (Ports & Adapters) |
| `CI_CD_USE_CASES.md` | Profils Maven et CI/CD des tests Use Cases |
| `TICKET_TRANSACTION_BLOCK_DETECTION.md` | Détection des blocages transactionnels (implémenté) |

### `documentations/features/` — en cours, propositions et suivi

| Document | Contenu | Statut |
|----------|---------|--------|
| `ACCELERATED_FILTERS.md` | Filtres accélérés (filter model + GIN faits ; colonnes générées à venir) | Partiel |
| `VERIFICATION_MIGRATION_CONFIGURATION.md` | Règles d'acceptation de migration de configuration | Étude en cours |
| `TICKET_MAIL_NON_BLOCKING.md` | Découplage de l'envoi d'e-mail du thread HTTP | À implémenter |
| `TESTCONTAINERS_CONTAINER_MIGRATION.md` | Migration optionnelle `@BeforeAll → @Container` | Non implémenté |
| `PORTAGE_DEVELOP.md` | Suivi du portage depuis `Refactoring_deposit` | Méta / historique |