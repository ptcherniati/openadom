package fr.inrae.ore.cascade.model.core;
import fr.inrae.ore.cascade.model.chunk.Chunk;
import fr.inrae.ore.cascade.model.workflow.WorkflowConfig;
import java.util.stream.Stream;
public interface Source<T> {
    Stream<Chunk<T>> read(String correlationId);
    default void onWorkflowStart(WorkflowConfig config) {}
    String getName();
    default void validate() {}
}
