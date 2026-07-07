package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;
import org.benchmark.model.enums.StateScope;
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

    @Test
    void parsesExplicitScopedPreconditionsAndEffects() {
        ToolCatalog catalog = new ToolCatalogLoader("test-tool-catalog-scoped-state.yaml").getCatalog();
        ToolCapability routerCapability = catalog.family("scoped_route_process").tools().getFirst().capabilities().getFirst();
        ToolCapability verifierCapability = catalog.family("scoped_route_process").tools().get(1).capabilities().getFirst();

        assertTrue(routerCapability.effects().stream()
                .anyMatch(effect -> effect.scope() == StateScope.SHARED
                        && effect.variable().equals("route_path_state")
                        && effect.valueRef().equals("inspected")));
        assertTrue(verifierCapability.preconditions().stream()
                .anyMatch(precondition -> precondition.scope() == StateScope.SHARED
                        && precondition.variable().equals("route_path_state")
                        && precondition.value().equals("inspected")));
    }

    @Test
    void adaptsLegacyStateMapsToToolScope() {
        ToolCatalog catalog = new ToolCatalogLoader("test-tool-catalog-single-family.yaml").getCatalog();
        ToolCapability capability = catalog.family("router_process").tools().getFirst().capabilities().getFirst();

        assertTrue(capability.effects().stream()
                .allMatch(effect -> effect.scope() == StateScope.TOOL));
    }

    @Test
    void rejectsMixedLegacyAndScopedStateDefinitions() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ToolCatalogLoader("test-tool-catalog-mixed-scoped-legacy.yaml"));

        assertTrue(error.getMessage().contains("must not mix legacy state maps"));
    }

    @Test
    void rejectsSharedStateNamesTiedToToolIds() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ToolCatalogLoader("test-tool-catalog-bad-shared-state-name.yaml"));

        assertTrue(error.getMessage().contains("must not include producing tool id"));
    }
}
