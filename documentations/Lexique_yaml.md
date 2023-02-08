---
title: Lexique pour le fichier de configuration OpenADOM
subtitle: Inventaire des mots clefs du fichier de configuration OpenADOM
author:
- VARLOTEAUX Lucile
- TCHERNIATINSKY Philippe

date: 10/10/2022

lang: fr
numbersections: true
documentclass: scrreprt
toc: true
toc-depth: 6
toc-title: "Table des matières"
fontsize: 12pt
linestretch: 1
linkcolor: black

---

# Lexique

Vous trouverez ici une liste des termes immuables, se trouvant dans le _fichier de configuration_, accompagnés d'une définition et d'exemple.

## OpenADOM 
__version :__ nous l'utilisons pour la version de `OpenADOM` (actuellement, c'est la version 1).

## L'application
__application :__ dans cette partie nous décrirons l'application.

- __version :__ Nous changeons la version de l'application avant d'importer les _fichiers de configuration_ sur `OpenAdom`.

- __defaultLanguage :__ On peut définir ici une langue d'affichage par défaut en utilisant les [abréviations](https://www.npmjs.com/package/i18n-locales) du nom de la langue. 
(ex : français -> fr, anglais -> en)

- __name :__ Le nom de l'application qui sera enregistré en base de données.

- __internationalizationName__" : L'affichage du  nom (__name__) en fonction de la langue : (cf.[internationalisationName](#internationalisationname))
  ```
  ```
  
## Les étiquettes (_tags_)
__tags__: Création d'un regroupements sous une étiquette permettant de filtré l'affichages des listes des [__references__](#les-rfrentiels-_references_) et des [__datatypes__](#les-types-de-donnes-_datatypes_).
Mais aussi les [colonnes](#les-colonnes-du-fichier), les [colonnes calculées](#les-colonnes-calculées), les [colones dynamiques](#les-colonnes-dynamiques) d'une [__reference__](#les-rfrentiels-_references_) et les [data](#data), les [components](#components) et les [computedComponents](#computedcomponents) d'un [__datatype__](#les-types-de-donnes-_datatypes_).

L'étiquette ```__hidden__``` est une étiquette qui n'a pas besoin d'êtres mise dans la liste de création. Nous l'utiliserons pour les données que l'on veux enregistrer en base mais que l'on ne veux pas rendre accessible à l'utilisateur.

``` yaml
tags:
  # le nom du tag
  localization: 
    # la traduction du tag
    fr: Localisation
    en: Localization
```

## Les référentiels (_references_)
__references :__ Un ensemble d'informations permettant de préciser le contexte de la mesure ou de l'observation. 

En déportant ces informations dans des fichiers __references__, on évite la répétition d'informations. On utilisera la clef d'une information pour y faire référence.

- __internationalizationName :__ (cf.[internationalisationName](#internationalisationname))
- __internationalizedColumns :__ permet de relier des colonnes contenant une information et ses traductions

- __internationalizationDisplay :__ permet de définir pour chaque langue comment le référentiel s'affiche dans l'interface.
  - __pattern:__ L'expression qui génère la chaîne d'affichage.
  ```yaml
  pattern:
    en: "the name: {colonneMonNom}"
    fr: "le nom: {colonneMonNom}"
    ...:
  ```
  
- __keyColumns :__ Un tableau des noms des colonnes faisant partie de la clef primaire.
- __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur la référence pour filtrer l'affichage de la liste des références
- __columns :__ (cf.  [Les colonnes du fichier](#les-colonnes-du-fichier))
- __computedColumns :__ (cf. [Les colonnes calculées](#les-colonnes-calculées))
- __dynamicColumns :__ (cf. [Les colonnes dynamiques](#les-colonnes-dynamiques))
 
### Les colonnes du fichier

  __columns :__ Chaque _columns_ est une colonne se trouvant dans le fichier du référentiel. Par conséquent, elle doit avoir le même nom que dans le fichier.
  - __checker__ permet de valider une colonne ou préciser son format (cf [checker](#checker))
  
  - __defaultValue__ : la valeur par défaut si aucune valeur n'est fournie. (cf. [groovy](#groovy))

  - __presenceConstraint__ : (cf.[presenceConstraint](#presence-constraint))

  - __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur la colonne pour filtré l'affichage du tableau


### Les colonnes calculées
__computedColumns :__ Une _computedColumns_ est une colonne qui n'est pas présente dans le fichier et dont la valeur est une constante ou le résultat d'un calcul.
  - __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur la colonne calculée pour filtrer l'affichage du tableau
  - __checker__ permet de valider une colonne ou préciser son format (cf [checker](#checker))
  - __computation:__ section de calcul de la donnée 
  - __presenceConstraint__ : (cf.[presenceConstraint](#presence-constraint))~~

### Les colonnes dynamiques
__dynamicColumns :__ Une _dynamicColumns_ est un ensemble de colonnes dont le nom est la concaténation d'un préfixe et d'une valeur dans un référentiel. 
  
  Par exemple s'il existe un référentiel "propriétés" avec les valeurs (couleur, catégorie, obligatoire), on pourrait avoir dans un autre référentiel (en utilisant le préfixe "pts_") pts_couleur, pts_catégorie et pts_obligatoire, en les déclarant comme _dynamicColumns_.

- __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur la colonne dynamique pour filtrer l'affichage du tableau
- __headerPrefix :__ on définit la chaine de caractères servant à identifier les colonnes dynamique se trouvant dans le fichier
- __internationalizationName :__ (cf.[internationalisationName](#internationalisationname))
- __presenceConstraint__ :(cf.[presenceConstraint](#presence-constraint))
- __reference:__ nom de la référence listant les colonnes dynamiques attendues
- __referenceColumnToLookForHeader :__ nom de la colonne listant les noms de colonnes dynamiques attendues

## Définition de référentiels hiérarchiques
__compositeReferences :__ Une référence composite est créée en indiquant un lien parent-enfant entre un ou plusieurs référentiels ou une récursion sur un référentiel. Cela permet de générer une clef hiérarchique qui sera utilisée pour afficher hiérarchiquement ces référentiels (par exemple pour les [authorizations](#authorization) ou pour le dépôt sur un [repository](#repository)).

- __internationalizationName :__ (cf.[internationalisationName](#internationalisationname))
  - __internationalizationName :__ (cf.[internationalisationName](#internationalisationname))
  - __reference:__ Le nom du référentiel de jointure

  - __parentRecursiveKey :__ nom de la colonne parent se trouvant dans le même référentiel que la colonne enfant (dans le cas d'un référentiel qui fait référence à lui-même).

  - __parentKeyColumn :__ nom de la colonne parent se trouvant dans le référentiel enfant.

## Les types de données (_dataTypes_)
__dataTypes :__

- __internationalizationName :__ (cf.[internationalisationName](#internationalisationname))
- __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur le types de donnée pour filtrer l'affichage de la liste des dataTypes.
- __data :__ description de l'organisation de l'enregistrement des données dans la base de données sous la forme de composantes de variables.
  On utilise comme identifiant le nom de la variable. (cf. [data](#data))
- __format :__ description du format du fichier (cf. [format](#format))
- __uniqueness :__ C'est là qu'on définit une contrainte d'unicité, en listant la liste des _variable components_ qui composent la clef. Si un fichier possède des lignes en doublon avec lui-même il sera rejeté.
- __validations__: Cette section permet de rajouter des validations sur les données. On donnera un nom à chacune des validations en l'utilisant comme clef. 
  - __checker__: (cf. [checker](#checker))
  - __internationalizationName__ (cf. [internationalizationName](#internationalisationname))
- __authorization :__ Cette section permet de définir les informations sur lesquelles on posera les droits. Elle est aussi utilisée pour le dépôt sur un [repository](#repository) (cf. [authorization](#authorization))
- __repository :__ Permet la gestion du dépôt des fichiers par période et contexte en se basant sur les informations de la section  ([authorization](#authorization))

### data
  Les données du fichier sont enregistrées dans des [components](#components) et sont liées soit à des informations des colonnes, soit à des informations de l'en-tête. On peut aussi générer des données calculées ([computedComponents](#computedcomponents))

- __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur le data pour filtrer l'affichage de le tableau du dataType.
- __components :__ On utilise comme clef le nom de la composante
  - __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur le components pour filtrer l'affichage de le tableau du dataType.
- __computedComponents :__  On utilise comme clef le nom de la composante calculée
  - __tags__ : Un tableau des noms des étiquettes (déclaré dans la partie cf. [Les étiquettes](#les-tiquettes-_tags_))souhaité sur le computedComponents pour filtrer l'affichage de le tableau du dataType.
- __chartDescription__: permet de définir les données utilisées pour générer des graphes
  - __value__: nom de la composante portant la valeur
  - __unit__: nom de la composante portant l'unité
  - __standardDeviation__: nom de la composante portant l'écart type
  - __gap__: durée à partir de laquelle on considère qu'il y a un saut de données
  - __aggregation__: composante de variable utiliser pour aggréger les données
    - __variable__: nom de la variable
    - __composante__: nom de la composante

#### Components
  - __checker__ (cf. [checker](#checker))
  - __defaultValue__ (cf. [groovy](#groovy))

#### ComputedComponents
  - __checker__ (cf. [checker](#checker))
  - __computation :__ (cf. [computation](#computation))comme clef.
  - __checker__: (cf. [checker](#checker))
  - __internationalizationName :__ (cf.[internationalisationName](#internationalisationname))

### format

- __constants :__ définition des informations présentes dans l'en-tête du fichier

  - __rowNumber :__ numéro de la ligne
  - __columnNumber :__ numéro de la colonne
  - __exportHeader :__ Un nom de colonne fictif (pour les expressions groovy)
  - __headerName __: Pour les informations présentes sous la ligne d'en-tête le nom de la colonne (à la place du numéro de la colonne) ; par exemple pour une ligne d'unité ou de contraintes.
  - __boundTo__: <a id="boundTo" name="boundTo"></a>permet de relier l'information à une composante de variable.
- __headerLine :__ numéro de la ligne des noms des colonnes
- __firstRowLine :__ numéro de la première ligne de données du tableau
- __columns :__ Liste des colonnes du fichier CSV de données
  - __header :__ "nom de la colonne se trouvant dans le fichier"
  - __boundTo :__ (cf. [boundTo](#boundTo)).
- __repeteadColumns __: Définit des colonnes dont le nom répond à un pattern donné contenant des informations.
  - __boundTo :__ (cf. [boundTo](#boundTo)).
  - __exportHeader :__ Un nom de colonne fictif (pour les expressions groovy)
  - __headerPattern __: une expression régulière dont les groupes sont liés à des composantes de variables dans la section tokens
    - __tokens__: un tableau de token dans l'ordre de leur groupe dans l'expression régulière. La valeur du groupe sera utilisée comme valeur de la colonne fictive.
        - __boundTo :__ (cf. [boundTo](#boundTo)).
        - __exportHeader :__ Un nom de colonne fictif (pour les expressions groovy)

### Authorization
- __dataGroups :__ Un regroupement des variables (projection) pour leur donner des droits différents. Une ligne du fichier est découpée en autant de lignes qu'il y a de *dataGroups*.

  - __data :__ Liste des noms des `variables` définis dans `data`
  - __label :__ nom du groupe de données
    
- __authorizationScopes :__ On définit des composantes de contexte. Elles doivent être liées à des référentiels composites (checker Reference) ce qui permettra de les sélectionner dans un arbre pour limiter la portée de l'autorisation. On donnera un nom à chaque `authorizationScopes en l'utilisant comme clef.
  - __variable :__ nom d'un _data_
  - __component :__ nom d'un _component_ dans un _data_
  - __internationalizationName: (cf.[internationalisationName](#internationalisationname)) 
  
- __timeScope :__ On définit des composantes de temporelles (checker Date) pour limiter les autorisations à des intervales de dates. Si les valeurs correspondent à une agrégation temporelle, il faudra préciser la durée de la période de aggregation dans le champ duration du checker (par défaut 1 jour).
- __columnsDescription __: décrit comment dans le panneau des autorisations, chacun des rôles (depot, suppression publication..), doit être affiché. 
  - __display__: visibilité de la 
  - __title__: le nom du rôle
  - __internationalizationName__: (cf.[internationalisationName](#internationalisationname))
  - __withDatagroups __: true si le rôle peut ne porter que sur certaines variables
  - __withPeriods __: true si le rôle peut être limité à certaines périodes.
Par défaut, la configuration suivante sera appliquée pour afficher les autorisations. Vous devrez la redéfinir entièrement pour lui apporter des changements : 
  
```yaml
---
admin:
  display: true
  title: admin
  withPeriods: false
  withDataGroups: false
  internationalizationName:
    en: Administration
    fr: Administration
delete:
  display: true
  title: delete
  withPeriods: false
  withDataGroups: false
  internationalizationName:
    en: Deletion
    fr: Suppression
depot:
  display: true
  title: depot
  withPeriods: false
  withDataGroups: false
  internationalizationName:
    en: Deposit
    fr: Dépôt
extraction:
  display: true
  title: extraction
  withPeriods: true
  withDataGroups: true
  internationalizationName:
    en: Extraction
    fr: Extraction
publication:
  display: true
  title: publication
  withPeriods: false
  withDataGroups: false
  internationalizationName:
    en: Publication
    fr: Publication
```

### Repository

  - __filePattern :__ Permet de définir une expression régulière pour remplir les champs du formulaire automatiquement. Chaque "groupe de l'expression correspond à un __token__ ou un __authorizationScope__, qu'on ordonne
  - __authorizationScope :__ Permet de définir quel groupe correspond à quel __authorizationScopes__ (la clef est le nom de l'__authorizationScopes__)

  - __startDate :__ Section de la date de début
    - __token : numéro du groupe ()
  - __endDate :__ Section de la date de fin
    - __token : numéro du groupe ()

## Sections génériques
### InternationalisationName

Pour internationaliser les noms et clefs, on utilise la section **internationalizationName**

```yaml
internationalizationName:
  en: expression en anglais
  fr: expression en français
  ...: 
```

### Groovy
La section <a id="groovy" name="groovy"></a>groovy accepte trois paramètres

- __expression :__ une expression groovy (pour le checker GroovyExpression doit renvoyer true si la valeur est valide)
- __references :__ une liste de référentiels pour lesquels on veut disposer des valeurs dans l'expression
- __datatypes :__ une liste de datatypes pour lesquels on veut disposer des valeurs dans l'expression


  La différence entre une section groovy de la section params d'un checker __groovy__ et une section groovy de la section transformation de la section params, tient dans le fait que pour un checker groovy l'expression renvoyée est un booléen tandis que dans la transformation l'expression groovy renvoie une nouvelle valeur.
 
Les sections **defaultValue** sont des expressions groovy ; par example :
 - 42
 - "quarante-deux"
 - true
 - 9.8*datum.masse
 - datum.date.day+" "+datum.date.time"

### Checker 
__checker:__ c'est la section où on réalise les validations des données et leur typage. 

- __name:__ il y a plusieurs vérifications possibles :

  - vérifier la nature type d'un champ (float, integer, date)  ( Integer, Float, Date)
  - vérifier que la valeur vérifie une expression régulière ( RegularExpression)
  - ajouter un lien avec un référentiel (Reference)
  - vérifier un script
    - le script renvoie true ( groovy.expression)
    - le script renvoie une valeur (`defaultValue`, `computation.expression`, `transformation.groovy`)
- __params:__ section permettant de passer des paramètres au validateur

#### Contenu de la section __params__ :

- __multiplicity__ : La colonne est une valeur (**ONE**), ou un ensemble de valeurs séparées par une virgule (**MANY** ex: 42,36  chat,chien)
- __duration__: pour un checker **Date**, utilisé comme information temporelle (**timeScope**), on peut préciser la durée de la mesure.
> Une durée est définie au sens SQL d'un [interval](https://www.postgresql.org/docs/current/functions-datetime.html#OPERATORS-DATETIME-TABLE) ('1 HOUR', '2 WEEKS', '30 MINUTES').
- __groovy__: Pour un checker **GroovyExpression**. (cf. [groovy](#groovy)) pour vérifier la valeur avec un script groovy.
- __pattern__: Pour un checker Date ou **RegularExpression** : soit une expression régulière, soit une expression de format de date.
- __reftype__: Pour un checker **Reference** : le nom d'un référentiel
- __required__: true si la valeur doit être fournie
- __transformation__

On peut rajouter une section __transformation__ pour modifier la valeur avant sa vérification :

Cette __transformation__ peut être configurée avec
- __codify :__ la valeur sera alors échappée pour être transformée en clé naturelle (Ciel orangé -> ciel_orange)
- __groovy :__ permet de déclarer une transformation de la valeur avec une expression Groovy (qui doit retourner une chaîne de caractère) (cf. [groovy](#groovy))

  - __validations__: cette section permet de rajouter des validations sur plusieurs colonnes en même temps. On donnera un nom à chacune des validations en l'utilisant comme clef de la validation.
    - __internationalizationName :__ (cf.[internationalisationName](#internationalisationname))
    - __checker__: (cf. [checker](#checker))
    - __columns__: un tableau de colonnes sur lesquelles porte la validation.

#### Computation
- __computation:__ section permettant la modification de la données avant sa vérification
  - __groovy__ (cf. [groovy](#groovy))
  - __references__: un tableau des référentiels à ajouter au contexte de calcul
  - __datatypes__: un tableau des types de données à ajouter au contexte de calcul

#### Presence constraint
- __presenceConstraint__ :Pour dire si la colonne doit être présente dans le fichier (**MANDATORY**), ou si on peut l'omettre (**OPTIONAL**)