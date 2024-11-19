---
title: Documentation fichiers de configuration pour OpenADOM
subtitle: Documentation décrivant la structure du fichier de configuration de l'application OpenADOM
author:
  - TCHERNIATINSKY Philippe
  - VARLOTEAUX Lucile
date: `date +%D`

lang: fr-FR
numbersections: true
documentclass: scrreprt
output:
  pdf_document: 
    latex_engine: pdflatex
toc: true
toc-depth: 6
toc-title: "Table des matières"
fontsize: 12pt
mainfont: TeX Gyre Pagella
mainfontoptions:
- Numbers=Lowercase
- Numbers=Proportional
linestretch: 1
linkcolor: blue
colorlinks: true
urlstyle: sf
links-as-notes: true
link-citations: true
pagenumberinf: true
hyperrefoptions:
  - linktoc=all
  - pdfwindowui
hyphenation:
  csv: ;
usepackege: 
  hyphenate: true
---

# Introduction

Ce document permet d'aider un gestionnaire de SI à décrire son domaine dans un fichier de configuration, qui une fois déposé dans l'application, génèrera une base de données et les outils permettant de l'alimenter et de la consulter.

Chaque fichier de configuration déposé génèrera un schéma dédié dans la base de données.

## <a id="prealable" />Préalable

Avant de commencer l'écriture du fichier de configuration, il faut travailler à définir le modèle des données que vous voulez traiter dans la base de données.

Vous avez en votre possession un certain nombre de fichiers (format csv) contenant les données. Un fichier de données respecte un certain format. En particulier les en-têtes de colonnes doivent être fixés et le contenu sous un en-tête a un format déterminé (date, valeur flottante, entier, texte..).

Chaque format de fichier correspond à ce que l'on appellera un type de données. Il regroupe plusieurs variables correspondant à :

- une thématique,
- un pas de temps,
- une structuration des données
- ...

Chaque ligne peut être identifiée par sous-ensemble de colonnes. Cet identifiant permet de créer ou de mettre à jour une donnée, selon qu'elle est ou non déjà présente en base.

Chaque ligne porte, sur une ou plusieurs colonnes, une information de temporalité.

Chaque ligne porte aussi, sur une ou plusieurs colonnes, des informations sur le contexte d'acquisition des variables des autres colonnes.

On peut vouloir aussi faire figurer dans la base de données certaines informations non présentes dans le fichier de données.

- des informations liées aux variables que l'on fournit sous la forme de fichier de référentiels (description de site, description de méthodes, description d'unités, description d'outils...)
- des informations constantes, ne dépendant pas du fichier (par exemple l'unité de la variable)
- des informations constantes pour l'ensemble du fichier (par exemple le site correspondant aux valeurs du fichier). Ces informations pouvant être décrites dans un cartouche, avant l'en-tête de colonne ou juste sous l'en-tête de colonne (valeur minimum ou maximum)
- des informations calculées à partir d'informations du fichier, d'informations des référentiels déjà déposés ou même des données déjà publiées.

## exemple

Supposons que l'on ait un fichier de données météorologiques



```csv
  Région;Val de Loire;;;
  Période;06/2004;;;
  Date de mesure:Site;Précipitation;Température moyenne;Température minimale;Température maximale
  01/06/2004;Os1;30;20;10;24
  07/06/2004;Os1;2;22;14;27
  07/06/2004;Os2;0;21;9;28
```

- La temporalité est portée par la colonne "Date de mesure".
- Le contexte est porté par l'information du cartouche d'en-tête "Région" et la colonne "Site".
- On identifie 4 variables:
    - _date_ au format dd/MM/yyyy (format au sens SQL : https://www.postgresql.org/docs/current/functions-formatting.html#FUNCTIONS-FORMATTING-DATETIME-TABLE). Cette variable n'a qu'une seule composante "day". On note que les moyennes sont calculées à la journée.
    - _localization_ qui fait référence à un site de la colonne "Site", avec deux composantes (site et region)
    - _precipitation_ qui correspond à la pluviométrie de la colonne "Précipitation" avec deux composantes (value,unit=mm)
    - _temperature_ qui se réfère aux colonnes "Température moyenne", "Température minimale" et "Température maximale" avec 4 composantes (value,min,max,unit=°C)

Du coup, on peut aussi définir des référentiels pour préciser ses informations

__region.csv__
```csv
code ISO 3166-2;nom
FR-ARA	Auvergne-Rhône-Alpes
FR-BFC	Bourgogne-Franche-Comté
FR-BRE	Bretagne
FR-CVL	Centre-Val de Loire
FR-COR	Corse
FR-GES	Grand Est
FR-HDF	Hauts-de-France
FR-IDF	Île-de-France
FR-NOR	Normandie
FR-NAQ	Nouvelle-Aquitaine
FR-OCC	Occitanie
FR-PDL	Pays de la Loire
FR-PAC	Provence-Alpes-Côte d'Azur
```

__site.csv__
```csv
nom:Date de création;region
Os1;01/01/2000;FR-CVL
Os2;01/01/2000;FR-CVL
```
Les sites font référence aux régions.

__unite.csv__
```csv
nom;nom_fr;nom_en;code
temperature;Température;Temperature;°C
precipitation;Précipitation;Precipitation;mm
```
Le fait de dire que l'unité d'une donnée fait référence au référentiel unite signifie :

- que l'unité doit être présente dans ce référentiel,
- que l'on ne pourra pas supprimer une unité du référentiel si on y a fait référence.

On aurait pu rajouter des responsables de site et de région, des descriptions des variables, des intervalles de valeurs...

Ainsi nous avons pu faire une analyse de notre domaine et le format des fichiers qui s'y rapportent. Nous pouvons commencer l'écriture du fichier de configuration.

## Vocabulaire

###  <a id="code" />Clefs et code

Dans un fichier, on définit une ou plusieurs colonnes qui correspondent à la clef d'idendification de la ligne. 
Cette clef naturelle permet lors d'une insertion / suppression de retrouver cette ligne dans la base de données et, 
si elle est présente, de la mettre à jour. Dans le cas contraire, une nouvelle ligne est créée.

#### code

Pour enregistrer ces clefs dans la base de données, et pour éviter les erreurs, les clefs sont codées. 
Le code utilisé n'autorise que les chiffres, les lettres minuscules et majuscules ainsi que le caractère souligné (underscore).

Cependant, pour permettre une plus grande souplesse, les accents sont supprimés, les majuscules sont remplacées par 
les minuscules, les espace et les tirets (-) sont remplacés par des _ et les autres caractères sont remplacés par leur 
nom ascii en majuscules.

- L'année de départ → lAPOSTROPHEannee_de_depart
- µmol m-2 s-1 → MICROSIGNmol_m2_s1
- m²/m² → mSUPERSCRIPTTWOSOLIDUSmSUPERSCRIPTTWO
- °C → DEGREESIGNc

Ainsi les valeurs Elévation, élévation, elevation ou même EléVaTioN renvoient toutes le même code.

Ces transformations sont faites de manière transparente.

> :information_source: Quand on fait référence à un référentiel, que cela soit pour un type de données ou pour un autre référentiel, on utilise la clef naturelle de ce référentiel. Cependant, il sera possible de demander la mise en code de la valeur avant de rechercher son existence dans le référentiel de référence.

#### <a id="naturalKey" />Clef naturelle.

Elle est construite en concaténant les valeurs des différentes colonnes composant la clef. Le signe de concaténation est le double underscore '__'.

- Forme géométrique de la colonie + prisme → forme_geometrique_de_la_colonie__prisme
- Ensoleillement + Ensoleillé → ensoleillement__ensoleille
- Piégeage en montée + Couleur des individus → piegeage_en_montee__couleur_des_individus

#### <a id="hierarchicalKey" />Clef hiérarchique

Elle est construite en concaténant les clefs naturelles de différents référentiels. Le signe de concaténation de la clef hiérarchique est le point '.'

Ainsi si on a une parcelle "1", dans le site "Site 1" du type de site "Site d'étude" :

| référentiel  | Nom                                           | Clef naturelle                                                                                                                      | Clef hiérarchique                                                                                                                                                                      |
|--------------|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Type de site | <span style="color:blue"> Site étude</span> | <span style="color:blue"> site_etude</span>                                                                              | <span style="color:blue">site_etude</span>                                                                                                                                  |
| Site         | <span style="color:green">Site 1</span>       | <span style="color:blue">site_etude</span>__ <span style="color:green">site_1</span>                                     | <span style="color:blue">site_etude</span>.<span style="color:blue">site_etude</span> __<span style="color:green">site_1</span>                                  |
| Parcelle     | <span style="color:red">1</span>              | <span style="color:blue"> site_etude</span>__  <span style="color:green">site_1</span>__<span style="color:red">1</span> | <span style="color:blue">site_etude</span>.<span style="color:blue">site_etude</span> __<span style="color:green">site_1</span>.<span style="color:red">1</span> |



### <a id="referentiels" />Référentiels

__references__: Un ensemble d'informations permettant de préciser le contexte de la mesure ou de l'observation.

En déportant ces informations dans des fichiers __references__, on évite la répétition d'informations. On utilisera la clef d'une information pour y faire référence.

### <a id="datatypes" />Types de données

__data__ : un ensemble de données correspondant à une thématique et un format de fichier commun.

__variable__ : correspond à un ensemble de données, qualifiant ou se rapportant à une variable de mesure, d'observation, d'informations, de temporalité ou de contexte.

__component__ : un ensemble de valeur qui servent à décrire une variable (valeur, écart type, nombre de mesures; indice de qualité; méthode d'obtention...)

__authorizationScope__ : une ou des informations contextuelles (variable-component) qui ont du sens pour limiter les autorisations.

__timeScope__ :  l'information de temporalité d'une ligne ayant du sens pour limiter des authorisations à une période.

__dataGroups__ : un découpage, sous forme de partitionnement de variables, en un ensemble de groupes de variables (__dataGroups__), pour limiter les droits à la totalité ou à des sous ensembles de variables.

On pourrait dans notre exemple distinguer 3 __dataGroups__:

- informations(date et localization)
- precipitation(precipitation)
- temperature (temperature)

Mais on peut aussi faire le choix d'un seul groupe

- all(date,localization,precipitation,temperature)

Ou de 4 groupes en découpant informations en date et localization

### <a id="identificateur" />Identificateurs

Lorsque l'on doit déclarer un ensemble de descriptions dans une section, on leur attribue un identificateur unique. 

Par exemple pour déclarer les descriptions des référentiels, des types de données, des colonnes… 
Comme ces identificateurs sont repris comme nom de table ou de champs dans la base de données, ils doivent respecter certaines 
règles imposées par PostgreSQL. 

> Les identificateurs SQL doivent commencer avec une lettre minuscule, Les caractères suivants dans un identificateur peuvent être des lettres, des tirets bas ou des chiffres (0-9).
> 
> De plus leur longueur ne peut dépasser 63 caractères. Il faut prendre en compte que lors de la création des vues, 
> les identificateurs peuvent être combinés entre eux ou préfixés / suffixés. Il est donc préférable d'utiliser des noms courts.
> 
> Vous serez informés lors du dépôt du fichier d'identificateurs incorrects.

Il est conseillé d'utiliser une [convention de nommage](https://sqlpro.developpez.com/cours/standards/) pour choisir un identificateur.

- pour un [référentiel](#references) le préfixe tr pour table référentiel, le nom du référentiel et un trigramme unique pour ce référentiel : tr_villes_vil
- pour une [donnée](#data) le préfixe t pour table fonctionnelle, le nom du type de données et un trigramme unique : t_meteo_met
- pour les [colonnes](#columns), on préfixe avec le trigramme de la table d'origine suivit du nom explicite de la colonne :
  - vil_nom
  - met_temperature
  
L'utilisation de convention de nommage rendra la description de votre fichier de configuraion plus lisible, 
ainsi que la compréhension des vues et tables générées automatiquement.

Ces identificateurs seront aussi les clefs pour les valeurs dans le champ JSON (refValues pour les référentiels; 
dataValues pour les données ; les variables et les composantes)
