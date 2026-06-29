package org.benchmark.gen.scenario;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads scenario patterns from {@code scenarios.yaml} on the classpath.
 */
public class ScenarioLoader {

    private final List<ScenarioPattern> patterns;

    /**
     * Loads scenario patterns from the default classpath resource.
     */
    public ScenarioLoader() {
        this("scenarios.yaml");
    }

    /**
     * Loads scenario patterns from a specific classpath resource.
     *
     * @param resourcePath classpath-relative scenario file path
     */
    public ScenarioLoader(String resourcePath) {
        this.patterns = load(resourcePath);
    }

    /**
     * Returns the validated scenario templates available to the generator pipeline.
     *
     * @return immutable list of scenario patterns
     */
    public List<ScenarioPattern> getPatterns() {
        return patterns;
    }

    @SuppressWarnings("unchecked")
    private List<ScenarioPattern> load(String resourcePath) {
        Yaml yaml = new Yaml();
        InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Scenario file not found on classpath: " + resourcePath);
        }

        try (InputStream input = resource) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> rawRoot)) {
                throw new IllegalStateException("Scenario file must contain a top-level mapping: " + resourcePath);
            }

            Object scenariosValue = rawRoot.get("scenarios");
            if (!(scenariosValue instanceof List<?> rawScenarioList) || rawScenarioList.isEmpty()) {
                throw new IllegalStateException("No scenarios defined in " + resourcePath);
            }

            List<ScenarioPattern> result = new ArrayList<>();
            for (Object rawEntry : rawScenarioList) {
                result.add(parsePattern(requireMap(rawEntry, "scenario entry")));
            }
            return List.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close scenario resource: " + resourcePath, e);
        }
    }

    private ScenarioPattern parsePattern(Map<String, Object> entry) {
        String pattern = requireString(entry, "pattern", "scenario entry");
        String description = optionalString(entry.get("description"), "description", pattern);

        List<?> rawSteps = requireList(entry.get("steps"), "steps", pattern);
        List<ScenarioStep> steps = new ArrayList<>();
        for (Object rawStep : rawSteps) {
            steps.add(parseStep(requireMap(rawStep, "step for scenario '" + pattern + "'")));
        }

        Map<String, Map<String, List<String>>> pools = new HashMap<>();
        Object poolsValue = entry.get("pools");
        if (poolsValue != null) {
            Map<String, Object> rawPools = requireMap(poolsValue, "pools for scenario '" + pattern + "'");
            for (Map.Entry<String, Object> poolEntry : rawPools.entrySet()) {
                Map<String, Object> domainMap = requireMap(
                        poolEntry.getValue(),
                        "pool '" + poolEntry.getKey() + "' for scenario '" + pattern + "'");
                Map<String, List<String>> domainPools = new HashMap<>();
                for (Map.Entry<String, Object> domainEntry : domainMap.entrySet()) {
                    domainPools.put(
                            domainEntry.getKey(),
                            copyStringList(domainEntry.getValue(),
                                    "pool '" + poolEntry.getKey() + "." + domainEntry.getKey()
                                            + "' for scenario '" + pattern + "'"));
                }
                pools.put(poolEntry.getKey(), Map.copyOf(domainPools));
            }
        }

        validate(pattern, steps, pools);
        return new ScenarioPattern(pattern, description, steps, pools);
    }

    private ScenarioStep parseStep(Map<String, Object> raw) {
        String verb = requireString(raw, "verb", "scenario step");
        String noun = requireString(raw, "noun", "scenario step");
        Map<String, String> precondition = toStringMap(raw.get("precondition"), "precondition for step '" + verb + " " + noun + "'");
        Map<String, String> effect = toStringMap(raw.get("effect"), "effect for step '" + verb + " " + noun + "'");
        return new ScenarioStep(verb, noun, precondition, effect);
    }

    private Map<String, String> toStringMap(Object raw, String context) {
        if (raw == null) {
            return Map.of();
        }
        Map<String, Object> mapping = requireMap(raw, context);
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            result.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private Map<String, Object> requireMap(Object value, String context) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalStateException("Expected a mapping for " + context);
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException("Expected string keys in " + context);
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private List<?> requireList(Object value, String field, String context) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("Expected a list for " + field + " in " + context);
        }
        return list;
    }

    private List<String> copyStringList(Object value, String context) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalStateException("Expected a list of strings for " + context);
        }
        List<String> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (!(item instanceof String text)) {
                throw new IllegalStateException("Expected string entries in " + context);
            }
            result.add(text);
        }
        return List.copyOf(result);
    }

    private String requireString(Map<String, Object> entry, String field, String context) {
        Object value = entry.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Expected non-blank string field '" + field + "' in " + context);
        }
        return text;
    }

    private String optionalString(Object value, String field, String context) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw new IllegalStateException("Expected string field '" + field + "' in " + context);
        }
        return text;
    }

    /**
     * Validates that every template variable referenced in steps has a matching pool entry.
     */
    private void validate(String pattern, List<ScenarioStep> steps, Map<String, Map<String, List<String>>> pools) {
        for (ScenarioStep step : steps) {
            validateTemplateVars(pattern, step.verb(), pools);
            validateTemplateVars(pattern, step.noun(), pools);
            step.precondition().keySet().forEach(k -> validateTemplateVars(pattern, k, pools));
            step.precondition().values().forEach(v -> validateTemplateVars(pattern, v, pools));
            step.effect().keySet().forEach(k -> validateTemplateVars(pattern, k, pools));
            step.effect().values().forEach(v -> validateTemplateVars(pattern, v, pools));
        }
    }

    private void validateTemplateVars(String pattern, String text, Map<String, Map<String, List<String>>> pools) {
        int start = 0;
        while ((start = text.indexOf('{', start)) != -1) {
            int end = text.indexOf('}', start);
            if (end == -1) break;
            String variable = text.substring(start + 1, end);
            if (!pools.containsKey(variable)) {
                throw new IllegalStateException(
                        "Scenario '" + pattern + "' references variable '{" + variable
                                + "}' but no pool is defined for it");
            }
            start = end + 1;
        }
    }
}
