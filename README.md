ore-si-ng
=========

POC d'implantation sur une base de l'utilisation de Postgresql sans
Hibernate.

Reste à faire
=============
- les roles par defaut d'admin
- création des roles par application lors de sa création
- l'interface de gestion des utilisateur
  - création d'utilisateur
  - modification des droits d'utilisateurs
- le service Rest de stockage de fichier annexe
- l'interface de persistence de fichier annexe
- lors du parsing des fichiers CSV reporter la ligne de l'erreur
- sortir en flux les exports (pas de representation string intermédiaire)
- compresser les fichiers en base

Fait
====
- Le schéma de persistence
- Les services Rest
  - création/restitution d'une application
  - création/restitution de données de référenciel
  - création/restitution de data 
- modification du user pour l'execution des requetes SQL
- création de role pour chaque donnée de référenciel
 