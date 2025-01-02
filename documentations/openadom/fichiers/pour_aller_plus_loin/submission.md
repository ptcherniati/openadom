#### <a id="submission" />Stratégies de dépôt (OA_submission)

La section **OA_submission** permet de définir comment les fichiers de données seront déposées. 

Il existe 2 Stratégies de dépôt:
- Une Stratégie **OA_INSERTION** les données sont insérées(insert) ou viendront remplacer (update) les données existantes correspondant à une même clef naturelle (**OA_naturalKey**)
- Une Stratégie **OA_VERSIONING** par versionnement, où l'ensemble des données d'un fichier est entendue comme une "version" d'un "jeux de données" . Si une version d'un jeu de données est déjà déposée, les données de cette version sont supprimées avant d'insérer les nouvelles valeurs.
Un "jeu de données" est défini par des éléments de contexte et/ou temporels (ex: type de données + projet + site + date de début + date de fin).
Une "version" est un fichier identifié pour un jeu de données défini.

A ce stade, on a choisi que le versionnement n'est disponible que pour des sections [**OA_data**](#data) marquées par le tag `__DATA__` -> un fichier yaml qui a un référetiel (tag __REFERENCE__ ou pas de tag __DATA__) et pour lequel une stratégie de versionnement est déclarée, est invalide. Le rejet du yaml s'accompagne d'un message d'erreur contextuel. Ceci afin d'éviter une incohérence dans le yaml pour ceux qui le construisent.

Les sections [**OA_data**](#data) non marquées avec `__DATA__` ou sans section **OA_submission** seront traitées avec une Stratégie de dépôt **OA_INSERTION**, que celle-ci soit déclarée ou non.

Voici un exemple de section définissant une Stratégie de dépôt : 
```yaml
    OA_tags: [__DATA__]
    
    OA_submission:   #optional
      OA_strategy: OA_VERSIONING  #optional
      OA_submissionScope:   #mandatory
        OA_referenceScope:   #optional
          -   #optional
            OA_component: dat_site  #mandatory
            OA_reference: tr_site_sit  #optional
            OA_i18n:   #optional
              fr: Choisir un site
              en: Choose a site
            #OA_exportHeader:   #optional, si le component n'existe pas pour le type de données (sinon erreur de validation yaml)
              #OA_i18n:   #optional
                #fr: Site
          - OA_component: dat_plot  #mandatory
            OA_i18n:   #optional
              fr: Choisir un plot
              en: Choose a plot
        OA_timeScope:   #optional
          OA_component: dat_date_heure  #mandatory
      OA_fileName:   #optional
        OA_filePattern: (.*)_(.*)_(.*)_(.*).csv  #mandatory
        OA_matchPatternScopes:   #optional
          - dat_site  #optional  
          - dat_plot      
          - __START_DATE__
          - __END_DATE__

```

##### Définition de la stratégie
-Stratégie par versionnement :
```yaml
  OA_strategy: OA_VERSIONING 
```
L'étiquette `__DATA__` doit être présente dans la section **OA_tags**
  
-Stratégie par insertion (cas par défaut) :
```yaml
  OA_strategy: OA_INSERTION
```

##### Définition d'informations passées lors du dépôt

La section **OA_submissionScope** permet de définir des informations qui seront saisies lors du dépôt. 
Dans le cas d'une stratégie de dépôt par versionnement (**OA_VERSIONING**), ce sont ces informations qui seront utilisées comme clef d'identification d'un jeu de données.
Si deux fichiers sont déposés avec les même informations, les deux fichiers seront considérés comme 
étant deux versions d'un même jeu de données. Une seule version d'un jeu de données peut être enregistrée dans la base de données (publiée).

Si la section **OA_submissionScope** est absente (ou vide) et que la stratégie de dépôt est le versionnement (**OA_VERSIONING**),
alors c'est le nom du fichier qui sera considéré comme identificateur du jeu de données. Deux fichiers soumis avec le 
même nom seront alors considérés comme  étant deux versions d'un même jeu de données. 
A un temps t, Un seul de ces fichier pourra être enregistré (publié) dans la base de données 

Dans la section  **OA_submissionScope** on peut définir des informations de contexte et 
des informations de temporalité : 

###### Informations de contexte -> **OA_referenceScopes** 
On définit dans **OA_referenceScopes**, sous la forme d'un tableau (présence d'un tiret - devant chaque description), 
les informations de contexte que l'utilisateur devra fournir lors du dépôt de son fichier.

Cette section peut être omise s'il n'y a pas d'information de contexte à fournir.

Il faut fournir un nom de composant 
```yaml
    OA_component: dat_site
```
Si le composant a été déclaré dans la section [**OA_data**](#data), on vérifiera que ce composant a été déclaré
comme référençant une autre donnée. C'est à dire qu'il a été déclaré avec une section [**OA_checker**](#referenceChecker)
de type (OA_name:) OA_reference. Ou bien qu'il existe une validation pour ce composant avec 
une section [**OA_checker**](#referenceChecker) de type (OA_name:) OA_reference.

```yaml
      site_validation:   #mandatory
        OA_i18n:   #optional
          fr: Validation du site
          en: Site validation
        OA_required: true  #optional
        OA_checker:   #mandatory
          OA_name: OA_reference  #mandatory
          OA_params:   #optional
            OA_reference:   #mandatory
              OA_name: tr_site_sit  #mandatory
            OA_multiplicity: ONE  #optional
        OA_components:   #optional
          - dat_site  #optional
```
Si ce composant existe (dans le type de données concerné par la section OA_submission), la valeur de ce composant (dans un fichier de données qui sera déposé) devra être la même que celle fournie lors du dépôt (renseignée via l'ihm càd au travers d'une liste déroulante ou bien via le nom du fichier).

On peut fournir un nom de référentiel :
```yaml
    OA_reference: tr_site_sit  
```
Si le composant existe, on peut omettre cette information. Si on la fournit, cela devra être le même référentiel que celui
déclaré dans la section [checker](#referenceChecker) (du composant ou de sa validation).

Si le composant n'existe pas, cette information est obligatoire et doit correspondre à un référentiel existant.

En l'absence de composant existant, un composant sera créé avec pour identificateur la valeur de
**OA_component** et comme référentiel la valeur de **OA_reference**. C'est à dire que les valeurs fournies 
pour ce composant lors du dépôt devront être présentes dans les données désignées par **OA_reference**.
Ex: le nom du site figurant dans le nom d'un fichier de données doit exister dans le référentiel des sites.

Enfin on peut renseigner des informations d'internationalisation :
- **OA_i18n** pour définir le nom du menu dans l'IHM permettant de sélectionner une valeur pour le composant.
- **OA_exportHeader** pour définir le nom de la colonne à l'exportation (affichage ou fichier) correspondant au composant.

###### Informations de temporalité -> **OA_timeScope** 

Cette section est facultative. 

Si elle est présente, au moment du dépôt l'utilisateur saisira les bornes temporelles de son fichier.
```yaml
        OA_timeScope:   #optional
          OA_component: dat_date_heure  
```
Les valeurs du composant **OA_timeScope** > **OA_component** devront être comprise entre ces bornes.

Le composant definit dans **OA_component** devra avoir été déclaré dans la section [**OA_data**](#data)
et avoir été défini comme étant une date. (présence d'un [checker](#referenceChecker) OA_name: OA_date dans la définition ou dans une validation)

Par exemple :
```yaml
    OA_computedComponents:   #optional
      dat_date_heure:   #optional
        OA_computation:
          OA_expression: >  #optional
            return datum.dat_date + " " + datum.dat_heure
        OA_checker:   #mandatory
          OA_name: OA_date  #mandatory
          OA_params:   #optional
            OA_pattern: dd/MM/yyyy HH:mm:ss  #mandatory
            OA_multiplicity: ONE  #optional
        OA_exportHeader:   #optional
          OA_i18n:   #optional
            fr: Date complète
            en: Complete date
```

Les dates des bornes seront fournies avec le pattern de date décrit dans le [**OA_checker**](#dateChecker).

##### Utilisation du nom du fichier pour fournir les informations définies dans la section **OA_submissionScope**

La section **OA_fileName** permet d'extraire du nom du fichier les données définies dans la section OA_submissionScope.
Elle permet de remplir automatiquement le formulaire de dépôt, et / ou de compléter les informations 
fournies si celles-ci sont manquantes.

On fournit une expression régulière dans **OA_filePattern**. Chaque groupe de parenthèses représentant une 
information de la section OA_submissionScope.

**OA_matchPatternScopes** est un tableau correspondant aux composants identifiés dans **OA_submissionScope** > **OA_referenceScopes** et **OA_submissionScope** > **OA_timeScope**.
Le premier groupe extrait avec l'expresssion régulière correspondra au premier **OA_referenceScopes** déclaré, le deuxième au deuxième et ainsi de suite jusqu'à passer à **OA_timeScope** pour récupérer __START_DATE__ et __END_DATE__ qui sont des termes réservés pour définir une période.

dans notre example :

```yaml
      OA_fileName:   #optional
        OA_filePattern: (.*)_(.*)_(.*)_(.*).csv  #mandatory
        OA_matchPatternScopes:   #optional
          - dat_site  #optional  
          - dat_plot      
          - __START_DATE__
          - __END_DATE__
```
Si on fournit le fichier avec le nom siteA_plotA1_2014-01-01_2014-12-31.csv, 
l'expression régulière trouvera les groupes
- 1: siteA
- 2: plotA1
- 3: 2014-01-01
- 4: 2014-12-31

les données ainsi extraites seront stockés de la manière suivante :

 ```json
    {
      "dat_site": "siteA",
      "dat_plot": "plotA1",
      "dat_date_heure": "date:2014-01-03T00:00:00:dd/MM/yyyy HH:mm:ss "  
    } 
```
ou 2014-01-03T00:00:00 devra être compris entre les bornes 2014-01-01 et 2014-12-31

**IMPORTANT** : Une section OA_fileName n'a pas de sens si il n'existe pas de section OA_submissionScope (c'est équivalent à une stratégie sans versionnement) --> le yaml doit être rejeté à la validation avec un message contextuel.


    Il est a noter que l'on peut utiliser la section OA_submissionScope avec une stratégie
    OA_INSERTION. Cela a pour but de forcer les valeurs des composants déclarés dans la section
    OA_referenceScopes ainsi que l'intervale de valeur du composant "date" déclaré dans la 
    section OA_timeScope
