package fr.inrae.ore.cascade.model.chunk;
import java.time.Instant;
import java.util.List;
public class ChunkMetadata {
    private final int logicalRecordCount;
    public ChunkMetadata(String correlationId, String sourceName, List<?> tags, Instant timestamp, Object extra) {
        this.logicalRecordCount = 0;
    }
    private ChunkMetadata(int logicalRecordCount) { this.logicalRecordCount = logicalRecordCount; }
    public int logicalRecordCount() { return logicalRecordCount; }
    public ChunkMetadata withLogicalRecordCount(int count) { return new ChunkMetadata(count); }
}
