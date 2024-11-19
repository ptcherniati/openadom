# Lien vers un référentiel

Pour lier un référentiel à un autre on utilise un checker OA_reference. C'est la clef naturelle générée qui est utilisée pour faire ce lien (OA_naturalKey).

Voici une base pour lancer la discussion

## Cas simple
Pour un composant donné de type OA_basicComponents ou  OA_patternComponents, la valeur entrée dans la colonne correspond à la clef naturelle déclarée dans le oA_data référencé.
-> la valeur stockée est la clef naturelle (ltree)
-> on stocke aussi la  clef technique de type UUID dans refsLinkedTo

A l'affichage on propose le diplay de la ligne référencée.
A l'extraction on renvoie la clef naturelle de la ligne référencée (pour pouvoir faire le lien avec la ligne référencée dans le fichier de référence)

## La valeur permet de générer la clef en la codifiant
Il n'y a plus de section transformation ou codify

Je propose à la place de regarder la valeur en entrée
-> si elle est déjà codifiée -> voir cas simple
-> sinon on la codifie

En sortie affichage et export, on se retrouve dans le cas simple. Il n'y a aucune raison de stocker la valeur en entrée, puisqu'elle n'est jamais utilisée.

## La valeur peut être calculée comme une clef composite de plusieurs colonnes.
A noter que la valeur contenue dans la colonne est forcement de type clef naturelle. Il ne peut s'agir d'une information partielle non discriminante.

-> on peut procéder comme dans la V1 avec un OA_computedComponents qui portera le checker.
-> on peut faire cela avec une nouvelle fonctionalitée en idiquant la liste des colonnes composant cette clef.

Dans le deuxième cas on ne peut pas avoir cela dans un OA_basicComponent ou un OA_patternComponent car on cible 
une seule colonne et que l'information est contenue par plusieurs colonnes.

On ne pourra pas non plus mettre cela dans une validation. La validation n'a pas de component porteur et on ne saura donc
à quel composant affecter la clef naturelle récupérée.

Il n'est pas non plus souhaitable d'indiquer à ce niveau le nom du composant. Pour des questions de lisibilité, 
il est préférable d'avoir tous les noms des composants au même niveau.

On pourrait considerer un OA_computedComponents avec  une alternative de déclaration
```yaml
OA_computedComponents:
  siteref:
    OA_computation:
      OA_expression: >
        return datum.type_de_site + "__" + datum.site
    OA_checker:
      OA_name: OA_reference
      OA_params:
        OA_reference:
          OA_name: site


```
pourrait être remplacé par 
```yaml
OA_computedComponents:
  siteref: 
    OA_computation:
      OA_naturalKey: [type_de_site,site] # la clef sera générée en s'appuyant sur les colonnes type_de_site et site
    OA_checker: 
        OA_name: OA_reference
        OA_params:
            OA_reference:
              OA_name: site
```

En ce cas on fournira au compilateur la même expression que celle fournie par l'expression datum.type_de_site + "__" + datum.site.
Les règles de codification s'appliquent comme dans le cas simple ainsi que les valeurs en sortie.


## La valeur est une clef primaire secondaire
ex

site.csv
```csv
typeDeSite;site;code
parcelle;1;5f21
```
data.csv
```csv
codeDeSite
5f21
```
Cette fois nous avons un component porteur de l'information de référence. 
Nous pouvons donc appliquer la transformation dans un OA_basicComponents ou un OA_basicPattern. Par example de la façon suivante.
```yaml
OA_basicComponents:
  codeDeSite: 
    OA_checker: 
        OA_name: OA_reference
        OA_params:
            OA_reference:
              OA_name: site
              OA_primaryKeyColumn: code
```
    On peut ajouter une clef d'unicité automatiquement pour le champ code de site
    On peut faire la comparaison sur les valeurs codifiées de code et de codeDeSite.

En sortie comme dans le cas simple, la valeur en entrée est ignorée. On peut la mettre au besoin dans le display.

## Conserver la valeur en entrée.
Je ne vois aucune raison de le faire. 
-> la valeur en entrée n'est jamais utilisée (on renvoie le display ou la clef naturelle)
-> Du fait qu'elle permet de construire une clef naturelle c'est que l'on a déjà toutes les informations das la table référencée pour afficher cette valeur dans le display.
-> Cela n'a aucun sens :
Léman
LeMaN
léman
le man
l em an 
Permettent tous de générer la clef primaire leman. Alors quelle valeur est la bonne?

