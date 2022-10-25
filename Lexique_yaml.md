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

__version :__ Nous changeon la version de l'application avant d'importer les yaml sur OpenAdom.

__defaultLanguage :__ On peut définir ici une langue d'affichage par défaul en utilisant les abréviations du nom de la langue. 
(ex : français -> fr, anglais -> en)

__internationalizationName :__ 

__name :__

## Les références
__references :__ Un ensemble d'informations permettant de préciser le contexte de la mesure ou de l'observation. 

En déportant ces informations dans des fichiers __references__, on évite la répétition d'informations. On utilisera la clef d'une information pour y faire référence.
__internationalizationName:__

__internationalizedColumns:__

__internationalizationDisplay:__

__pattern:__ Le _pattern_ pour une expression régulière.

__keyColumns :__ 

__columns :__ Chaque _columns_ est une colonne se trouvant dans le fichier de référence. Par concéquent elle doit avoir le même nom que dans le fichier.

### Les colonnes calculés
__computedColumns :__ Une _computedColumns_ est une colonne qui n'est pas présente dans le fichier et dont la valeur est une constante ou le résultat d'un calcul.

__computation:__

__expression:__

__references:__
### Les colonnes dinamique
__dynamicColumns :__ Une _dynamicColumns_ est un ensemble de colonnes dont la clef est la concaténation d'un préfixe et d'une valeur d'un référentiel. Par exemple s'il existe un référentiel "propriétés" avec les valeurs (couleur, catégorie, obligatoire), on pourrait avoir dans un autre référentiel (en utilisant le préfixe "pts_") pts_couleur, pts_catégorie et pts_obligatoire, en les déclarant comme _dynamicColumns_.

__headerPrefix:__

__reference:__

__referenceColumnToLookForHeader:__

### Les checkers
__checker:__ c'est la section où on réalise les vérifications des données.

__name:__ il y a plusieurs vérifications possibles :
- vérifier la nature d'un champ (float, integer, date)  ( Integer, Float, Date)
- vérifier une expression régulière ( RegularExpression)
- ajouter un lien avec un référentiel (Reference)
- vérifier un script (le script renvoyant true) ( GroovyExpression)

Contenu de la section __params__:

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

On peut rajouter une section __transformations__ pour modifier la valeur avant sa vérification :

Cette <a id="transformation" />transformation peut être configurée avec
- codify : la valeur sera alors échappée pour être transformée en clé naturelle (Ciel orangée -> ciel_orange)
- groovy : permet de déclarer une transformation de la valeur avec une expression Groovy (qui doit retourner une chaîne de caractère)


La section groovy accepte trois paramètres
- expression : une expression groovy (pour le checker GroovyExpression doit renvoyer true si la valeur est valide)
- references : une liste de référentiels pour lesquels on veut disposer des valeurs dans l'expression
- datatypes : une liste de datatypes pour lesquels on veut disposer des valeurs dans l'expression

> :alert: La différence entre une section groovy de la section params d'un checker __groovy__ et une section groovy de la section transformation de la section params, tient dans le fait que pour un checker groovy l'expression renvoyée est un booléen tandis que dans la transformation l'expression groovy renvoie une nouvelle valeur.


## les clés hiérarchique des références
__compositeReferences:__

__components:__

__reference:__ Le référentiels de jointure

__parentRecursiveKey:__

__parentKeyColumn:__ 