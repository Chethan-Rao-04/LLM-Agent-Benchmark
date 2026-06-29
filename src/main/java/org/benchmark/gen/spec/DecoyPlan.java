package org.benchmark.gen.spec;

import java.util.List;

/**
 * Declares the decoy surface requested for a generated case.
 *
 * @param semanticDecoyCount number of semantic decoys generated from the scenario pool
 * @param randomDistractorCount number of unrelated same-domain distractor tools
 * @param semanticDecoyKinds intended semantic decoy categories for later scorer and generator upgrades
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
     * Matches the current generator behavior while making the intended decoy role explicit.
     */
    public static DecoyPlan currentDefault(int semanticDecoyCount, int randomDistractorCount) {
        List<DecoyKind> kinds = semanticDecoyCount == 0
                ? List.of()
                : List.of(
                        DecoyKind.SIMILAR_INTENT_WRONG_RESOURCE,
                        DecoyKind.SIMILAR_COMMANDS_WRONG_STATE_PATH
                ).subList(0, Math.min(semanticDecoyCount, 2));
        return new DecoyPlan(semanticDecoyCount, randomDistractorCount, kinds);
    }
}
