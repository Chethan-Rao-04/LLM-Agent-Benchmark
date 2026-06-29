package org.benchmark.gen.spec;

import org.benchmark.gen.scenario.ResolvedScenario;
import org.benchmark.model.enums.Domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Semantic source of truth for a generated benchmark case.
 *
 * @param intentDescription user-level task intent before proprietary command names are applied
 * @param domain benchmark domain for the case
 * @param capabilitySteps ordered semantic capabilities required to satisfy the case
 * @param expectedFinalState target final state after all required capabilities succeed
 * @param decoyPlan intended decoy shape for the generated case
 * @param scoringPolicy scoring contract attached to this case
 */
public record BenchmarkCaseSpec(
        String intentDescription,
        Domain domain,
        List<CapabilityStep> capabilitySteps,
        Map<String, String> expectedFinalState,
        DecoyPlan decoyPlan,
        ScoringPolicy scoringPolicy
) {
    public BenchmarkCaseSpec {
        intentDescription = Objects.requireNonNull(intentDescription, "intentDescription must not be null");
        domain = Objects.requireNonNull(domain, "domain must not be null");
        capabilitySteps = List.copyOf(Objects.requireNonNull(capabilitySteps, "capabilitySteps must not be null"));
        expectedFinalState = Map.copyOf(Objects.requireNonNull(expectedFinalState, "expectedFinalState must not be null"));
        decoyPlan = Objects.requireNonNull(decoyPlan, "decoyPlan must not be null");
        scoringPolicy = Objects.requireNonNull(scoringPolicy, "scoringPolicy must not be null");
    }

    /**
     * Adapts the current resolved scenario into the new semantic model without changing generation behavior.
     */
    public static BenchmarkCaseSpec fromScenario(ResolvedScenario scenario,
                                                 int semanticDecoyCount,
                                                 int randomDistractorCount) {
        Objects.requireNonNull(scenario, "scenario must not be null");
        return new BenchmarkCaseSpec(
                scenario.description(),
                scenario.domain(),
                scenario.steps().stream()
                        .map(CapabilityStep::fromResolvedStep)
                        .toList(),
                scenario.cumulativeExpectedState(),
                DecoyPlan.currentDefault(semanticDecoyCount, randomDistractorCount),
                ScoringPolicy.currentDefault()
        );
    }
}
