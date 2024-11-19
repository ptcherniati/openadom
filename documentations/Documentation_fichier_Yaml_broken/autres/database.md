## Description de la base de données

L'application openAdom s'appuie sur une base de données. On passe les identifiants de connexion à la base de données en réglant les variables d'environneemnt:

```properties
DB_HOST_PORT=localhost:5432
DB_DATABASE=openadom
DB_USER=openAdomTechUser
DB_PASSWORD=z2I<i}qclq)D?xqT
```

L'utillisateur de la base de données peut être le créateur de la base de données. Mais il est préférable d'utiliser un utilisateur technique en éxécutant le script "migration/openadom_user.sql "migration/openadom_user.sql".

Nous appelerons le role technique "openAdomTechUser" pas la suite. 

C'est le rôle utilisé par l'application.
- pour créer des roles et des objets dans la base de données.
- pour attribuer des roles (GRANT) à des utilisateurs et des roles
- pour poser des droits et policies sur ces objets.
- pour exécuter des requêtes sql avec différent rôles (set role..)

### Démarrage de l'application et initialisation de la base de données

#### Premier démarrage
Lorsque l'on lance l'application pour la première fois, le schema public est initialisé.

Deux tables sont créés :
- **application** pour gérer les configurations des SI créés dans l'application
- **oresiuser** pour gérer les utilisateurs de la base de données et les signature des chartes des différents SI.

quatre roles sont créés :
- **openAdomAdmin** le role administrateur qui permet d'autoriser des utilisateurs à créer des Si avec un pattern de nom définit. Il peut déléguer ce rôle.
- **applicationCreator** le role attribué à des créateurs d'application. Il autorise les utilisateurs à créer une/ des applications dont le nom correspond à l'un des patterns d'autorization qui lui a été attribué par un "openAdomAdmin".
- **anonymous** : ce rôle vient remplacer un utilisateur dont le compte a été supprimé. Il n'a aucun droit.
- **public** : ce rôle porte les droits en lecture des données des différents SI. Tout utilisateur d'openadom est placé dans cce rôle.
̀̀̀

```
❗ 
 Un **applicationCreator** ne peut pas accéder aux application, ni les modifier. S'il créé une application il deviendra administrateur de cette application. S'il ne l'a pas créer, ces droits sur le SI dependront des administrateurs de cette application.
```

Après le démarrage, il convient de créer les utilisateurs qui pourront permettre la création du SI.


```sql
-- mot de passe `xxxx`
INSERT INTO OreSiUser (id, login, password, email, accountstate,  authorizations) values ('5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9'::uuid, 'poussin','$2a$12$4gAH34ZwgvgQNS0pbR5dGem1Nle0AT/.UwrZWfqtqMiJ0hXeYMvUG', 'poussin@inrae.fr', 'active','{}');
DROP ROLE IF EXISTS "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
CREATE ROLE "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9";
GRANT "openAdomAdmin" TO "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" WITH INHERIT TRUE;

/*
 et si on veut qu'il puisse créer des applications
 */
GRANT "applicationCreator" TO "5a4dbd41-3fc9-4b3e-b593-a46bc888a7f9" WITH INHERIT TRUE;
UPDATE  OreSiUser set authorizations='{.*}'

```

### Création des SI

Lorsqu'un **applicationCreator** créé un SI, un schema avec le nom du SI est créé dans la base de données.

Les rôles suivants sont aussi créés : (exemple pour l'application avec l'uuid "application avec l'uuid "87716b08-8da6-43e0-a787-102af09b6dfc")
- 87716b08-8da6-43e0-a787-102af09b6dfc_applicationManager : c'est l'administrateur du SI il est le propriétaire du schema et de ses objets. Le créateur du Si est placé dans ce rôle.
- 87716b08-8da6-43e0-a787-102af09b6dfc_userManager : il attribue les droits aux utilsateurs du SI.
- 87716b08-8da6-43e0-a787-102af09b6dfc_writer : il a accès aux différentes tables en écriture. Cependant un utilisateur placé dans ce rôle devra ausssi acquérir les policies neccéssaires.
- 87716b08-8da6-43e0-a787-102af09b6dfc_reader : il a accès aux différentes tables en lecture. Cependant un utilisateur placé dans ce rôle devra ausssi acquérir les policies neccéssaires.



``` mermaid
graph LR
classDef user fill:#E6F3FF,stroke:#4D94FF,stroke-width:2px;
classDef openAdomTechUser fill:#FFE6E6,stroke:#FF4D4D,stroke-width:2px;
classDef adminGroup fill:#FFD700,stroke:#B8860B,stroke-width:2px;
classDef appGroup fill:#98FB98,stroke:#228B22,stroke-width:2px;

User((Utilisateur)):::user
openAdomTechUser((Utilisateur technique)):::openAdomTechUser
openAdomAdmin((Administrateur de openAdom)):::adminGroup
applicationCreator((Créateur de SI)):::adminGroup
applicationManager((Administrateur d'un SI)):::appGroup
userManager((Administrateur des utilisateurs d'un SI)):::appGroup
writer((Droits en écriture sur le SI)):::appGroup
reader((Droits en lecture sur le SI)):::appGroup

openAdomAdmin -->|est un| applicationCreator
applicationManager -->|est un| userManager
userManager -->|est un| writer
writer -->|est un| reader

User -->|peut être| openAdomAdmin
openAdomAdmin -->|créé| openAdomAdmin
openAdomAdmin -->|créé avec pattern| applicationCreator
applicationCreator -->|créé Si et devient| applicationManager
applicationManager -->|créé| applicationManager
applicationManager -->|créé| userManager
applicationManager -->|créé avec policies| writer
applicationManager -->|créé avec policies| reader

User -->|peut être| applicationCreator
User -->|peut être| applicationManager
User -->|peut être| userManager
User -->|peut être| writer
User -->|peut être| reader

openAdomTechUser -->|exécute en tant que| user

```