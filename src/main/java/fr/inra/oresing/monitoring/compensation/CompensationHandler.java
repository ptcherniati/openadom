package fr.inra.oresing.monitoring.compensation;

/**
 * Strategy interface : declare comment compenser un type d'operation
 * specifique en cas d'echec ( apres TTL ) .
 *
 * <p>Pour ajouter un nouveau type d'op a compenser :
 * <ol>
 *   <li>Crée un {@code @Component} qui implemente cette interface</li>
 *   <li>{@link #operationType()} retourne un identifiant unique
 *       ( ex. {@code "IMPORT_BINARYFILE"} , {@code "EXTRACTION_TMP_DIR"} )</li>
 *   <li>{@link #compensate(CompensationLogEntry)} implemente la logique
 *       de cleanup ( IDEMPOTENTE - peut etre appelee plusieurs fois sans
 *       effet de bord ) avec smart-check si necessaire pour eviter
 *       data loss</li>
 * </ol>
 *
 * <p>Au site d'usage :
 * <pre>
 *   UUID compId = compensationLogService.record(opType, table, id, ...);
 *   try {
 *       doRiskyOperation();
 *       compensationLogService.confirm(compId);
 *   } catch (Exception e) {
 *       // best-effort cleanup synchrone ; sinon sweeper rattrapera
 *       compensationLogService.compensateNow(compId);
 *       throw e;
 *   }
 * </pre>
 *
 * <p><b>REGLE D'OR</b> : un handler ne doit JAMAIS supprimer de donnees
 * metier valides . Il DOIT verifier l'etat reel via la target table
 * avant tout DELETE . Voir {@code BinaryFileCompensationHandler} comme
 * reference d'implementation .
 *
 * @author R.YAHIAOUI
 */
public interface CompensationHandler {

    /** Identifiant unique du type d'op gere ( discriminant routing ) . */
    String operationType();

    /**
     * Execute la compensation pour cette row .
     * <ul>
     *   <li>Doit etre IDEMPOTENT ( appel multiple = effet identique )</li>
     *   <li>Doit appliquer le smart-check pour proteger les donnees valides</li>
     *   <li>Throw RuntimeException si echec ( le sweeper retry avec backoff )</li>
     * </ul>
     */
    void compensate(CompensationLogEntry entry);
}
