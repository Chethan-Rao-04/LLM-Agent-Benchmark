package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;

import java.util.List;
import java.util.Objects;

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
            if (family.fillerCommandRoles().isEmpty()) {
                throw new IllegalStateException("Family '" + family.id()
                        + "' must declare at least one filler command role");
            }
            for (String fillerCommandRole : family.fillerCommandRoles()) {
                int separator = fillerCommandRole.indexOf(' ');
                if (separator <= 0 || separator == fillerCommandRole.length() - 1) {
                    throw new IllegalStateException("Family '" + family.id()
                            + "' has invalid filler command role '" + fillerCommandRole + "'");
                }
            }
            boolean hasWorkflow = workflows.stream()
                    .anyMatch(workflow -> workflow.familyId().equals(family.id()));
            if (!hasWorkflow) {
                throw new IllegalStateException("Family '" + family.id() + "' has no workflows");
            }
            for (String decoyFamilyId : family.decoyFamilyIds()) {
                ToolFamily decoyFamily = families.stream()
                        .filter(candidate -> candidate.id().equals(decoyFamilyId))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Family '" + family.id()
                                + "' references missing decoy family '" + decoyFamilyId + "'"));
                if (decoyFamily.domain() != family.domain()) {
                    throw new IllegalStateException("Family '" + family.id()
                            + "' references cross-domain decoy family '" + decoyFamilyId + "'");
                }
            }
        }

        for (WorkflowTemplate workflow : workflows) {
            boolean familyExists = families.stream().anyMatch(family -> family.id().equals(workflow.familyId()));
            if (!familyExists) {
                throw new IllegalStateException("Workflow '" + workflow.id()
                        + "' references missing family '" + workflow.familyId() + "'");
            }
            for (WorkflowStepTemplate step : workflow.steps()) {
                if (!step.optionProfile().isBlank()
                        && optionProfiles.stream().noneMatch(profile -> profile.id().equals(step.optionProfile()))) {
                    throw new IllegalStateException("Workflow '" + workflow.id()
                            + "' references missing option profile '" + step.optionProfile() + "'");
                }
            }
        }
    }
}
