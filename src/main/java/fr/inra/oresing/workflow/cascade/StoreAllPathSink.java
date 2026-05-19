package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.core.Sink;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cascade {@link Sink} pour le pipeline MERGE_FILE : recoit un chunk unique
 * contenant le path du fichier {@code merged.csv} produit par
 * {@link MergedFileChunkCollector#finish()} , et lance 1 seul {@code COPY}
 * massif Postgres vers la table finale via
 * {@link DataRepository#storeAll(Path)} .
 *
 * <p>Combine avec le collector , l'UI cascade reflete fidelement la realite :
 *
 * <ul>
 *   <li><b>SOURCE</b> : N chunks lus depuis le CSV upload</li>
 *   <li><b>TRANSFORM</b> : N chunks transformes ( ecrits sur disque )</li>
 *   <li><b>COLLECTOR</b> : N chunks accumules , puis 1 chunk merge produit</li>
 *   <li><b>SINK</b> : <b>1 chunk recu = 1 COPY DB reel</b></li>
 * </ul>
 *
 * <p>Plus de compteur SINK trompeur a {@code &times; 222} alors qu'aucune
 * ecriture DB n'a eu lieu . Plus de step post-cascade {@code storeAll} dans
 * openADOM puisque le sink l'execute en bout de pipeline cascade .
 *
 * @author R.YAHIAOUI
 */
public final class StoreAllPathSink implements Sink<Path>, RowCountingSink {

    private final DataRepository repository;
    /**
     * Optionnel : si fourni , chaque batch UPSERT publie son rowcount via
     * {@link WorkflowActiveRegistry#addFinalRows} et chaque transition de
     * sous-phase ( MERGE_LOCAL / TEMP_LOAD / UPSERT_FINAL ) via
     * {@link WorkflowActiveRegistry#setSubPhase} . La live view
     * MERGE_FILE consomme ces signaux pour rendre 3 progress bars
     * distinctes ( merge local % , COPY -> TEMP indeterminate , UPSERT
     * -> finale % ) . Null pour les usages hors-pipeline ( tests , appels
     * directs ) ou la sink fonctionne sans publication de progress .
     */
    private final WorkflowActiveRegistry registry;

    /**
     * Mode "deferred to caller" pour MERGE_FILE ( phase B / cascade 3.0.0 ) :
     * quand {@code true} , {@link #write(Chunk)} ne lance pas
     * {@link DataRepository#storeAll} inline sur le thread sink-1 mais capte
     * le chemin du {@code merged.csv} . Le caller invoquera plus tard la
     * methode {@link #takeDeferredMergedPath()} pour recuperer le path et
     * executer le UPSERT sur sa propre connexion ( afterCommit Spring tx ) .
     * Evite le deadlock sink-1 vs caller-thread sur les row-locks de la
     * table finale ( meme cause que le DEFERRED_TO_CALLER cascade 3.0.0
     * cote StagingPostgresSink ) .
     *
     * <p>Hors tx Spring ( {@code false} ) : comportement historique , storeAll
     * inline dans {@link #write} = compatible cascade 2.x .
     */
    private final boolean deferToCaller;

    /**
     * Path capte en mode {@link #deferToCaller} . AtomicReference pour la
     * visibilite cross-thread ( sink-1 ecrit , caller-thread lit ) .
     * Consomme une fois via {@link #takeDeferredMergedPath()} .
     */
    private final AtomicReference<Path> deferredMergedPath = new AtomicReference<>();

    /**
     * correlationId du workflow en cours . Capte au {@code setup} ;
     * {@link AtomicReference} pour rester thread-safe en cas de
     * publication parallele future ( aujourd'hui le contrat sink
     * MERGE_FILE est sequentiel ) .
     */
    private final AtomicReference<UUID> currentCorrelationId = new AtomicReference<>();

    /**
     * Cumul des rows reellement ecrites en table finale par
     * {@link DataRepository#storeAll(Path)} depuis le dernier
     * {@link #setup(String)} . Lue par {@code CascadeImportPipeline}
     * via le contrat {@link RowCountingSink#getRowsWritten()} pour
     * surcharger {@code workflow_log.records_processed} ( cascade
     * voit 1 chunk = 1 path emis , pas le rowcount du COPY interne ) .
     *
     * <p>{@link AtomicLong} pour rester correct si cascade decidait un
     * jour d'invoquer {@link #write} sur plusieurs threads ( aujourd'hui
     * le path MERGE_FILE n'emet qu'un chunk unique ; defensif ) .
     */
    private final AtomicLong rowsWritten = new AtomicLong(0L);

    /** Constructeur historique : pas de publication de progress sub-phase ( tests ) . */
    public StoreAllPathSink(DataRepository repository) {
        this(repository, null, false);
    }

    /**
     * Constructeur instrumente sans deferred mode . Compatible cascade 2.x .
     */
    public StoreAllPathSink(DataRepository repository, WorkflowActiveRegistry registry) {
        this(repository, registry, false);
    }

    /**
     * Constructeur complet : registry + mode deferred ( phase B ) .
     *
     * @param deferToCaller si {@code true} , {@link #write} capte le path
     *                      au lieu d invoquer storeAll inline ; le caller
     *                      doit invoquer {@link #takeDeferredMergedPath} et
     *                      executer le UPSERT sur sa propre connexion
     *                      ( typiquement Spring afterCommit ) .
     */
    public StoreAllPathSink(DataRepository repository, WorkflowActiveRegistry registry,
                            boolean deferToCaller) {
        this.repository    = Objects.requireNonNull(repository, "repository cannot be null");
        this.registry      = registry;
        this.deferToCaller = deferToCaller;
    }

    @Override
    public String getName() {
        return "StoreAllPathSink";
    }

    /**
     * Reset du compteur a chaque demarrage de workflow . Defensif : meme
     * si le pipeline cree aujourd'hui une instance par {@code execute()} ,
     * le contrat {@link RowCountingSink} exige {@code getRowsWritten}
     * cumulatif depuis le dernier {@code setup} pour rester correct si
     * cascade re-utilise un sink entre workflows ( pooling , reuse
     * future-proof ) .
     */
    @Override
    public void setup(String correlationId) {
        rowsWritten.set(0L);
        deferredMergedPath.set(null);
        UUID corr = parseUuid(correlationId);
        currentCorrelationId.set(corr);
    }

    /**
     * Ecrit le merged.csv ( chunk unique produit par le collector ) dans la
     * table finale via {@code COPY ... FROM STDIN} . 1 chunk = 1 COPY DB
     * massif . Idempotent du point de vue du sink : le repository peut
     * lever une exception si la transaction echoue ; cascade gerera la
     * remontee + rollback via le mecanisme habituel .
     */
    @Override
    public void write(Chunk<Path> chunk) {
        if (chunk.records().isEmpty()) {
            return;
        }
        Path mergedFile = chunk.records().get(0);

        if (deferToCaller) {
            // Capture seulement ; le caller invoquera storeAll en afterCommit
            // sur sa propre connexion ( evite le deadlock sink-1 vs caller
            // outer-tx sur les row-locks de la table finale ) .
            deferredMergedPath.set(mergedFile);
            return;
        }

        UUID corr = currentCorrelationId.get();
        java.util.function.LongConsumer onBatch = (registry != null && corr != null)
                ? n -> registry.addFinalRows(corr, n)
                : n -> { };
        java.util.function.Consumer<String> onPhase = (registry != null && corr != null)
                ? phase -> registry.setSubPhase(corr, phase)
                : phase -> { };
        long upserted = repository.storeAll(mergedFile, onBatch, onPhase);
        rowsWritten.addAndGet(upserted);
    }

    /**
     * Renvoie le path {@code merged.csv} capte en mode {@link #deferToCaller} ,
     * et reset le slot ( consume-once ) . Toujours {@link java.util.Optional#empty()}
     * en mode synchrone ou avant que cascade ait emit le chunk merge .
     *
     * <p>Le caller doit invoquer {@code repository.storeAll} sur le path
     * renvoye depuis sa propre connexion ( typiquement
     * {@code TransactionSynchronization.afterCommit()} ) .
     */
    public java.util.Optional<Path> takeDeferredMergedPath() {
        Path p = deferredMergedPath.getAndSet(null);
        return java.util.Optional.ofNullable(p);
    }

    /**
     * Permet au runner deferred de reporter le rowcount apres execution
     * sur la connexion caller . Sans ce setter , {@link #getRowsWritten}
     * resterait a 0 en mode deferred ( {@link #write} ne fait pas le
     * UPSERT ) ce qui casse le {@code effectiveRecordsProcessed} dans
     * {@code workflow_log.records_processed} .
     */
    public void recordDeferredRowsWritten(long rows) {
        rowsWritten.set(rows);
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s); }
        catch (IllegalArgumentException e) { return null; }
    }

    @Override
    public void teardown(String correlationId) {
        // No-op .
    }

    @Override
    public long getRowsWritten() {
        return rowsWritten.get();
    }
}