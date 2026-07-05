package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCatalogLoaderTest {

    @Test
    void loadsDefaultCatalogWithExpectedVerticalSliceCoverage() {
        ToolCatalog catalog = new ToolCatalogLoader().getCatalog();

        assertEquals(10, catalog.families().size());
        assertEquals(20, catalog.workflows().size());
        assertFalse(catalog.optionProfiles().isEmpty());
        assertTrue(catalog.optionProfiles().stream()
                .allMatch(profile -> profile.style() == OptionProfileStyle.NONE
                        || profile.style() == OptionProfileStyle.REQUIRED));
        assertTrue(catalog.optionProfiles().stream()
                .noneMatch(profile -> profile.id().contains("optional")));
        assertEquals(5, catalog.familiesForDomain(Domain.MANUFACTURING).size());
        assertEquals(5, catalog.familiesForDomain(Domain.NETWORK_INFRA).size());
    }
}
