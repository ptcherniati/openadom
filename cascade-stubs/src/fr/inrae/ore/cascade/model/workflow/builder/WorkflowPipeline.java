package fr.inrae.ore.cascade.model.workflow.builder;
import fr.inrae.ore.cascade.model.core.ChunkCollector;
import fr.inrae.ore.cascade.model.core.Sink;
public interface WorkflowPipeline {
    WorkflowPipelineConfig to(Sink<?> sink);
    WorkflowPipeline collect(ChunkCollector<?> collector);
    // Legacy aliases kept for compatibility
    default WorkflowPipelineConfig withSink(Sink<?> sink) { return to(sink); }
    default WorkflowPipeline withCollector(ChunkCollector<?> c) { return collect(c); }
}
