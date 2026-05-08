package fr.inrae.ore.cascade.model.core;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.collector.CollectorContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
public interface ChunkCollector<T> {
    String getName();
    void initialize(CollectorContext context);
    void accept(Chunk<T> chunk);
    CompletableFuture<Optional<Chunk<T>>> finish();
}
