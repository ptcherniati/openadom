package fr.inra.oresing.workflow.cascade.config;

import fr.inrae.ore.cascade.model.workflow.PipelineMode;

/**
 * Override per-call des parametres strategiques du cascade import pipeline .
 *
 * <p>Utilise par les flux qui veulent surcharger les defauts globaux de
 * {@link ImportProperties} ( exposes via {@code cascade.import.*} ) pour
 * un workflow donne sans impacter les autres flux qui passent par le
 * meme pipeline cascade .
 *
 * <p><b>Cas d'usage principal :</b> la republication ( toggle publish/
 * unpublish ) qui veut un profil memoire-friendly ( STREAMING + chunk
 * petit + parallelism reduit ) , distinct de l'upload initial qui
 * favorise le throughput ( STAGED + chunk gros + parallelism eleve ) .
 *
 * <p><b>Semantique :</b> chaque field {@code null} = utilise le defaut
 * de {@code ImportProperties} ; un field non-null surcharge cette
 * valeur uniquement pour ce workflow . Les autres fields de
 * {@code ImportProperties} ( chunksTempDir , collectorChunkSize ,
 * referenceCacheMaxEntries , etc . ) ne sont pas surchargables ; ils
 * restent globaux ( decision design : ce sont des params techniques
 * qui ne devraient pas varier par flux ) .
 *
 * <p><b>Backward compat :</b> {@link #empty()} = aucune surcharge ,
 * comportement identique a ne pas passer d'override . Tous les
 * callers existants peuvent continuer a appeler la version
 * sans-override de {@link CascadeImportPipeline#execute} .
 *
 * @author R.YAHIAOUI
 */
public record CascadeRuntimeOverride(
        PipelineMode                            pipelineMode,
        ImportProperties.SinkStrategy           sinkStrategy,
        ImportProperties.StagingStrategy        stagingStrategy,
        Integer                                 parallelism,
        Integer                                 chunkSizeLines,
        Integer                                 maxErrorsThreshold
) {

    /** Aucune surcharge - le pipeline utilise integralement {@link ImportProperties} . */
    public static final CascadeRuntimeOverride EMPTY =
            new CascadeRuntimeOverride(null, null, null, null, null, null);

    /** @return une instance sans surcharge ( equivalent de {@link #EMPTY} ) . */
    public static CascadeRuntimeOverride noOverride() {
        return EMPTY;
    }

    /** True si AU MOINS un field est surcharge ( != null ) . */
    public boolean hasAnyOverride() {
        return pipelineMode != null
                || sinkStrategy != null
                || stagingStrategy != null
                || parallelism != null
                || chunkSizeLines != null
                || maxErrorsThreshold != null;
    }
}
