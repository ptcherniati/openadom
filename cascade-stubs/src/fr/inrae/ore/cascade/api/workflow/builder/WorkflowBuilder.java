package fr.inrae.ore.cascade.api.workflow.builder;
import fr.inrae.ore.cascade.model.core.ChunkCollector;
import fr.inrae.ore.cascade.model.core.Sink;
import fr.inrae.ore.cascade.model.core.Source;
import fr.inrae.ore.cascade.model.core.Transformation;
import fr.inrae.ore.cascade.model.workflow.PipelineMode;
import fr.inrae.ore.cascade.model.workflow.Workflow;
import fr.inrae.ore.cascade.model.workflow.builder.WorkflowPipeline;
import fr.inrae.ore.cascade.model.workflow.builder.WorkflowPipelineConfig;
public class WorkflowBuilder {
    private WorkflowBuilder() {}
    public static InitBuilder create() { return new InitBuilder(); }
    public static class InitBuilder {
        public UserBuilder forUser(String userId) { return new UserBuilder(); }
    }
    public static class UserBuilder {
        public SourceBuilder from(Source<?> source) { return new SourceBuilder(); }
    }
    public static class SourceBuilder {
        public WorkflowPipeline transform(Transformation<?,?> t) { return new PipelineImpl(); }
    }
    private static class PipelineImpl implements WorkflowPipeline {
        public WorkflowPipelineConfig to(Sink<?> sink) { return new ConfigImpl(); }
        public WorkflowPipeline collect(ChunkCollector<?> c) { return this; }
    }
    private static class ConfigImpl implements WorkflowPipelineConfig {
        public WorkflowPipelineConfig withCorrelationId(String s) { return this; }
        public WorkflowPipelineConfig withSourceChunkSize(int size) { return this; }
        public WorkflowPipelineConfig withCollectorChunkSize(int size) { return this; }
        public WorkflowPipelineConfig withMaxErrors(int max) { return this; }
        public WorkflowPipelineConfig withPipelineMode(PipelineMode mode) { return this; }
        public WorkflowPipelineConfig withPipelineQueueCapacity(int capacity) { return this; }
        public WorkflowPipelineConfig withSinkParallelism(int p) { return this; }
        public WorkflowPipelineConfig enableMetrics() { return this; }
        public Workflow build() { return null; }
    }
}
