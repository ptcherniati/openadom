# Projet de la version V2 de l'application des ORE.

Le projet est constitué de 2 sous projet :

- La partie serveur qui fournit les web services de l'application
- La partie UI qui fournit une interface VueJS permettant d'interroger ces Web Services.

## Objectifs

- Utilisation de java >=10
- Suppression de la couche ORM
- Utilisation de web services
- Accès aux ressources par une interface indépendante par example VueJS, mais aussi des applications comme R-Shiny ou en attaquant directement la base de données.
- Simplification du ticket d'accès technique pour les développeurs (interface ou services)


## Environnement de développement

### Prérequis

  - **JDK** 25
  - **Maven** 3.9.11+
  - **Docker** (pour PostgreSQL et autres services)
  - **PostgreSQL** 18

Pour construire le projet avec Maven, l'utilisateur doit avoir le droit de démarrer de conteneurs docker.

Sous Linux, cela consiste à ajouter l'utilisateur au groupe `docker` :
```bash
sudo usermod -aG docker $USER
newgrp docker
```

### Stack Technologique

| Composant | Version | Rôle |
|-----------|---------|------|
| Java | 25 | Langage principal, features modernes (records, sealed interfaces) |
| Spring Boot | 4.0.0 | Framework Web réactif |
| Spring WebFlux | 4.0.0 | Programmation réactive (Flux, Mono) |
| Spring Security | 4.0.0 | Authentification et autorisation |
| Spring Data | 4.0.0 | Accès aux données |
| PostgreSQL | 18 | Base de données relationnelle |
| Flyway | 11.7.2 | Migrations BD |
| Micrometer | (inclus SB 4.0.0) | Framework de métriques |
| Prometheus | (via Micrometer) | Collection des métriques |
| Actuator | 4.0.0 | Endpoints de monitoring |
| JUnit 5 | (inclus SB 4.0.0) | Framework de tests |
| Mockito | (inclus SB 4.0.0) | Mocking dans les tests |

### Vérifier la qualité du projet

```bash
mvn test
```

### Démarrer l'interface en local

**🔒 PRÉREQUIS DE SÉCURITÉ** :

Avant de démarrer, créer un fichier `.env` à la racine du projet avec les variables d'environnement :

```bash
# .env - Configuration locale
# ⚠️ CHANGER CES VALEURS EN PRODUCTION

# Base de données PostgreSQL
DB_HOST_PORT=localhost:5432
DB_DATABASE=openadom
DB_USER=openAdomTechUser         # ⚠️ Nom de l'utilisateur technique (à garder cohérent avec les scripts SQL)
DB_PASSWORD=xxxxxxxx             # ⚠️ À CHANGER: mot de passe sécurisé

# Pool de connexions
SPRING_DATASOURCE_HIKARI_CONNECTION-TIMEOUT=30000
SPRING_DATASOURCE_HIKARI_MINIMUM-IDLE=5
SPRING_DATASOURCE_HIKARI_MAXIMUM-POOL-SIZE=20
SPRING_DATASOURCE_HIKARI_IDLE-TIMEOUT=600000
SPRING_DATASOURCE_HIKARI_MAX-LIFETIME=1800000
SPRING_DATASOURCE_HIKARI_AUTO-COMMIT=false

# Serveur
SERVER_PORT=8080
ALLOWED_ORIGIN=http://localhost:3000

# Flyway
SPRING_FLYWAY_PLACEHOLDERS_PUBLIC-ROLE-ID=public

# Mail (optionnel pour développement)
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_TEST-CONNECTION=false
SPRING_MAIL_SMTP_STARTSSL_ENABLE=false
SPRING_MAIL_SMTP_STARTSSL_REQUIRED=false
MAIL_FROM=noreply@example.com

# Autres
SPRING_SERVLET_ENCODING_FORCE=true
SPRING_THREADS_VIRTUAL_ENABLED=false
SWAGGER-UI-URI=http://localhost:8080
VIEW_STRATEGY=default

# File Sender (optionnel)
FILE_SENDER_BASE_URL=
FILE_SENDER_USER_NAME=
FILE_SENDER_API_KEY=

# Workflow
MAX_REQUESTS_PER_USER=60
CHUNKER_THREADS=2
WORKER_THREADS=2
MERGER_THREADS=2
LOADER_THREADS=2
```

**Important** : Le nom d'utilisateur `DB_USER` dans `.env` **doit correspondre** au nom utilisé dans les scripts SQL (par défaut : `openAdomTechUser`).

#### **Phase 1 : Démarrer les Containers**

La base de données sera créée avec un rôle `dbuser` propriétaire, et un rôle technique `openAdomTechUser` pour l'application.

```bash
docker-compose up --build --force-recreate -d
```

Cela démarre :
- PostgreSQL 18 (base de données)
- pgAdmin 4 (interface DB optionnelle)
- Prometheus et Grafana (monitoring optionnel)

#### **Phase 2 : Démarrer le Backend**

Le backend crée automatiquement les tables dans le schéma `public` via Flyway.

```bash
mvn spring-boot:run
```

L'application démarre sur `http://localhost:8080`

#### **Phase 3 : Créer le Premier Utilisateur (Admin Système)**

**⚠️ IMPORTANT** : C'est l'**UNIQUE** utilisateur créé manuellement en PostgreSQL !

Cet utilisateur est l'**administrateur système** :
- Il se connecte à l'application
- Il valide les demandes de création de compte
- Il attribue les droits de création d'application aux utilisateurs
- Il n'a **pas accès** aux applications ni aux données

Exécuter le script SQL suivant via pgAdmin ou psql :

```sql
-- ========================================
-- SCRIPT DE CRÉATION DU PREMIER UTILISATEUR
-- ========================================
-- SEUL utilisateur créé manuellement en PostgreSQL
-- Tous les autres (y compris créateurs d'applis) 
-- sont créés via l'application
-- ========================================

-- Étape 1 : Créer l'utilisateur admin dans la table OreSiUser
INSERT INTO OreSiUser (id, login, password, email, accountstate, authorizations) 
VALUES (
  '5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uuid,   -- ⚠️ À CHANGER: générer nouveau UUID
  'openadom',                                      -- Nom de connexion
  '$2a$12$4gAH34ZwgvgQNS0pbR5dGem1Nle0AT/.UwrZWfqtqMiJ0hXeYMvUG',  -- ⚠️ À CHANGER: hash bcrypt (mot de passe actuel: xxxx)
  'admin@example.com',                             -- ⚠️ À CHANGER: email réel de l'admin
  'active',                                        -- État du compte
  '{}'                                             -- Autorisations vides (admin de domaine)
);

-- Étape 2 : Créer le rôle PostgreSQL associé à cet utilisateur
-- Le nom du rôle DOIT correspondre à l'UUID de l'utilisateur
DROP ROLE IF EXISTS "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";  -- ⚠️ Utiliser le même UUID
CREATE ROLE "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";

-- Ajouter un commentaire pour identifier le rôle
COMMENT ON ROLE "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" IS 'openadom (system admin)';

-- Étape 3 : Assigner le rôle "openAdomAdmin" à cet utilisateur
-- Ce rôle donne les droits d'administration système
GRANT "openAdomAdmin" TO "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" WITH INHERIT TRUE;

-- Étape 4 : Lier le rôle utilisateur à l'utilisateur technique de l'application
-- IMPORTANT: Le nom "openAdomTechUser" doit correspondre à celui configuré dans .env (DB_USER)
GRANT "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" TO "openAdomTechUser" WITH INHERIT TRUE;

-- ========================================
-- VÉRIFICATION
-- ========================================
-- Vérifier que l'utilisateur a été créé :
SELECT id, login, email, accountstate FROM OreSiUser WHERE login = 'openadom';

-- Vérifier les rôles PostgreSQL :
\du "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9"
```

**Identifiants de connexion par défaut** (à changer en production) :
- Utilisateur : `openadom`
- Mot de passe : `xxxx`

**Générer un hash bcrypt pour un nouveau mot de passe** :
```bash
# Option 1 : Via htpasswd (Apache utils)
htpasswd -nbB user VotreNouveauMotDePasse | cut -d: -f2

# Option 2 : Via site web
# https://bcrypt-generator.com/ (sélectionner coût 12)

# Option 3 : Via Python
python3 -c "import bcrypt; print(bcrypt.hashpw(b'VotreMotDePasse', bcrypt.gensalt(12)).decode())"
```

#### **Phase 4 : Admin Crée des Utilisateurs et Attribue les Droits**

Une fois connecté à l'application avec les identifiants `openadom`, l'administrateur système peut :

**1. Inviter/Créer des utilisateurs** (via l'application ou auto-inscription)

**2. Attribuer les droits via l'interface** :

```
Administration → Utilisateurs → [Sélectionner utilisateur]
  → Ajouter droits d'application créator
  → Pattern(s) autorisé(s) : acbb*, monsore*, ago*, etc.
```

**Les utilisateurs créés dans l'application** :

| Rôle | Créé par | Méthode |
|------|----------|--------|
| **Admin Système** | Manuel SQL | Script Phase 3 (unique!) |
| **Créateur d'Application** | Admin Système | Interface app (Authorization Resources) |
| **Application Manager** | Créateur d'app | Auto-créé lors création app |
| **User Manager** | App Manager | Interface app |
| **Writer** | App Manager / User Manager | Interface app |
| **Reader** | App Manager / User Manager | Interface app |

**Exemple de flux complet** :

```
1. Admin "openadom" se connecte
   ↓
2. Alice crée son compte via l'app (self-register ou invitée)
   ↓
3. Admin attribue à Alice : pattern "acbb*"
   ↓
4. Alice se reconnecte → crée "acbblis" (APPLICATION_MANAGER auto)
   ↓
5. Alice invite Bob dans "acbblis"
   ↓
6. Alice attribue à Bob : USER_MANAGER
   ↓
7. Bob attribue à Charlie : READER
```

#### **Phase 5 : Installer les Dépendances du Frontend**

Se placer dans le dossier ui :
```bash
cd ui
npm ci
```

#### **Phase 6 : Démarrer le Frontend**

```bash
npm run serve
```

L'interface démarre sur `http://localhost:3000`

### Accéder à la base de données

---

## Modèle de Sécurité et Autorisation

### Architecture des Rôles

OpenADOM utilise un modèle d'autorisation hiérarchique basé sur les **domaines** :

```
┌─────────────────────────────────────────┐
│  Administrateur de Domaine              │
│  (Premier utilisateur)                  │
│  • Initialise les droits                │
│  • Délègue à des créateurs d'applis     │
│  • Pas accès aux applications           │
└──────────────┬──────────────────────────┘
               │ Crée
               ↓
┌─────────────────────────────────────────┐
│  Créateur d'Application (par domaine)   │
│  Domaine: ACBB, AGO, DEB, etc.          │
│  • Crée applications du domaine         │
│  • Administrateur de ses applications   │
│  • Propriétaire du schéma               │
│  • Peut déléguer des droits restreints  │
└──────────────┬──────────────────────────┘
               │ Crée
               ↓
┌─────────────────────────────────────────┐
│  Application                            │
│  (ex: ACBB.LIS, AGO.MYC)               │
│  Possède son propre schéma PostgreSQL  │
│  et des utilisateurs avec droits       │
│  restreints à ses données              │
└─────────────────────────────────────────┘
```

### Catégories d'Utilisateurs

#### **1. Utilisateurs Système**

Ces utilisateurs gèrent les droits de création d'applications et n'ont pas accès aux données.

##### **a. Admin Système (SYSTEM_OPENADOM_ADMIN)**
- **Créé manuellement** via script SQL (premier utilisateur)
- **Rôle PostgreSQL** : `openAdomAdmin`
- **Permissions** :
  - Déléguer les droits de création d'application (applicationCreator)
  - Gérer les utilisateurs système
  - **Aucun accès** aux applications ou données
- **Cas d'usage** : Initialisation du système, création d'admin de domaines

##### **b. Créateur d'Application (SYSTEM_APPLICATION_CREATOR)**
- **Créé par** : Admin Système
- **Autorisations JSON** : `{"acbb*": [], "ago*": [], ...}` (patterns de noms)
- **Permissions par pattern** :
  - Créer des applications respectant le pattern (ex: `acbb*` → `acbblis`, `acbbsol`)
  - Devient automatiquement APPLICATION_MANAGER de ses applications
  - Propriétaire du schéma PostgreSQL
  - Peut déléguer des droits restreints
- **Exemple** :
  ```json
  {
    "acbb*": [],     // Peut créer acbblis, acbbsol, etc.
    "monsore*": []   // Peut créer monsore2024, monsoredata, etc.
  }
  ```

#### **2. Utilisateurs d'Application**

Ces utilisateurs ont des droits sur des applications spécifiques.

##### **a. APPLICATION_MANAGER (Super Admin Application)**
- **Créé automatiquement** : Quand un utilisateur crée une application
- **Rôle PostgreSQL** : `{applicationId}_applicationManager`
- **Permissions** :
  - Modifier la configuration de l'application
  - Créer/supprimer des utilisateurs de l'application
  - Créer/modifier les autorisations
  - Gérer tous les droits de l'application
  - Propriétaire du schéma
- **Cas d'usage** : Fondateur de l'application, gestion complète

##### **b. USER_MANAGER (Gestionnaire d'Utilisateurs)**
- **Créé par** : APPLICATION_MANAGER
- **Rôle PostgreSQL** : `{applicationId}_userManager`
- **Permissions** :
  - Attribuer des autorisations reader/writer à d'autres utilisateurs
  - Gérer les droits d'accès aux données
  - Voir les utilisateurs de l'application
  - **Ne peut PAS** : modifier la configuration, supprimer l'application
- **Cas d'usage** : Délégation de la gestion des droits sans donner tous les pouvoirs

##### **c. WRITER (Écriture)**
- **Créé par** : APPLICATION_MANAGER ou USER_MANAGER
- **Rôle PostgreSQL** : `{applicationId}_writer`
- **Permissions** :
  - Modifier les données dont il a les droits (selon policies)
  - Uploader des fichiers
  - Publier des données (si autorisé)
  - Lire les données dont il a les droits
- **Limitations** : Droits restreints par les policies d'autorisation
- **Cas d'usage** : Saisie de données, contributeur

##### **d. READER (Lecture)**
- **Créé par** : APPLICATION_MANAGER ou USER_MANAGER
- **Rôle PostgreSQL** : `{applicationId}_reader`
- **Permissions** :
  - Lire les données dont il a les droits (selon policies)
  - Télécharger les fichiers autorisés
  - Consulter les exports
  - **Ne peut PAS** : modifier, uploader, publier
- **Limitations** : Droits restreints par les policies d'autorisation
- **Cas d'usage** : Consultation, analyse, extraction de données

### Pattern de Nom d'Application

**IMPORTANT** : Les noms d'application doivent respecter le pattern suivant :
```
[a-z][a-z_0-9]{1,39}
```

**Règles** :
- Entre **2 et 40 caractères**
- Commence par une **lettre minuscule**
- Contient uniquement : **lettres minuscules, chiffres, underscore (_)**
- ❌ **PAS de point (.)** ni de caractères spéciaux
- ❌ **PAS de majuscules**

**Exemples valides** :
- `acbb_lis`
- `ago_myc`
- `monsore2024`
- `debplatform`

**Exemples invalides** :
- `ACBB.LIS` ❌ (majuscules + point)
- `acbb.lis` ❌ (point interdit)
- `AcbbLis` ❌ (majuscules)
- `a` ❌ (trop court, minimum 2 caractères)

### Flux de Création d'Application

```
1. Admin Système crée "Alice" (applicationCreator avec pattern "acbb*")
   ↓
2. Alice se connecte → Crée "acbblis" (respecte pattern acbb*)
   ↓
3. Système :
   • Crée schéma "acbblis" dans PostgreSQL
   • Alice = APPLICATION_MANAGER (super admin)
   • Crée rôles : reader, writer, userManager
   ↓
4. Alice crée "Bob" comme USER_MANAGER pour acbblis
   ↓
5. Bob peut attribuer des droits reader/writer à d'autres utilisateurs
   ↓
6. Alice crée "Charlie" avec rôle reader
   ↓
7. Charlie se connecte → Accès en lecture à acbblis
   ↓
8. Charlie ne peut pas :
   • Créer une autre application
   • Accéder à acbbother
   • Modifier les données (seulement reader)
   • Modifier le schéma
```

### Domaines et Patterns

Les utilisateurs **applicationCreator** reçoivent des **patterns** qui définissent quels noms d'applications ils peuvent créer.

**Patterns supportés** :
- `acbb*` : Toute application commençant par `acbb` (ex: `acbblis`, `acbbsol`, `acbb2024`)
- `ago*` : Applications agronomie (ex: `agomyc`, `agodata`)
- `monsore*` : Applications MonSORE (ex: `monsore2024`, `monsoredata`)
- `deb*` : Applications DEB (ex: `debplatform`)
- `pattern*` : Applications de test avec pattern

**Format des autorisations** :
```json
{
  "acbb*": [],      // Peut créer acbbXXX
  "monsore*": []    // Peut créer monsoreXXX
}
```

**Exemples de créations valides** :

| Pattern | Applications autorisées | Applications refusées |
|---------|-------------------------|----------------------|
| `acbb*` | `acbblis`, `acbbsol`, `acbb2024` | `ago123`, `monsore`, `debxxx` |
| `ago*` | `agomyc`, `agodata` | `acbblis`, `agro` (ne commence pas par `ago`) |
| `monsore*` | `monsore2024`, `monsoretest` | `mon`, `monsore.data` (point interdit) |

**Rôles PostgreSQL correspondants** :
```sql
openAdomAdmin            -- Admin système
{userId}                 -- Rôle utilisateur (UUID)
{applicationId}_applicationManager
{applicationId}_userManager  
{applicationId}_writer
{applicationId}_reader
```

### Autorizations JSON

Les autorisations d'un utilisateur sont stockées en JSON :

```json
{
  "ACBB": ["CREATE_APPLICATION"],
  "AGO": ["CREATE_APPLICATION", "DELEGATE_RIGHTS"],
  "DEB": ["READ_ONLY"]
}
```

- **Clé** : Domaine (ACBB, AGO, etc.)
- **Valeur** : Liste des permissions pour ce domaine

### Accéder à la base de données

En ligne de commande :

```bash
psql -h localhost -U openAdomTechUser openadom
```

Via pgAdmin :

```
http://localhost:8083/
```

Pour s'authentifier sur PGAdmin, l'identifiant est `si-ore-developpement@list.forge.codelutin.com` et le mot de passe est `test`.

Une fois authentifié dans PGAdmin, on peut accéder à la base de données en renseignant le mot de passe `xxxxxxxx`

### Création d'un Utilisateur pour Tester

**🔑 FLUX DE CRÉATION D'UTILISATEUR** :

**Seul le premier utilisateur (admin système) est créé manuellement en PostgreSQL.**

Tous les autres utilisateurs sont créés dans l'application :

```
┌─────────────────────────────────┐
│ Admin Système                   │
│ (créé manuellement en SQL)      │
│ Se connecte à l'app             │
└────────────────┬────────────────┘
                 │
                 ↓
┌─────────────────────────────────┐
│ Admin invite/crée Alice dans    │
│ l'application                   │
│ (via Administration → Utilisateurs)
└────────────────┬────────────────┘
                 │
                 ↓
┌─────────────────────────────────┐
│ Admin attribue à Alice :        │
│ Pattern applicationCreator      │
│ {"acbb*": []}                   │
└────────────────┬────────────────┘
                 │
                 ↓
┌─────────────────────────────────┐
│ Alice se connecte et crée       │
│ "acbblis" (devient APPLICATION_ │
│ MANAGER automatiquement)        │
└─────────────────────────────────┘
```

**Identifiants de test fournis** :
- Admin système : `openadom` / `xxxx` (créé manuellement)
- Autres utilisateurs : [Voir first_roles.sql](src/main/resources/migration/first_roles.sql) (pour développement/tests)
---

## Architecture du Projet

### Vue d'Ensemble

Le backend OpenADOM suit une **architecture en couches** avec un pattern **Clean Architecture** utilisant des **Use Cases**. Cette approche garantit une séparation des préoccupations et une maintenabilité optimale.

```
┌─────────────────────────────────────────┐
│          REST Resources                 │
│  (OreSiResources, ApplicationResources) │
└──────────────────┬──────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────┐
│        Use Cases Layer (46 UC)          │
│  • application/   (7 use cases)         │
│  • data/          (10 use cases)        │
│  • security/      (5 use cases)         │
│  • storage/       (13 use cases)        │
│  • metadata/      (6 use cases)         │
│  • email/         (1 use case)          │
└──────────────────┬──────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────┐
│        Domain Services Layer            │
│  • ApplicationService                   │
│  • DataService                          │
│  • AuthenticationService                │
│  • EmailService                         │
│  • Autres services métier               │
└──────────────────┬──────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────┐
│      Domain Layer (Entités métier)      │
│  • Application, ApplicationInformation  │
│  • OreSiUser, Authorization             │
│  • DataFile, DataValue                  │
│  • Autres domaines métier               │
└──────────────────┬──────────────────────┘
                   │
                   ↓
┌─────────────────────────────────────────┐
│   Persistence Layer (Repositories)      │
│  • ApplicationRepository                │
│  • UserRepository                       │
│  • DataRepository                       │
│  • PostgreSQL via Spring Data           │
└─────────────────────────────────────────┘
```

### Structure des Packages

#### **1. REST Resources** (`rest/`)

Entrée HTTP de l'application :

```
OreSiResources.java           ← Point d'entrée principal (46 use cases)
ApplicationResources.java     ← Gestion des applications
AuthorizationResources.java   ← Gestion des autorisations
NormalizationResources.java   ← Normalisation
... et autres resources
```

#### **2. Use Cases** (`rest/usecases/`)

**6 domaines métier, 46 use cases :**

- **application/** : GetApplicationsUseCase, CreateApplicationUseCase, ValidateConfigurationUseCase, ChangeApplicationConfigurationUseCase, etc.
- **data/** : FindDataUseCase, FilterListUseCase, DeleteDataUseCase, BuildDataZipUseCase, SendZipLinkByMailUseCase, etc.
- **security/authentication/** : GetCurrentUserUseCase
- **security/authorization/** : GetAllUsersUseCase, GetAuthorizationScopesUseCase, etc.
- **storage/** : GetFileUseCase, RemoveFileUseCase, CreateOrUpdateAdditionalFileUseCase, GetStoreFileUseCase, etc.
- **metadata/** : BuildSynthesisUseCase, BuildNormalizedSchemaUseCase, CreateOrUpdateRightsRequestUseCase, etc.
- **email/** : SendUploadErrorsMailUseCase

**Pattern standard :**

```java
@Component
@RequiredArgsConstructor
public class GetApplicationsUseCase {
    private final ApplicationService applicationService;
    
    public Flux<ReactiveResult> execute(List<ApplicationInformation> filters) {
        return applicationService.getApplications(filters);
    }
}
```

#### **3. Services Domain** (`rest/services/`)

Logique métier réutilisable :
- `ApplicationService`
- `DataService`
- `AuthenticationService`
- `AuthorizationService`
- `EmailService`
- ... etc

#### **4. Domain** (`domain/`)

Entités métier pures :
- `Application`, `ApplicationInformation`
- `OreSiUser`, `Authorization`
- `DataFile`, `DataValue`
- `BinaryFile`, etc.

#### **5. Persistence** (`persistence/`)

Repositories et accès BD :
- `ApplicationRepository`
- `UserRepository`
- `DataRepository`
- ... etc

---

### Paramètres et Configuration

#### **1. Configuration Principale** : `src/main/resources/application.yml`

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/openadom
    username: openAdomTechUser
    password: xxxxxxxx
  jpa:
    hibernate.ddl-auto: validate
  mail:
    host: smtp.example.com
    port: 587
logging:
  level:
    root: INFO
    fr.inra.oresing: DEBUG
```

#### **2. Configuration Test** : `src/test/resources/application-testmail.yml`

Configuration spécifique aux tests.

#### **3. Migrations BD** : `src/main/resources/migration/`

- `V1__initial_schema.sql` - Schéma initial
- `V2__create_roles.sql` - Rôles PostgreSQL
- `openadom_user.sql` - Utilisateur technique
- `first_roles.sql` - Rôles d'application

**Outil** : Flyway (migrations automatiques)

#### **4. Monitoring** : `src/main/resources/prometheus.properties`

Métriques exposées à `/actuator/prometheus`

---

## Monitoring et Métriques

### Architecture de Monitoring

Le backend OpenADOM implémente un système de monitoring complet avec **Micrometer** et **Prometheus** :

```
┌─────────────────────────────────┐
│    Application (Spring Boot)    │
│   • Endpoints REST              │
│   • Use Cases                   │
│   • Services métier             │
└────────────────┬────────────────┘
                 │
                 ↓
         ┌───────────────┐
         │  Micrometer   │    ← Framework de métriques
         │  Registry     │       Collecte tous les compteurs
         └───────┬───────┘
                 │
                 ↓
      ┌──────────────────────┐
      │   Prometheus Export  │    ← Format standard
      │  /actuator/prometheus│    Exposé en HTTP
      └──────────┬───────────┘
                 │
         ┌───────┴──────────┐
         ↓                  ↓
    ┌─────────┐       ┌──────────┐
    │Prometheus│      │ Grafana  │
    │ Server  │       │ Dashboard│
    │ (opt.)  │       │ (opt.)   │
    └─────────┘       └──────────┘
```

### Endpoints Actuator Disponibles

```bash
# GET /actuator
# Lister tous les endpoints

# GET /actuator/health
# État de santé : {status, components}

# GET /actuator/prometheus
# Métriques format Prometheus (scape-able)

# GET /actuator/metrics
# Lister toutes les métriques disponibles

# GET /actuator/metrics/{nom}
# Détails d'une métrique spécifique

# GET /actuator/info
# Informations app (version, build, git)

# GET /actuator/loggers
# État des loggers

# GET /actuator/configprops
# Configuration actuelle

# GET /actuator/env
# Variables d'environnement

# GET /actuator/flyway
# Migrations BD appliquées

# GET /actuator/beans
# Beans Spring disponibles
```

### Métriques Collectées Automatiquement

#### **1. HTTP Requests**
```
http.server.requests           # Tous les appels HTTP
  - count                      # Nombre de requêtes
  - duration (ms)              # Temps de réponse
  - duration.max               # Max observé
  Tags: method, status, uri_template
```

**Exemple:**
```bash
curl http://localhost:8080/actuator/metrics/http.server.requests
```

#### **2. Async Task Execution**
```
async.errors                   # Erreurs dans les threads async
  - count                      # Nombre d'erreurs

executor.rejections            # Tasks rejetées (queue pleine)
  - count                      # Nombre de rejets

executor.queue.size            # Taille queue thread pool
  - value                      # Nombre items en attente
```

#### **3. Métrics Tomcat**
```
tomcat.sessions.created        # Sessions créées
tomcat.sessions.active         # Sessions actives
tomcat.threads.current         # Threads actuels
tomcat.threads.config.max      # Threads configurés max
http.server.requests.active    # Requêtes actives
```

#### **4. JVM/Système**
```
jvm.memory.used                # Mémoire JVM utilisée
jvm.memory.max                 # Mémoire JVM max
jvm.gc.memory.allocated        # Allocations GC
process.cpu.usage              # CPU process
process.uptime                 # Temps depuis démarrage
```

#### **5. Base de Données**
```
spring.data.repository.*       # Métriques repositories
  - count                      # Nombre d'appels
  - duration                   # Temps d'exécution
```

### Custom Metrics avec @Timed

Les endpoints REST sont automatiquement instrumentés avec `@Timed` :

```java
@GetMapping("/applications")
@Timed(value = "applications.get", description = "Get applications")
public Flux<Application> getApplications() {
    // ...
}
```

Génère une métrique : `applications.get` avec statut HTTP et durée.

### Custom Tags pour Observations

**OreSiWebMvcTagsContributor** ajoute des tags contextuels :

```
app_name       ← Nom de l'application (depuis URL)
data_type      ← Type de données (depuis URL)
```

Exemple de requête avec tags :
```bash
/api/applications/myapp/data/mydata
# Tags: app_name="myapp", data_type="mydata"
```

### Configuration Actuator (`application.yml`)

```yaml
management:
  endpoints:
    web:
      exposure:
        # Endpoints exposés en HTTP
        include: "health,info,prometheus,metrics,flyway,beans,logfile,loggers,env,configprops"
  
  endpoint:
    health:
      show-components: always
      show-details: always
  
  health:
    mail:
      enabled: true              # Vérifie SMTP
    db:
      enabled: true              # Vérifie BD
    ping:
      enabled: true              # Ping simple
    diskspace:
      enabled: true              # Espace disque
  
  metrics:
    tags:
      application: oresing-backend  # Tag global sur toutes les métriques
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.75,0.95,0.99
  
  prometheus:
    metrics:
      export:
        enabled: true             # Export format Prometheus
```

### Utiliser les Métriques en Local

#### **1. Vérifier les métriques en direct**
```bash
curl http://localhost:8080/actuator/metrics/http.server.requests | jq
```

#### **2. Exporter pour Prometheus (format texte)**
```bash
curl http://localhost:8080/actuator/prometheus | head -50
```

#### **3. Via Docker Compose**

Si Prometheus et Grafana sont lancés :
```bash
docker-compose up -d prometheus grafana
# Prometheus : http://localhost:9090
# Grafana : http://localhost:3000
```

#### **4. Query Prometheus**
```
# Nombre total de requêtes
http_server_requests_seconds_count

# Requests par endpoint
http_server_requests_seconds_count{uri_template="/api/applications"}

# Requests par statut
http_server_requests_seconds_count{status="200"}

# p95 latency
histogram_quantile(0.95, http_server_requests_seconds_bucket)
```

#### **5. Grafana Dashboards**

Fichiers pré-configurés :
- [grafana/provisioning/dashboard/pem-dashboard.json](grafana/provisioning/dashboard/pem-dashboard.json)

Contient des panels pour :
- Requests rate (req/sec)
- Latency (p50, p95, p99)
- Error rate
- Thread pool usage
- JVM memory
- Database connections

### Async Task Monitoring

Les pools d'exécution asynchrone (chunker, worker, merger, loader) exposent des métriques :

```java
// Dans AsyncExecutorConfiguration.java
Metrics.counter("async.errors", 
    "threadPoolName", "worker",
    "status", "task_failed")
    .increment();

Metrics.counter("executor.rejections").increment();
```

Monitorer :
```bash
curl http://localhost:8080/actuator/metrics/async.errors
curl http://localhost:8080/actuator/metrics/executor.rejections
```

---

### Comment Déboguer

#### **1. Ajouter un Breakpoint**
- Clic dans la marge gauche d'un fichier Java
- F9 pour reprendre l'exécution

#### **2. Activer les Logs DEBUG**
```yaml
logging.level.fr.inra.oresing: DEBUG
logging.level.org.springframework.web: DEBUG
```

#### **3. Inspecter les Requêtes HTTP**
```yaml
logging.level:
  org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping: TRACE
```

#### **4. Accéder à la Base de Données**
```bash
psql -h localhost -U openAdomTechUser openadom
# ou via pgAdmin : http://localhost:8083/
```

#### **5. Consulter les Métriques**
```bash
curl http://localhost:8080/actuator/prometheus
```

---

### Comment Ajouter une Nouvelle Fonctionnalité

**Exemple : Ajouter une action "Dupliquer une application"**

**1. Créer le Use Case**
```
src/main/java/fr/inra/oresing/rest/usecases/application/
  DuplicateApplicationUseCase.java
```

**2. Créer le Test**
```
src/test/java/fr/inra/oresing/rest/usecases/application/
  DuplicateApplicationUseCaseTest.java
```

**3. Implémenter dans le Service**
```
src/main/java/fr/inra/oresing/rest/services/
  ApplicationService.java
  // Ajouter : public UUID duplicateApplication(UUID id)
```

**4. Exposer dans la Resource REST**
```
src/main/java/fr/inra/oresing/rest/
  ApplicationResources.java ou OreSiResources.java
  // @PostMapping("/applications/{id}/duplicate")
```

**5. Tester**
```bash
mvn clean compile
mvn test -Puse-cases
curl -X POST http://localhost:8080/api/applications/{id}/duplicate
```

---

### Outils et Commandes

```bash
# Compilation
mvn clean compile

# Tests
mvn test                                    # Tous
mvn test -Puse-cases                        # Seulement use cases
mvn test -Dtest=GetApplicationsUseCaseTest  # Test spécifique

# Lancer l'app
mvn spring-boot:run

# Vérifier la qualité
mvn clean verify

# Générer types TypeScript
mvn clean compile
```

---

### Points d'Entrée Clés

| **Classe** | **Rôle** |
|-----------|---------|
| `OreSiResources` | Endpoint principal REST |
| `ApplicationService` | Logique métier applications |
| `GetApplicationsUseCase` | Use case récupération apps |
| `Application` | Entité métier |
| `ApplicationRepository` | Accès DB |
| `ExceptionMessage` | Gestion des erreurs |

---

### Checklist Nouvelle Fonctionnalité

- [ ] Créer le **Use Case** dans `usecases/{domaine}/`
- [ ] Ajouter tag `@Tag("use-cases")`
- [ ] Créer le **Test** correspondant
- [ ] Implémenter le **Service** si nécessaire
- [ ] Créer l'**entité Domain** si nécessaire
- [ ] Créer le **Repository** si accès BD
- [ ] Exposer dans **Resource REST**
- [ ] Tester : `mvn test -Puse-cases`
- [ ] Compiler : `mvn clean compile`
- [ ] Valider : `mvn clean verify`

