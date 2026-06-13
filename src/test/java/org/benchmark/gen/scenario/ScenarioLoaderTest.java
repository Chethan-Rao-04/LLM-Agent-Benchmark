package org.benchmark.gen.scenario;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioLoaderTest {

    @Test
    void loadsValidScenarioResource() {
        ScenarioLoader loader = new ScenarioLoader("test-scenarios-valid.yaml");

        assertEquals(1, loader.getPatterns().size());
        assertEquals("provision_environment", loader.getPatterns().getFirst().pattern());
        assertEquals(1, loader.getPatterns().getFirst().steps().size());
    }

    @Test
    void rejectsMalformedScenarioStructureWithHelpfulMessage() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new ScenarioLoader("test-scenarios-invalid.yaml"));

        assertEquals("Expected a list for steps in broken_scenario", error.getMessage());
    }
}
