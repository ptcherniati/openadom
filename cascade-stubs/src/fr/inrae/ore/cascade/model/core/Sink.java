package fr.inrae.ore.cascade.model.core;
import fr.inrae.ore.cascade.model.chunk.Chunk;
public interface Sink<T> {
    String getName();
    void setup(String correlationId);
    void write(Chunk<T> chunk);
    void teardown(String correlationId);
}
