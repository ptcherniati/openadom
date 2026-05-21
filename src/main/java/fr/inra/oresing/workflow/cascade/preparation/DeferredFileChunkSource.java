package fr.inra.oresing.workflow.cascade.preparation;

import fr.inra.oresing.workflow.cascade.FileChunkSource;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.core.Source;
import fr.inrae.ore.cascade.model.workflow.WorkflowConfig;

import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Source cascade {@code Source<Path>} qui differe la resolution du
 * chemin d'entree jusqu'au hook {@link #onWorkflowStart(WorkflowConfig)} .
 *
 * <p>Sert de complement au SPI cascade 3.3.0 {@code DataPreparator} :
 * lorsque le preparator produit le fichier ( CSV re-encode , extrait
 * d'archive , reference cache pre-warm , etc . ) PENDANT le scope du
 * workflow cascade , la source ne peut etre construite avec un Path
 * fixe au moment de WorkflowBuilder.build() puisque ledit Path n'existe
 * pas encore . Cette source resoud lazy via un {@link Supplier} fourni
 * par le caller , typiquement adosse a un {@link java.util.concurrent.atomic.AtomicReference}
 * dont le preparator a la responsabilite de set la valeur .
 *
 * <p>Ordre garanti par cascade ( cf WorkflowExecutor ) :
 * <ol>
 *   <li>{@code beforeWorkflow} interceptors firing ;</li>
 *   <li>{@code DataPreparator.prepare(ctx)} : produit le Path , set
 *       l'AtomicReference ;</li>
 *   <li>{@link #onWorkflowStart(WorkflowConfig)} : resoud le Path ,
 *       construit le {@link FileChunkSource} delegate ;</li>
 *   <li>{@link #read(String)} : delegate au FileChunkSource .</li>
 * </ol>
 *
 * <p>Si le Supplier renvoie {@code null} pendant onWorkflowStart , une
 * {@link IllegalStateException} est levee : cela signifie que le
 * preparator n'a pas livre son contrat , faute usually programmatique
 * cote host . Aucun fallback magique : on prefere echouer fort que
 * tourner sur un fichier vide ou un Path obsolete .
 *
 * <p>{@link #getName()} retourne un nom logique fourni au constructeur
 * ( en general derive de l'id du binaryfile source , pas du Path qui
 * n'est pas connu ) , de sorte que l'evenement {@code WorkflowStartEvent}
 * cascade publie un nom de source coherent meme avant la resolution
 * du Path .
 *
 * @author R.YAHIAOUI
 * @since openadom adoption cascade 3.3.0 ( Phase 2 )
 */
public final class DeferredFileChunkSource implements Source<Path> {

    private final String           sourceName;
    private final Supplier<Path>   pathSupplier;
    private final Path             chunksDir;
    private final int              fallbackChunkSizeLines;

    /** Resolved lazily by onWorkflowStart , null avant . AtomicReference pour
     *  visibilite cross-thread ( cascade peut appeler read sur un worker
     *  different du thread qui a fait onWorkflowStart , mais via une
     *  synchronisation explicite cote executor ) . */
    private final AtomicReference<FileChunkSource> delegateRef = new AtomicReference<>();

    /**
     * @param sourceName             nom logique pour les events cascade
     *                               ( ex : {@code "deferred-csv:<binaryFileId>"} )
     * @param pathSupplier           supplier evalue dans
     *                               {@link #onWorkflowStart(WorkflowConfig)} ;
     *                               doit avoir ete renseigne par le
     *                               {@code DataPreparator} en amont
     * @param chunksDir              repertoire ou les chunks physiques
     *                               seront ecrits par le delegate
     * @param fallbackChunkSizeLines fallback chunk size si
     *                               {@link WorkflowConfig#sourceChunkSize()}
     *                               vaut {@code Integer.MAX_VALUE}
     */
    public DeferredFileChunkSource(String         sourceName,
                                   Supplier<Path> pathSupplier,
                                   Path           chunksDir,
                                   int            fallbackChunkSizeLines) {
        this.sourceName             = Objects.requireNonNull(sourceName, "sourceName must not be null");
        this.pathSupplier           = Objects.requireNonNull(pathSupplier, "pathSupplier must not be null");
        this.chunksDir              = Objects.requireNonNull(chunksDir, "chunksDir must not be null");
        this.fallbackChunkSizeLines = fallbackChunkSizeLines;
    }

    @Override
    public void onWorkflowStart(WorkflowConfig config) {
        FileChunkSource current = delegateRef.get();
        if (current != null) {
            // Idempotence defensive : si cascade appelle onWorkflowStart
            // plusieurs fois ( ne devrait pas , mais le contrat lifecycle
            // n'interdit pas ) on re-resoud pas , on delegate .
            current.onWorkflowStart(config);
            return;
        }
        Path resolved = pathSupplier.get();
        if (resolved == null) {
            throw new IllegalStateException(
                    "DataPreparator did not produce a Path before the SOURCE stage for "
                            + sourceName
                            + " ; ensure the preparator sets the AtomicReference inside prepare(ctx)");
        }
        FileChunkSource created = new FileChunkSource(resolved, chunksDir, fallbackChunkSizeLines);
        delegateRef.set(created);
        created.onWorkflowStart(config);
    }

    @Override
    public Stream<Chunk<Path>> read(String correlationId) {
        return requireDelegate().read(correlationId);
    }

    @Override
    public String getName() {
        return sourceName;
    }

    @Override
    public void validate() {
        // Avant onWorkflowStart le delegate n'existe pas ; le Path n'est
        // pas resolvable . Valider la config statique ( chunksDir non null ,
        // fallback > 0 ) et deferer la validation du Path jusqu'apres la
        // resolution lazy . Cascade rappelle validate sur le delegate
        // implicitement via FileChunkSource.read() ; pas de duplicate ici .
        if (chunksDir == null) {
            throw new IllegalStateException("chunksDir is null for source " + sourceName);
        }
        if (fallbackChunkSizeLines <= 0) {
            throw new IllegalArgumentException(
                    "fallbackChunkSizeLines must be > 0 ( got " + fallbackChunkSizeLines + " )");
        }
        FileChunkSource current = delegateRef.get();
        if (current != null) {
            current.validate();
        }
    }

    @Override
    public long estimatedRecordCount() {
        FileChunkSource d = delegateRef.get();
        return d != null ? d.estimatedRecordCount() : -1L;
    }

    @Override
    public OptionalInt estimatedTotalChunks() {
        FileChunkSource d = delegateRef.get();
        return d != null ? d.estimatedTotalChunks() : OptionalInt.empty();
    }

    private FileChunkSource requireDelegate() {
        FileChunkSource d = delegateRef.get();
        if (d == null) {
            throw new IllegalStateException(
                    "DeferredFileChunkSource.read() called before onWorkflowStart() : "
                            + sourceName
                            + " ; cascade lifecycle invariant violated");
        }
        return d;
    }
}