package org.benchmark.gen.spec;

/**
 * Declares the decoy surface requested for a generated case.
 *
 * @param semanticDecoyCount number of target-like wrong tools generated from neighboring catalog families
 * @param randomDistractorCount number of unrelated same-domain distractor tools
 */
public record DecoyPlan(
        int semanticDecoyCount,
        int randomDistractorCount
) {
    public DecoyPlan {
        if (semanticDecoyCount < 0) {
            throw new IllegalArgumentException("semanticDecoyCount must not be negative");
        }
        if (randomDistractorCount < 0) {
            throw new IllegalArgumentException("randomDistractorCount must not be negative");
        }
    }

    /**
     * Uses the single target-like wrong neighboring-tool kind.
     */
    public static DecoyPlan currentDefault(int semanticDecoyCount, int randomDistractorCount) {
        return new DecoyPlan(semanticDecoyCount, randomDistractorCount);
    }
}
