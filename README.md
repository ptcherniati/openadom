# Projet de la version V2 de l'application des ORE.

Le projet est constitué de 2 sous projet :

- La partie serveur qui fournit les web services de l'application

- La partie UI qui fournit une interface VueJS permettant d'interroger ces Web Services.

## Objectifs

- Utilisation de java >=10
- Suppression de la couche ORM
- Utilisation de web services
- Accès aux ressources par une interface indépendante par exemple VueJS, mais aussi des applications comme R-Shiny ou en attaquant directement la base de données.
- Simplification du ticket d'accès technique pour les développeurs (interface ou services)


## Environnement de développement

### Prérequis

  - JDK ≥ 11
  - maven 3
  - Docker
  - nodejs 12

Pour constuire le projet avec maven, l'utilisateur doit avoir le droit de démarrer de conteneurs docker.

Sous Linux, cela consiste à ajoute l'utilisateur au groupe `docker`

### Vérifier la qualité du projet

```bash
mvn test
```

### Démarrer l'interface en local

D'abord, il convient de démarrer la base de données

```bash
docker-compose up
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
psql -h localhost -U dbuser ore-si
```

Via pgAdmin :

```
http://localhost:8083/
```

Pour s'authetifier sur PGAdmin, l'identifiant est `si-ore-developpement@list.forge.codelutin.com` et le mot de passe est `test`.

Une fois authentifié dans PGAdmin, on peut accéder à la base de données en renseignant le mot de passe `xxxxxxxx`

### Création d'un utilisateur

Afin d'essayer l'application, il faut pouvoir se connecter. Il faut pour cela créer un utilisateur


```sql
-- mot de passe `xxxx`
INSERT INTO OreSiUser (id, login, password, authorizations) values ('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uuid, 'poussin', '$2a$12$4gAH34ZwgvgQNS0pbR5dGem1Nle0AT/.UwrZWfqtqMiJ0hXeYMvUG','{}');
DROP ROLE IF EXISTS "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
CREATE ROLE "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
GRANT "superadmin" TO "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";

```

et si on veut qu'il puisse créer des applications
```postgresql
GRANT "applicationCreator" TO "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
UPDATE  OreSiUser set authorizations='{.*}'

```