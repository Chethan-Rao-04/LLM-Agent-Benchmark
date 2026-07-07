package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCatalogLoaderTest {

    @Test
    void loadsDefaultCatalogWithExpectedVerticalSliceCoverage() {
        ToolCatalog catalog = new ToolCatalogLoader().getCatalog();

        assertEquals(6, catalog.families().size());
        assertEquals(12, catalog.workflows().size());
        assertFalse(catalog.optionProfiles().isEmpty());
        assertTrue(catalog.optionProfiles().stream()
                .allMatch(profile -> profile.style() == OptionProfileStyle.NONE
                        || profile.style() == OptionProfileStyle.REQUIRED));
        assertTrue(catalog.optionProfiles().stream()
                .noneMatch(profile -> profile.id().contains("optional")));
        assertEquals(3, catalog.familiesForDomain(Domain.MANUFACTURING).size());
        assertEquals(3, catalog.familiesForDomain(Domain.NETWORK_INFRA).size());
        assertTrue(catalog.families().stream().allMatch(family -> !family.tools().isEmpty()));
        assertTrue(catalog.workflows().stream()
                .allMatch(workflow -> workflow.steps().stream()
                        .map(WorkflowStepTemplate::toolId)
                        .distinct()
                        .count() >= 2));
    }

    @Test
    void rejectsInvalidFillerCommandRole() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ToolCatalogLoader("test-tool-catalog-invalid-filler-role.yaml"));

        assertTrue(error.getMessage().contains("invalid filler command role"));
    }

    @Test
    void rejectsRequiredOptionProfileWithoutFlags() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ToolCatalogLoader("test-tool-catalog-invalid-option-profile.yaml"));

        assertTrue(error.getMessage().contains("must declare at least one allowed flag"));
    }

    @Test
    void rejectsWorkflowThatReferencesMissingCapability() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ToolCatalogLoader("test-tool-catalog-missing-capability.yaml"));

        assertTrue(error.getMessage().contains("references missing capability"));
    }
}
