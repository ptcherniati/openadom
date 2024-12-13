with espece as (select distinct(naturalkey) as espece
                from monsore.referencevalue
                where referencetype='OA_data.yaml'),
     couleur as (select array_agg(distinct naturalkey) couleur
                 from monsore.referencevalue
                 where referencetype='valeurs_qualitatives'
     )
select
    'projet_manche' projet,
    'nivelle' site,
    'p1' plateforme,
    to_char(('2015-01-01'::date +a),'DD/MM/YYYY') date,
    espece ,
    couleur[(random()*2+1)] "Couleur des individus",
    (random()*100+1)::int "Nombre d'individus"
from espece, couleur,
     generate_series(0,364) s(a);