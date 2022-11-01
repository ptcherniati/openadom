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
  - faire en sorte que la suppression de données de référence ne soit pas possible si elles sont utilisées
  
Fait
====

  - Le schéma de persistence
  - Les services Rest
    - création/restitution d'une application
    - création/restitution de données de référenciel
    - création/restitution de data 
  - modification du user pour l'execution des requetes SQL
  - création de role pour chaque donnée de référenciel
 
 
Sécurité
========

Un `superadmin` qui a tous les droits, il peut créer des utilisateurs et modifier
les droits utilisateurs.

Les utilisateurs peuvent avoir le droit de créer ou non de nouvelles applications.

Chaque application a 5 roles associé:

  - admin: peut tout faire, y compris ajouter des droits pour cette application à d'autres utilisateurs
  - writer: peut créer des référenciels ou des données
  - data_writer: peut créer des données
  - reader: peut lire toutes les données
  - restricted_reader: peut lire seulement certaines données, en fonction des référenciels

Le droit d'admin est donné par défaut au créateur de l'application.
