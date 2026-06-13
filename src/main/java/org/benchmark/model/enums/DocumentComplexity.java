package org.benchmark.model.enums;

/**
 * Documentation degradation profiles used for benchmark prompts.
 */
public enum DocumentComplexity {
    /** Fully structured, consistent documentation. */
    CLEAN,
    /** Omits subsets of commands/options/details. */
    INCOMPLETE,
    /** Removes helpful structure such as headings/formatting. */
    UNSTRUCTURED,
    /** Injects contradictory option values and effects within the documentation. */
    LOGICAL_CONFLICT
}
