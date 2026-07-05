package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;

import java.util.List;
import java.util.Objects;

/**
 * Static semantic family used to generate proprietary tool instances.
 *
 * @param id unique family identifier
 * @param domain owning benchmark domain
 * @param labels semantic labels kept in the hidden catalog
 * @param purpose neutral family purpose statement
 * @param nameFragments dynamic tool-name fragments
 * @param workflowIds supported workflow identifiers
 * @param decoyFamilyIds neighboring semantic families used for decoys
 * @param stateVariables family-shaped state schema hints
 * @param fillerCommandRoles family-shaped filler command phrases
 * @param querySymptoms symptom-oriented user query templates
 */
public record ToolFamily(
        String id,
        Domain domain,
        List<String> labels,
        String purpose,
        List<String> nameFragments,
        List<String> workflowIds,
        List<String> decoyFamilyIds,
        List<String> stateVariables,
        List<String> fillerCommandRoles,
        List<String> querySymptoms
) {
    public ToolFamily {
        id = Objects.requireNonNull(id, "id must not be null");
        domain = Objects.requireNonNull(domain, "domain must not be null");
        labels = List.copyOf(Objects.requireNonNull(labels, "labels must not be null"));
        purpose = Objects.requireNonNull(purpose, "purpose must not be null");
        nameFragments = List.copyOf(Objects.requireNonNull(nameFragments, "nameFragments must not be null"));
        workflowIds = List.copyOf(Objects.requireNonNull(workflowIds, "workflowIds must not be null"));
        decoyFamilyIds = List.copyOf(Objects.requireNonNull(decoyFamilyIds, "decoyFamilyIds must not be null"));
        stateVariables = List.copyOf(Objects.requireNonNull(stateVariables, "stateVariables must not be null"));
        fillerCommandRoles = List.copyOf(Objects.requireNonNull(fillerCommandRoles, "fillerCommandRoles must not be null"));
        querySymptoms = List.copyOf(Objects.requireNonNull(querySymptoms, "querySymptoms must not be null"));
    }
}
