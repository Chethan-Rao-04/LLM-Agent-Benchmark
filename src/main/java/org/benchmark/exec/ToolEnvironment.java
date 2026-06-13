package org.benchmark.exec;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mutable runtime state for one tool within one benchmark session.
 *
 * <p>The benchmark keeps one environment per tool. Each environment starts with
 * the tool's declared state keys and is updated as commands run.</p>
 */
public class ToolEnvironment {

    private final Set<String> declaredKeys = new LinkedHashSet<>();
    private final Map<String, String> values = new LinkedHashMap<>();

    /**
     * Creates a tool environment pre-loaded with the tool's declared state keys.
     *
     * @param schemaKeys variable names declared by the tool specification
     */
    public ToolEnvironment(Set<String> schemaKeys) {
        if (schemaKeys != null) {
            declaredKeys.addAll(schemaKeys);
        }
    }

    /**
     * Sets a state variable. Passing {@code null} removes the current value.
     */
    public synchronized void set(String variable, String value) {
        declaredKeys.add(variable);
        if (value == null) {
            values.remove(variable);
            return;
        }
        values.put(variable, value);
    }

    /**
     * Reads a state variable. Returns {@code null} when the variable is unset.
     */
    public synchronized String get(String variable) {
        return values.get(variable);
    }

    /**
     * Returns a point-in-time copy of all declared and runtime state values.
     */
    public synchronized Map<String, String> snapshot() {
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String key : declaredKeys) {
            snapshot.put(key, values.get(key));
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            snapshot.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return snapshot;
    }
}
