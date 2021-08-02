# Aide fichier Yaml

## création de fichier Yaml :

Vous trouverez ci dessous un exemple de fichier Yaml fictif qui décrit les partie attendues dans celui ci pour qu'il soit valide. **Attention le format Yaml est sensible** il faut donc respecter l'indentation. 

Il y a 5 parties (<span style="color: orange">sans indentation</span>) attendues dans le fichier : 

  * version, 
  * application, 
  * references,
  * compositeReferences, 
  * dataTypes

<span style="color: orange">l'indentation du fichier yaml est très importante.</span>

### 1/ on commence par mettre la version du parser de yaml. On commence par la version 0

``` yaml
	version: 0
```

<span style="color: orange">*version* n'est pas indenté.</span>

### 2/ on présente l'application avec son nom et sa la version du fichier yaml (on commence par la version 1): 

``` yaml
	appliaction:
		nom: Aplication_Nom
		version: 1
```

<span style="color: orange">*application* n'est pas indenté. *nom* et *version* sont indentés de 1.</span>

### 3/ on présente l'application dans la partie *references*, on y liste les noms des colonnes souhaitées (dans *column*) en précisant la liste de colonnes qui seront les celles utilisées comme lien entre les données (dans *keyColumn*): 

``` yaml
	references:
		nomDeLaReferences:
			keyColumns: [nom de la colone clé ref 1]
			columns:
				nom de la colone clé:
				nom des autres colones 1:
				nom des autres colones 2:
		nomDeLaReferences2:
			keyColumns: [nom de la colone clé ref 2]
			columns:
				nom de la colone clé:
				nom des autres colones 1:
				nom des autres colones 2:
				etc..
```

<span style="color: orange">*references* n'est pas indenté. *nomDeLaReferences* et *nomDeLaReferences2* sont indentés de 1. *keyColumns* et *columns* sont indentés de 2. Le contenue de *columns* seront indenté de 3.</span>

### 4/ on fait le lien entre les différentes références pour cela nous utiliserons les colonnes que  nous aurons définies comme des colonnes clé (*keyColumn*) précédemment:

``` yaml
	compositeReferences:
		localizations:
			components:
				- reference: nomDeLaReferences
				- parentKeyColumn: "nom de la colone clé ref 1"
					reference: nomDeLaReferences2
				- parentKeyColumn: "nom de la colone clé ref 2"
					reference: nomDeLaReferences3
```

<span style="color: orange">*compositeReferences* n'est pas indenté. *localizations* est indenté de 1. *components* est indenté de 2. *- reference* et *- parentKeyColumn* sont indentés de 3. Le *reference* qui est sous parentKeyColumn est indenté de 4.</span>

### 5/ on met les infos des *dataTypes* qui va regrouper les données utiles pour la création de l'application (*data* et *authorization*) :  
####  5.1/ Nous regrouperons les données par nom (*nomDonnée*).</h4>

``` yaml
	dataTypes: 
		nomDonnée:
```

<span style="color: orange">*dataTypes* n'est pas indenté. *nomDonnée* est indenté de 1.</span>

##### 5.1.1/ *authorization* on y mettra les informations de *dataGroup*, *localizationScope* et *timeScope* : 
 Dans *dataGroup* on y retrouvera la liste de données qui sont obligatoire pour le bon fonctionnement de l'application, elle seront regroupées par type (exemple *referentiel* pour regrouper *localization* et *date* ou encore *quantitatif* pour regrouper les données avec une quantité) ou toutes ensemble (dans *all*).

``` yaml
			authorization:
				dataGroups:
					all:
						label: "Toutes les données"
						data:
							- localization
							- date
				localizationScope:
					variable: localization
					component: ref1
				timeScope:
					variable: date
					component: day
```

##### 5.1.2/ dans *data* nous retrouverons les données mises dans *dataGroup -> all -> data* soit localization et date dans notre exemple précédent. 
Les nom dans *component* ci dessus devront être décrit dans la partie *components* de *date* ci dessous.

``` yaml
			data:
				date:
					components:
						day:
							checker:
							name: Date
							params:
								pattern: dd/MM/yyyy
						time:
							checker:
							name: Date
							params:
								pattern: hh:mm:ss
				localization:
					components:
						ref1:
							checker:
							name: Reference
							params:
								refType: nomDeLaReferences*
						ref2:
							checker:
							name: Reference
							params:
								refType: nomDeLaReferences2*
```

<span style="color: red">*/!\ refType doit forcément être identique aux noms des références déclarées dans la partie references /!\</span>


##### 5.1.3/: 

``` yaml
			validations:
				exempledeDeRegleDeValidation:
					description: "Juste un exemple"
					checker:
						name: GroovyExpression
						params:
							expression: "true"
```

##### 5.1.4/ ensuite on va décrire le format des données attendues (*format*) décrite dans la partie *dataTypes* : 

``` yaml
			format:
				constants:
					- rowNumber: 1
					columnNumber: 2
					boundTo:
						variable: localization
						component: nomDonnée
					exportHeader: "nom par defaut du fichier csv"
```

*headerLine* permet de mettre le nombre de lignes qui seront utilisées pour mettre les informations du cartouche.

``` yaml
				headerLine: 1
```

*firstRowLine* sera égale au numéro de la première ligne dans la quelle se trouvera les premières données.
``` yaml
				firstRowLine: 2
```

*columns* est la partie dans laquelle nous décrirons toutes les colonnes et leurs types de données que nous attendons dans chaque colonne du fichier CSV (pour l'exemple utilisé ici c'est pour les données du fichier nomDonnées.csv):

``` yaml
				columns:
					- header: "nomRef2"
						boundTo:
						variable: localization
						component: ref2
					- header: "nomRef1"
						boundTo:
						variable: localization
						component: ref1
					- header: "date"
						boundTo:
						variable: date
						component: day
					- header: "heure"
						boundTo:
						variable: date
						component: time
```

## lors de l'importation du fichier yaml :
	
* mettre le nom de l'application en minuscule,
* sans espace,
* sans accent,
* sans chiffre et 
* sans caractaire speciaux

# Aide fichier .csv  
	
## lors de la création du fichier csv : 
	
* cocher lors de l'enregistrement du fichier 
  * Éditer les paramètre du filtre
  * Sélectionner le point virgule
* ne pas mettre d'espace ni de majuscule dans la colonne Key

## lors de l'ouverture du fichier csv via libre office:  
	
* sélectionner le séparateur en ";"
