package fr.inrae.ore.cascade.model.chunk;
import java.util.List;
public class Chunk<T> {
    private final int chunkIndex;
    private final List<T> records;
    private final ChunkMetadata metadata;
    public Chunk(int chunkIndex, List<T> records, ChunkMetadata metadata) {
        this.chunkIndex = chunkIndex;
        this.records = records;
        this.metadata = metadata;
    }
    public int chunkIndex() { return chunkIndex; }
    public List<T> records() { return records; }
    public ChunkMetadata metadata() { return metadata; }
}
