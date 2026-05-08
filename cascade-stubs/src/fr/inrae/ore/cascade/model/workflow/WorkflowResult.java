package fr.inrae.ore.cascade.model.workflow;
import fr.inrae.ore.cascade.api.defaults.db.staging.DeferredFinalize;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
public interface WorkflowResult {
    ProcessingStatus status();
    List<String> errors();
    Optional<Throwable> fatalError();
    Optional<WorkflowStage> failedStage();
    long recordsProcessed();
    long recordsFailed();
    int chunksProcessed();
    Duration duration();
    Optional<DeferredFinalize> deferredFinalize();
}
