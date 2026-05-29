package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.workflow.cascade.config.ImportProperties.IntraDuplicatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * P1-3 - Détection des doublons de clé naturelle ( contrainte
 * {@code hierarchicalKey_uniqueness} ) <b>intra-import</b> , directement sur
 * la table de staging , AVANT la boucle UPSERT de
 * {@link StagingFinalizeSql#runFinalize} .
 *
 * <h2>Pourquoi en base ( staging ) et pas en Java</h2>
 * Les 4 colonnes de la clé de conflit ( {@code application} ,
 * {@code referenceType} , {@code hierarchicalKey} , {@code patternColumnName} )
 * sont calculées en Java ( {@code DataTransformer} ) puis stockées dans la
 * colonne {@code data} JSONB du staging ( clés en minuscules ) . Un simple
 * {@code GROUP BY ... HAVING count(*) > 1} sur ces 4 champs est donc
 * <b>autoritatif par construction</b> : il groupe sur exactement les mêmes
 * champs que l'UPSERT extrait pour résoudre le {@code ON CONFLICT} . Aucune
 * re-dérivation de clé , 0 passe disque supplémentaire , 0 heap JVM ( vs un
 * {@code Set} Java qui exploserait la RAM sur 50M lignes ) .
 *
 * <h2>Garantie « même résultat final »</h2>
 * <ul>
 *   <li>{@code OFF} : aucun scan , comportement strictement identique à avant .</li>
 *   <li>{@code WARN} ( défaut ) : un scan d'agrégat en lecture seule ; en
 *       l'absence de doublon ( cas nominal ) l'import se poursuit à
 *       l'identique . En présence de doublons , on logue un WARN détaillé
 *       puis l'UPSERT se déroule comme avant ( merge silencieux cross-batch
 *       ou erreur Postgres {@code 21000} intra-batch ) - <b>donc même résultat
 *       qu'aujourd'hui</b> , mais tracé .</li>
 *   <li>{@code FAIL} : lève {@link IntraImportDuplicateException} avant toute
 *       mutation ( pas de merge silencieux , pas de {@code 21000} opaque ) .</li>
 * </ul>
 *
 * <p>Read-only : ne modifie jamais le staging ni la table cible .
 *
 * @author R.YAHIAOUI
 */
public final class IntraImportDuplicateDetector {

    private static final Logger log = LoggerFactory.getLogger(IntraImportDuplicateDetector.class);

    /** Nombre de groupes en doublon remontés dans le log / le message d'erreur . */
    private static final int SAMPLE_LIMIT = 20;

    private IntraImportDuplicateDetector() { }

    /**
     * Compte les groupes ( application , referenceType , hierarchicalKey ,
     * patternColumnName ) présents plus d'une fois dans le staging et applique
     * la {@code policy} . No-op si {@code policy == OFF} ou {@code null} .
     *
     * @param connection    connexion ( sticky finalize ) - réutilisée , pas de commit ici .
     * @param stagingTable  nom de la table de staging ( qualifié , ex
     *                      {@code oa_staging.referencevalue_import_shared} ) .
     * @param correlationId filtre SHARED_UNLOGGED ; {@code null} pour
     *                      PER_CONNECTION_TEMP ( pas de colonne correlation_id ) .
     * @param policy        politique courante ( lue à chaud via supplier ) .
     * @throws IntraImportDuplicateException si {@code policy == FAIL} et au
     *                                       moins un doublon est détecté .
     */
    public static void check(Connection connection,
                             String stagingTable,
                             String correlationId,
                             IntraDuplicatePolicy policy) throws SQLException {
        if (policy == null || policy == IntraDuplicatePolicy.OFF) {
            return;
        }
        boolean filtered = (correlationId != null && !correlationId.isBlank());

        long dupGroups;
        long excessRows;
        try (PreparedStatement ps = connection.prepareStatement(countSql(stagingTable, filtered))) {
            if (filtered) ps.setObject(1, UUID.fromString(correlationId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                dupGroups = rs.getLong(1);
                excessRows = rs.getLong(2);
            }
        }

        if (dupGroups == 0) {
            // Cas nominal : aucun doublon -> aucune trace , import inchangé .
            return;
        }

        List<String> sample = fetchSample(connection, stagingTable, filtered, correlationId);
        String detail = String.format(
                "Doublons de clé naturelle détectés dans l'import : %d clé(s) en double "
                        + "( %d ligne(s) excédentaire(s) ) . Échantillon ( application , referenceType , "
                        + "hierarchicalKey , patternColumnName , occurrences ) : %s%s",
                dupGroups, excessRows,
                String.join(" | ", sample),
                dupGroups > sample.size() ? " ..." : "");

        if (policy == IntraDuplicatePolicy.FAIL) {
            throw new IntraImportDuplicateException(detail);
        }
        // WARN : on trace mais on laisse l'UPSERT se dérouler comme avant
        // ( garantie « même résultat final » par rapport au comportement actuel ) .
        log.warn("[IntraImportDuplicate] {}", detail);
    }

    /**
     * Agrégat en un seul scan : nombre de groupes dupliqués + total des lignes
     * excédentaires ( somme de {@code n - 1} ) .
     */
    static String countSql(String stagingTable, boolean filtered) {
        return "WITH dup AS ("
                + " SELECT count(*) AS n"
                + " FROM " + stagingTable + " s"
                + (filtered ? " WHERE s.correlation_id = ?::uuid" : "")
                + " GROUP BY data->>'application' , data->>'referencetype' ,"
                + "          data->>'hierarchicalkey' , data->>'patterncolumnname'"
                + " HAVING count(*) > 1"
                + " )"
                + " SELECT COALESCE(count(*),0)::bigint AS dup_groups ,"
                + "        COALESCE(sum(n - 1),0)::bigint AS excess_rows"
                + " FROM dup";
    }

    /** Échantillon ordonné par fréquence décroissante , borné à {@link #SAMPLE_LIMIT} . */
    static String sampleSql(String stagingTable, boolean filtered) {
        return "SELECT data->>'application' , data->>'referencetype' ,"
                + "       data->>'hierarchicalkey' , data->>'patterncolumnname' , count(*) AS n"
                + " FROM " + stagingTable + " s"
                + (filtered ? " WHERE s.correlation_id = ?::uuid" : "")
                + " GROUP BY 1 , 2 , 3 , 4"
                + " HAVING count(*) > 1"
                + " ORDER BY n DESC"
                + " LIMIT " + SAMPLE_LIMIT;
    }

    private static List<String> fetchSample(Connection connection,
                                            String stagingTable,
                                            boolean filtered,
                                            String correlationId) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sampleSql(stagingTable, filtered))) {
            if (filtered) ps.setObject(1, UUID.fromString(correlationId));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(String.format("(%s , %s , %s , %s , %d)",
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getLong(5)));
                }
            }
        }
        return rows.stream().collect(Collectors.toList());
    }
}
