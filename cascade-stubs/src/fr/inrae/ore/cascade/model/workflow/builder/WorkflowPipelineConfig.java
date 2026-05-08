package fr.inrae.ore.cascade.model.workflow.builder;
import fr.inrae.ore.cascade.model.workflow.PipelineMode;
import fr.inrae.ore.cascade.model.workflow.Workflow;
public interface WorkflowPipelineConfig {
    WorkflowPipelineConfig withCorrelationId(String correlationId);
    WorkflowPipelineConfig withSourceChunkSize(int size);
    WorkflowPipelineConfig withCollectorChunkSize(int size);
    WorkflowPipelineConfig withMaxErrors(int max);
    WorkflowPipelineConfig withPipelineMode(PipelineMode mode);
    WorkflowPipelineConfig withPipelineQueueCapacity(int capacity);
    WorkflowPipelineConfig withSinkParallelism(int parallelism);
    WorkflowPipelineConfig enableMetrics();
    Workflow build();
}
