package org.benchmark.exec;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable runtime state for one tool within one benchmark session.
 *
 * <p>Each generated tool represents a simulated machine (e.g. a conveyor, a router).
 * This class holds the live, modifiable state variables for that machine —
 * things like {@code system_status}, {@code temperature}, {@code pressure}, etc.</p>
 *
 * <p>A tool environment is created from the tool's schema (the set of declared
 * variable names) and then mutated by command effects during execution.</p>
 */
public class ToolEnvironment {

    /** Shared state key used by every tool for readiness checks. */
    public static final String SYSTEM_STATUS_KEY = "system_status";
    /** State value indicating the machine is ready for commands. */
    public static final String SYSTEM_STATUS_RUNNING = "RUNNING";
    /** State value indicating the machine needs initialization. */
    public static final String SYSTEM_STATUS_SHUTDOWN = "SHUTDOWN";

    private final Set<String> declaredKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, String> values = new ConcurrentHashMap<>();

    /**
     * Creates a tool environment pre-loaded with the tool's declared state schema.
     *
     * @param schemaKeys variable names from the tool specification
     */
    public ToolEnvironment(Set<String> schemaKeys) {
        if (schemaKeys != null) {
            declaredKeys.addAll(schemaKeys);
        }
    }

    /**
     * Sets a state variable. Pass {@code null} to delete.
     */
    public void set(String variable, String value) {
        declaredKeys.add(variable);
        if (value == null) {
            values.remove(variable);
        } else {
            values.put(variable, value);
        }
    }

    /**
     * Reads a state variable. Returns {@code null} if unset.
     */
    public String get(String variable) {
        return values.get(variable);
    }

    /**
     * Returns a point-in-time copy of all state — both declared and runtime variables.
     */
    public Map<String, String> snapshot() {
        Map<String, String> snap = new LinkedHashMap<>();
        for (String k : declaredKeys) {
            snap.put(k, values.get(k));
        }
        for (Map.Entry<String, String> e : values.entrySet()) {
            snap.putIfAbsent(e.getKey(), e.getValue());
        }
        return snap;
    }
}
