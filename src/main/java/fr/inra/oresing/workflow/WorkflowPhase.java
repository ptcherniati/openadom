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
 * {@link #DELETE_ROWS} -&gt; {@link #SYNTHESIS_REBUILD}
 * ( inside the atomic tx that toggles {@code binaryfile.published=false} ;
 *   commit visibility and synthesis rebuild are folded into the same step )
 * -&gt; {@link #DONE} .
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

    /**
     * Preparation du contexte cascade : chargement des LineCheckers ,
     * resolution des reference rows pour displayByNaturalKey , normalisation
     * CSV ( BOM / re-encoding ) , pre-warm reference cache . Peut prendre
     * 1-3 min sur gros datatypes car DataService.getAsynchroneImporterContext
     * charge en memoire toutes les rows de chaque ReferenceType lie au
     * datatype publie .
     */
    public static final String CASCADE_PREPARING  = "CASCADE_PREPARING";

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

    /** Reconstruction reference_reference apres bulk UPSERT ( shared SQL flow ) . */
    public static final String REFREF_REBUILD     = "REFREF_REBUILD";

    /** Re-encodage CSV ( sous-phase de {@link #CASCADE_PREPARING} ) :
     *  {@code DataImporter.prepareContextForDataTreatment} reecrit le body
     *  CSV vers un fichier temp via CSVPrinter ( ou fast-path skip-reencoding )
     *  pour que le chunker downstream puisse splitter les lignes en toute
     *  securite ( quotes / newlines embarques ) . Peut prendre 30s-3min
     *  sur 1M+ lignes . */
    public static final String CSV_REENCODING     = "CSV_REENCODING";

    /** Pre-warm du cache reference ( sous-phase de {@link #CASCADE_PREPARING} ) :
     *  scan du fichier temp pour pre-calculer les valeurs de reference
     *  vues >= 2 fois , evite N appels DB par chunk transform . Peut
     *  prendre 30s-2min selon le nombre de references distinctes . */
    public static final String PREWARM_REFS       = "PREWARM_REFS";

    /** Pre-DELETE counting via {@code SELECT count(*) ... WHERE binaryfile=?}
     *  pour annoncer recordsTotal au UI avant le DELETE potentiellement long . */
    public static final String COUNTING_ROWS      = "COUNTING_ROWS";

    /** Workflow terminé . */
    public static final String DONE               = "DONE";

    /**
     * Sous-phases qui s'execitent APRES que la bar "UPSERT staging -> table
     * finale" a atteint 100 % mais AVANT que le workflow ne passe
     * COMPLETED . Le bloc finalize oa-live ( WorkflowFinalizeBadge.vue )
     * rend un spinner indeterminate avec le label localise via
     * {@code phase.<lowercase>} pour eviter que l'utilisateur croie que
     * la cascade est figee pendant 10 s a 2 min de traitement post-UPSERT .
     *
     * <p>Centralise ici pour eviter la divergence entre la liste backend
     * ( source : {@link
     * fr.inra.oresing.workflow.phase.WorkflowPhaseTracker#transitionTo} )
     * et la liste frontend ( oa-live POST_UPSERT_PHASES_LABELS ) .
     */
    public static final java.util.Set<String> POST_UPSERT_PHASES = java.util.Set.of(
            REFREF_REBUILD,
            SYNTHESIS_REBUILD,
            CACHE_CAPTURE
    );
}
