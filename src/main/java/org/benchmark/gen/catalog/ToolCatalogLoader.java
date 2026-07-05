package org.benchmark.gen.catalog;

import org.benchmark.model.enums.Domain;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the hidden static tool catalog from YAML.
 */
public class ToolCatalogLoader {

    private final ToolCatalog catalog;

    public ToolCatalogLoader() {
        this("tool-catalog.yaml");
    }

    public ToolCatalogLoader(String resourcePath) {
        this.catalog = load(resourcePath);
    }

    public ToolCatalog getCatalog() {
        return catalog;
    }

    @SuppressWarnings("unchecked")
    private ToolCatalog load(String resourcePath) {
        Yaml yaml = new Yaml();
        InputStream resource = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Tool catalog file not found on classpath: " + resourcePath);
        }

        try (InputStream input = resource) {
            Object loaded = yaml.load(input);
            if (!(loaded instanceof Map<?, ?> rawRoot)) {
                throw new IllegalStateException("Tool catalog file must contain a top-level mapping: " + resourcePath);
            }

            List<OptionProfile> optionProfiles = parseOptionProfiles(rawRoot.get("optionProfiles"));
            List<ToolFamily> families = parseFamilies(rawRoot.get("families"));
            List<WorkflowTemplate> workflows = parseWorkflows(rawRoot.get("workflows"));
            return new ToolCatalog(families, workflows, optionProfiles);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close tool catalog resource: " + resourcePath, e);
        }
    }

    private List<OptionProfile> parseOptionProfiles(Object rawValue) {
        List<?> rawProfiles = requireList(rawValue, "optionProfiles");
        List<OptionProfile> profiles = new ArrayList<>(rawProfiles.size());
        for (Object rawProfile : rawProfiles) {
            Map<String, Object> entry = requireMap(rawProfile, "option profile");
            profiles.add(new OptionProfile(
                    requireString(entry, "id", "option profile"),
                    parseEnum(OptionProfileStyle.class, requireString(entry, "style", "option profile"), "option profile style"),
                    copyStringList(entry.get("allowedFlags"), "allowedFlags for option profile"),
                    parseEnum(OptionValueMode.class, requireString(entry, "valueMode", "option profile"), "option profile valueMode")
            ));
        }
        return List.copyOf(profiles);
    }

    private List<ToolFamily> parseFamilies(Object rawValue) {
        List<?> rawFamilies = requireList(rawValue, "families");
        List<ToolFamily> families = new ArrayList<>(rawFamilies.size());
        for (Object rawFamily : rawFamilies) {
            Map<String, Object> entry = requireMap(rawFamily, "tool family");
            families.add(new ToolFamily(
                    requireString(entry, "id", "tool family"),
                    parseEnum(Domain.class, requireString(entry, "domain", "tool family"), "tool family domain"),
                    copyStringList(entry.get("labels"), "labels for tool family"),
                    requireString(entry, "purpose", "tool family"),
                    copyStringList(entry.get("nameFragments"), "nameFragments for tool family"),
                    copyStringList(entry.get("workflowIds"), "workflowIds for tool family"),
                    copyStringList(entry.get("decoyFamilyIds"), "decoyFamilyIds for tool family"),
                    copyStringList(entry.get("stateVariables"), "stateVariables for tool family"),
                    copyStringList(entry.get("fillerCommandRoles"), "fillerCommandRoles for tool family"),
                    copyStringList(entry.get("querySymptoms"), "querySymptoms for tool family")
            ));
        }
        return List.copyOf(families);
    }

    private List<WorkflowTemplate> parseWorkflows(Object rawValue) {
        List<?> rawWorkflows = requireList(rawValue, "workflows");
        List<WorkflowTemplate> workflows = new ArrayList<>(rawWorkflows.size());
        for (Object rawWorkflow : rawWorkflows) {
            Map<String, Object> entry = requireMap(rawWorkflow, "workflow");
            workflows.add(new WorkflowTemplate(
                    requireString(entry, "id", "workflow"),
                    requireString(entry, "familyId", "workflow"),
                    requireString(entry, "intent", "workflow"),
                    parseWorkflowSteps(entry.get("steps")),
                    toStringMap(entry.get("expectedFinalState"), "expectedFinalState for workflow"),
                    copyStringList(entry.get("queryOutcomes"), "queryOutcomes for workflow")
            ));
        }
        return List.copyOf(workflows);
    }

    private List<WorkflowStepTemplate> parseWorkflowSteps(Object rawValue) {
        List<?> rawSteps = requireList(rawValue, "workflow steps");
        List<WorkflowStepTemplate> steps = new ArrayList<>(rawSteps.size());
        for (Object rawStep : rawSteps) {
            Map<String, Object> entry = requireMap(rawStep, "workflow step");
            steps.add(new WorkflowStepTemplate(
                    requireString(entry, "role", "workflow step"),
                    copyStringList(entry.get("verbSeeds"), "verbSeeds for workflow step"),
                    copyStringList(entry.get("nounSeeds"), "nounSeeds for workflow step"),
                    toStringMap(entry.get("preconditionTemplate"), "preconditionTemplate for workflow step"),
                    toStringMap(entry.get("effectTemplate"), "effectTemplate for workflow step"),
                    optionalString(entry.get("optionProfile"))
            ));
        }
        return List.copyOf(steps);
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
        return Map.copyOf(result);
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

    private List<?> requireList(Object value, String context) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("Expected a list for " + context);
        }
        return list;
    }

    private List<String> copyStringList(Object value, String context) {
        if (!(value instanceof List<?> rawList)) {
            throw new IllegalStateException("Expected a list of strings for " + context);
        }
        List<String> result = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalStateException("Expected non-blank string entries in " + context);
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

    private String optionalString(Object value) {
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw new IllegalStateException("Expected string field for optional value");
        }
        return text;
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String context) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown " + context + ": " + value, e);
        }
    }
}
