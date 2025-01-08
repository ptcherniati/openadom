
#### <a id="constantComponents" />Constantes du csv (OA_constantComponents)

Les constantes permettent de déclarer les données enregistrées dans les cartouches du fichier csv. 

Pour le fichier : __flux_journalier.csv__
```
  ************* Début de la cartouche **************
 1| Site          | Hesse                          |
 2| Theme         | flux                           |
 3| Frequence     | journalier                     |
 4| Date de debut | 01/01/2008                     |
 5| Date de fin   | 05/01/2008                     |
 6| Commentaire   | un commentaire                 | 
  ************** Fin de la cartouche ***************
 7|               |                                |                            |
 8| Date          | Carbon dioxide concentration   | Water vapour concentration |
  |---------------|--------------------------------|----------------------------|
 9| date          | CO2                            | H2O                        |
10| dd/mm/yyyy    | µmol mol-1                     | mmol mol-1                 |
11| 01/01/2008    | 425,10298875                   | 5,3855572917               |
12| 02/01/2008    | 418,4319752083                 | 3,6077222917               |
```

On définira le yaml suivant :

```yaml
OA_data:
  t_flux_j_flj:
    OA_dataHeaderLine: 8
    OA_dataFirstLine: 11
    OA_naturalKey:
      - flj_date
    OA_constantComponents:
      flj_site:
        OA_exportHeader:
          OA_title:
            fr: Site
            en: Site
        OA_required: true
        OA_importHeaderTarget:
          OA_rowNumber: 1 # On définit la ligne où ce trouve la donnée
          OA_columnNumber: 2 # On définit la colonne où ce trouve la donnée
      flj_theme:
        OA_exportHeader:
          OA_title:
            fr: Theme
            en: Theme
        OA_required: true
        OA_importHeaderTarget:
          OA_rowNumber: 2
          OA_columnNumber: 2
      flj_frequence:
        OA_exportHeader:
          OA_title:
            fr: Frequence
            en: Frequence
        OA_required: true
        OA_importHeaderTarget:
          OA_rowNumber: 3
          OA_columnNumber: 2
      flj_date_start:
        OA_exportHeader:
          OA_title:
            fr: Date de debut
            en: Start date
        OA_required: true
        OA_importHeaderTarget:
          OA_rowNumber: 4
          OA_columnNumber: 2
      flj_date_end:
        OA_exportHeader:
          OA_title:
            fr: Date de fin
            en: End date
        OA_required: true
        OA_importHeaderTarget:
          OA_rowNumber: 5
          OA_columnNumber: 2
      flj_site:
        OA_exportHeader:
          OA_title:
            fr: Commentaire
            en: Comment
        OA_required: true
        OA_importHeaderTarget:
          OA_rowNumber: 6
          OA_columnNumber: 2
    OA_basicComponents:
      flj_date:
        OA_importHeader: Date
      flj_carbon_dioxide_concentration:
        OA_importHeader: Carbon dioxide concentration
      flj_Water_vapour_concentration:
        OA_importHeader: Water vapour concentration
```

