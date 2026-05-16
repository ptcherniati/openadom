package fr.inra.oresing.workflow;

/**
 * Phase names persisted to {@code workflow_log.metadata.phase} and exposed
 * to oa-live for live progress display . Update via
 * {@link fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository#updatePhase} .
 *
 * <p>String constants instead of an enum so that workflow_log persistence
 * stays schema-stable when new phases are added in future migrations
 * ( no DB-level enum to ALTER ) and so that test fixtures can reference
 * the strings directly without compile-time enum imports .
 *
 * <h2>Conventions</h2>
 *
 * <ul>
 *   <li>UPPER_SNAKE_CASE - keeps with the existing PG schema style ;</li>
 *   <li>verb_NOUN order ( {@code DELETE_ROWS} not {@code ROWS_DELETE} ) ;</li>
 *   <li>terminal phase = {@code DONE} for every workflow type so the UI can
 *       use a single check to recognise completion .</li>
 * </ul>
 *
 * <h2>Sequencing per workflow type</h2>
 *
 * <p>PUBLISH cascade :
 * {@link #CASCADE_RUNNING} -&gt; {@link #COMMIT_VISIBILITY}
 * -&gt; {@link #SYNTHESIS_REBUILD} -&gt; ( {@link #CACHE_CAPTURE} optional )
 * -&gt; {@link #DONE} .
 *
 * <p>PUBLISH FAST ( direct COPY from cache ) :
 * {@link #DELETE_REFREF} -&gt; {@link #DELETE_EXISTING} -&gt; {@link #COPY_IN}
 * -&gt; {@link #INSERT_REFREF} -&gt; {@link #COMMIT_VISIBILITY}
 * -&gt; {@link #SYNTHESIS_REBUILD} -&gt; {@link #DONE} .
 *
 * <p>UNPUBLISH :
 * {@link #DELETE_ROWS} -&gt; {@link #COMMIT_VISIBILITY}
 * -&gt; {@link #SYNTHESIS_REBUILD} -&gt; {@link #DONE} .
 *
 * <p>DELETE_FILE :
 * {@link #DELETE_ROWS} -&gt; {@link #DELETE_FILE_ROW}
 * -&gt; {@link #SYNTHESIS_REBUILD} -&gt; {@link #DONE} .
 *
 * <p>BUILD_CACHE ( admin re-snapshot of an already-published file ) :
 * {@link #CACHE_CAPTURE} -&gt; {@link #DONE} .
 *
 * @author R.YAHIAOUI
 */
public final class WorkflowPhase {

    private WorkflowPhase() { }

    /** Cascade pipeline ( SOURCE / TRANSFORM / SINK ) en cours d'execution . */
    public static final String CASCADE_RUNNING    = "CASCADE_RUNNING";

    /** DELETE FROM reference_reference WHERE referenceid IN ( ... ) . */
    public static final String DELETE_REFREF      = "DELETE_REFREF";

    /** DELETE FROM referencevalue WHERE binaryFile = ? ( wipe existing rows ) . */
    public static final String DELETE_EXISTING    = "DELETE_EXISTING";

    /** COPY referencevalue FROM stdin ( FORMAT BINARY ) , source = cache LO . */
    public static final String COPY_IN            = "COPY_IN";

    /** INSERT INTO reference_reference ( referenceid , referencesby ) . */
    public static final String INSERT_REFREF      = "INSERT_REFREF";

    /** DELETE rows referencevalue d'un fichier ( unpublish ou delete-file ) . */
    public static final String DELETE_ROWS        = "DELETE_ROWS";

    /** DELETE de la row binaryfile elle-meme ( delete-file uniquement ) . */
    public static final String DELETE_FILE_ROW    = "DELETE_FILE_ROW";

    /** UPDATE binaryfile.published flag + COMMIT atomic + recordEnd workflow_log . */
    public static final String COMMIT_VISIBILITY  = "COMMIT_VISIBILITY";

    /** Rebuild oresisynthesis ( aggregation table per app + datatype ) . */
    public static final String SYNTHESIS_REBUILD  = "SYNTHESIS_REBUILD";

    /** Direct COPY de referencevalue vers cache Large Object ( OAVR header + binary payload ) . */
    public static final String CACHE_CAPTURE      = "CACHE_CAPTURE";

    /** Workflow terminé . */
    public static final String DONE               = "DONE";
}
