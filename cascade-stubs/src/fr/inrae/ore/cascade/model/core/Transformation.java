package fr.inrae.ore.cascade.model.core;
import fr.inrae.ore.cascade.model.chunk.Chunk;
public interface Transformation<A, B> {
    Chunk<B> transformChunk(Chunk<A> chunk);
    String getName();
}
