# Lexique

Vous trouverez ici une liste des thermes immuable se trouvant dans le yaml avec une définition et peut être un exemple.

Ces thermes seront classés par catégorie : 

- description de l'application
- les références
- les clés hiérarchique des références
- les types de données

## OpenADOM 
__version :__ nous l'utilisons pour la version de OpenADOM (actuellement, c'est la version 1).

## L'application
__application :__ dans cette partie nous décrirons l'application.

- __version :__ Nous changeon la version de l'application avant d'importer les yaml sur OpenAdom.

- __defaultLanguage :__ On peut définir ici une langue d'affichage par défaul en utilisant les abréviations du nom de la langue. 
(ex : français -> fr, anglais -> en)

- __internationalizationName :__ Le nom traduit qui s'affichera en fonction de la langue.

  - en
  - fr

- __name :__ Le nom de l'application qui sera enregisté en base de donée.

## Les références
__references :__ Un ensemble d'informations permettant de préciser le contexte de la mesure ou de l'observation. 

En déportant ces informations dans des fichiers __references__, on évite la répétition d'informations. On utilisera la clef d'une information pour y faire référence.

- __internationalizationName :__ Affichage du nom de la référence en fonction de la langue

  - en
  - fr
- __internationalizedColumns:__
- __internationalizationDisplay:__

  - __pattern:__ Le _pattern_ pour une expression régulière.
- __keyColumns :__ On y liste les noms des colonnes servant de clé.
- __columns :__ Chaque _columns_ est une colonne se trouvant dans le fichier de référence. Par concéquent elle doit avoir le même nom que dans le fichier.

### Les colonnes calculés
__computedColumns :__ Une _computedColumns_ est une colonne qui n'est pas présente dans le fichier et dont la valeur est une constante ou le résultat d'un calcul.

- __computation:__ section de calcule de la donnée

- __expression :__ une expression groovy (pour le checker GroovyExpression doit renvoyer true si la valeur est valide)

- __references:__ liste des références utile pour l'expression groovy
### Les colonnes dinamique
__dynamicColumns :__ Une _dynamicColumns_ est un ensemble de colonnes dont la clef est la concaténation d'un préfixe et d'une valeur d'un référentiel. Par exemple s'il existe un référentiel "propriétés" avec les valeurs (couleur, catégorie, obligatoire), on pourrait avoir dans un autre référentiel (en utilisant le préfixe "pts_") pts_couleur, pts_catégorie et pts_obligatoire, en les déclarant comme _dynamicColumns_.

- __internationalizationName :__ Affichage du nom de la colonne souhaité en fonction de la langue

  - en
  - fr
- __headerPrefix :__ on définit la chaine de caratère servant à identifier les colonnes dinamique se trouvant dans le fichier
- __reference:__ nom de la référence listant les colonnes dinamiques attendue
- __referenceColumnToLookForHeader:__ nom de la colonne listant les colonnes dinamiques attendue 

### Les vérifications (checkers)
__checker:__ c'est la section où on réalise les vérifications des données.

- __name:__ il y a plusieurs vérifications possibles :

  - vérifier la nature d'un champ (float, integer, date)  ( Integer, Float, Date)
  - vérifier une expression régulière ( RegularExpression)
  - ajouter un lien avec un référentiel (Reference)
  - vérifier un script (le script renvoyant true) ( GroovyExpression)

- Contenu de la section __params__:

| name           | References | Integer | Float | Date | GroovyExpression | RegularExpression | *                                                                      |
|----------------|-----------|--------|------|------|------------------|-------------------|------------------------------------------------------------------------|
| refType        | X         |        |      |      |                  |                   | Le référentiels de jointure                                            |
| pattern        |           |        |      |      |                  | X                 | Le pattern pour une expression régulière                               |
| transformation | X         | X      | X    | X    | X                | X                 | La définition d'une transformation à faire avant de vérifier la valeur |
| required       | X         | X      | X    | X   | X    | X                | La valeur ne peut être nulle (true)                                    |
| multiplicity   | X         |        |      |      |      |                  | La colonne contient un tableau de référence (true)                     |
| groovy         |           |        |       |     | X    |                  | La définition d'une expression groovy                                  |
| duration       |           |        |      | X |         |                  | Pour une date la durée de cette date                                   |

> :information_source: Une durée est définie au sens SQL d'un [interval](https://www.postgresql.org/docs/current/functions-datetime.html#OPERATORS-DATETIME-TABLE) ('1 HOUR', '2 WEEKS', '30 MINUTES').

On peut rajouter une section __transformations__ pour modifier la valeur avant sa vérification :

Cette __transformation__ peut être configurée avec
- __codify :__ la valeur sera alors échappée pour être transformée en clé naturelle (Ciel orangée -> ciel_orange)
- __groovy :__ permet de déclarer une transformation de la valeur avec une expression Groovy (qui doit retourner une chaîne de caractère)


La section groovy accepte trois paramètres
- __expression :__ une expression groovy (pour le checker GroovyExpression doit renvoyer true si la valeur est valide)
- __references :__ une liste de référentiels pour lesquels on veut disposer des valeurs dans l'expression
- __datatypes :__ une liste de datatypes pour lesquels on veut disposer des valeurs dans l'expression

> :alert: La différence entre une section groovy de la section params d'un checker __groovy__ et une section groovy de la section transformation de la section params, tient dans le fait que pour un checker groovy l'expression renvoyée est un booléen tandis que dans la transformation l'expression groovy renvoie une nouvelle valeur.


## Les clés hiérarchiques des références
__compositeReferences :__ permet d'ordonnée l'affichage des références, de la mise en place qui clé hiérarchique entre des références ou entre 2 colonnes d'un fichier (la colonne clé et une autre)

- __components:__

  - __reference:__ Le nom du référentiel de jointure

  - __parentRecursiveKey:__ nom de la colonne parent se trouvant dans le même référentiel que la colonne enfant.

  - __parentKeyColumn:__ nom de la colonne parent se trouvant dans le référentiel enfant.

## Les types de données
__dataTypes :__
- __internationalizationName :__ Affichage du nom du type de donnée en fonction de la langue

  - en
  - fr

- __repository :__ Permet la gestion du dépot des fichiers par période et authorisation (__authorization__)

  - __filePattern :__ Permet de définir un paterne pour remplir les champs du formulaire automatiquement. Chaque "(.*)" correspond à un __token__ ou un __authorizationScope__, qu'on ordonne
  - __authorizationScope :__ Permet de définir qu'elle "(.*)" correspond à quelle __authorizationScopes__

    - __startDate :__ Section de la date de début
    - __endDate :__ Section de la date de fin 

      - __token :__ Permet de définir qu'elle "(.*)" 

- __data :__ description de l'organisation de l'enregistrement des données dans la base de donnée.

  - __components :__ 
  - __computedComponents :__

    - __computation :__
- __authorization :__ il permet de définir des groupes de variables. 

  - __dataGroups :__ Une ligne du fichier est découpée en autant de ligne que de *dataGroups*.

    - __data :__ Liste des noms des __variable__ définit dans __data__
    - __label :__ nom du groupe de donnée
  - __authorizationScopes :__ On définit des composantes de portée. Il s'agit là de définir un ensemble de composantes que l'on pourra sélectionner dans un arbre, pour limiter la portée de l'autorisation. Pour que l'interface puisse proposer des choix de portée, il est nécessaire que toutes les composantes citées dans authorizationScope soient liées à un référentiel avec une section checker de type References.
  - __timeScope :__ On définit des composantes de temps. Elle définira la portée temporelle de la ligne. Cette composante doit nécessairement être liée à un checker de type Date.

    - __variable :__ nom d'un _data_
    - __component :__ nom d'un _component_ dans un _data_
- __uniqueness :__ C'est là qu'on définit une contrainte d'unicité, en listant la liste des _variable components_ qui composent la clef.Si un fichier possède des lignes en doublon avec lui-même il sera rejeté.

- __format :__ description du format du fichier

  - __constants :__ description de l'en-tête du fichier s'il y en a une

    - __rowNumber :__ numéro de la ligne d'en-tête
    - __columnNumber :__ numéro de la colonne d'en-tête
    - __exportHeader :__
  - __headerLine :__ numéro de la ligne des nom des colonnes
  - __firstRowLine :__ munéro de la première ligne de données du tableau
  - __columns :__ Liste des colonne du fichier CSV de donnée

    - __header :__ "nom de la colonne se trouvant dans le fichier"
    - __boundTo :__ c'est là que l'ont fait le lien entre une colonne du fichier et son _data_ via des _variable components_.