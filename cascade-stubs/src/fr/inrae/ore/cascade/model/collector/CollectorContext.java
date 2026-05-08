package fr.inrae.ore.cascade.model.collector;
import java.nio.file.Path;
public class CollectorContext {
    private final String correlationId;
    public CollectorContext(String correlationId, int slotIndex, Object a, Object b, Object c, Path tempDir) {
        this.correlationId = correlationId;
    }
    public String correlationId() { return correlationId; }
}
