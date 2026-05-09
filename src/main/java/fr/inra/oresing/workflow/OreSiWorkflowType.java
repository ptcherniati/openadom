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

    /** Toggle the {@code published} flag of a binary file (one-shot action). */
    PUBLISH_TOGGLE
}
