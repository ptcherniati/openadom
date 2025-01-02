### <a id="data" />Description des données (OA_Data)

On décrit les données de références et les types de données dans la partie *OA_data*, on y liste les noms des colonnes souhaitées (dans [*OA_basicComponents*](#basicComponents), [*OA_computedComponents*](#computedComponents), [*OA_dynamicComponents*](#dynamicComponents), [*OA_patternComponents*](#patternComponents), [*OA_constantComponents*](#constantComponents));en précisant la liste des colonnes qui forme la clef naturelle (dans [*OA_naturalKey*](#naturalKey)).

Pour ajouter une référence, on ajoute dans la section "*OA_data*" une description de ce référentiel.

- un [__OA_basicComponents__](#basicComponents) est une colonne du fichier, 
- un [__OA_computedComponents__](#computedComponents) est une colonne qui n’est pas présente dans le fichier et dont la valeur est une constante ou le résultat d'un calcul, 
- un [__OA_dynamicComponents__](#dynamicComponents) est un ensemble de colonnes dont la clef est la concaténation d'un préfixe et d'une valeur d'un référentiel. Par exemple s’il existe un référentiel "propriétés" avec les valeurs (couleur, catégorie, obligatoire), on pourrait avoir dans un autre référentiel (en utilisant le préfixe "pts_") pts_couleur, pts_catégorie et pts_obligatoire, en les déclarant comme [__dynamicColumns__](#dynamicColumns), 
- un [__OA_patternComponents__](#patternComponents) est un ensemble de colonnes dont le nom à un format commun et qui par conséquent répond à un pattern. Par exemple avec des colonnes dont le nom répond au pattern variable_profondeur_répétition : SWC_([0-9]*)_([0-9]*), 
- un [__OA_constantComponents__](#constantComponents) est le descriptif de la cartouche du fichier csv. On précisera le nombre de lignes dans la cartouche dans rowNumber et le nombre de colonnes utiliser dans la cartouche dans columnNumber. On peut aussi choisir pour des informations sous l’en-tête de préciser le nom de l’en-tête headerName en lieu et place du numéro de colonne.

