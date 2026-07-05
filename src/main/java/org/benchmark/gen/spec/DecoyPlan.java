package org.benchmark.gen.spec;

import java.util.List;

/**
 * Declares the decoy surface requested for a generated case.
 *
 * @param semanticDecoyCount number of target-like wrong tools generated from neighboring catalog families
 * @param randomDistractorCount number of unrelated same-domain distractor tools
 * @param semanticDecoyKinds compatibility metadata for target-like wrong tools
 */
public record DecoyPlan(
        int semanticDecoyCount,
        int randomDistractorCount,
        List<DecoyKind> semanticDecoyKinds
) {
    public DecoyPlan {
        if (semanticDecoyCount < 0) {
            throw new IllegalArgumentException("semanticDecoyCount must not be negative");
        }
        if (randomDistractorCount < 0) {
            throw new IllegalArgumentException("randomDistractorCount must not be negative");
        }
        semanticDecoyKinds = semanticDecoyKinds == null ? List.of() : List.copyOf(semanticDecoyKinds);
    }

    /**
     * Uses the single target-like wrong neighboring-tool kind.
     */
    public static DecoyPlan currentDefault(int semanticDecoyCount, int randomDistractorCount) {
        List<DecoyKind> kinds = semanticDecoyCount == 0
                ? List.of()
                : List.of(DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE);
        return new DecoyPlan(semanticDecoyCount, randomDistractorCount, kinds);
    }
}
