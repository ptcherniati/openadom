package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.checker.Multiplicity;

/**
 * Définit la sémantique "cellule vide" pour les colonnes des
 * {@code referencevalue.refvalues} JSONB et expose la double facette
 * de cette définition :
 * <ul>
 *   <li>{@link #isEmpty(String)} : prédicat Java appliqué aux valeurs
 *       remontées par JDBC ( wire-format {@link String} ; SQL {@code NULL}
 *       devient Java {@code null} ).</li>
 *   <li>{@link #sqlPredicate(Multiplicity)} : prédicat SQL injecté dans
 *       les clauses {@code WHERE} qui doivent détecter les cellules
 *       vides côté base ( {@code EXISTS} de {@code hasEmpty} , filtre
 *       d'options dans les sondages distincts ) .</li>
 * </ul>
 *
 * <h2>Pourquoi cette classe</h2>
 *
 * <p>Avant cette extraction , la détection était dupliquée à 3 endroits
 * dans {@link DataRepository} avec des sémantiques divergentes :
 * <ul>
 *   <li>{@code v -> v == null} dans {@code getColumnDistinctValues} :
 *       ne détectait QUE le SQL {@code NULL} . Ratait les chaînes vides
 *       {@code ""} qui sont la représentation effective des cellules
 *       CSV vides dans le JSONB ( cf rapport ticket_519 / image #114
 *       où le bouton "( vide )" n'apparaissait pas alors que la table
 *       contenait visiblement des valeurs vides ) .</li>
 *   <li>{@code IS NULL} dans le predicat SQL ONE : même bug .</li>
 *   <li>{@code elt = 'null'::jsonb} dans le predicat SQL MANY : même
 *       bug + manquait la détection des éléments {@code ""} dans les
 *       tableaux JSON .</li>
 * </ul>
 *
 * <p>En centralisant : une seule définition de "vide" , appliquée
 * symétriquement Java et SQL , testable en isolation , évolutive
 * ( ajout futur de sémantiques type whitespace-only ou sentinelle
 * applicative = 1 endroit à modifier ) .
 *
 * <h2>Sémantique "vide"</h2>
 *
 * <p>Une cellule est <b>vide</b> si :
 * <ul>
 *   <li><b>Cellule scalaire ( {@link Multiplicity#ONE} )</b> : la valeur
 *       est absente du JSON ( clé manquante ) , {@code null} en JSON ,
 *       ou la chaîne vide {@code ""} .</li>
 *   <li><b>Cellule tableau ( {@link Multiplicity#MANY} )</b> : le tableau
 *       est absent / {@code null} , vide ( {@code []} ) , ou contient au
 *       moins un élément {@code null} ou {@code ""} . Convention :
 *       l'utilisateur veut filtrer les enregistrements où au moins une
 *       case du tableau est vide .</li>
 * </ul>
 *
 * <h2>Threadsafety</h2>
 *
 * <p>Toutes les méthodes sont statiques et pures . Aucune mutation
 * d'état . Sûres en accès concurrent .
 */
public final class EmptyCellPredicate {

    /**
     * Expression JSON path utilisée par les requêtes SQL pour extraire
     * la valeur scalaire d'une colonne {@code ONE} . Centralisée ici
     * pour rester en phase avec {@link #sqlPredicate(Multiplicity)} :
     * si on change l'opérateur ( ex {@code ->>} vs {@code #>>} ) il
     * faut le faire ici ET dans tous les call sites , la centralisation
     * réduit ce risque .
     */
    static final String JSON_PATH_ONE = "rv.refvalues #>> ARRAY[:componentKey]";

    /**
     * Expression JSON path utilisée pour déplier les éléments d'une
     * colonne {@code MANY} via {@code LATERAL} . Pendant {@link #JSON_PATH_ONE}
     * pour le cas tableau .
     */
    static final String JSON_UNFOLD_MANY =
            "jsonb_array_elements_text(COALESCE(rv.refvalues -> :componentKey, '[]'::jsonb))";

    private static final String SQL_PREDICATE_ONE =
            "(" + JSON_PATH_ONE + " IS NULL OR " + JSON_PATH_ONE + " = '')";

    /**
     * Tableau {@code MANY} considéré vide si : absent / non-array
     * ( {@code jsonb_array_length} renvoie {@code NULL} ; on coalesce
     * sur 0 ) , vide ( length 0 ) , ou contenant au moins un élément
     * {@code null} ou {@code ""} .
     */
    private static final String SQL_PREDICATE_MANY = """
            (COALESCE(jsonb_array_length(rv.refvalues -> :componentKey), 0) = 0
            OR EXISTS (
                SELECT 1 FROM jsonb_array_elements(rv.refvalues -> :componentKey) elt
                WHERE elt = 'null'::jsonb OR elt = '""'::jsonb
            ))""";

    private EmptyCellPredicate() {
        // util class
    }

    /**
     * Predicat Java pour une valeur scalaire remontée par JDBC . Couvre
     * le SQL {@code NULL} ( = Java {@code null} ) et la chaîne vide .
     *
     * @param value valeur extraite par {@code ResultSet.getString(...)}
     * @return {@code true} si la cellule est considérée comme vide
     */
    public static boolean isEmpty(final String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Predicat SQL pour la clause {@code WHERE} . Renvoie un fragment
     * paramétrable ( {@code :componentKey} ) à injecter dans la requête
     * appelante .
     *
     * @param multiplicity multiplicité de la colonne ( gouverne la
     *                     forme du predicat ; voir
     *                     {@link Multiplicity} )
     * @return fragment SQL prêt à être concaténé , entre parenthèses
     */
    public static String sqlPredicate(final Multiplicity multiplicity) {
        return switch (multiplicity) {
            case ONE -> SQL_PREDICATE_ONE;
            case MANY -> SQL_PREDICATE_MANY;
        };
    }
}
