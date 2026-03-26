package org.benchmark.model.enums;

/**
 * Documentation degradation profiles used for benchmark prompts.
 */
public enum DocumentComplexity {
    /** Fully structured, consistent documentation. */
    CLEAN,
    /** Adds random gibberish/noise tokens. */
    GIBBERISH_NOISE,
    /** Adds irrelevant but coherent contextual noise. */
    CONTEXTUAL_NOISE,
    /** Omits subsets of commands/options/details. */
    INCOMPLETE,
    /** Removes helpful structure such as headings/formatting. */
    UNSTRUCTURED,
    /** Injects contradictory guidance in the text. */
    LOGICAL_CONFLICT
}
