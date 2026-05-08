package fr.inrae.ore.cascade.core.execution;
import java.util.List;
import java.util.concurrent.ExecutorService;
public class WorkflowPoolRegistry {
    private static final WorkflowPoolRegistry INSTANCE = new WorkflowPoolRegistry();
    public static WorkflowPoolRegistry getInstance() { return INSTANCE; }
    public static boolean isInitialized() { return false; }
    public static boolean useVirtualThreads() { return false; }
    public List<PoolSnapshot> snapshotPools() { return List.of(); }
    public ExecutorService getSourceExecutor() { return null; }
    public ExecutorService getTransformExecutor() { return null; }
    public ExecutorService getSinkExecutor() { return null; }
    public ExecutorService getOrderingExecutor() { return null; }
}
