package org.benchmark.gen.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Static workflow definition owned by one tool family.
 *
 * @param id unique workflow identifier
 * @param familyId owning family identifier
 * @param intent user-facing intent for the workflow
 * @param steps ordered workflow steps
 * @param expectedFinalState expected full cumulative end state
 * @param queryOutcomes outcome-oriented user query templates
 */
public record WorkflowTemplate(
        String id,
        String familyId,
        String intent,
        List<WorkflowStepTemplate> steps,
        Map<String, String> expectedFinalState,
        List<String> queryOutcomes
) {
    public WorkflowTemplate {
        id = Objects.requireNonNull(id, "id must not be null");
        familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        intent = Objects.requireNonNull(intent, "intent must not be null");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
        expectedFinalState = Map.copyOf(Objects.requireNonNull(expectedFinalState, "expectedFinalState must not be null"));
        queryOutcomes = List.copyOf(Objects.requireNonNull(queryOutcomes, "queryOutcomes must not be null"));
    }
}
