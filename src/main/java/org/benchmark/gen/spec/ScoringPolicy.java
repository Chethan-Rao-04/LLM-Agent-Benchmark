package org.benchmark.gen.spec;

/**
 * Semantic scoring intent for a generated case.
 *
 * @param penalizeSemanticDecoyUse whether semantic decoy executions should be scored separately
 */
public record ScoringPolicy(
        boolean penalizeSemanticDecoyUse
) {
    /**
     * Enables semantic decoy scoring while keeping the existing composite score unchanged.
     */
    public static ScoringPolicy currentDefault() {
        return new ScoringPolicy(true);
    }
}
