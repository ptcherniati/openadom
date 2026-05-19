package fr.inra.oresing.workflow.cascade.config;

import fr.inrae.ore.cascade.core.execution.WorkflowPoolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;

/**
 * Implementation par defaut du {@link PoolReloader} branchee sur les
 * pools cascade reels via {@code WorkflowPoolRegistry.getInstance()} .
 *
 * <p>Limites :
 * <ul>
 *   <li>{@code parallelism} : resize a chaud sans drain . Threads en
 *       cours finissent leur task ; surplus libere apres
 *       {@code keepAliveTime} .</li>
 *   <li>{@code queue size} : impossible runtime ( {@code BlockingQueue}
 *       interne fixee a la construction ) .</li>
 *   <li>{@code virtualThreads} : impossible hot-swap ( type executor
 *       incompatible ) .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class CascadePoolReloader implements PoolReloader {

    /**
     * Resolveur d'{@link ExecutorService} par stage . Indirection pour
     * les tests : on peut injecter un Function qui retourne un mock .
     * Constructeur sans-arg utilise le singleton cascade .
     */
    private final Function<Stage, ExecutorService> executorResolver;

    @org.springframework.beans.factory.annotation.Autowired
    public CascadePoolReloader() {
        this.executorResolver = stage -> {
            WorkflowPoolRegistry rm = WorkflowPoolRegistry.getInstance();
            return switch (stage) {
                case SOURCE    -> rm.getSourceExecutor();
                case TRANSFORM -> rm.getTransformExecutor();
                case SINK      -> rm.getSinkExecutor();
                case ORDERING  -> rm.getOrderingExecutor();
            };
        };
    }

    /** Constructeur de test : injecter un resolveur d'executor mockable . */
    public CascadePoolReloader(Function<Stage, ExecutorService> executorResolver) {
        this.executorResolver = executorResolver;
    }

    @Override
    public void resize(Stage stage, int newSize) {
        if (newSize < 1) {
            throw new IllegalArgumentException(
                    "Pool size must be >= 1 ( got " + newSize + " )");
        }
        ExecutorService es = executorResolver.apply(stage);
        if (!(es instanceof ThreadPoolExecutor tpe)) {
            throw new IllegalStateException(
                    "Pool " + stage + " is not a ThreadPoolExecutor "
                            + "( virtual threads executor ? ) ; resize requires restart");
        }
        int oldCore = tpe.getCorePoolSize();
        int oldMax  = tpe.getMaximumPoolSize();
        if (newSize > oldMax) {
            tpe.setMaximumPoolSize(newSize);
            tpe.setCorePoolSize(newSize);
        } else {
            tpe.setCorePoolSize(newSize);
            tpe.setMaximumPoolSize(newSize);
        }
        log.info("CascadePoolReloader : pool {} resized {} -> {} ( max {} -> {} )",
                stage, oldCore, newSize, oldMax, newSize);
    }

    @Override
    public PoolSnapshot snapshot(Stage stage) {
        ExecutorService es = executorResolver.apply(stage);
        if (!(es instanceof ThreadPoolExecutor tpe)) {
            return new PoolSnapshot(stage, -1, -1, -1, -1, -1, -1);
        }
        return new PoolSnapshot(
                stage,
                tpe.getCorePoolSize(),
                tpe.getMaximumPoolSize(),
                tpe.getActiveCount(),
                tpe.getPoolSize(),
                tpe.getQueue().size(),
                tpe.getQueue().size() + tpe.getQueue().remainingCapacity());
    }
}