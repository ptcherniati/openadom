package fr.inra.oresing.workflow.cascade;

/**
 * Capability marker exposed by sinks whose effective row count differs
 * from what cascade observes through {@code Chunk.records().size()} .
 *
 * <p>Cascade calcule {@code WorkflowResult.recordsProcessed()} en
 * cumulant la taille des chunks emis par le sink ( {@code Chunk.recordCount()} ) .
 * Pour les sinks qui ecrivent <em>une unite opaque</em> ( typiquement
 * {@link StoreAllPathSink} qui recoit 1 path agrege contenant N rows ) ,
 * cette valeur reflete le nombre d'unites , pas le nombre reel de rows
 * effectivement persistees en base . Le pipeline doit alors interroger
 * le sink via cette interface pour obtenir le rowcount reel et le
 * propager a {@code workflow_log.records_processed} ( et donc au
 * dashboard Integrite ) .
 *
 * <p>Contract :
 * <ul>
 *   <li>Le compteur est cumulatif : {@link #getRowsWritten()} retourne
 *       la somme depuis le dernier {@code setup} .</li>
 *   <li>Une implementation correcte n'incremente le compteur qu'apres
 *       confirmation de la persistance ( pas avant ) , afin que le
 *       getter ne mente pas en cas d'exception .</li>
 *   <li>Implementations doivent reset {@code rowsWritten} dans
 *       {@code Sink.setup} pour rester correctes si cascade re-utilise
 *       l'instance entre workflows .</li>
 * </ul>
 *
 * @since openADOM cascade 2.3.0 integration
 * @author R.YAHIAOUI
 */
public interface RowCountingSink {

    /**
     * @return cumul des rows reellement persistees en base par ce sink
     *         depuis le dernier {@code setup} ; zero quand aucune
     *         ecriture n'a eu lieu .
     */
    long getRowsWritten();
}
