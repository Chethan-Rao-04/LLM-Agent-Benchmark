package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Hidden static semantic catalog used to generate proprietary benchmark cases.
 */
public record ToolCatalog(
        List<ToolFamily> families,
        List<WorkflowTemplate> workflows,
        List<OptionProfile> optionProfiles
) {
    public ToolCatalog {
        families = List.copyOf(Objects.requireNonNull(families, "families must not be null"));
        workflows = List.copyOf(Objects.requireNonNull(workflows, "workflows must not be null"));
        optionProfiles = List.copyOf(Objects.requireNonNull(optionProfiles, "optionProfiles must not be null"));
        validate(families, workflows, optionProfiles);
    }

    public List<ToolFamily> familiesForDomain(Domain domain) {
        return families.stream()
                .filter(family -> family.domain() == domain)
                .toList();
    }

    public ToolFamily family(String familyId) {
        return families.stream()
                .filter(family -> family.id().equals(familyId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown tool family: " + familyId));
    }

    public List<WorkflowTemplate> workflowsForFamily(String familyId) {
        return workflows.stream()
                .filter(workflow -> workflow.familyId().equals(familyId))
                .toList();
    }

    public WorkflowTemplate workflow(String workflowId) {
        return workflows.stream()
                .filter(workflow -> workflow.id().equals(workflowId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown workflow: " + workflowId));
    }

    public OptionProfile optionProfile(String profileId) {
        return optionProfiles.stream()
                .filter(profile -> profile.id().equals(profileId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown option profile: " + profileId));
    }

    private static void validate(List<ToolFamily> families,
                                 List<WorkflowTemplate> workflows,
                                 List<OptionProfile> optionProfiles) {
        for (OptionProfile optionProfile : optionProfiles) {
            if (optionProfile.style() == OptionProfileStyle.REQUIRED
                    && optionProfile.allowedFlags().isEmpty()) {
                throw new IllegalStateException("Required option profile '" + optionProfile.id()
                        + "' must declare at least one allowed flag");
            }
        }

        for (ToolFamily family : families) {
            if (family.tools().isEmpty()) {
                throw new IllegalStateException("Family '" + family.id()
                        + "' must declare at least one tool");
            }
            if (family.workflows().isEmpty()) {
                throw new IllegalStateException("Family '" + family.id() + "' has no workflows");
            }

            Set<String> toolIds = new HashSet<>();
            for (CatalogTool tool : family.tools()) {
                if (!toolIds.add(tool.id())) {
                    throw new IllegalStateException("Family '" + family.id()
                            + "' declares duplicate tool '" + tool.id() + "'");
                }
                validateTool(family, tool, optionProfiles);
            }

            for (WorkflowTemplate workflow : family.workflows()) {
                if (!workflow.familyId().equals(family.id())) {
                    throw new IllegalStateException("Workflow '" + workflow.id()
                            + "' has wrong family id '" + workflow.familyId()
                            + "' for family '" + family.id() + "'");
                }
                Set<String> targetToolIds = new HashSet<>();
                for (WorkflowStepTemplate step : workflow.steps()) {
                    CatalogTool tool = family.tools().stream()
                            .filter(candidate -> candidate.id().equals(step.toolId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Workflow '" + workflow.id()
                                    + "' references missing tool '" + step.toolId() + "'"));
                    targetToolIds.add(tool.id());
                    tool.capabilities().stream()
                            .filter(capability -> capability.id().equals(step.capabilityId()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Workflow '" + workflow.id()
                                    + "' references missing capability '" + step.capabilityId()
                                    + "' on tool '" + step.toolId() + "'"));
                }
                if (targetToolIds.size() < 2) {
                    throw new IllegalStateException("Workflow '" + workflow.id()
                            + "' must reference at least two distinct tools");
                }
            }
        }

        for (WorkflowTemplate workflow : workflows) {
            boolean familyExists = families.stream().anyMatch(family -> family.id().equals(workflow.familyId()));
            if (!familyExists) {
                throw new IllegalStateException("Workflow '" + workflow.id()
                        + "' references missing family '" + workflow.familyId() + "'");
            }
        }
    }

    private static void validateTool(ToolFamily family,
                                     CatalogTool tool,
                                     List<OptionProfile> optionProfiles) {
        if (tool.capabilities().isEmpty()) {
            throw new IllegalStateException("Tool '" + tool.id()
                    + "' in family '" + family.id() + "' must declare at least one capability");
        }
        for (String fillerCommandRole : tool.fillerCommandRoles()) {
            int separator = fillerCommandRole.indexOf(' ');
            if (separator <= 0 || separator == fillerCommandRole.length() - 1) {
                throw new IllegalStateException("Tool '" + tool.id()
                        + "' has invalid filler command role '" + fillerCommandRole + "'");
            }
        }

        Set<String> capabilityIds = new HashSet<>();
        for (ToolCapability capability : tool.capabilities()) {
            if (!capabilityIds.add(capability.id())) {
                throw new IllegalStateException("Tool '" + tool.id()
                        + "' declares duplicate capability '" + capability.id() + "'");
            }
            if (!capability.optionProfile().isBlank()
                    && optionProfiles.stream().noneMatch(profile -> profile.id().equals(capability.optionProfile()))) {
                throw new IllegalStateException("Tool '" + tool.id()
                        + "' references missing option profile '" + capability.optionProfile() + "'");
            }
        }
    }
}
