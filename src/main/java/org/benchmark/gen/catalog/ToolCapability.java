package org.benchmark.gen.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hidden capability definition used to generate one concrete command.
 *
 * @param id stable catalog identifier, never shown to the model
 * @param role semantic stage label
 * @param verbSeeds candidate full-word verbs
 * @param nounSeeds candidate full-word nouns
 * @param preconditionTemplate local state required before execution
 * @param effectTemplate local state written after execution
 * @param optionProfile referenced option profile id, or blank when no profile applies
 */
public record ToolCapability(
        String id,
        String role,
        List<String> verbSeeds,
        List<String> nounSeeds,
        Map<String, String> preconditionTemplate,
        Map<String, String> effectTemplate,
        String optionProfile
) {
    public ToolCapability {
        id = Objects.requireNonNull(id, "id must not be null");
        role = Objects.requireNonNull(role, "role must not be null");
        verbSeeds = List.copyOf(Objects.requireNonNull(verbSeeds, "verbSeeds must not be null"));
        nounSeeds = List.copyOf(Objects.requireNonNull(nounSeeds, "nounSeeds must not be null"));
        preconditionTemplate = preconditionTemplate == null ? Map.of() : Map.copyOf(preconditionTemplate);
        effectTemplate = effectTemplate == null ? Map.of() : Map.copyOf(effectTemplate);
        optionProfile = optionProfile == null ? "" : optionProfile;
    }
}
