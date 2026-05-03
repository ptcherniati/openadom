package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.core.Sink;

import java.nio.file.Path;
import java.util.Objects;

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
public final class StoreAllPathSink implements Sink<Path> {

    private final DataRepository repository;

    public StoreAllPathSink(DataRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    @Override
    public String getName() {
        return "StoreAllPathSink";
    }

    @Override
    public void setup(String correlationId) {
        // No-op : DataRepository n'a pas de ressource a setup .
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
        repository.storeAll(mergedFile);
    }

    @Override
    public void teardown(String correlationId) {
        // No-op .
    }
}
