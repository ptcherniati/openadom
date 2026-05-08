package fr.inrae.ore.cascade.core.monitoring;
import fr.inrae.ore.cascade.model.listener.WorkflowListener;
import fr.inrae.ore.cascade.model.workflow.Workflow;
import java.util.List;
public abstract class WorkflowEventBus {
    private static WorkflowEventBus instance;
    private boolean heartbeatEnabled = false;
    private long heartbeatPeriodMillis = 0L;
    public static WorkflowEventBus getInstance() {
        if (instance == null) instance = new WorkflowEventBus() {};
        return instance;
    }
    public void register(WorkflowListener listener) {}
    public void subscribe(WorkflowListener listener) {}
    public void unsubscribe(WorkflowListener listener) {}
    public boolean cancel(String correlationId, String reason) { return false; }
    public void setLifecycleHeartbeatPeriodMillis(long periodMs) { this.heartbeatPeriodMillis = periodMs; }
    public void setLifecycleHeartbeatEnabled(boolean enabled) { this.heartbeatEnabled = enabled; }
    public boolean isLifecycleHeartbeatEnabled() { return heartbeatEnabled; }
    public long getLifecycleHeartbeatPeriodMillis() { return heartbeatPeriodMillis; }
    public List<Workflow> getAllActiveWorkflows() { return List.of(); }
}
