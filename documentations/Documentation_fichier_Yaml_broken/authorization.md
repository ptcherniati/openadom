
#### <a id="authorization" />Authorisations sur les données (OA_authorization)

La section **OA_authorization** permet de restreindre l'accès à des données. 

    La section OA_authorization est indépendante de la section OA_submission. Cependant, il est 
    intéressant que tous les composants décrits dans la section OA_submission soit aussi décrits
    dans la section OA_authorization. Ainsi les vérifications de droits seront effectuées au 
    moment du dépôt sans avoir à lire le fichier.

Les authorizations peuvent porter sur des informations contextuelles **OA_authorizationScope** ou 
temporelles **OA_timeScope**.

```yaml
  OA_authorizations:   #mandatory
      OA_authorizationScope:   #optional
        - dat_site  #optional
      OA_timeScope: dat_date_heure  #optional
```

##### restrictions des droits sur des informations contextuelles
Cette section **OA_authorizationScope** est optionnelle. Elle consiste en un tableau d'identificateurs de composants.
Les composants ciblés devront avoir été déclarés comme référençant une autre donnée. C'est à dire qu'il a été déclaré 
avec une section  [**OA_checker**](#referenceChecker) de type (OA_name:) OA_reference. 
Ou bien qu'il existe une validation pour ce composant avec une section [**OA_checker**](#referenceChecker) 
de type (OA_name:) OA_reference.

```yaml
      OA_authorizationScope:   #optional
        - dat_site  #optional
```
Les valeurs soumises pour ces composants seront alors autorisées si l'utilisateur a obtenu les droits pour ces valeurs.

##### restrictions des droits sur des informations temporelles
Cette section **OA_timeScope** est optionnelle. Elle déclare un identificateurs de composant.
Le composant ciblé devra avoir été déclaré dans la section [**OA_data**](#data)
et avoir été définit comme étant une date. (présence d'un [checker](#referenceChecker) OA_name: OA_date 
dans la définition ou dans une validation)


```yaml
      OA_timeScope: dat_date_heure  #optional
```
Les dates soumises pour ce composant seront alors autorisées si l'utilisateur a obtenu les droits pour 
un intervale de dates qui les inclues.


##### Application des droits
Les droits sont appliqués au dépôt pour l'ensemble du fichier. Si une ligne soumise lors du dépôt sort du domaine
autorisé, alors l'ensemble du fichier sera refusé.
A l'extraction, seules les lignes autorisées seront affichées.