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
 * @param expectedSharedState target shared state after all required capabilities succeed
 * @param decoyPlan intended decoy shape for the generated case
 * @param scoringPolicy scoring contract attached to this case
 */
public record BenchmarkCaseSpec(
        String intentDescription,
        Domain domain,
        List<CapabilityStep> capabilitySteps,
        Map<String, String> expectedFinalState,
        Map<String, String> expectedSharedState,
        DecoyPlan decoyPlan,
        ScoringPolicy scoringPolicy,
        String toolFamilyId,
        String workflowId
) {
    public BenchmarkCaseSpec(String intentDescription,
                             Domain domain,
                             List<CapabilityStep> capabilitySteps,
                             Map<String, String> expectedFinalState,
                             DecoyPlan decoyPlan,
                             ScoringPolicy scoringPolicy) {
        this(intentDescription, domain, capabilitySteps, expectedFinalState, Map.of(),
                decoyPlan, scoringPolicy, "", "");
    }

    public BenchmarkCaseSpec(String intentDescription,
                             Domain domain,
                             List<CapabilityStep> capabilitySteps,
                             Map<String, String> expectedFinalState,
                             Map<String, String> expectedSharedState,
                             DecoyPlan decoyPlan,
                             ScoringPolicy scoringPolicy) {
        this(intentDescription, domain, capabilitySteps, expectedFinalState, expectedSharedState,
                decoyPlan, scoringPolicy, "", "");
    }

    public BenchmarkCaseSpec(String intentDescription,
                             Domain domain,
                             List<CapabilityStep> capabilitySteps,
                             Map<String, String> expectedFinalState,
                             DecoyPlan decoyPlan,
                             ScoringPolicy scoringPolicy,
                             String toolFamilyId,
                             String workflowId) {
        this(intentDescription, domain, capabilitySteps, expectedFinalState, Map.of(),
                decoyPlan, scoringPolicy, toolFamilyId, workflowId);
    }

    public BenchmarkCaseSpec {
        intentDescription = Objects.requireNonNull(intentDescription, "intentDescription must not be null");
        domain = Objects.requireNonNull(domain, "domain must not be null");
        capabilitySteps = List.copyOf(Objects.requireNonNull(capabilitySteps, "capabilitySteps must not be null"));
        expectedFinalState = Map.copyOf(Objects.requireNonNull(expectedFinalState, "expectedFinalState must not be null"));
        expectedSharedState = expectedSharedState == null ? Map.of() : Map.copyOf(expectedSharedState);
        decoyPlan = Objects.requireNonNull(decoyPlan, "decoyPlan must not be null");
        scoringPolicy = Objects.requireNonNull(scoringPolicy, "scoringPolicy must not be null");
        toolFamilyId = toolFamilyId == null ? "" : toolFamilyId;
        workflowId = workflowId == null ? "" : workflowId;
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
