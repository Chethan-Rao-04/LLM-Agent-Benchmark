package org.benchmark.gen.scenario;

import org.benchmark.model.enums.Domain;

import java.util.List;
import java.util.Map;

/**
 * A fully resolved scenario ready for tool generation and benchmark execution.
 *
 * @param patternName identifier of the source scenario pattern
 * @param description human-readable description
 * @param domain the domain this scenario was resolved for
 * @param steps ordered resolved steps with concrete verbs, nouns, and state maps
 * @param cumulativeExpectedState the final state after all steps execute in order
 * @param resolvedPoolValues the concrete values chosen for each pool variable
 */
public record ResolvedScenario(
        String patternName,
        String description,
        Domain domain,
        List<ResolvedStep> steps,
        Map<String, String> cumulativeExpectedState,
        Map<String, String> resolvedPoolValues
) {
    public ResolvedScenario {
        steps = List.copyOf(steps);
        cumulativeExpectedState = Map.copyOf(cumulativeExpectedState);
        resolvedPoolValues = resolvedPoolValues == null ? Map.of() : Map.copyOf(resolvedPoolValues);
    }
}
