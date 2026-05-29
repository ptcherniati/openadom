package fr.inra.oresing.monitoring.compensation.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Logique partagee de nettoyage d'une row {@code binaryfile} ORPHELINE
 * ( = sans aucune row {@code referencevalue} associee ) , reutilisee par
 * plusieurs {@link fr.inra.oresing.monitoring.compensation.CompensationHandler} :
 *
 * <ul>
 *   <li>{@code IMPORT_BINARYFILE} : import qui a fail apres l'INSERT
 *       binaryfile ( Phase 1 committee ) mais avant le confirm cascade ;</li>
 *   <li>{@code DELETE_FILE_ROW} : DELETE_FILE dont les rows ont ete
 *       supprimees ( Phase DELETE_ROWS committee ) mais dont la suppression
 *       de la binaryfile elle-meme a echoue -> on la termine en avant .</li>
 * </ul>
 *
 * <p><b>REGLE D'OR ( anti-perte de donnees )</b> : on ne supprime la
 * binaryfile QUE s'il ne reste aucune {@code referencevalue} qui la
 * reference . Si des rows existent encore , cela signifie que la
 * suppression des donnees n'a PAS reellement eu lieu ( ou qu'un import a
 * reussi ) : on s'abstient pour ne jamais orpheliner / detruire des
 * donnees metier valides . Idempotent ( appel multiple = effet identique ) .
 *
 * <p>SRP : 1 composant = 1 responsabilite ( smart-delete binaryfile ) ;
 * les handlers ne sont que des adaptateurs de routing par op_type .
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class OrphanBinaryFileCleaner {

    /** Whitelist d'identifiant SQL pour interpoler le schema sans risque d'injection. */
    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private final JdbcTemplate jdbc;

    public OrphanBinaryFileCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Supprime la binaryfile {@code fileId} du schema applicatif {@code schema}
     * UNIQUEMENT si plus aucune {@code referencevalue} ne la reference .
     *
     * @throws IllegalArgumentException si le schema n'est pas un identifiant SQL valide
     */
    public void deleteIfOrphan(String schema, UUID fileId) {
        if (schema == null || !SAFE_IDENT.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid target_schema for binaryfile cleanup : " + schema);
        }
        String existsSql = "SELECT EXISTS ("
                + "SELECT 1 FROM " + schema + ".referencevalue WHERE binaryfile = ?::uuid )";
        Boolean stillHasRows = jdbc.queryForObject(existsSql, Boolean.class, fileId);

        if (Boolean.TRUE.equals(stillHasRows)) {
            log.warn("REGLE D'OR : binaryfile {}.binaryfile/{} a encore des referencevalue ; "
                    + "suppression SKIPPED ( donnees presentes = pas une orpheline )", schema, fileId);
            return;
        }
        int deleted = jdbc.update("DELETE FROM " + schema + ".binaryfile WHERE id = ?::uuid", fileId);
        log.info("OrphanBinaryFileCleaner : binaryfile {}.binaryfile/{} -> {}",
                schema, fileId, deleted > 0 ? "supprimee" : "deja absente");
    }
}
