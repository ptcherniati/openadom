### <a id="data" />Description des données (OA_Data)

On décrit les données de références et les types de données dans la partie *OA_data*, on y liste les noms des colonnes souhaitées (dans {{< var page-refs.basic-comp.link >}}, {{< var page-refs.computed-comp.link >}}, {{< var page-refs.pattern-comp.link >}}, {{< var page-refs.pattern-comp.link >}}, {{< var page-refs.constant-comp.link >}};en précisant la liste des colonnes qui forme la  {{< var page-refs.vocab.link-nk >}}.

Pour ajouter une référence, on ajoute dans la section "*OA_data*" une description de ce référentiel.

- un {{< var page-refs.basic-comp.link >}}
 est une colonne du fichier, 
- un {{< var page-refs.computed-comp.link >}}
 est une colonne qui n’est pas présente dans le fichier et dont la valeur est une constante ou le résultat d'un calcul, 
- un {{< var page-refs.dynamic-comp.link >}}
 est un ensemble de colonnes dont la clef est la concaténation d'un préfixe et d'une valeur d'un référentiel. Par exemple s’il existe un référentiel "propriétés" avec les valeurs (couleur, catégorie, obligatoire), on pourrait avoir dans un autre référentiel (en utilisant le préfixe "pts_") pts_couleur, pts_catégorie et pts_obligatoire, en les déclarant comme {{< var page-refs.dynamic-comp.link >}}
, 
- un {{< var page-refs.pattern-comp.link >}}
 est un ensemble de colonnes dont le nom à un format commun et qui par conséquent répond à un pattern. Par exemple avec des colonnes dont le nom répond au pattern variable_profondeur_répétition : SWC_([0-9]*)_([0-9]*), 
- un {{< var page-refs.constant-comp.link >}} est le descriptif de la cartouche du fichier csv. On précisera le nombre de lignes dans la cartouche dans rowNumber et le nombre de colonnes utiliser dans la cartouche dans columnNumber. On peut aussi choisir pour des informations sous l’en-tête de préciser le nom de l’en-tête headerName en lieu et place du numéro de colonne.

