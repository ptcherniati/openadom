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

  - JDK ≥ 25
  - maven 3
  - Docker
  - postgresql 17.5

Pour constuire le projet avec maven, l'utilisateur doit avoir le droit de démarrer de conteneurs docker.

Sous Linux, cela consiste à ajoute l'utilisateur au groupe `docker`

### Vérifier la qualité du projet

```bash
mvn test
```

### Démarrer l'interface en local

D'abord, il convient de démarrer la base de données. La base de données sera créée avec un role dbuser propriétaire de la base de données.

Pour des raisons de sécurité, il convient de créer un role technique "openAdomTechUser". En exécutant le script "src/main/resources/migration/openadom_user.sql"

Le docker-compose mettra à jour la base de données créée en applicant ce script.

```bash
 docker-compose up --build --force-recreate -d
```

Ensuite, on démarre le backend

```bash
mvn spring-boot:run
```

Si cela n'a pas déjà été fait, installer les dépendances du frontend

on se place dans le dossier ui
```bash
cd ui
```
puis on récupère les sources
```bash
npm ci
```

Enfin, on démarre le frontend

```bash
npm run serve
```

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

### Création d'un utilisateur

Afin d'essayer l'application, il faut pouvoir se connecter. Il faut pour cela créer un utilisateur


```sql
-- mot de passe `xxxx`
-- openadom ne peut pas créer d'application à moins de se donner ce droit
INSERT INTO OreSiUser (id, login, password, email, accountstate,  authorizations) values ('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uuid, 'openadom','$2a$12$4gAH34ZwgvgQNS0pbR5dGem1Nle0AT/.UwrZWfqtqMiJ0hXeYMvUG', 'poussin@inrae.fr', 'active','{}');
DROP ROLE IF EXISTS "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
CREATE ROLE "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
COMMENT ON ROLE "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" IS 'openadom';
GRANT "openAdomAdmin" TO "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" WITH INHERIT TRUE;
GRANT "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" TO "openAdomTechUser" WITH INHERIT TRUE;

```
[Comptes utilisateurs d'openadom](src/main/resources/migration/first_roles.sql)