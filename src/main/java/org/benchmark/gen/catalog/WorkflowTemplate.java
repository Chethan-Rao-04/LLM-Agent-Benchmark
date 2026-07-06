package org.benchmark.gen.catalog;

import java.util.List;
import java.util.Objects;

/**
 * Static workflow definition owned by one tool family.
 *
 * @param id unique workflow identifier
 * @param familyId owning family identifier
 * @param intent user-facing intent for the workflow
 * @param steps ordered workflow steps
 */
public record WorkflowTemplate(
        String id,
        String familyId,
        String intent,
        List<WorkflowStepTemplate> steps
) {
    public WorkflowTemplate {
        id = Objects.requireNonNull(id, "id must not be null");
        familyId = Objects.requireNonNull(familyId, "familyId must not be null");
        intent = Objects.requireNonNull(intent, "intent must not be null");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
    }
}
