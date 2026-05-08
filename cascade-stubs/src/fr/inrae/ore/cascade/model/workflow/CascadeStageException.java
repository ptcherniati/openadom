package fr.inrae.ore.cascade.model.workflow;
public class CascadeStageException extends RuntimeException {
    private final WorkflowStage stageValue;
    public CascadeStageException(String message, WorkflowStage stage) {
        super(message);
        this.stageValue = stage;
    }
    public CascadeStageException(String message, WorkflowStage stage, Throwable cause) {
        super(message, cause);
        this.stageValue = stage;
    }
    public WorkflowStage stage() { return stageValue; }
}
