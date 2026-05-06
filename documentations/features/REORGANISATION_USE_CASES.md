# Réorganisation des Use Cases en sous-packages domaine-métier

> **Statut** : ✅ Implémenté — commit `c840510`  
> **Auteur** : Philippe TCHERNIATINSKY  
> **Date** : 2026-04-03  
> **Branche** : develop  

---

## Contexte

La modernisation initiale (commit `f5f8b270`) a introduit **46 Use Cases** répartis dans des packages
plats (`storage/`, `metadata/`, `email/`, etc.). Le commit de réorganisation `c840510` a ensuite
restructuré ces Use Cases en **sous-packages domaine-métier** pour améliorer la navigabilité et
la modularité du code.

---

## Structure des packages avant / après

### Avant `c840510`

```
usecases/
├── storage/
│   ├── CreateDataUseCase
│   ├── GetCharteUseCase
│   ├── GetFileUseCase        ← créé mais jamais branché (voir §Anomalies)
│   ├── GetFileWithDataUseCase
│   ├── GetFilesOnRepositoryUseCase
│   ├── GetReferencedBinaryFilesUseCase
│   ├── GetStoreFileUseCase
│   ├── RemoveFileUseCase
│   ├── UnPublishVersionBeforeDeleteUseCase
│   ├── FindAdditionalFileUseCase
│   ├── CreateOrUpdateAdditionalFileUseCase
│   ├── DeleteAdditionalFilesUseCase
│   └── GetAdditionalFilesZipStreamUseCase
├── metadata/
│   ├── GetSynthesisUseCase
│   ├── GetSynthesisWithVariableUseCase
│   ├── BuildSynthesisUseCase
│   ├── BuildNormalizedSchemaUseCase
│   ├── GetNormalizedSchemaUseCase
│   ├── FindRightsRequestUseCase
│   └── CreateOrUpdateRightsRequestUseCase
├── email/
│   └── SendUploadErrorsMailUseCase
├── security/
│   ├── authentication/
│   │   └── GetCurrentUserUseCase
│   └── authorization/
│       ├── GetAdminAuthorizationsUseCase
│       ├── GetApplicationAuthorizationsUseCase
│       ├── GetAllUsersUseCase
│       └── GetAuthorizationScopesUseCase
├── application/              (7 use cases — non modifiés)
└── data/                     (10 use cases — non modifiés)
```

### Après `c840510` (structure actuelle)

```
usecases/
├── storage/
│   ├── binaryfile/
│   │   ├── CreateDataUseCase
│   │   ├── GetCharteUseCase
│   │   ├── GetFileUseCase        ← orphelin non branché (voir §Anomalies)
│   │   ├── GetFileWithDataUseCase
│   │   ├── GetFilesOnRepositoryUseCase
│   │   ├── GetReferencedBinaryFilesUseCase
│   │   ├── GetStoreFileUseCase
│   │   └── RemoveFileUseCase
│   ├── additionalfile/
│   │   ├── FindAdditionalFileUseCase
│   │   ├── CreateOrUpdateAdditionalFileUseCase
│   │   ├── DeleteAdditionalFilesUseCase
│   │   └── GetAdditionalFilesZipStreamUseCase
│   └── versioning/
│       └── UnPublishVersionBeforeDeleteUseCase
├── metadata/
│   ├── synthesis/
│   │   ├── GetSynthesisUseCase
│   │   ├── GetSynthesisWithVariableUseCase
│   │   └── BuildSynthesisUseCase
│   ├── normalization/
│   │   ├── BuildNormalizedSchemaUseCase
│   │   └── GetNormalizedSchemaUseCase
│   └── rightsrequest/
│       ├── FindRightsRequestUseCase
│       └── CreateOrUpdateRightsRequestUseCase
├── messaging/                    (renommé depuis `email/`)
│   └── SendUploadErrorsMailUseCase
├── security/
│   ├── authentication/
│   │   └── GetCurrentUserUseCase
│   └── authorization/
│       ├── GetAdminAuthorizationsUseCase
│       ├── GetApplicationAuthorizationsUseCase
│       ├── GetAllUsersUseCase
│       └── GetAuthorizationScopesUseCase
├── application/                  (7 use cases — non modifiés)
└── data/                         (10 use cases — non modifiés)
```

---

## Contrôleurs mis à jour

Le commit `c840510` a mis à jour les imports dans **6 contrôleurs** :

| Contrôleur | Changement principal |
|---|---|
| `OreSiResources.java` | Tous les imports storage/metadata/messaging mis à jour |
| `FileResources.java` | `storage.binaryfile.GetFileWithDataUseCase` |
| `AdditionalFilesResources.java` | `storage.additionalfile.*` |
| `NormalizationResources.java` | `metadata.normalization.*` |
| `SynthesisResources.java` | `metadata.synthesis.*` |
| `ApplicationResources.java` | Voir diff c840510 |

Les contrôleurs `BundleResources.java` et `AuthorizationResources.java` utilisaient
déjà les sous-packages `security/` qui avaient été créés dès `f5f8b270` — ils n'ont
donc pas nécessité de mise à jour dans `c840510`.

---

## Vérification de complétude (post-mortem `c840510`)

### ✅ Imports correctement mis à jour

Un scan exhaustif confirme qu'aucun fichier `.java` n'importe encore les anciennes
localisations :

```
fr.inra.oresing.rest.usecases.storage.<Classe>         → aucun résidu
fr.inra.oresing.rest.usecases.metadata.<Classe>        → aucun résidu
fr.inra.oresing.rest.usecases.email.<Classe>           → aucun résidu
```

### ✅ Anomalie corrigée : `GetFileUseCase` désormais branché

**Constat initial** : `GetFileUseCase` était le seul Use Case des 46 qui **n'était injecté dans
aucun contrôleur REST**. Il a été créé dans le commit `f5f8b270` en même temps que ses 45
homologues, puis déplacé dans `c840510`, mais n'avait jamais été connecté à un endpoint.

**Ce Use Case diffère de `GetFileWithDataUseCase`** :
- `GetFileUseCase` → retourne `BinaryFile` **sans** le contenu binaire (métadonnées uniquement)
- `GetFileWithDataUseCase` → retourne `BinaryFile` **avec** le contenu binaire (flux de données)

**Correction apportée** : Ajout d'un endpoint dédié aux métadonnées dans `FileResources.java`.

Les deux endpoints sont désormais disponibles dans `FileResources` :

| Endpoint | Méthode | Retour | Use Case |
|---|---|---|---|
| `GET /api/v1/applications/{name}/file/{id}` | Stream binaire | `application/octet-stream` | `GetFileWithDataUseCase` |
| `GET /api/v1/applications/{name}/file/{id}/info` | **Métadonnées JSON** | `application/json` (`BinaryFileResult`) | `GetFileUseCase` |

Le `BinaryFileResult` contient : `id`, `name`, `comment`, `size`, `params` (createdate, createuser,
published, publisheddate, publisheduser, binaryFileDataset) et `referencedFiles` (si le fichier
est publié).

---

## Validation de la migration

Le commit `c840510` documente explicitement que la migration a été validée par :

```bash
mvn clean compile       # compilation réussie
# Suite OreSiResourcesTest : 122 tests passés
```

---

## Rationale de la structure finale

| Package | Cohésion métier |
|---|---|
| `storage/binaryfile` | Fichiers binaires de données importées |
| `storage/additionalfile` | Fichiers additionnels (cartes, chartes…) |
| `storage/versioning` | Gestion des versions (publication/dépublication) |
| `metadata/synthesis` | Génération et consultation des synthèses |
| `metadata/normalization` | Schémas de normalisation |
| `metadata/rightsrequest` | Demandes et gestion des droits d'accès |
| `messaging` | Notifications (email d'erreurs d'import, futurs autres canaux) |
| `security/authentication` | Authentification utilisateur courant |
| `security/authorization` | Gestion des autorisations et scopes |
| `application` | Cycle de vie des applications (SI) |
| `data` | Recherche, filtrage et manipulation des données métier |

---

## Liens

- Commit de grande modernisation : `f5f8b270` (création des 46 Use Cases)
- Commit de réorganisation : `c840510ad48973a7d28594fd36169d27ce5129de`
- Architecture générale : [`documentations/openadom/fichiers/introduction/architecture_et_services.qmd`](../openadom/fichiers/introduction/architecture_et_services.qmd)
- Feuille de route qualité Sonar : [`SONAR_ROADMAP.md`](../../SONAR_ROADMAP.md)