package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;

import java.util.List;
import java.util.Objects;

/**
 * Static process family used to generate related proprietary tool instances.
 *
 * @param id unique family identifier
 * @param domain owning benchmark domain
 * @param purpose neutral family purpose statement
 * @param queryTemplates outcome-oriented user query templates
 * @param tools abstract tools available inside this process family
 * @param workflows ordered multi-tool workflows for this family
 */
public record ToolFamily(
        String id,
        Domain domain,
        String purpose,
        List<String> queryTemplates,
        List<CatalogTool> tools,
        List<WorkflowTemplate> workflows
) {
    public ToolFamily {
        id = Objects.requireNonNull(id, "id must not be null");
        domain = Objects.requireNonNull(domain, "domain must not be null");
        purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        queryTemplates = List.copyOf(Objects.requireNonNull(queryTemplates, "queryTemplates must not be null"));
        tools = List.copyOf(Objects.requireNonNull(tools, "tools must not be null"));
        workflows = List.copyOf(Objects.requireNonNull(workflows, "workflows must not be null"));
    }

    public CatalogTool tool(String toolId) {
        return tools.stream()
                .filter(tool -> tool.id().equals(toolId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown tool '" + toolId
                        + "' for family '" + id + "'"));
    }
}
