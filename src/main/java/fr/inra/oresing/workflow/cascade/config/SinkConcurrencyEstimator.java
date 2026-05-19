package fr.inra.oresing.workflow.cascade.config;

import java.util.Map;

/**
 * Estime le nombre effectif de sinks utilises par workflow selon
 * la combinaison sinkStrategy + stagingStrategy + pipelineMode + pool config .
 * Sert au hint UI dans "Stratégies cascade" pour expliquer a l'admin
 * si son choix resulte en 1 sink serie ou N sinks paralleles .
 *
 * <p>Pure : pas de dependence Spring , testable isolement .
 * Aligne sur cascade 2.1.0 ( PipelineMode + sinkParallelism ) .
 *
 * @author R.YAHIAOUI
 */
public final class SinkConcurrencyEstimator {

    private SinkConcurrencyEstimator() {}

    public enum Mode {
        /** Sink force a 1 par contrainte de strategie ( PER_CONNECTION_TEMP sticky ) . */
        SINGLE_FORCED,
        /** Sink intrinsequement serie ( MERGE_FILE = 1 COPY massif post-merge ) . */
        SINGLE_INHERENT,
        /** Sink parallele borne par pool.sink . */
        PARALLEL_POOL
    }

    public record Estimate(Mode mode, int effectiveSinks, String explanation) {}

    /**
     * Calcule l'estimation a partir d'une config courante .
     *
     * @param effectiveConfig map { fieldName : value } typiquement issue
     *        de {@link ConfigFieldRegistry#snapshot()}
     */
    public static Estimate estimate(Map<String, Object> effectiveConfig) {
        String sink     = strOf(effectiveConfig, "sinkStrategy");
        String staging  = strOf(effectiveConfig, "stagingStrategy");
        String pipeline = strOf(effectiveConfig, "pipelineMode");
        int poolSink    = intOf(effectiveConfig, "pool.sink", 1);

        if ("MERGE_FILE".equals(sink)) {
            return new Estimate(Mode.SINGLE_INHERENT, 1,
                    "MERGE_FILE = sink séquentiel ( agrégateur fichier ) , "
                            + "1 thread quelles que soient les autres options .");
        }
        if ("DIRECT_COPY".equals(sink) && "PER_CONNECTION_TEMP".equals(staging)) {
            return new Estimate(Mode.SINGLE_FORCED, 1,
                    "DIRECT_COPY + PER_CONNECTION_TEMP = sticky connection unique , "
                            + "sinkParallelism forcé à 1 ( CascadeImportPipeline ) .");
        }
        if ("DIRECT_COPY".equals(sink)
                && ("SHARED_UNLOGGED".equals(staging) || "PER_WORKFLOW_TABLE".equals(staging))) {
            // Table UNLOGGED ( partagee ou dediee ) -> sink workers
            // peuvent tourner en parallele avec autant de threads que
            // pool.sink le permet . PipelineMode ( STAGED ou PIPELINED )
            // n'influe pas sur le nombre de sink workers ; il influe
            // sur le moment ou le sink demarre vs transform .
            int n = Math.max(1, poolSink);
            String stagingLabel = "SHARED_UNLOGGED".equals(staging)
                    ? "SHARED_UNLOGGED" : "PER_WORKFLOW_TABLE";
            String pipelineLabel = "PIPELINED".equals(pipeline) ? "PIPELINED" : "STAGED";
            String why = pipelineLabel + " + " + stagingLabel + " = " + n
                    + " sink thread" + (n > 1 ? "s" : "") + " ( pool.sink ) "
                    + ( "PIPELINED".equals(pipeline)
                        ? "; transform / sink en pipeline parallele via bounded queue ."
                        : "; transform finit pour tous les chunks avant que le sink demarre ." );
            return new Estimate(n > 1 ? Mode.PARALLEL_POOL : Mode.SINGLE_FORCED, n, why);
        }
        // fallback : MERGE_FILE-like ou config indéfinie
        return new Estimate(Mode.SINGLE_INHERENT, 1,
                "Configuration indéfinie ; sink supposé séquentiel ( 1 thread ) .");
    }

    private static String strOf(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }
    private static int intOf(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? def : Integer.parseInt(String.valueOf(v)); }
        catch (NumberFormatException ex) { return def; }
    }
}
