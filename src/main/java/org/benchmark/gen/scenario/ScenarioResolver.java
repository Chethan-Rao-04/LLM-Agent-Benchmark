package org.benchmark.gen.scenario;

import org.benchmark.model.enums.Domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Resolves a {@link ScenarioPattern} into a {@link ResolvedScenario} by substituting
 * template variables with concrete values from domain-specific pools.
 *
 * <p>This class deals only with template resolution. Command-name abbreviation
 * is handled centrally by {@code CommandAbbreviator} at the point where
 * {@code CommandObject}s are created.</p>
 */
public class ScenarioResolver {

    private final Random random;

    /**
     * Creates the resolver that picks concrete pool values for each scenario variable.
     *
     * @param random shared random source
     */
    public ScenarioResolver(Random random) {
        this.random = random;
    }

    /**
     * Resolves a scenario pattern for the given domain.
     */
    public ResolvedScenario resolve(ScenarioPattern pattern, Domain domain) {
        Map<String, String> bindings = resolveBindings(pattern.pools(), domain);

        List<ResolvedStep> resolvedSteps = new ArrayList<>();
        Map<String, String> cumulativeState = new LinkedHashMap<>();

        for (ScenarioStep step : pattern.steps()) {
            String verb = substitute(step.verb(), bindings);
            String noun = substitute(step.noun(), bindings);
            Map<String, String> precondition = substituteMap(step.precondition(), bindings);
            Map<String, String> effect = substituteMap(step.effect(), bindings);

            resolvedSteps.add(new ResolvedStep(verb, noun, precondition, effect));
            cumulativeState.putAll(effect);
        }

        return new ResolvedScenario(
                pattern.pattern(),
                pattern.description(),
                domain,
                resolvedSteps,
                cumulativeState,
                bindings
        );
    }

    private Map<String, String> resolveBindings(Map<String, Map<String, List<String>>> pools, Domain domain) {
        Map<String, String> bindings = new HashMap<>();
        String domainKey = domain.name();

        for (Map.Entry<String, Map<String, List<String>>> poolEntry : pools.entrySet()) {
            String variable = poolEntry.getKey();
            Map<String, List<String>> domainMap = poolEntry.getValue();
            List<String> values = domainMap.get(domainKey);

            if (values == null || values.isEmpty()) {
                throw new IllegalStateException(
                        "No pool values for variable '" + variable + "' in domain " + domain);
            }

            bindings.put(variable, values.get(random.nextInt(values.size())));
        }
        return bindings;
    }

    private String substitute(String text, Map<String, String> bindings) {
        String result = text;
        for (Map.Entry<String, String> binding : bindings.entrySet()) {
            result = result.replace("{" + binding.getKey() + "}", binding.getValue());
        }
        return result;
    }

    private Map<String, String> substituteMap(Map<String, String> original, Map<String, String> bindings) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : original.entrySet()) {
            result.put(substitute(entry.getKey(), bindings), substitute(entry.getValue(), bindings));
        }
        return result;
    }
}
