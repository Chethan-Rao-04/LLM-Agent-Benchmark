package org.benchmark.gen.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One semantic workflow step before the final proprietary command surface is generated.
 *
 * @param role short semantic stage label
 * @param verbSeeds candidate full-word verbs for the step
 * @param nounSeeds candidate full-word nouns for the step
 * @param preconditionTemplate required state before the step
 * @param effectTemplate state written by the step
 * @param optionProfile referenced option profile id, or blank when no profile applies
 */
public record WorkflowStepTemplate(
        String role,
        List<String> verbSeeds,
        List<String> nounSeeds,
        Map<String, String> preconditionTemplate,
        Map<String, String> effectTemplate,
        String optionProfile
) {
    public WorkflowStepTemplate {
        role = Objects.requireNonNull(role, "role must not be null");
        verbSeeds = List.copyOf(Objects.requireNonNull(verbSeeds, "verbSeeds must not be null"));
        nounSeeds = List.copyOf(Objects.requireNonNull(nounSeeds, "nounSeeds must not be null"));
        preconditionTemplate = preconditionTemplate == null ? Map.of() : Map.copyOf(preconditionTemplate);
        effectTemplate = effectTemplate == null ? Map.of() : Map.copyOf(effectTemplate);
        optionProfile = optionProfile == null ? "" : optionProfile;
    }
}
