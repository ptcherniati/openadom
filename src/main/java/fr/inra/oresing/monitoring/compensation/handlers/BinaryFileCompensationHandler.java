package fr.inra.oresing.monitoring.compensation.handlers;

import fr.inra.oresing.monitoring.compensation.CompensationHandler;
import fr.inra.oresing.monitoring.compensation.CompensationLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Compensation handler pour {@code IMPORT_BINARYFILE} : supprime un
 * row binaryfile orphelin laisse par un import qui a fail apres la
 * Phase 1 ( INSERT binaryfile committed ) mais avant le confirm
 * Phase 3 ( workflow / cascade rollback ) .
 *
 * <p><b>REGLE D'OR : NE JAMAIS supprimer une row referencevalue .</b>
 * Avant tout DELETE binaryfile , on verifie qu'aucune row referencevalue
 * ne lui est associee . Si oui : le cascade a en realite reussi ( c'est
 * le confirm log qui a fail ) → on saute la compensation , on garde
 * binaryfile + referencevalue intacts , et on laisse le sweeper DELETE
 * la row de log seulement .
 *
 * <p>Sans ce smart-check , un cascade succès suivi d'un confirm log
 * raté aboutirait a {@code DELETE binaryfile → ON DELETE CASCADE
 * supprime referencevalue rows} = perte de donnees catastrophique .
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class BinaryFileCompensationHandler implements CompensationHandler {

    public static final String OP_TYPE = "IMPORT_BINARYFILE";

    /** Whitelist regex pour interpoler target_schema en SQL ( eviter injection ) . */
    private static final Pattern SAFE_IDENT = Pattern.compile("^[a-z_][a-z0-9_]*$");

    private final JdbcTemplate jdbc;

    public BinaryFileCompensationHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String operationType() { return OP_TYPE; }

    @Override
    public void compensate(CompensationLogEntry entry) {
        String schema = entry.targetSchema();
        UUID fileId   = UUID.fromString(entry.targetId());

        if (schema == null || !SAFE_IDENT.matcher(schema).matches()) {
            throw new IllegalArgumentException("Invalid target_schema for compensation : " + schema);
        }

        // ╔════════════════════════════════════════════════════════════════╗
        // ║  REGLE D'OR : NE JAMAIS supprimer une row referencevalue .    ║
        // ║  Smart-check : on ne delete binaryfile que si AUCUNE row      ║
        // ║  referencevalue ne lui est associee . Sinon le cascade a en   ║
        // ║  realite reussi → preserver les donnees .                     ║
        // ╚═══════════════════════════════════════════════════════════════╝
        // Sonar java:S2077 – "schema" est validé par SAFE_IDENT (^[a-z_][a-z0-9_]*$)
        //// => uniquement des identifiants SQL simples, sans guillemets ni caractères spéciaux.
        //// La valeur bindée (fileId) reste paramétrée via ?.
        String existsSql = "SELECT EXISTS ("
                + "SELECT 1 FROM " + schema + ".referencevalue WHERE binaryfile = ?::uuid )";
        Boolean cascadeProducedRows = jdbc.queryForObject(existsSql, Boolean.class, fileId);

        if (Boolean.TRUE.equals(cascadeProducedRows)) {
            log.warn("REGLE D'OR : binaryfile {} ( schema {} ) a des referencevalue rows associees ; "
                            + "compensation SKIPPED , log row sera DELETE seulement ( cascade succeeded , confirm rate )",
                    fileId, schema);
            return;   // pas de DELETE binaryfile
        }

        // Pas de rows referencevalue → DELETE binaryfile safe
        String deleteSql = "DELETE FROM " + schema + ".binaryfile WHERE id = ?::uuid";
        int deleted = jdbc.update(deleteSql, fileId);

        if (deleted > 0) {
            log.info("Compensated orphan binaryfile {}.binaryfile/{} ( cascade rolled back )",
                    schema, fileId);
        } else {
            log.info("binaryfile {}.binaryfile/{} already absent , log row cleanup only",
                    schema, fileId);
        }
    }
}