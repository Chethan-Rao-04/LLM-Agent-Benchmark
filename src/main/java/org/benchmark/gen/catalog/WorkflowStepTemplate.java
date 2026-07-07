package org.benchmark.gen.catalog;

import java.util.Objects;

/**
 * One ordered workflow step referencing a capability on an abstract tool.
 *
 * @param toolId owning abstract tool id
 * @param capabilityId referenced capability id on the tool
 */
public record WorkflowStepTemplate(
        String toolId,
        String capabilityId
) {
    public WorkflowStepTemplate {
        toolId = Objects.requireNonNull(toolId, "toolId must not be null");
        capabilityId = Objects.requireNonNull(capabilityId, "capabilityId must not be null");
    }
}
