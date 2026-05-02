package fr.inra.oresing.workflow.cascade.config;

import java.util.Map;

/**
 * Estime le nombre effectif de sinks utilises par workflow selon
 * la combinaison strategy + pool config . Sert au hint UI dans
 * "Stratégies cascade" pour expliquer a l'admin si son choix
 * resulte en 1 sink serie ou N sinks paralleles .
 *
 * <p>Pure : pas de dependence Spring , testable isolement .
 *
 * @author R.YAHIAOUI
 */
public final class SinkConcurrencyEstimator {

    private SinkConcurrencyEstimator() {}

    public enum Mode {
        SINGLE_FORCED,           // sink force a 1 ( PER_CONNECTION_TEMP sticky )
        SINGLE_INHERENT,         // sink intrinsequement serie ( MERGE_FILE merge-file )
        PARALLEL_POOL,           // sink parallel borne par poolSink ( SHARED_UNLOGGED async )
        PARALLEL_DIRECT_WRITE    // sink parallel via SYNC + directWriteParallel
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
        String exec     = strOf(effectiveConfig, "executionMode");
        boolean dwp     = boolOf(effectiveConfig, "directWriteParallel");
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
            // SHARED_UNLOGGED + PER_WORKFLOW_TABLE : meme regle de
            // parallelisme ( table UNLOGGED accessible cross-conn -> sink
            // workers ne sont pas sticky a une seule connexion ) . Le nom
            // de la table differe ( partagee vs dediee ) mais cote sink
            // pool , c'est equivalent .
            int n = "ASYNC".equals(exec) ? poolSink : (dwp ? poolSink : 1);
            String stagingLabel = "SHARED_UNLOGGED".equals(staging)
                    ? "SHARED_UNLOGGED" : "PER_WORKFLOW_TABLE";
            String why = "ASYNC".equals(exec)
                    ? "ASYNC + " + stagingLabel + " = " + n + " sink threads ( pool.sink ) ."
                    : (dwp
                        ? "SYNC + directWriteParallel + " + stagingLabel + " = " + n
                                + " sink threads ( pool.sink ) ."
                        : "SYNC + " + stagingLabel + " sans directWriteParallel = 1 sink ( serie ) .");
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
    private static boolean boolOf(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(v));
    }
    private static int intOf(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        try { return v == null ? def : Integer.parseInt(String.valueOf(v)); }
        catch (NumberFormatException ex) { return def; }
    }
}
