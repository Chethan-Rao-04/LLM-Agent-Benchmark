package org.benchmark.model.objects;

import org.benchmark.model.enums.StateScope;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable state precondition for a command.
 *
 * @param scope state map to read
 * @param variable required state variable
 * @param value required state value
 */
public record StateRequirement(StateScope scope, String variable, String value) {

    public StateRequirement(String variable, String value) {
        this(StateScope.TOOL, variable, value);
    }

    public StateRequirement {
        scope = scope == null ? StateScope.TOOL : scope;
        variable = Objects.requireNonNull(variable, "variable must not be null");
        value = Objects.requireNonNull(value, "value must not be null");
    }

    public static List<StateRequirement> fromToolMap(Map<String, String> state) {
        if (state == null || state.isEmpty()) {
            return List.of();
        }
        return state.entrySet().stream()
                .map(entry -> new StateRequirement(StateScope.TOOL, entry.getKey(), entry.getValue()))
                .toList();
    }

    public static Map<String, String> toolMap(List<StateRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return Map.of();
        }
        Map<String, String> toolState = new LinkedHashMap<>();
        for (StateRequirement requirement : requirements) {
            if (requirement.scope() == StateScope.TOOL) {
                toolState.put(requirement.variable(), requirement.value());
            }
        }
        return Map.copyOf(toolState);
    }
}
