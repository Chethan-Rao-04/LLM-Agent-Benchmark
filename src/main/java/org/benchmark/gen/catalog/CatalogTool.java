package org.benchmark.gen.catalog;

import java.util.List;
import java.util.Objects;

/**
 * Hidden abstract tool definition inside a process family.
 *
 * @param id stable catalog identifier, never shown to the model
 * @param purpose neutral tool purpose used to generate public descriptions
 * @param nameFragments fragments used for dynamic concrete tool names
 * @param stateVariables local state schema hints for this tool
 * @param fillerCommandRoles support command phrases used for recovery commands
 * @param capabilities executable functionality owned by this abstract tool
 */
public record CatalogTool(
        String id,
        String purpose,
        List<String> nameFragments,
        List<String> stateVariables,
        List<String> fillerCommandRoles,
        List<ToolCapability> capabilities
) {
    public CatalogTool {
        id = Objects.requireNonNull(id, "id must not be null");
        purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        nameFragments = List.copyOf(Objects.requireNonNull(nameFragments, "nameFragments must not be null"));
        stateVariables = List.copyOf(Objects.requireNonNull(stateVariables, "stateVariables must not be null"));
        fillerCommandRoles = List.copyOf(Objects.requireNonNull(fillerCommandRoles, "fillerCommandRoles must not be null"));
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
    }

    public ToolCapability capability(String capabilityId) {
        return capabilities.stream()
                .filter(capability -> capability.id().equals(capabilityId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown capability '" + capabilityId
                        + "' for tool '" + id + "'"));
    }
}
