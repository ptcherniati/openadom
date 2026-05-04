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
     * {@link WorkflowActiveRegistry#setMergeFilePhase} . La live view
     * MERGE_FILE consomme ces signaux pour rendre 3 progress bars
     * distinctes ( merge local % , COPY -> TEMP indeterminate , UPSERT
     * -> finale % ) . Null pour les usages hors-pipeline ( tests , appels
     * directs ) ou la sink fonctionne sans publication de progress .
     */
    private final WorkflowActiveRegistry registry;

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
        this(repository, null);
    }

    /**
     * Constructeur instrumente : publie sub-phase + per-batch row count
     * dans {@code registry} pour alimenter la live view MERGE_FILE 3 bars .
     */
    public StoreAllPathSink(DataRepository repository, WorkflowActiveRegistry registry) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.registry   = registry;
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
        UUID corr = currentCorrelationId.get();
        java.util.function.LongConsumer onBatch = (registry != null && corr != null)
                ? n -> registry.addFinalRows(corr, n)
                : n -> { };
        java.util.function.Consumer<String> onPhase = (registry != null && corr != null)
                ? phase -> registry.setMergeFilePhase(corr, phase)
                : phase -> { };
        long upserted = repository.storeAll(mergedFile, onBatch, onPhase);
        rowsWritten.addAndGet(upserted);
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
