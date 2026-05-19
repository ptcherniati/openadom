package fr.inra.oresing.workflow;

import fr.inrae.ore.cascade.api.WorkflowTypeKey;

/**
 * Type-safe enumeration of openADOM workflow types.
 *
 * <p>Each value's {@link Enum#name()} matches the canonical string
 * stored in {@code oa_audit.workflow_log.workflow_type} and emitted on
 * the cascade event bus. The corresponding {@code String} constants
 * remain in {@link fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry}
 * for the existing call sites that rely on them.
 *
 * <p>Implements cascade's {@link WorkflowTypeKey} marker so callers may
 * pass a strongly-typed value to APIs that accept it.
 *
 * @author R.YAHIAOUI
 */
public enum OreSiWorkflowType implements WorkflowTypeKey {

    /** Bulk data import (csv staging + finalize). */
    IMPORT,

    /** Extraction of data as zip archive. */
    EXTRACT_ZIP,

    /** Extraction of data as csv stream. */
    EXTRACT_CSV,

    /** Extraction of additional binary files. */
    EXTRACT_ADDITIONAL_FILES,

    /** Extraction of an application charte. */
    EXTRACT_CHARTE,

    /**
     * Publication d'un fichier deja stocke : DELETE rows existantes ( si
     * republie ) + INSERT data via cascade pipeline ( pipeline complet
     * STAGING + finalize ) . Lance par toggle Publier dans le frontend .
     */
    PUBLISH,

    /**
     * Depublication : DELETE rows referencevalue WHERE fileId = X . Le
     * binaryfile reste en place . Lance par toggle Depublier dans le
     * frontend .
     */
    UNPUBLISH,

    /**
     * Suppression d'un fichier : DELETE binaryfile + DELETE rows si le
     * fichier etait publie au moment de la suppression .
     */
    DELETE_FILE,

    /**
     * Construction admin du cache {@code binaryfile.processed_data} pour un
     * fichier deja uploade : execute la pipeline cascade en mode capture-only
     * ( pas d'ecriture vers referencevalue ) , persiste le JSON valide+
     * transforme + le {@code configHash} pour activer le Publish FAST path
     * au prochain republish . Equivalent " pre-compute " du cache .
     */
    BUILD_CACHE
}
