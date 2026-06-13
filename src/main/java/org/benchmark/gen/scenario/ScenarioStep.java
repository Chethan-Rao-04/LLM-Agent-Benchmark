package org.benchmark.gen.scenario;

import java.util.Map;

/**
 * One step in a scenario pattern template, before variable substitution.
 *
 * @param verb action verb (may contain {@code {variable}} placeholders)
 * @param noun target noun (may contain {@code {variable}} placeholders)
 * @param precondition state entries that must hold before this step executes, or empty
 * @param effect state mutations applied after successful execution, or empty
 */
public record ScenarioStep(
        String verb,
        String noun,
        Map<String, String> precondition,
        Map<String, String> effect
) {
    public ScenarioStep {
        precondition = precondition == null ? Map.of() : Map.copyOf(precondition);
        effect = effect == null ? Map.of() : Map.copyOf(effect);
    }
}
