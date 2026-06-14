package org.benchmark.gen.scenario;

import java.util.List;
import java.util.Map;

/**
 * A reusable scenario pattern loaded from YAML.
 *
 * <p>Template variables like {@code {capability}} in step verbs, nouns, preconditions,
 * and effects are resolved at generation time using the domain-specific pools.</p>
 *
 * @param pattern unique identifier for this scenario pattern
 * @param description human-readable description
 * @param steps ordered template steps
 * @param pools maps variable name to domain-name to concrete value list
 */
public record ScenarioPattern(
        String pattern,
        String description,
        List<ScenarioStep> steps,
        Map<String, Map<String, List<String>>> pools
) {
    public ScenarioPattern {
        steps = List.copyOf(steps);
        pools = pools == null ? Map.of() : Map.copyOf(pools);
    }
}
