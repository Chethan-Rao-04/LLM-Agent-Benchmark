package org.benchmark.gen.spec;

/**
 * Semantic scoring intent for a generated case.
 *
 * @param requireExpectedFinalState whether passing the case requires the target final state
 * @param penalizeSemanticDecoyUse whether semantic decoy executions should be scored separately
 */
public record ScoringPolicy(
        boolean requireExpectedFinalState,
        boolean penalizeSemanticDecoyUse
) {
    /**
     * Enables semantic decoy scoring while keeping the existing composite score unchanged.
     */
    public static ScoringPolicy currentDefault() {
        return new ScoringPolicy(true, true);
    }
}
