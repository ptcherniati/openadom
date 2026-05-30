package fr.inra.oresing.workflow.cascade;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P4b - Builder du SQL UPSERT staging -> table finale .
 *
 * <p>Deux strategies :
 * <ul>
 *   <li>{@link #buildJsonbPopulateRecord} : legacy , utilise
 *       {@code jsonb_populate_record(NULL::target, data)} . Auto-typing par Postgres
 *       via syscache lookup . Simple , robuste a tout changement de schema sans
 *       redeploy , mais 20-30% du temps finalize sur gros datasets ( bench audit ) .</li>
 *   <li>{@link #buildColumnExtraction} : extraction explicite
 *       {@code (data->>'col')::pg_type} par colonne . Types resolus une seule fois
 *       au demarrage du finalize via {@link #fetchColumnPgTypes} . Plus rapide car
 *       evite syscache lookup par row .</li>
 * </ul>
 *
 * <p>Garantie iso-resultats : les deux SQL produisent des rows identiques en valeurs et
 * types pour toute donnee valide en entree . Mapping {@code udt_name -> cast PG} couvre
 * les types utilises par {@code referencevalue} ( uuid , jsonb , ltree , text ,
 * timestamptz ) ; un type non-mappe leve une {@link IllegalStateException} explicite a
 * la construction ( fail-fast vs comportement silencieux faux ) .
 *
 * <p>Thread-safe : cache statique partage entre workflows ( meme schema -> meme types ) .
 * Cle = {@code schema + "." + table} . Recharge automatique impossible a chaud ; en cas
 * de migration Flyway ajoutant une colonne , redeploy backend pour rafraichir .
 *
 * @author R.YAHIAOUI
 */
public final class StagingUpsertSqlBuilder {

    private StagingUpsertSqlBuilder() { }

    /**
     * Cache des types de colonnes par {@code schema.table} . Populated lazy au premier
     * appel a {@link #fetchColumnPgTypes} . Eviction = redemarrage du backend ; suffisant
     * tant que les schemas ne changent pas a chaud ( pratique : Flyway migration =
     * redeploy ) .
     */
    private static final Map<String, Map<String, String>> COLUMN_TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * SQL legacy avec {@code jsonb_populate_record} . Genere le meme SQL que
     * l'implementation historique de {@code StagingFinalizeSql} pour preserver la
     * compatibilite bit-a-bit lors de la mise en place du feature flag .
     */
    public static String buildJsonbPopulateRecord(String stagingTable, String targetTableSqlId,
                                                  String columnList, boolean filtered, int batchLimit) {
        return "WITH batch AS ("
                + "   DELETE FROM " + stagingTable
                + "   WHERE ctid IN ("
                + "     SELECT ctid FROM " + stagingTable
                + (filtered ? "     WHERE correlation_id = ?" : "")
                + "     LIMIT " + batchLimit
                + "   )"
                + "   RETURNING data"
                + " )"
                + " INSERT INTO " + targetTableSqlId + " (" + columnList + ")"
                + " SELECT " + columnList
                + " FROM batch , jsonb_populate_record( NULL::" + targetTableSqlId + " , data )"
                + " ON CONFLICT ON CONSTRAINT \"hierarchicalKey_uniqueness\""
                + " DO UPDATE SET"
                + "   updateDate     = current_timestamp,"
                + "   hierarchicalKey = EXCLUDED.hierarchicalKey,"
                + "   naturalKey     = EXCLUDED.naturalKey,"
                + "   refsLinkedTo   = EXCLUDED.refsLinkedTo,"
                + "   refValues      = EXCLUDED.refValues,"
                + "   binaryFile     = EXCLUDED.binaryFile,"
                + "   \"authorization\" = EXCLUDED.\"authorization\"";
    }

    /**
     * P4b - SQL optimise avec extraction colonne par colonne . Pour chaque {@code col} de
     * {@code targetColumns} :
     * <ul>
     *   <li>type {@code jsonb} ou {@code json} -> {@code (data->'col')::jsonb}
     *       ( preserve la sous-structure JSON sans serialiser/parser ) ;</li>
     *   <li>type {@code text} ou {@code varchar} -> {@code data->>'col'} ( sans cast ,
     *       {@code ->>} retourne deja text ) ;</li>
     *   <li>autres types ( uuid , ltree , timestamptz , int4 , ... ) ->
     *       {@code (data->>'col')::pg_type} .</li>
     * </ul>
     *
     * @param targetColumns  liste des colonnes cibles ( ordre = ordre INSERT INTO )
     * @param columnPgTypes  map {@code col_name (lower) -> udt_name PG} ( cf
     *                       {@link #fetchColumnPgTypes} )
     */
    public static String buildColumnExtraction(String stagingTable, String targetTableSqlId,
                                               String[] targetColumns, Map<String, String> columnPgTypes,
                                               boolean filtered, int batchLimit) {
        String columnList = String.join(",",
                java.util.Arrays.stream(targetColumns).map(String::toLowerCase).toList());
        StringBuilder selectExprs = new StringBuilder();
        for (int i = 0; i < targetColumns.length; i++) {
            if (i > 0) selectExprs.append(',');
            selectExprs.append(buildColumnExtractExpr(targetColumns[i], columnPgTypes));
        }
        return "WITH batch AS ("
                + "   DELETE FROM " + stagingTable
                + "   WHERE ctid IN ("
                + "     SELECT ctid FROM " + stagingTable
                + (filtered ? "     WHERE correlation_id = ?" : "")
                + "     LIMIT " + batchLimit
                + "   )"
                + "   RETURNING data"
                + " )"
                + " INSERT INTO " + targetTableSqlId + " (" + columnList + ")"
                + " SELECT " + selectExprs
                + " FROM batch"
                + " ON CONFLICT ON CONSTRAINT \"hierarchicalKey_uniqueness\""
                + " DO UPDATE SET"
                + "   updateDate     = current_timestamp,"
                + "   hierarchicalKey = EXCLUDED.hierarchicalKey,"
                + "   naturalKey     = EXCLUDED.naturalKey,"
                + "   refsLinkedTo   = EXCLUDED.refsLinkedTo,"
                + "   refValues      = EXCLUDED.refValues,"
                + "   binaryFile     = EXCLUDED.binaryFile,"
                + "   \"authorization\" = EXCLUDED.\"authorization\"";
    }

    /**
     * Construit l'expression d'extraction pour une colonne donnee . Visible package-private
     * pour faciliter les tests unitaires .
     */
    static String buildColumnExtractExpr(String col, Map<String, String> columnPgTypes) {
        String lcCol = col.toLowerCase(Locale.ROOT);
        String pgType = columnPgTypes.get(lcCol);
        if (pgType == null) {
            throw new IllegalStateException("P4b : type PG inconnu pour colonne '" + col
                    + "' . Verifier que la colonne existe en DB et que fetchColumnPgTypes() "
                    + "a bien ete appelle apres migration Flyway . columnPgTypes=" + columnPgTypes);
        }
        // jsonb / json : preserve la sous-structure via -> ( vs ->> qui force serialise text ) .
        // Si data->'col' est null ( cle absente ) , ::jsonb sur SQL NULL retourne NULL = OK .
        // P4b : la cle JSON est lowercase ( JsonRowMapper : PropertyNamingStrategies.LOWER_CASE ) ;
        // on emet lcCol cote path JSON pour matcher ( ex : data->>'patterncolumnname' , pas
        // data->>'patternColumnName' qui ne matcherait jamais ) .
        if ("jsonb".equals(pgType) || "json".equals(pgType)) {
            return "(data->'" + lcCol + "')::" + pgType + " AS " + lcCol;
        }
        // text / varchar : ->>'col' retourne deja text , pas besoin de cast explicite .
        if ("text".equals(pgType) || "varchar".equals(pgType) || "bpchar".equals(pgType)) {
            return "(data->>'" + lcCol + "') AS " + lcCol;
        }
        // Tous autres types : cast explicite via ->>'col'::pg_type .
        // uuid , ltree , timestamptz , timestamp , date , int4 , int8 , bool , numeric , ...
        return "(data->>'" + lcCol + "')::" + pgType + " AS " + lcCol;
    }

    /**
     * Resout les types Postgres de toutes les colonnes d'une table cible via
     * {@code information_schema.columns} . Resultat mis en cache statique pour
     * eviter le re-query par workflow .
     *
     * <p>Cache cleared au demarrage uniquement ( pas de TTL ) . Si une migration
     * Flyway ajoute une colonne en cours de vie JVM , le cache est obsolete jusqu'au
     * prochain redemarrage backend . Acceptable car Flyway tourne au boot Spring
     * avant que les workflows ne demarrent .
     */
    public static Map<String, String> fetchColumnPgTypes(Connection conn, String schema, String table) throws SQLException {
        final String cacheKey = schema + "." + table;
        Map<String, String> cached = COLUMN_TYPE_CACHE.get(cacheKey);
        if (cached != null) return cached;

        // information_schema.columns expose udt_name = nom natif Postgres du type
        // ( uuid , jsonb , ltree , text , timestamptz , ... ) . Plus precis que
        // data_type qui retourne des libelles SQL standard parfois ambigus .
        //
        // Match case-insensitive sur schema + table : le nom de table arrive ici
        // tel qu'ecrit dans l'identifiant SQL ( ex : "referenceValue" ) , mais
        // Postgres replie les identifiants non quotes en minuscules au catalogue
        // ( table reelle = "referencevalue" ) . Une comparaison exacte ne trouvait
        // donc AUCUNE colonne -> IllegalStateException -> fallback systematique sur
        // le legacy jsonb_populate_record ( 20-30% plus lent ) a CHAQUE import .
        // lower()=lower() resout le lookup que l'identifiant soit quote ou non ,
        // sans changer les colonnes/types resolus ( iso-resultat ) .
        final String query = """
                SELECT column_name , udt_name
                FROM information_schema.columns
                WHERE lower(table_schema) = lower(?) AND lower(table_name) = lower(?)
                """;
        Map<String, String> result = new java.util.HashMap<>();
        try (var ps = conn.prepareStatement(query)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1).toLowerCase(Locale.ROOT), rs.getString(2));
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("P4b : aucune colonne trouvee pour " + cacheKey
                    + " . Verifier schema + table . Skip cache pour permettre retry .");
        }
        // putIfAbsent : si concurrent appel populate le cache d'abord , garder la 1re valeur
        // pour eviter de gaspiller une 2eme query .
        Map<String, String> existing = COLUMN_TYPE_CACHE.putIfAbsent(cacheKey, Map.copyOf(result));
        return existing != null ? existing : Map.copyOf(result);
    }

    /**
     * Vide le cache statique des types . A appeler apres une migration Flyway ajoutant des
     * colonnes ( hot reload sans redeploy ) ou depuis les tests d'integration .
     */
    public static void clearCache() {
        COLUMN_TYPE_CACHE.clear();
    }
}
