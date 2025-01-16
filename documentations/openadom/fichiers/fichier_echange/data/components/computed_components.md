---
  title: Colonnes calculées 
  subtitle: OA_computedComponents
  abstract: >
    Une colonne calculée est une colonne qui n'est pas présente dans le fichier. Ses valeurs sont issues du résultat d'un calcul ou d'une concaténation.
---
Par exemple on veut avoir la valeur de la date complète en concaténant la date (tds_date) et l'heure (tds_heure).

Dans l'{{< var page-refs.groovy.link >}}, les valeurs sont dans une map datum :
- datum.tds_date
- datum.tds_heure

On définit dans le fichier de configuration la section OA_data suivante:

```yaml
OA_data:
  tr_type_de_site_tds:
    OA_dataHeaderLine: 1
    OA_dataFirstLine: 2
    OA_naturalKey:
      - tds_nom
    OA_basicComponents:
      tds_nom: 
        OA_importHeader: Nom
      tds_date: 
        OA_importHeader: Date
        OA_checker:
          OA_name: OA_date
          OA_params:
            OA_pattern: dd/MM/yyyy
      tds_heure: 
        OA_importHeader: Heure
        OA_checker:
          OA_name: OA_date
          OA_params:
            OA_pattern: HH:mm:ss
    OA_computedComponents:
      tds_date_heure:
        OA_computation:
          OA_expression: >
            return datum.tds_date + " " + datum.tds_heure
        OA_checker:
          OA_name: OA_date
          OA_params:
            OA_pattern: dd/MM/yyyy HH:mm:ss
            OA_multiplicity: ONE
        OA_exportHeader:
          OA_title:
            fr: Date complète
            en: Complete date
```

