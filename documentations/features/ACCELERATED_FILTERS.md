# Filtres accélérés — Réflexion, analyse et plan d'implémentation

> **Statut** : Proposition — branche `develop`
> **Auteur** : analyse Copilot — Mai 2026
> **Contexte** : openADOM v2, PostgreSQL 18

---

## Table des matières

1. [Contexte et motivation](#1-contexte-et-motivation)
2. [Limites du modèle générique actuel](#2-limites-du-modèle-générique-actuel)
3. [Principes de la solution](#3-principes-de-la-solution)
4. [Architecture en trois couches](#4-architecture-en-trois-couches)
5. [Granularité des axes — tags métier](#5-granularité-des-axes--tags-métier)
6. [Coûts et compromis](#6-coûts-et-compromis)
7. [Expérience utilisateur conversationnelle](#7-expérience-utilisateur-conversationnelle)
8. [Fichier de configuration — syntaxe YAML](#8-fichier-de-configuration--syntaxe-yaml)
9. [Schéma base de données](#9-schéma-base-de-données)
10. [Maintien de la synthèse — triggers statement-level](#10-maintien-de-la-synthèse--triggers-statement-level)
11. [Routage des requêtes dans le query builder](#11-routage-des-requêtes-dans-le-query-builder)
12. [Plan d'implémentation](#12-plan-dimplémentation)
13. [Annexe — estimations de volume ACBB](#13-annexe--estimations-de-volume-acbb)

---

## 1. Contexte et motivation

openADOM stocke toutes les données métier (référentiels et mesures) dans une unique
table `referencevalue` dont les colonnes de valeur sont du JSONB (`refValues`,
`refsLinkedTo`). Ce modèle générique permet d'accueillir n'importe quel domaine de SI
sans DDL spécifique, mais empêche toute optimisation ciblée de la recherche.

Les filtres actuels utilisent des JSONPath :

```sql
refvalues @@ '$.swc_variable == "humidite_volumique"'
```

Ces prédicats s'appuient sur l'index GIN `referenceType_refValue_gin_idx`. Un GIN
est efficace pour les tests d'existence ou de containment, mais :

- **Lent sur les plages temporelles** : extraire une date d'un JSONB pour la comparer
  à un intervalle ne peut pas utiliser un GIN de façon optimale.
- **Pas d'index-only scan** : le heap doit toujours être relu.
- **COUNT(*) avec filtre JSONPath** : O(N) sur la table complète — inacceptable sur
  100 M lignes (cas ACBB SWC sur 20 ans).
- **Aucune estimation préalable de résultat** pour l'UX : l'utilisateur ne sait pas
  combien de lignes son filtre retournera avant de lancer l'extraction.

### Exemple concret : ACBB t_swc_swc

- ~8 sites × ~3 parcelles/site = 24 parcelles
- PatternComponent `swc_value` : 1 variable × 3 répétitions × 4 profondeurs = 12 lignes
  `referencevalue` par horodatage
- Fréquence semi-horaire (48 mesures/jour) sur 20 ans
- **Volume estimé : 100 M lignes** pour ce seul datatype

---

## 2. Limites du modèle générique actuel

| Problème | Impact |
|---|---|
| Filtres JSONPath sur colonnes JSONB | Plan d'exécution dégradé : bitmap scan GIN + heap fetch |
| COUNT(*) lent | Impossible d'afficher "N résultats" avant extraction |
| Pas de décomposition temporelle fine | Plage de dates → seq scan ou bitmap OR énorme |
| Pas de synthèse multi-axes | Impossible de guider l'utilisateur par filtrage progressif |
| Droits RLS ignorés dans les vues matérialisées simples | Un COUNT brut ne respecte pas les autorisations |

---

## 3. Principes de la solution

La solution repose sur trois principes non négociables :

1. **Généricité préservée** : aucune modification du modèle générique existant. Les
   optimisations sont **optionnelles** et déclarées dans le fichier de configuration
   du domaine par l'administrateur qui connaît son domaine.

2. **Droits préservés** : la RLS PostgreSQL s'applique intégralement à l'extraction.
   La table de synthèse encode les `requiredAuthorizations` pour que les comptages
   préalables soient exacts par rapport aux droits de l'utilisateur.

3. **Choix éclairé de l'administrateur** : le découpage (granularité temporelle,
   buckets numériques) détermine directement la sélectivité des index et la taille
   de la synthèse. Plus le découpage est fin, plus les résultats sont discriminants,
   plus les index sont efficaces — mais plus la table de synthèse est volumineuse
   et l'import coûteux. L'administrateur, seul connaisseur du domaine, fait ce choix
   en conscience.

---

## 4. Architecture en trois couches

```
┌─────────────────────────────────────────────────────────────────────┐
│  COUCHE 1 : Index B-tree partiel spécifique au datatype             │
│                                                                     │
│  Colonnes GENERATED AS STORED sur referencevalue + index partiel    │
│  WHERE referencetype = 'xxx'                                        │
│  → Seek O(log N) pour l'extraction finale                           │
│  → PostgreSQL 18 skip scan si première(s) colonne(s) non fixées     │
└─────────────────────────────────────────────────────────────────────┘
         ↑ alimenté par import                ↓ utilisé par extraction

┌─────────────────────────────────────────────────────────────────────┐
│  COUCHE 2 : Table de synthèse af_synthesis_{datatype}               │
│                                                                     │
│  Ligne par (ref1 × ref2 × ... × période × requiredAuthorizations)   │
│  Colonnes : row_count + min/max pour axes numériques continus        │
│  Maintien : trigger AFTER INSERT/DELETE statement-level              │
│  → COUNT exact par droits utilisateur en < 1 ms                     │
│  → Bornes min/max pour guider les sliders numériques dans l'UI      │
└─────────────────────────────────────────────────────────────────────┘
         ↑ alimentée par trigger              ↓ utilisée par UX

┌─────────────────────────────────────────────────────────────────────┐
│  COUCHE 3 : Interface conversationnelle de filtres progressifs       │
│                                                                     │
│  Choix site → COUNT résiduel / 10 → parcelle → COUNT / 4            │
│  → profondeur → COUNT final → "Extraire N lignes"                   │
│  → Extraction via index B-tree → RLS → résultat paginé              │
└─────────────────────────────────────────────────────────────────────┘
```

### Flux complet

```
1. L'utilisateur ouvre l'écran de filtres
       │
       ▼
2. L'UI interroge af_synthesis_{datatype}
   avec les droits de l'utilisateur
   → affiche les valeurs distinctes disponibles + count total
       │
3. L'utilisateur sélectionne un site
       │
       ▼
4. Requête synthèse : WHERE site = 'X' AND rights @> user_rights
   → count = 1 000 000 lignes accessibles pour ce site
       │
5. L'utilisateur affine : parcelle 'P3'
       │
       ▼
6. Requête synthèse : WHERE site = 'X' AND parcelle = 'P3' ...
   → count = 100 000 lignes
       │
7. L'utilisateur sélectionne profondeur : entre 10 et 60 cm
   (bornes affichées depuis min/max de la synthèse)
       │
       ▼
8. "Vous allez extraire environ 25 000 lignes. Confirmer ?"
       │
9. L'utilisateur confirme → extraction réelle
       │
       ▼
10. SELECT ... FROM referencevalue
    WHERE referencetype = 'xxx'
      AND _af_site_key = 'X'
      AND _af_parcelle = 'P3'
      AND _af_profondeur_bucket BETWEEN 1 AND 6
    -- RLS s'applique automatiquement
    -- Index B-tree → seek rapide
```

---

## 5. Granularité des axes — tags métier

### 5.1 Axes temporels

La granularité temporelle est définie par un **tag métier** (constante de liste) :

| Tag | Description | Colonne générée | Cardinalité (20 ans) |
|---|---|---|---|
| `__DAILY__` | Découpage journalier | `date_trunc('day', …)::date` | ~7 300 |
| `__WEEKLY__` | Semaines ISO | `date_trunc('week', …)::date` | ~1 043 |
| `__MONTHLY__` | Mois civil | `date_trunc('month', …)::date` | ~240 |
| `__QUARTERLY__` | Trimestres | `date_trunc('quarter', …)::date` | ~80 |
| `__YEARLY__` | Années | `date_trunc('year', …)::date` | ~20 |

**Règle de choix** : plus la granularité est fine (`__DAILY__`), plus l'index est
sélectif (peu de lignes par valeur), mais plus la table de synthèse est grande.
Pour des données semi-horaires sur 20 ans avec 24 sites × 10 variables × 4
profondeurs :

| Granularité | Lignes synthèse (sans droits) | Lignes synthèse (D=5 niveaux droits) | Volume synthèse |
|---|---|---|---|
| YEARLY | 24×10×4×20 = 19 200 | 96 000 | < 1 MB |
| MONTHLY | 24×10×4×240 = 230 400 | 1 152 000 | ~10 MB |
| WEEKLY | 24×10×4×1043 = 1 001 280 | 5 006 400 | ~50 MB |
| DAILY | 24×10×4×7300 = 7 008 000 | 35 040 000 | ~350 MB |

→ **MONTHLY est le bon compromis** pour ACBB : sélectivité suffisante, volume
raisonnable, pertinent pour l'UX (l'utilisateur pense en mois/saisons).

### 5.2 Axes numériques — buckets

Pour les colonnes numériques continues (profondeur en cm, altitude, indice de
qualité), deux modes :

**Mode `ALL`** : chaque valeur distincte devient une entrée de synthèse.
Approprié si la cardinalité est faible et connue (ex : profondeur = {10, 30, 60, 90} cm).

**Mode `STEP_N`** (ex : `STEP_10`) : arrondi à la dizaine inférieure.
```sql
-- Colonne générée pour profondeur en buckets de 10 cm
_af_profondeur_bucket INTEGER
  GENERATED ALWAYS AS (
    (refvalues->>'swc_profondeur')::integer / 10 * 10
  ) STORED
```

| Mode | Exemple profondeur | Cardinalité | Usage recommandé |
|---|---|---|---|
| `ALL` | {10, 30, 60, 90} → 4 valeurs | Faible (< 20) | Valeurs discrètes connues |
| `STEP_10` | 0-9→0, 10-19→10, … | ~10 buckets pour 0-100 cm | Plage continue, usage courant |
| `STEP_100` | 0-99→0, 100-199→100, … | ~5 buckets pour 0-500 m | Altitude, pression |

**Axes flottants libres** (valeurs mesurées : CO2, H2O, température) : pas de
découpage en synthèse. La synthèse stocke uniquement `min` et `max` pour alimenter
un slider dans l'UI. Le filtre effectif utilise directement l'index B-tree sur la
colonne générée. L'UX n'affiche pas de COUNT préalable pour ces axes.

---

## 6. Coûts et compromis

### 6.1 Coût sur l'import (dépôt de fichiers)

Les colonnes `GENERATED ALWAYS AS … STORED` sont calculées lors de chaque INSERT.
L'index B-tree est mis à jour à chaque INSERT/DELETE.

| Composant | Surcoût estimé par ligne insérée |
|---|---|
| Calcul de 4 colonnes générées TEXT/DATE | ~5 µs (extraction JSONB + cast) |
| Mise à jour index B-tree partiel | ~10-20 µs (seek + write WAL) |
| Trigger statement-level synthèse | Une seule requête GROUP BY par batch → ~2-5 s par fichier |

**Pour ACBB SWC** : un fichier semi-horaire de 1 an = 48 × 365 × 12 = 210 240 lignes.
Surcoût estimé : 210 240 × 25 µs ≈ **5 secondes** d'overhead d'index par import.

Comparé à la durée d'import actuelle (quelques minutes pour un fichier annuel),
ce surcoût est acceptable.

**Point d'attention** : si plusieurs fichiers sont importés en parallèle (pipeline
Cascade), les mises à jour d'index concurrentes peuvent générer des contentions
sur les pages B-tree. À monitorer sur les premiers imports de masse. Il est possible
de désactiver temporairement l'index (`ALTER INDEX … UNUSABLE` / REINDEX) pour
les imports batch massifs, puis de le reconstruire en une fois.

### 6.2 Coût sur la suppression

La suppression d'un fichier (`publishedBinaryFile DELETE`) déclenche le trigger
statement-level :
- Suppression des lignes `referencevalue` → trigger recalcule la synthèse pour les
  combinaisons affectées (par `binaryfile`)
- Mise à jour de l'index B-tree : O(N_lignes_fichier × log N_total)

Pas de surcoût exceptionnel par rapport à l'import.

### 6.3 Stockage total (ACBB SWC, 20 ans)

| Élément | Volume |
|---|---|
| Table `referencevalue` (heap existant) | ~48 GB |
| Colonnes générées `_af_*` (4 × ~20 bytes) | +8 GB |
| Index B-tree partiel `af_idx_swc` | ~10 GB |
| Table synthèse `af_synthesis_swc` (MONTHLY, D=5) | ~10 MB |
| **Delta lié aux filtres accélérés** | **~18 GB** |

Le delta de 18 GB est à comparer au gain : requête de filtre passant de **> 1 minute**
(seq scan JSONPath sur 100 M lignes) à **< 100 ms** (index seek).

### 6.4 Pourquoi ne pas stocker les IDs dans la synthèse

Stocker `UUID[]` au lieu de `row_count` a été évalué et rejeté :

- Taille de la synthèse : 9 MB → 8 GB (facteur 900×)
- Maintenance par trigger : O(N) par update (PostgreSQL copie tout le tableau à
  chaque `array_append`)
- Gain fonctionnel nul : l'extraction doit de toute façon passer par
  `referencevalue` pour que la RLS s'applique

---

## 7. Expérience utilisateur conversationnelle

### 7.1 Principe

L'interface de filtres accélérés fonctionne en mode **conversationnel progressif** :
chaque sélection affine le COUNT résiduel affiché à l'utilisateur, comme un
entonnoir.

```
[Datatype: SWC - Humidité du sol]

Sélectionnez un site :
  ○ Lusignan    (1 234 500 lignes accessibles)
  ○ Versailles  (  987 200 lignes accessibles)
  ○ Clermont    (  543 100 lignes accessibles)
  [Sélection : Lusignan → 1 234 500 lignes]

Sélectionnez une parcelle :
  ○ Par01  (412 100)
  ○ Par02  (409 700)
  ○ Par03  (412 700)
  [Sélection : Par01 → 412 100 lignes]

Sélectionnez une plage de profondeur (cm) :
  ○ 10 cm  (103 000)
  ○ 30 cm  (103 000)
  ○ 60 cm  (103 050)
  ○ 90 cm  (103 050)
  [Sélection : 10 et 30 cm → 206 000 lignes]

Sélectionnez une période :
  ┌─────────────────────────────────────┐
  │ De : [janv. 2018] À : [déc. 2023]  │  → 14 400 lignes
  └─────────────────────────────────────┘

Valeur mesurée (optionnel) :
  ┌──────────────────────────────────────┐
  │ Min : [-12.3]  Max : [98.7]          │
  │ Filtre : [      ] à [      ]         │
  └──────────────────────────────────────┘
  (aucun COUNT préalable — filtre appliqué à l'extraction)

══════════════════════════════════════════
  Résultat estimé : 14 400 lignes (avec vos droits)
  [Extraire]
══════════════════════════════════════════
```

### 7.2 Requêtes sous-jacentes

Chaque étape appelle un endpoint dédié :

```
GET /api/v2/applications/{app}/data/{datatype}/filter-summary
    ?group=grp_principal
    &ref1=Lusignan
    &ref2=Par01
    &period_from=2018-01
    &period_to=2023-12
```

Requête SQL :
```sql
SELECT
    _af_ref3,                        -- profondeur : axe suivant non fixé
    SUM(row_count) AS accessible_count
FROM {app}.af_synthesis_swc
WHERE
    _af_site       = :ref1
    AND _af_parcelle = :ref2
    AND _af_period BETWEEN :from AND :to
    AND requiredauthorizations @> :user_required_authorizations
GROUP BY _af_ref3
ORDER BY _af_ref3;
```

Durée : **< 5 ms** (index PK sur la synthèse).

### 7.3 Filtres désactivés hors liste désignée

Si `OA_disableNonDesignatedFilters: true` est configuré, les filtres sur les colonnes
non désignées sont rejetés avec une réponse HTTP 400 explicite (`FILTER_NOT_INDEXED`).
Cela garantit que l'utilisateur ne peut pas déclencher accidentellement un seq scan
lent.

Si `OA_disableNonDesignatedFilters: false` (défaut), les filtres hors liste utilisent
le chemin GIN existant. L'UI peut les signaler visuellement comme "filtres non accélérés".

---

## 8. Fichier de configuration — syntaxe YAML

### 8.1 Exemple complet (ACBB t_swc_swc)

```yaml
OA_data:
  t_swc_swc:
    # ... sections existantes inchangées ...

    OA_acceleratedFilters:
      # Stratégie globale :
      #   index   → index B-tree uniquement (extraction rapide, pas de synthèse)
      #   synthesis → synthèse uniquement (COUNT rapide, extraction via GIN)
      #   both    → les deux (recommandé)
      OA_strategy: both

      # Désactiver les filtres non désignés (défaut: false)
      # Si true : les filtres sur d'autres colonnes retournent HTTP 400
      OA_disableNonDesignatedFilters: false

      OA_filterGroups:
        - OA_id: grp_principal            # identifiant alphanumérique unique

          # Axes référentiels (colonnes TEXT, cardinalité faible/connue)
          # Ordre = ordre de décomposition dans l'index B-tree
          # Le premier axe doit être le plus sélectif (ex: site)
          OA_referentials:
            - sit_key                      # depuis refsLinkedTo
            - par_parcelle                 # depuis refValues ou refsLinkedTo

          # Axe temporel
          OA_dateColumn: swc_date
          # Granularité : DAILY | WEEKLY | MONTHLY | QUARTERLY | YEARLY
          OA_dateGranularity: MONTHLY

          # Axes numériques discrets (cardinalité faible)
          OA_numericDiscrete:
            - column: swc_profondeur
              # Mode : ALL (toutes valeurs) ou STEP_N (buckets de largeur N)
              mode: ALL

          # Axes numériques continus (valeurs mesurées)
          # Pas de synthèse → uniquement min/max dans la synthèse + index direct
          OA_numericContinuous:
            - column: swc_value
              # Buckets optionnels pour l'index B-tree (améliore le skip scan)
              # STEP_10 : arrondi à la dizaine → bucket INTEGER
              indexBucketMode: STEP_10
```

### 8.2 Règles de validation

- `OA_id` : pattern `[a-z][a-z_0-9]{2,30}`, unique dans le datatype
- `OA_referentials` : chaque élément doit référencer un `OA_basicComponent` ou
  `OA_computedComponent` existant ; le checker peut être `OA_reference`, `OA_string`
  ou toute valeur discrète
- `OA_dateColumn` : doit référencer un composant avec checker `OA_date`
- `OA_dateGranularity` : valeur parmi `DAILY`, `WEEKLY`, `MONTHLY`, `QUARTERLY`,
  `YEARLY`
- `OA_numericDiscrete[].column` : composant avec checker `OA_integer` ou `OA_float`
- `OA_numericDiscrete[].mode` : `ALL` ou `STEP_N` (N entier > 0)
- `OA_numericContinuous[].indexBucketMode` : `NONE` | `STEP_N` (défaut: `NONE`)

---

## 9. Schéma base de données

### 9.1 Colonnes générées sur `referencevalue`

Pour chaque groupe `OA_filterGroup`, des colonnes `_af_{groupId}_{axis}` sont
ajoutées à `referencevalue`. Le préfixe `_af_` garantit l'absence de collision avec
les clés JSONB métier.

```sql
-- Exemple pour grp_principal de t_swc_swc
ALTER TABLE {app}.referencevalue
  -- Référentiels (depuis refsLinkedTo ou refValues selon la configuration)
  ADD COLUMN IF NOT EXISTS _af_grp_principal_site     TEXT
      GENERATED ALWAYS AS (refsLinkedTo->>'sit_key') STORED,
  ADD COLUMN IF NOT EXISTS _af_grp_principal_parcelle TEXT
      GENERATED ALWAYS AS (refsLinkedTo->>'par_parcelle') STORED,
  -- Axe temporel (granularité MONTHLY)
  ADD COLUMN IF NOT EXISTS _af_grp_principal_period   DATE
      GENERATED ALWAYS AS (
          date_trunc('month',
              (refvalues->>'swc_date')::date
          )::date
      ) STORED,
  -- Axe numérique discret (ALL → valeur brute)
  ADD COLUMN IF NOT EXISTS _af_grp_principal_profondeur INTEGER
      GENERATED ALWAYS AS (
          (refvalues->>'swc_profondeur')::integer
      ) STORED,
  -- Axe numérique continu (bucket STEP_10 pour l'index)
  ADD COLUMN IF NOT EXISTS _af_grp_principal_value_bucket INTEGER
      GENERATED ALWAYS AS (
          CASE WHEN (refvalues->>'swc_value') ~ '^-?[0-9]+\.?[0-9]*$'
               THEN ((refvalues->>'swc_value')::double precision / 10)::integer * 10
               ELSE NULL END
      ) STORED;
```

### 9.2 Index B-tree partiel (PostgreSQL 18 skip scan)

```sql
-- Index partiel : WHERE referencetype limite la taille et améliore le seek
CREATE INDEX IF NOT EXISTS af_idx_grp_principal_swc
  ON {app}.referencevalue (
    referencetype,
    _af_grp_principal_site,
    _af_grp_principal_parcelle,
    _af_grp_principal_profondeur,
    _af_grp_principal_period
    -- value_bucket séparé si utilisé seul fréquemment :
  )
  WHERE referencetype = 't_swc_swc';

-- Index séparé pour les requêtes sur les valeurs numériques continues
CREATE INDEX IF NOT EXISTS af_idx_grp_principal_swc_value
  ON {app}.referencevalue (
    referencetype,
    _af_grp_principal_site,
    _af_grp_principal_value_bucket
  )
  WHERE referencetype = 't_swc_swc'
    AND _af_grp_principal_value_bucket IS NOT NULL;
```

**Note PostgreSQL 18** : le skip scan permet d'utiliser `af_idx_grp_principal_swc`
même si `site` ou `parcelle` ne sont pas fixés dans le prédicat. Le moteur effectue
un seek pour chaque valeur distincte de la première colonne non fixée. Avec 24 sites
et 3 parcelles, le coût du skip est 72 seek B-tree — négligeable.

### 9.3 Table de synthèse

```sql
CREATE TABLE IF NOT EXISTS {app}.af_synthesis_grp_principal_swc (
    -- Axes de décomposition (ordre identique à l'index)
    _af_site            TEXT    NOT NULL,
    _af_parcelle        TEXT    NOT NULL,
    _af_profondeur      INTEGER,         -- NULL si valeur non numérique
    _af_period          DATE    NOT NULL,
    -- Droits encodés (pour COUNT exact par utilisateur)
    requiredauthorizations {app}.requiredauthorizations NOT NULL,
    -- Métriques
    row_count           BIGINT  NOT NULL DEFAULT 0,
    -- Bornes pour axes continus (guides slider UI)
    value_min           DOUBLE PRECISION,
    value_max           DOUBLE PRECISION,
    -- Clé primaire
    CONSTRAINT af_synthesis_grp_principal_swc_pk
        PRIMARY KEY (
            _af_site, _af_parcelle, _af_profondeur,
            _af_period, requiredauthorizations
        )
);

-- Index pour les requêtes partielles de l'UI conversationnelle
CREATE INDEX IF NOT EXISTS af_synth_site_period_idx
    ON {app}.af_synthesis_grp_principal_swc (_af_site, _af_period);
CREATE INDEX IF NOT EXISTS af_synth_parcelle_idx
    ON {app}.af_synthesis_grp_principal_swc (_af_site, _af_parcelle, _af_period);
```

---

## 10. Maintien de la synthèse — triggers statement-level

Le modèle de maintien s'appuie sur les **triggers statement-level** existants du
projet (voir `referencevalue_count_stats` dans `V2__app_schema_complete.sql`).

### 10.1 Trigger AFTER INSERT

```sql
CREATE OR REPLACE FUNCTION {app}.af_synthesis_grp_principal_swc_after_insert()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    -- Recalcul incrémental : uniquement les combinaisons des nouvelles lignes
    INSERT INTO {app}.af_synthesis_grp_principal_swc (
        _af_site, _af_parcelle, _af_profondeur, _af_period,
        requiredauthorizations, row_count, value_min, value_max
    )
    SELECT
        _af_grp_principal_site,
        _af_grp_principal_parcelle,
        _af_grp_principal_profondeur,
        _af_grp_principal_period,
        (authorization).requiredauthorizations,
        COUNT(*),
        MIN((refvalues->>'swc_value')::double precision),
        MAX((refvalues->>'swc_value')::double precision)
    FROM {app}.referencevalue
    WHERE referencetype = 't_swc_swc'
      AND binaryfile = (SELECT DISTINCT binaryfile FROM inserted_rows LIMIT 1)
    GROUP BY 1, 2, 3, 4, 5
    ON CONFLICT ON CONSTRAINT af_synthesis_grp_principal_swc_pk
    DO UPDATE SET
        row_count = {app}.af_synthesis_grp_principal_swc.row_count
                  + EXCLUDED.row_count,
        value_min = LEAST(
            {app}.af_synthesis_grp_principal_swc.value_min,
            EXCLUDED.value_min),
        value_max = GREATEST(
            {app}.af_synthesis_grp_principal_swc.value_max,
            EXCLUDED.value_max);
    RETURN NULL;
END;
$$;

CREATE TRIGGER af_synthesis_grp_principal_swc_insert
AFTER INSERT ON {app}.referencevalue
REFERENCING NEW TABLE AS inserted_rows
FOR EACH STATEMENT
WHEN (pg_trigger_depth() = 0)
EXECUTE FUNCTION {app}.af_synthesis_grp_principal_swc_after_insert();
```

### 10.2 Trigger AFTER DELETE

Pour la suppression, un recalcul complet des combinaisons affectées est nécessaire
car `row_count - delta` peut conduire à des valeurs incorrectes si des lignes
intermédiaires ont été modifiées :

```sql
CREATE OR REPLACE FUNCTION {app}.af_synthesis_grp_principal_swc_after_delete()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    affected_sites TEXT[];
BEGIN
    -- Identifier les sites affectés par la suppression
    SELECT ARRAY_AGG(DISTINCT _af_grp_principal_site)
    INTO affected_sites
    FROM deleted_rows;

    -- Supprimer les lignes de synthèse affectées
    DELETE FROM {app}.af_synthesis_grp_principal_swc
    WHERE _af_site = ANY(affected_sites);

    -- Recalculer depuis la source (utilise l'index af_idx_grp_principal_swc)
    INSERT INTO {app}.af_synthesis_grp_principal_swc (
        _af_site, _af_parcelle, _af_profondeur, _af_period,
        requiredauthorizations, row_count, value_min, value_max
    )
    SELECT
        _af_grp_principal_site,
        _af_grp_principal_parcelle,
        _af_grp_principal_profondeur,
        _af_grp_principal_period,
        (authorization).requiredauthorizations,
        COUNT(*),
        MIN((refvalues->>'swc_value')::double precision),
        MAX((refvalues->>'swc_value')::double precision)
    FROM {app}.referencevalue
    WHERE referencetype = 't_swc_swc'
      AND _af_grp_principal_site = ANY(affected_sites)  -- utilise l'index
    GROUP BY 1, 2, 3, 4, 5;
    RETURN NULL;
END;
$$;

CREATE TRIGGER af_synthesis_grp_principal_swc_delete
AFTER DELETE ON {app}.referencevalue
REFERENCING OLD TABLE AS deleted_rows
FOR EACH STATEMENT
WHEN (pg_trigger_depth() = 0)
EXECUTE FUNCTION {app}.af_synthesis_grp_principal_swc_after_delete();
```

### 10.3 Pourquoi statement-level et non ligne-par-ligne

| Critère | Statement-level | Row-level |
|---|---|---|
| Appels par import (210 K lignes) | 1 | 210 000 |
| Overhead trigger | ~2-5 s par import | ~60 min par import |
| Cohérence intermédiaire | Vue snapshot de toutes les nouvelles lignes | Ligne par ligne, états intermédiaires visibles |
| `REFERENCING NEW TABLE` | ✓ disponible (PG 10+) | Non applicable |

Le pattern `REFERENCING NEW TABLE AS inserted_rows` donne accès à la table
de transition (snapshot des lignes insérées) sans overhead ligne-par-ligne.
C'est exactement le modèle de `referencevalue_count_stats` existant.

---

## 11. Routage des requêtes dans le query builder

### 11.1 Détection des colonnes désignées

Dans `DataRequestBuilder.filter()`, une nouvelle branche détecte si la colonne
cible est déclarée dans un `OA_filterGroup` :

```java
// Pseudo-code
if (acceleratedFilterConfig.isDesignatedColumn(componentKey, dataType)) {
    // Générer un prédicat SQL natif sur la colonne générée
    String columnAlias = acceleratedFilterConfig.getGeneratedColumnName(componentKey, dataType);
    return columnAlias + " = :" + paramName;
    // ou pour les plages : columnAlias + " BETWEEN :from AND :to"
} else {
    // Chemin existant : JSONPath sur refValues/refsLinkedTo
    return buildJsonPathPredicate(componentKey, filter);
}
```

### 11.2 Requête d'extraction finale

```sql
SELECT ...
FROM {app}.referencevalue
WHERE referencetype = 't_swc_swc'         -- clause partielle de l'index
  AND _af_grp_principal_site = :site       -- colonne générée → B-tree seek
  AND _af_grp_principal_parcelle = :parc
  AND _af_grp_principal_profondeur = :depth
  AND _af_grp_principal_period
      BETWEEN :period_from AND :period_to
  -- Filtres non désignés (si autorisés) → JSONPath classique :
  AND refvalues @@ :jsonpath_filter
-- RLS appliquée automatiquement par PostgreSQL
ORDER BY _af_grp_principal_period, _af_grp_principal_site
LIMIT :limit OFFSET :offset;
```

### 11.3 Nouveau endpoint REST

```
GET /api/v2/applications/{app}/data/{dataType}/accelerated-filter-summary
    ?groupId=grp_principal
    &site=Lusignan           (optionnel)
    &parcelle=Par01          (optionnel)
    &periodFrom=2018-01      (optionnel, format dépend de la granularité)
    &periodTo=2023-12        (optionnel)
```

Réponse :
```json
{
  "groupId": "grp_principal",
  "totalCount": 14400,
  "axes": {
    "site": [
      {"value": "Lusignan", "count": 1234500},
      {"value": "Versailles", "count": 987200}
    ],
    "profondeur": [
      {"value": 10, "count": 103000},
      {"value": 30, "count": 103000},
      {"value": 60, "count": 103050},
      {"value": 90, "count": 103050}
    ],
    "period": [
      {"value": "2018-01", "count": 600},
      {"value": "2018-02", "count": 570},
      ...
    ]
  },
  "numericRanges": {
    "swc_value": {"min": -12.3, "max": 98.7}
  }
}
```

---

## 12. Plan d'implémentation

### Phase 1 — Modèles de configuration

- [ ] `AcceleratedFilterDescription` (record) : `strategy`, `disableNonDesignatedFilters`, `filterGroups`
- [ ] `AcceleratedFilterGroup` (record) : `id`, `referentials`, `dateColumn`, `dateGranularity`, `numericDiscrete`, `numericContinuous`
- [ ] `AcceleratedFilterStrategy` (enum) : `INDEX`, `SYNTHESIS`, `BOTH`
- [ ] `AcceleratedFilterGranularity` (enum) : `DAILY`, `WEEKLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`
- [ ] `AcceleratedFilterNumericMode` (enum) : `ALL`, `STEP_N`
- [ ] Intégration dans `StandardDataDescription` (champ nullable → rétrocompatibilité)
- [ ] Validation dans `ConfigurationSchemaNode` : vérification des colonnes référencées

### Phase 2 — Génération du schéma DDL

- [ ] `AcceleratedFilterSchemaBuilder` : génère les `ALTER TABLE ADD COLUMN` + `CREATE INDEX`
- [ ] `AcceleratedFilterSynthesisBuilder` : génère les `CREATE TABLE af_synthesis_*`
- [ ] `AcceleratedFilterTriggerBuilder` : génère les fonctions trigger + `CREATE TRIGGER`
- [ ] Intégration dans le pipeline de migration Flyway applicatif (version dynamique)
- [ ] Migration de suppression propre (DROP COLUMN + DROP TABLE + DROP INDEX + DROP TRIGGER)

### Phase 3 — Query builder

- [ ] Extension de `DataRequestBuilder.filter()` : détection colonnes désignées
- [ ] Génération des prédicats SQL natifs pour colonnes `_af_*`
- [ ] Support des plages numériques (BETWEEN) et de la granularité temporelle

### Phase 4 — Endpoint et UX

- [ ] Nouveau contrôleur `AcceleratedFilterController`
- [ ] Service `AcceleratedFilterSummaryService` : requête sur `af_synthesis_*`
- [ ] Jointure avec les droits utilisateur (`requiredauthorizations @> :user_rights`)
- [ ] Modèle de réponse `AcceleratedFilterSummaryResult`
- [ ] Frontend : composant de filtres conversationnels progressifs

### Phase 5 — Tests

- [ ] Test unitaire : `AcceleratedFilterSchemaBuilder` → SQL généré
- [ ] Test d'intégration : import d'un fichier SWC → trigger met à jour la synthèse
- [ ] Test de performance : requête synthèse < 5 ms, extraction < 100 ms
- [ ] Test de non-régression : les filtres existants (JSONPath) fonctionnent toujours

---

## 13. Annexe — estimations de volume ACBB

### Paramètres

| Paramètre | Valeur |
|---|---|
| Sites | 8 |
| Parcelles/site | 3 |
| Variables (PatternComponent) | 1 (humidité volumique) |
| Répétitions | 3 |
| Profondeurs | 4 (10, 30, 60, 90 cm) |
| Fréquence | Semi-horaire (48/jour) |
| Durée archive | 20 ans |
| Lignes/horodatage | 12 (1 var × 3 rép × 4 prof) |

### Volume table source (t_swc_swc)

```
Lignes/an = 48 × 365 × 24 parcelles × 12 = 5 045 760
Sur 20 ans = 100 915 200 ≈ 100 M lignes
Taille heap ≈ 100 M × 512 bytes = 48 GB
```

### Volume synthèse MONTHLY avec D=5 niveaux de droits

```
Combinaisons = 24 parcelles × 4 profondeurs × 240 mois × 5 droits
             = 115 200 lignes
Volume       ≈ 115 200 × 100 bytes = 11 MB
```

*(Nota : si on décompose site et parcelle séparément comme dans l'exemple,
24 parcelles = 8 sites × 3 parcelles ; la décomposition se fait au niveau
parcelle — le site est déduit par jointure avec tr_sites_sit)*

### Requêtes synthèse typiques

| Requête | Lignes lues dans synthèse | Durée estimée |
|---|---|---|
| Count total (tous axes libres) | 115 200 | ~2 ms |
| Filtré par site (8 sites, 1 fixé) | 14 400 | ~0.5 ms |
| Filtré par site + parcelle | 4 800 | ~0.2 ms |
| Filtré par site + parcelle + période 1 an | 240 | ~0.05 ms |

### Requêtes extraction via index B-tree

| Filtre | Lignes `referencevalue` scannées | Durée estimée |
|---|---|---|
| 1 site, toutes parcelles, toute période | 4 200 000 (seek 24 parcelles × range) | ~500 ms |
| 1 site, 1 parcelle, toute période | 1 680 000 (range continu) | ~200 ms |
| 1 site, 1 parcelle, 1 profondeur, 1 mois | ~175 (48 × 1 × 4 rép ÷ 4 prof) | < 1 ms |
| 1 site, toutes parcelles, 1 an | ~210 240 | ~25 ms |

Comparé au seq scan JSONPath actuel (100 M lignes → > 60 s), le gain est de
**1 à 3 ordres de grandeur** selon la sélectivité du filtre.
